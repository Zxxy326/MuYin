package tv.music.player.core.update

import android.Manifest
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import tv.music.player.BuildConfig
import tv.music.player.MainActivity
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.TimeUnit

data class UpdateInfo(
    val versionCode: Long,
    val versionName: String,
    val apkUrl: String,
    val sha256: String,
    val sizeBytes: Long,
    val notes: String,
)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class UpToDate(val currentVersion: String) : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data class Downloading(
        val info: UpdateInfo,
        val bytesRead: Long,
        val totalBytes: Long,
    ) : UpdateState {
        val progress: Float
            get() = if (totalBytes > 0L) (bytesRead.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f
    }

    data class Downloaded(
        val info: UpdateInfo,
        val file: File,
        val sizeBytes: Long,
    ) : UpdateState

    data class Failed(val message: String, val info: UpdateInfo? = null) : UpdateState
}

sealed interface InstallOutcome {
    data object Launched : InstallOutcome
    data object NeedPermission : InstallOutcome
    data class Failed(val message: String) : InstallOutcome
}

/**
 * 从 update.json 检查新版本，下载并校验 APK，再交给系统安装器覆盖安装。
 * 发布清单地址见 BuildConfig.UPDATE_MANIFEST_URL。
 */
class UpdateStore(context: Context) {
    private val appContext = context.applicationContext
    private val pm = appContext.packageManager
    private val dir = File(appContext.filesDir, "updates").also { it.mkdirs() }
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefs = appContext.getSharedPreferences("update_prefs", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()
    private val _availableUpdate = MutableStateFlow<UpdateInfo?>(null)
    val availableUpdate: StateFlow<UpdateInfo?> = _availableUpdate.asStateFlow()

    val currentVersionName: String = BuildConfig.VERSION_NAME
    val currentVersionCode: Long = BuildConfig.VERSION_CODE.toLong()

    private var job: Job? = null

    init {
        cleanStaleFiles()
        readPendingUpdate()?.let { info ->
            _availableUpdate.value = info
            _state.value = UpdateState.Available(info)
        }
    }

    fun check() {
        if (job?.isActive == true) return
        _state.value = UpdateState.Checking
        job = scope.launch {
            try {
                val info = fetchManifest()
                resolveManifest(info)
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                Log.w(TAG, "update check failed", t)
                _state.value = UpdateState.Failed("检查更新失败：${t.message ?: "网络不可用"}")
            }
        }
    }

    /** 启动时静默检查，有新版本才通知，不把设置页状态改成失败。 */
    fun autoCheckOnLaunch() {
        if (job?.isActive == true) return
        val now = System.currentTimeMillis()
        if (now - prefs.getLong(KEY_LAST_AUTO_CHECK, 0L) < AUTO_CHECK_INTERVAL_MS) return
        prefs.edit().putLong(KEY_LAST_AUTO_CHECK, now).apply()
        job = scope.launch {
            try {
                val info = fetchManifest()
                if (info.versionCode > currentVersionCode) {
                    _availableUpdate.value = info
                    savePendingUpdate(info)
                    if (_state.value is UpdateState.Idle) {
                        _state.value = UpdateState.Available(info)
                    }
                    notifyAvailable(info)
                } else {
                    _availableUpdate.value = null
                    clearPendingUpdate()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                Log.i(TAG, "auto update check skipped: ${t.message ?: "network unavailable"}")
            }
        }
    }

    private fun savePendingUpdate(info: UpdateInfo) {
        prefs.edit()
            .putString(
                KEY_PENDING_UPDATE,
                JSONObject()
                    .put("versionCode", info.versionCode)
                    .put("versionName", info.versionName)
                    .put("apkUrl", info.apkUrl)
                    .put("sha256", info.sha256)
                    .put("sizeBytes", info.sizeBytes)
                    .put("notes", info.notes)
                    .toString(),
            )
            .apply()
    }

    private fun readPendingUpdate(): UpdateInfo? = runCatching {
        val raw = prefs.getString(KEY_PENDING_UPDATE, null) ?: return@runCatching null
        val json = JSONObject(raw)
        val info = UpdateInfo(
            versionCode = json.optLong("versionCode", 0L),
            versionName = json.optString("versionName"),
            apkUrl = json.optString("apkUrl"),
            sha256 = json.optString("sha256"),
            sizeBytes = json.optLong("sizeBytes", 0L),
            notes = json.optString("notes"),
        )
        if (info.versionCode > currentVersionCode) info else null
    }.getOrNull()

    private fun clearPendingUpdate() {
        prefs.edit().remove(KEY_PENDING_UPDATE).apply()
    }

    private fun notifyAvailable(info: UpdateInfo) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return
        if (prefs.getLong(KEY_LAST_NOTIFIED, 0L) >= info.versionCode) return
        val manager = appContext.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "应用更新",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply { description = "发现幕音新版本时提醒" }
            )
        }
        val intent = Intent(appContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pending = PendingIntent.getActivity(
            appContext,
            NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(appContext, CHANNEL_ID)
        } else {
            Notification.Builder(appContext)
        }
        manager.notify(
            NOTIFICATION_ID,
            builder
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("幕音有新版本 ${info.versionName}")
                .setContentText("打开应用 → 设置 → 更新，下载并安装")
                .setContentIntent(pending)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .build()
        )
        prefs.edit().putLong(KEY_LAST_NOTIFIED, info.versionCode).apply()
    }

    fun download() {
        if (job?.isActive == true) return
        val info = when (val current = _state.value) {
            is UpdateState.Available -> current.info
            is UpdateState.Failed -> current.info
            else -> null
        } ?: return
        val urls = downloadCandidates(info)
        if (urls.isEmpty()) {
            _state.value = UpdateState.Failed("安装包地址无效或不是 HTTPS", info)
            return
        }
        job = scope.launch { downloadInternal(info, urls) }
    }

    fun cancelDownload() {
        val downloading = _state.value as? UpdateState.Downloading ?: return
        _state.value = UpdateState.Available(downloading.info)
        job?.cancel()
    }

    fun install(activity: Activity): InstallOutcome {
        val downloaded = _state.value as? UpdateState.Downloaded
            ?: return InstallOutcome.Failed("请先下载更新包")
        val file = downloaded.file
        if (!file.isFile || file.length() <= 0L) {
            _state.value = UpdateState.Available(downloaded.info)
            return InstallOutcome.Failed("更新包不存在，请重新下载")
        }
        val problem = validateApk(file)
        if (problem != null) {
            file.delete()
            _state.value = UpdateState.Failed(problem, downloaded.info)
            return InstallOutcome.Failed(problem)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !pm.canRequestPackageInstalls()) {
            return InstallOutcome.NeedPermission
        }
        return try {
            val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.updates", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                clipData = ClipData.newRawUri("update", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            activity.startActivity(intent)
            InstallOutcome.Launched
        } catch (e: ActivityNotFoundException) {
            InstallOutcome.Failed("系统没有可用的安装器")
        } catch (t: Throwable) {
            InstallOutcome.Failed(t.message ?: "无法启动安装器")
        }
    }

    fun openInstallSettings(activity: Activity) {
        val appSettings = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${activity.packageName}"),
        )
        runCatching { activity.startActivity(appSettings) }
            .onFailure {
                runCatching { activity.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS)) }
                    .onFailure { Log.w(TAG, "no install settings screen", it) }
            }
    }

    private fun resolveManifest(info: UpdateInfo) {
        if (info.versionCode <= currentVersionCode) {
            _availableUpdate.value = null
            clearPendingUpdate()
            _state.value = UpdateState.UpToDate(currentVersionName)
            return
        }
        if (!isAllowedUrl(info.apkUrl)) {
            _state.value = UpdateState.Failed(
                "新版本 ${info.versionName} 的安装包地址无效（正式版必须使用 HTTPS）",
                info,
            )
            return
        }
        if (!SHA256_PATTERN.matches(info.sha256)) {
            _state.value = UpdateState.Failed(
                "更新清单缺少有效的 SHA-256，已拒绝下载",
                info,
            )
            return
        }
        val existing = File(dir, fileName(info))
        if (existing.isFile && existing.length() > 0L) {
            if (info.sizeBytes <= 0L || existing.length() == info.sizeBytes) {
                _state.value = UpdateState.Downloaded(info, existing, existing.length())
                return
            }
            existing.delete()
        }
        _availableUpdate.value = info
        savePendingUpdate(info)
        _state.value = UpdateState.Available(info)
        notifyAvailable(info)
    }

    private fun fetchManifest(): UpdateInfo {
        val urls = manifestUrls()
        if (urls.isEmpty()) error("更新地址未配置或不是 HTTPS")
        val errors = mutableListOf<String>()
        var best: UpdateInfo? = null
        for (raw in urls) {
            val base = raw.toHttpUrlOrNull() ?: continue
            val requestUrl = base.newBuilder()
                .addQueryParameter("t", (System.currentTimeMillis() / 60_000L).toString())
                .build()
            try {
                val request = Request.Builder()
                    .url(requestUrl)
                    .header("Accept", "application/json")
                    .header("Cache-Control", "no-cache")
                    .header("User-Agent", UA)
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        errors += "${base.host}: HTTP ${response.code}"
                        return@use
                    }
                    val info = runCatching { parseManifest(response.body.string()) }
                        .onFailure { errors += "${base.host}: 清单格式错误" }
                        .getOrNull()
                    if (info != null && (best == null || info.versionCode > best!!.versionCode)) {
                        best = info
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                errors += "${base.host}: ${t.message ?: "请求失败"}"
            }
        }
        return best ?: throw IllegalStateException(
            errors.joinToString("；").ifBlank { "没有可用的更新地址" }
        )
    }

    private fun parseManifest(text: String): UpdateInfo {
        val root = JSONObject(text)
        val code = root.optLong("versionCode", 0L)
        val name = root.optString("versionName").trim()
        require(code > 0L && name.isNotBlank()) { "更新清单缺少 versionCode/versionName" }
        return UpdateInfo(
            versionCode = code,
            versionName = name,
            apkUrl = root.optString("apkUrl").trim(),
            sha256 = root.optString("sha256").trim().lowercase(Locale.ROOT),
            sizeBytes = root.optLong("sizeBytes", 0L).coerceAtLeast(0L),
            notes = root.optString("notes").trim(),
        )
    }

    private suspend fun downloadInternal(info: UpdateInfo, urls: List<HttpUrl>) {
        val target = File(dir, fileName(info))
        val part = File(dir, "${target.nameWithoutExtension}.part.apk")
        val errors = mutableListOf<String>()
        for ((index, url) in urls.withIndex()) {
            _state.value = UpdateState.Downloading(info, 0L, info.sizeBytes)
            try {
                downloadFrom(info, url, part)
                target.delete()
                if (!part.renameTo(target)) {
                    part.copyTo(target, overwrite = true)
                    part.delete()
                }
                val problem = validateApk(target)
                if (problem != null) {
                    target.delete()
                    fail(problem, info)
                    return
                }
                keepOnly(target)
                _availableUpdate.value = null
                clearPendingUpdate()
                _state.value = UpdateState.Downloaded(info, target, target.length())
                Log.i(TAG, "update downloaded ${info.versionName} from ${url.host} (${target.length()} bytes)")
                return
            } catch (e: CancellationException) {
                part.delete()
                throw e
            } catch (t: Throwable) {
                part.delete()
                val host = url.host
                errors += "$host: ${t.message ?: "网络错误"}"
                Log.w(TAG, "update mirror ${index + 1}/${urls.size} failed: $host: ${t.message}")
            }
        }
        fail(
            "下载失败：${errors.distinct().joinToString("；").ifBlank { "所有下载通道均不可用" }}",
            info,
        )
    }

    private suspend fun downloadFrom(info: UpdateInfo, url: HttpUrl, part: File) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", UA)
            .header("Accept", "application/octet-stream,*/*")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}")
            val body = response.body
            val total = body.contentLength().takeIf { it > 0L } ?: info.sizeBytes
            val digest = MessageDigest.getInstance("SHA-256")
            var read = 0L
            var lastNotify = 0L
            part.delete()
            body.byteStream().use { input ->
                part.outputStream().use { output ->
                    val buffer = ByteArray(128 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count <= 0) break
                        output.write(buffer, 0, count)
                        digest.update(buffer, 0, count)
                        read += count
                        val now = System.currentTimeMillis()
                        if (now - lastNotify >= 250L) {
                            lastNotify = now
                            _state.value = UpdateState.Downloading(info, read, total)
                        }
                    }
                }
            }
            if (read < MIN_APK_BYTES) {
                throw IllegalStateException("安装包内容过小，下载可能被拦截")
            }
            if (info.sizeBytes > 0L && read != info.sizeBytes) {
                throw IllegalStateException("安装包大小与清单不一致")
            }
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            if (!actual.equals(info.sha256, ignoreCase = true)) {
                throw IllegalStateException("SHA-256 校验失败，安装包可能损坏或被篡改")
            }
        }
    }

    private fun downloadCandidates(info: UpdateInfo): List<HttpUrl> {
        val raw = info.apkUrl.trim()
        val github = MIRROR_PREFIXES.firstOrNull { raw.startsWith(it) }
            ?.let { raw.removePrefix(it) }
            ?.takeIf { it.startsWith("https://github.com/") }
            ?: raw.takeIf { it.startsWith("https://github.com/") }
        val urls = if (github != null) {
            MIRROR_PREFIXES.map { it + github } + github
        } else {
            listOf(raw)
        }
        return urls.filter { isAllowedUrl(it) }
            .mapNotNull { it.toHttpUrlOrNull() }
            .distinct()
    }

    @Suppress("DEPRECATION")
    private fun validateApk(file: File): String? {
        val flags = PackageManager.GET_SIGNING_CERTIFICATES
        val archive = pm.getPackageArchiveInfo(file.absolutePath, flags)
            ?: return "更新包无法解析"
        if (archive.packageName != appContext.packageName) {
            return "更新包包名不匹配（${archive.packageName}）"
        }
        if (archive.longVersionCode <= currentVersionCode) {
            return "更新包版本不高于当前版本"
        }
        val archiveSigners = archive.signingInfo?.apkContentsSigners
        if (archiveSigners.isNullOrEmpty()) return "更新包未签名"
        val selfSigners = pm.getPackageInfo(appContext.packageName, flags)
            .signingInfo?.apkContentsSigners
        if (selfSigners.isNullOrEmpty()) return "无法读取当前应用签名"
        val matches = archiveSigners.any { candidate ->
            selfSigners.any { own -> candidate.toByteArray().contentEquals(own.toByteArray()) }
        }
        return if (matches) null else "更新包签名与当前应用不一致，无法覆盖安装"
    }

    private fun fail(message: String, info: UpdateInfo) {
        _state.value = UpdateState.Failed(message, info)
    }

    private fun keepOnly(file: File) {
        dir.listFiles { item -> item.isFile && item.name.endsWith(".apk") }?.forEach { item ->
            if (item != file) item.delete()
        }
    }

    private fun cleanStaleFiles() {
        dir.listFiles { item -> item.isFile && item.name.contains(".part") }?.forEach { it.delete() }
        dir.listFiles { item -> item.isFile && item.name.startsWith(FILE_PREFIX) && item.name.endsWith(".apk") }
            ?.forEach { file ->
                val code = file.name.removePrefix(FILE_PREFIX).removeSuffix(".apk").toLongOrNull()
                if (code != null && code <= currentVersionCode) file.delete()
            }
    }

    private fun manifestUrls(): List<String> {
        val configured = BuildConfig.UPDATE_MANIFEST_URL.trim()
        if (configured.isBlank()) return emptyList()
        val urls = linkedSetOf(configured)
        if (configured.contains("Zxxy326/TV-Music")) {
            urls += "https://raw.githubusercontent.com/Zxxy326/TV-Music/main/update.json"
            urls += "https://gcore.jsdelivr.net/gh/Zxxy326/TV-Music@main/update.json"
            urls += "https://fastly.jsdelivr.net/gh/Zxxy326/TV-Music@main/update.json"
            urls += "https://testingcf.jsdelivr.net/gh/Zxxy326/TV-Music@main/update.json"
            urls += "https://cdn.jsdelivr.net/gh/Zxxy326/TV-Music@main/update.json"
        }
        return urls.filter { isAllowedUrl(it) }
    }

    private fun isAllowedUrl(raw: String): Boolean {
        val url = raw.toHttpUrlOrNull() ?: return false
        return when (url.scheme) {
            "https" -> true
            "http" -> BuildConfig.DEBUG
            else -> false
        }
    }

    private fun fileName(info: UpdateInfo): String = "$FILE_PREFIX${info.versionCode}.apk"

    private companion object {
        const val TAG = "TvMusicUpdate"
        const val FILE_PREFIX = "tv-music-"
        const val MIN_APK_BYTES = 1024L * 1024L
        const val CHANNEL_ID = "tv_music_updates"
        const val NOTIFICATION_ID = 7301
        const val KEY_LAST_AUTO_CHECK = "last_auto_check"
        const val KEY_PENDING_UPDATE = "pending_update"
        const val KEY_LAST_NOTIFIED = "last_notified_version"
        const val AUTO_CHECK_INTERVAL_MS = 30L * 60L * 1000L
        val MIRROR_PREFIXES = listOf(
            "https://gh-proxy.com/",
            "https://ghproxy.net/",
        )
        val UA = "TVMusic-Updater/1.0 (Android ${Build.VERSION.RELEASE})"
        val SHA256_PATTERN = Regex("^[a-f0-9]{64}$")
    }
}
