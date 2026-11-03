package tv.music.player.core.source

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import tv.music.player.core.lx.LxFileStore
import tv.music.player.core.lx.LxJsEngine
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.charset.Charset
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import org.apache.commons.compress.archivers.zip.ZipFile as ApacheZipFile

data class MarketRelease(
    val tag: String,
    val name: String,
    val publishedAt: String,
    val notes: String,
    val zipName: String,
    val zipSize: Long,
    val zipUrl: String,
    val zipSha256: String,
    val htmlUrl: String,
) {
    val displayName: String get() = name.ifBlank { tag }
    val displayDate: String get() = publishedAt.take(10)
    val displayNotes: String
        get() = notes
            .replace(Regex("<[^>]+>"), " ")
            .replace(Regex("\\s+"), " ")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .trim()
}

data class MarketEntry(
    val key: String,
    val fileName: String,
    val folder: String,
    val sizeBytes: Long,
    val scriptName: String,
    val version: String,
    val author: String,
    val installed: Boolean,
    val bytes: ByteArray,
) {
    val displayName: String get() = scriptName.ifBlank { fileName.substringBeforeLast('.') }
}

sealed interface ReleaseState {
    data object Idle : ReleaseState
    data class Downloading(val progress: Float) : ReleaseState
    data object Inspecting : ReleaseState
    data class Ready(val entries: List<MarketEntry>) : ReleaseState
    data class Error(val message: String) : ReleaseState
}

data class MarketUiState(
    val loading: Boolean = false,
    val releases: List<MarketRelease> = emptyList(),
    val installedNames: Set<String> = emptySet(),
    val error: String? = null,
    val fromCache: Boolean = false,
    val details: Map<String, ReleaseState> = emptyMap(),
    val installingKey: String? = null,
    val message: String? = null,
)

/**
 * 像素音源市场移植：从 guoyue2010/lxmusic- 的 GitHub Release 拉取 zip，
 * 经镜像/官方下载后按 GitHub 提供的 SHA-256 校验，解压列出脚本供用户逐个安装。
 */
class SourceMarketStore(
    context: Context,
    private val fileStore: LxFileStore,
    private val engine: LxJsEngine,
) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("source_market", Context.MODE_PRIVATE)
    private val cacheDir = File(appContext.cacheDir, "source_market").also { it.mkdirs() }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val _state = MutableStateFlow(
        MarketUiState(
            releases = readCachedReleases().orEmpty(),
            installedNames = installedNames(),
        )
    )
    val state: StateFlow<MarketUiState> = _state.asStateFlow()

    private var loading = false

    fun load() {
        if (loading || _state.value.installingKey != null) return
        loading = true
        _state.value = _state.value.copy(loading = true, error = null)
        scope.launch {
            try {
                val releases = fetchReleases()
                writeCachedReleases(releases)
                _state.value = _state.value.copy(
                    loading = false,
                    releases = releases,
                    error = null,
                    fromCache = false,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                Log.w(TAG, "market load failed", t)
                val cached = readCachedReleases().orEmpty()
                _state.value = _state.value.copy(
                    loading = false,
                    releases = if (cached.isNotEmpty()) cached else _state.value.releases,
                    error = "读取音源市场失败：${friendly(t)}",
                    fromCache = cached.isNotEmpty(),
                )
            } finally {
                loading = false
            }
        }
    }

    fun openRelease(tag: String) {
        val release = _state.value.releases.firstOrNull { it.tag == tag } ?: return
        when (val current = _state.value.details[tag]) {
            is ReleaseState.Downloading, is ReleaseState.Inspecting, is ReleaseState.Ready -> return
            else -> Unit
        }
        setDetail(tag, ReleaseState.Downloading(0f))
        scope.launch {
            val zip = cacheDir.resolve("${release.tag}.zip")
            try {
                if (!zip.isFile || zip.length() < 4) {
                    downloadZip(release, zip) { progress ->
                        setDetail(tag, ReleaseState.Downloading(progress))
                    }
                }
                setDetail(tag, ReleaseState.Inspecting)
                val entries = inspectZip(zip)
                if (entries.isEmpty()) {
                    setDetail(tag, ReleaseState.Error("压缩包中没有找到 .js 音源脚本"))
                } else {
                    setDetail(tag, ReleaseState.Ready(entries))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                Log.w(TAG, "open release ${release.tag} failed", t)
                zip.delete()
                setDetail(tag, ReleaseState.Error(friendly(t)))
            }
        }
    }

    fun install(tag: String, entryKey: String) {
        if (_state.value.installingKey != null) return
        val detail = _state.value.details[tag] as? ReleaseState.Ready ?: return
        val entry = detail.entries.firstOrNull { it.key == entryKey } ?: return
        if (entry.installed) {
            _state.value = _state.value.copy(message = "「${entry.displayName}」已安装")
            return
        }
        _state.value = _state.value.copy(installingKey = entryKey, message = null)
        scope.launch {
            try {
                val written = fileStore.writeBytes(entry.bytes, entry.fileName)
                if (written.isNullOrBlank()) {
                    _state.value = _state.value.copy(
                        installingKey = null,
                        message = "安装失败：无法写入音源目录",
                    )
                    return@launch
                }
                val reloaded = engine.reload()
                val names = installedNames()
                val updated = detail.entries.map {
                    if (it.key == entryKey) it.copy(installed = true) else it
                }
                _state.value = _state.value.copy(
                    installingKey = null,
                    installedNames = names,
                    details = _state.value.details + (tag to ReleaseState.Ready(updated)),
                    message = if (reloaded) {
                        "已安装并重载「${entry.displayName}」"
                    } else {
                        "已写入「${entry.displayName}」，但引擎重载失败，请到音源页检查"
                    },
                )
                Log.i(TAG, "installed $tag/${entry.fileName} -> $written reloaded=$reloaded")
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                Log.w(TAG, "install failed", t)
                _state.value = _state.value.copy(
                    installingKey = null,
                    message = "安装失败：${t.message ?: "未知错误"}",
                )
            }
        }
    }

    fun clearMessage() {
        _state.value = _state.value.copy(message = null)
    }

    private fun setDetail(tag: String, value: ReleaseState) {
        _state.value = _state.value.copy(details = _state.value.details + (tag to value))
    }

    private fun installedNames(): Set<String> =
        fileStore.listFiles().map { it.name }.toSet()

    private suspend fun fetchReleases(): List<MarketRelease> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/releases?per_page=$RELEASE_LIMIT")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", UA)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val message = when (response.code) {
                    403, 429 -> "GitHub 接口限流（匿名 60 次/小时），请稍后重试"
                    404 -> "音源仓库不存在或已删除"
                    else -> "GitHub 返回 HTTP ${response.code}"
                }
                throw IOException(message)
            }
            val body = response.body.string()
            val array = JSONArray(body)
            val result = mutableListOf<MarketRelease>()
            for (i in 0 until array.length()) {
                val release = parseRelease(array.optJSONObject(i) ?: continue) ?: continue
                result += release
            }
            if (result.isEmpty()) throw IOException("仓库里没有可安装的 zip 发布")
            result
        }
    }

    private fun parseRelease(obj: JSONObject): MarketRelease? {
        val tag = obj.optString("tag_name").trim()
        val assets = obj.optJSONArray("assets") ?: return null
        var zip: JSONObject? = null
        for (i in 0 until assets.length()) {
            val asset = assets.optJSONObject(i) ?: continue
            if (asset.optString("name").lowercase().endsWith(".zip")) {
                zip = asset
                break
            }
        }
        val asset = zip ?: return null
        val url = asset.optString("browser_download_url").trim()
        if (tag.isBlank() || !url.startsWith("https://")) return null
        val digest = asset.optString("digest").trim()
        return MarketRelease(
            tag = tag,
            name = obj.optString("name").trim(),
            publishedAt = obj.optString("published_at").trim(),
            notes = obj.optString("body").trim(),
            zipName = asset.optString("name").trim(),
            zipSize = asset.optLong("size", 0L),
            zipUrl = url,
            zipSha256 = digest.removePrefix("sha256:").lowercase(),
            htmlUrl = obj.optString("html_url").trim(),
        )
    }

    private suspend fun downloadZip(
        release: MarketRelease,
        target: File,
        onProgress: (Float) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val candidates = (MIRROR_PREFIXES.map { it + release.zipUrl } + release.zipUrl)
            .distinct()
            .filter { it.startsWith("https://") }
        val errors = mutableListOf<String>()
        for (url in candidates) {
            val temp = File(target.parentFile, target.name + ".part")
            var lastNotify = 0L
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", UA)
                    .header("Accept", "application/octet-stream,*/*")
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                    val body = response.body
                    val total = body.contentLength().takeIf { it > 0 } ?: release.zipSize
                    if (total > MAX_ZIP_BYTES) throw IOException("压缩包超过 ${MAX_ZIP_BYTES / 1024 / 1024} MB")
                    var read = 0L
                    val digest = MessageDigest.getInstance("SHA-256")
                    temp.delete()
                    body.byteStream().use { input ->
                        temp.outputStream().use { output ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                val count = input.read(buffer)
                                if (count <= 0) break
                                output.write(buffer, 0, count)
                                digest.update(buffer, 0, count)
                                read += count
                                if (read > MAX_ZIP_BYTES) throw IOException("压缩包超过 ${MAX_ZIP_BYTES / 1024 / 1024} MB")
                                val now = System.currentTimeMillis()
                                if (now - lastNotify >= 150L) {
                                    lastNotify = now
                                    if (total > 0) onProgress((read.toFloat() / total).coerceIn(0f, 1f))
                                }
                            }
                        }
                    }
                    if (read < 4) throw IOException("下载内容为空")
                    if (!zipMagicOk(temp)) throw IOException("镜像返回的不是有效 zip")
                    val actual = digest.digest().joinToString("") { "%02x".format(it) }
                    if (release.zipSha256.isNotBlank() &&
                        !actual.equals(release.zipSha256, ignoreCase = true)
                    ) {
                        throw IOException("SHA-256 校验失败")
                    }
                    if (release.zipSize > 0 && read != release.zipSize) {
                        throw IOException("文件大小与 GitHub 清单不一致")
                    }
                    target.delete()
                    if (!temp.renameTo(target)) {
                        temp.copyTo(target, overwrite = true)
                        temp.delete()
                    }
                    onProgress(1f)
                    return@withContext
                }
            } catch (e: CancellationException) {
                temp.delete()
                throw e
            } catch (t: Throwable) {
                temp.delete()
                errors += "${url.substringAfter("//").substringBefore('/')}: ${t.message ?: "失败"}"
                Log.w(TAG, "download $url failed: ${t.message}")
            }
        }
        throw IOException(errors.distinct().joinToString("；").ifBlank { "下载失败" })
    }

    private suspend fun inspectZip(zip: File): List<MarketEntry> = withContext(Dispatchers.IO) {
        if (!zipMagicOk(zip)) throw IOException("缓存文件不是有效 zip")
        val utf8 = runCatching { readZip(zip, "UTF-8") }.getOrNull()
        val parsed = if (utf8 != null && utf8.isNotEmpty() && utf8.none { it.key.contains('�') }) {
            utf8
        } else {
            runCatching { readZip(zip, "GBK") }.getOrNull() ?: utf8.orEmpty()
        }
        if (parsed.isEmpty()) throw IOException("压缩包中没有可读取的 .js")
        val installed = installedNames()
        parsed.map { raw ->
            val info = runCatching { engine.scriptInfoFromBytes(raw.bytes, raw.fileName) }
                .getOrDefault(tv.music.player.core.lx.LxScriptInfo(fileName = raw.fileName, name = raw.fileName))
            MarketEntry(
                key = raw.key,
                fileName = raw.fileName,
                folder = raw.folder,
                sizeBytes = raw.bytes.size.toLong(),
                scriptName = info.name.ifBlank { raw.fileName.substringBeforeLast('.') },
                version = info.version,
                author = info.author,
                installed = raw.fileName in installed,
                bytes = raw.bytes,
            )
        }
    }

    private fun readZip(zip: File, encoding: String): List<ZipEntryData> {
        val result = mutableListOf<ZipEntryData>()
        val seen = mutableSetOf<String>()
        var totalBytes = 0L
        ApacheZipFile(zip, encoding).use { archive ->
            val entries = archive.entries
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (entry.isDirectory || !entry.name.endsWith(".js", ignoreCase = true)) continue
                val name = entry.name.replace('\\', '/')
                val base = name.substringAfterLast('/').trim()
                if (base.isBlank() || base == "." || base == "..") continue
                if (entry.size > MAX_ENTRY_BYTES) continue
                val folder = name.substringBeforeLast('/', "").substringAfterLast('/')
                val out = ByteArrayOutputStream()
                archive.getInputStream(entry).use { input ->
                    val buffer = ByteArray(16 * 1024)
                    var entryRead = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count <= 0) break
                        out.write(buffer, 0, count)
                        entryRead += count
                        if (entryRead > MAX_ENTRY_BYTES) break
                    }
                }
                val bytes = out.toByteArray()
                if (bytes.isEmpty() || bytes.size > MAX_ENTRY_BYTES) continue
                totalBytes += bytes.size
                if (totalBytes > MAX_TOTAL_BYTES || result.size >= MAX_ENTRIES) break
                if (!seen.add(base.lowercase())) continue
                result += ZipEntryData(name, base, folder, bytes)
            }
        }
        return result
    }

    private data class ZipEntryData(
        val key: String,
        val fileName: String,
        val folder: String,
        val bytes: ByteArray,
    )

    private fun zipMagicOk(file: File): Boolean {
        if (!file.isFile || file.length() < 4) return false
        return runCatching {
            file.inputStream().use { input ->
                val header = ByteArray(4)
                input.read(header) == 4 &&
                    header[0] == 'P'.code.toByte() && header[1] == 'K'.code.toByte() &&
                    header[2] == 3.toByte() && header[3] == 4.toByte()
            }
        }.getOrDefault(false)
    }

    private fun readCachedReleases(): List<MarketRelease>? = runCatching {
        val raw = prefs.getString(KEY_RELEASES, null) ?: return@runCatching null
        val array = JSONArray(raw)
        (0 until array.length()).mapNotNull { parseRelease(array.optJSONObject(it) ?: return@mapNotNull null) }
    }.getOrNull()

    private fun writeCachedReleases(releases: List<MarketRelease>) {
        runCatching {
            val array = JSONArray()
            releases.forEach { release ->
                array.put(
                    JSONObject()
                        .put("tag_name", release.tag)
                        .put("name", release.name)
                        .put("published_at", release.publishedAt)
                        .put("body", release.notes)
                        .put("html_url", release.htmlUrl)
                        .put(
                            "assets",
                            JSONArray().put(
                                JSONObject()
                                    .put("name", release.zipName)
                                    .put("size", release.zipSize)
                                    .put("browser_download_url", release.zipUrl)
                                    .put("digest", if (release.zipSha256.isBlank()) "" else "sha256:${release.zipSha256}")
                            )
                        )
                )
            }
            prefs.edit().putString(KEY_RELEASES, array.toString()).apply()
        }.onFailure { Log.w(TAG, "cache releases failed", it) }
    }

    private fun friendly(t: Throwable): String {
        val message = t.message.orEmpty()
        return when {
            message.contains("timed out", true) || message.contains("timeout", true) ->
                "连接超时，请检查网络或代理"
            message.contains("Unable to resolve host", true) ||
                message.contains("Failed to connect", true) ->
                "无法连接 GitHub，请检查网络"
            message.isBlank() -> t.javaClass.simpleName
            else -> message.take(100)
        }
    }

    private companion object {
        const val TAG = "TvMusicMarket"
        const val REPO_OWNER = "guoyue2010"
        const val REPO_NAME = "lxmusic-"
        const val RELEASE_LIMIT = 30
        const val KEY_RELEASES = "release_cache_v2"
        const val MAX_ENTRY_BYTES = 8L * 1024L * 1024L
        const val MAX_TOTAL_BYTES = 64L * 1024L * 1024L
        const val MAX_ZIP_BYTES = 64L * 1024L * 1024L
        const val MAX_ENTRIES = 200
        val UA = "TVMusic-Market/2.0 (Android ${android.os.Build.VERSION.RELEASE})"
        val MIRROR_PREFIXES = listOf(
            "https://ghproxy.net/",
            "https://gh-proxy.com/",
        )
    }
}
