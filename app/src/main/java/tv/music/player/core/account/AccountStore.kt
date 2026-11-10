package tv.music.player.core.account

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import tv.music.player.core.playback.DemoTrack
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

sealed interface QrLoginState {
    data object Idle : QrLoginState
    data object Loading : QrLoginState
    data class Waiting(val url: String, val status: String) : QrLoginState
    data class Success(val nickname: String) : QrLoginState
    data class Failed(val message: String) : QrLoginState
}

data class AccountUiState(
    val netease: AccountProfile? = null,
    val qq: AccountProfile? = null,
    val playlists: List<AccountPlaylist> = emptyList(),
    val recommended: List<AccountPlaylist> = emptyList(),
    val square: List<AccountPlaylist> = emptyList(),
    val squareCat: String = "华语",
    val squareLoading: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null,
    val qr: QrLoginState = QrLoginState.Idle,
    val message: String? = null,
    val loadingPlaylistId: String? = null,
    val playlistTracks: Map<String, List<DemoTrack>> = emptyMap(),
    val playlistError: String? = null,
)

/**
 * 账号与歌单：Cookie 使用 AndroidKeyStore AES-GCM 加密保存；
 * 网易云支持二维码登录，QQ 支持 Cookie 文件导入。Cookie 不写日志。
 */
class AccountStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("account_store", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val neteaseApi = NeteaseApi()
    private val qqApi = QqPlaylistApi()

    private val neteaseCookies = mutableMapOf<String, String>()
    private val qqCookies = mutableMapOf<String, String>()
    private var qrJob: Job? = null

    private val _state = MutableStateFlow(AccountUiState())
    val state: StateFlow<AccountUiState> = _state.asStateFlow()

    init {
        restore()
        if (neteaseCookies.isNotEmpty() || qqCookies.isNotEmpty()) refresh()
        refreshRecommended()
    }

    fun refresh() {
        if (_state.value.loading) return
        val hasNetease = neteaseCookies.isNotEmpty()
        val hasQq = qqCookies.isNotEmpty()
        if (!hasNetease && !hasQq) return
        _state.value = _state.value.copy(loading = true, error = null)
        scope.launch {
            val errors = mutableListOf<String>()
            var netease: AccountProfile? = _state.value.netease
            var qq: AccountProfile? = _state.value.qq
            var playlists = _state.value.playlists
            if (hasNetease) {
                val result = runCatching {
                    val profile = neteaseApi.fetchProfile(neteaseCookies)
                    val list = neteaseApi.fetchPlaylists(neteaseCookies, profile.uid)
                    profile to list
                }
                result.onSuccess { (profile, list) ->
                    netease = profile
                    playlists = playlists.filterNot { it.provider == NeteaseApi.PROVIDER } + list
                    saveProfile(profile)
                }.onFailure {
                    Log.w(TAG, "refresh netease failed: ${it.message}")
                    errors += "网易云：${friendly(it)}"
                }
            }
            if (hasQq) {
                val result = runCatching { qqApi.fetchAccount(qqCookies) }
                result.onSuccess { account ->
                    qq = account.profile
                    playlists = playlists.filterNot { it.provider == QqPlaylistApi.PROVIDER } + account.playlists
                    saveProfile(account.profile)
                }.onFailure {
                    Log.w(TAG, "refresh qq failed: ${it.message}")
                    qq = qq ?: AccountProfile(
                        provider = QqPlaylistApi.PROVIDER,
                        uid = qqCookies["qqmusic_uin"].orEmpty().ifBlank { qqCookies["uin"].orEmpty() },
                        nickname = "QQ 音乐已导入 Cookie",
                    )
                    errors += "QQ：${friendly(it)}"
                }
            }
            savePlaylists(playlists)
            _state.value = _state.value.copy(
                loading = false,
                netease = netease,
                qq = qq,
                playlists = playlists,
                error = errors.takeIf { it.isNotEmpty() }?.joinToString("；"),
            )
        }
    }

    fun startNeteaseQr() {
        qrJob?.cancel()
        _state.value = _state.value.copy(qr = QrLoginState.Loading, message = null)
        qrJob = scope.launch {
            try {
                val session = neteaseApi.createQr()
                _state.value = _state.value.copy(
                    qr = QrLoginState.Waiting(session.url, "等待扫码"),
                )
                val deadline = System.currentTimeMillis() + QR_TIMEOUT_MS
                while (isActive && System.currentTimeMillis() < deadline) {
                    delay(QR_POLL_MS)
                    when (val result = neteaseApi.pollQr(session.key)) {
                        QrPollResult.Waiting -> Unit
                        QrPollResult.Scanned -> updateWaiting(session.url, "已扫码，请在手机确认")
                        QrPollResult.Expired -> {
                            _state.value = _state.value.copy(qr = QrLoginState.Failed("二维码已过期，请重新获取"))
                            return@launch
                        }
                        is QrPollResult.Failed -> {
                            _state.value = _state.value.copy(qr = QrLoginState.Failed(result.message))
                            return@launch
                        }
                        is QrPollResult.Success -> {
                            neteaseCookies.clear()
                            neteaseCookies.putAll(result.cookies)
                            saveCookies(NeteaseApi.PROVIDER, neteaseCookies)
                            val profile = runCatching {
                                neteaseApi.fetchProfile(neteaseCookies)
                            }.getOrElse {
                                AccountProfile(
                                    provider = NeteaseApi.PROVIDER,
                                    uid = result.uid,
                                    nickname = result.nickname.ifBlank { "网易云用户" },
                                    avatarUrl = result.avatarUrl,
                                )
                            }
                            saveProfile(profile)
                            _state.value = _state.value.copy(
                                netease = profile,
                                qr = QrLoginState.Success(profile.nickname),
                                message = "已登录网易云：${profile.nickname}",
                            )
                            refreshNeteasePlaylists(profile)
                            delay(2_500L)
                            if (_state.value.qr is QrLoginState.Success) {
                                _state.value = _state.value.copy(qr = QrLoginState.Idle)
                            }
                            return@launch
                        }
                    }
                }
                if (_state.value.qr is QrLoginState.Waiting) {
                    _state.value = _state.value.copy(qr = QrLoginState.Failed("二维码已过期，请重新获取"))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                Log.w(TAG, "netease qr login failed: ${t.message}")
                _state.value = _state.value.copy(qr = QrLoginState.Failed(friendly(t)))
            }
        }
    }

    fun cancelQr() {
        qrJob?.cancel()
        qrJob = null
        _state.value = _state.value.copy(qr = QrLoginState.Idle)
    }

    fun importCookies(raw: String) {
        val parsed = parseCookieImports(raw)
        if (parsed.isEmpty()) {
            _state.value = _state.value.copy(
                message = "无法识别 Cookie：需要网易云 MUSIC_U 或 QQ qm_keyst/qqmusic_key",
            )
            return
        }
        val imported = mutableListOf<String>()
        parsed[NeteaseApi.PROVIDER]?.let { cookies ->
            neteaseCookies.clear()
            neteaseCookies.putAll(cookies)
            saveCookies(NeteaseApi.PROVIDER, neteaseCookies)
            imported += "网易云"
        }
        parsed["qq"]?.let { cookies ->
            qqCookies.clear()
            qqCookies.putAll(cookies)
            saveCookies("qq", qqCookies)
            _state.value = _state.value.copy(
                qq = AccountProfile(
                    provider = "qq",
                    uid = cookies["uin"].orEmpty().ifBlank { cookies["qqmusic_uin"].orEmpty() },
                    nickname = "QQ 音乐已导入 Cookie",
                ),
            )
            imported += "QQ"
        }
        _state.value = _state.value.copy(
            message = "已导入 ${imported.joinToString("、")} Cookie" +
                if (parsed.containsKey(NeteaseApi.PROVIDER)) "，正在读取账号…" else "",
        )
        refresh()
    }

    fun logout(provider: String) {
        qrJob?.cancel()
        if (provider == NeteaseApi.PROVIDER) {
            neteaseCookies.clear()
            prefs.edit().remove(KEY_NETEASE_COOKIES).remove(KEY_NETEASE_PROFILE).apply()
            _state.value = _state.value.copy(
                netease = null,
                qr = QrLoginState.Idle,
                playlists = _state.value.playlists.filterNot { it.provider == provider },
                playlistTracks = _state.value.playlistTracks.filterNot { it.key.startsWith("netease:") },
                message = "已退出网易云",
            )
            savePlaylists(_state.value.playlists)
        } else {
            qqCookies.clear()
            prefs.edit().remove(KEY_QQ_COOKIES).remove(KEY_QQ_PROFILE).apply()
            val playlists = _state.value.playlists.filterNot { it.provider == QqPlaylistApi.PROVIDER }
            savePlaylists(playlists)
            _state.value = _state.value.copy(
                qq = null,
                playlists = playlists,
                playlistTracks = _state.value.playlistTracks.filterNot { it.key.startsWith("qq:") },
                message = "已移除 QQ 音乐 Cookie",
            )
        }
    }

    fun refreshRecommended() {
        scope.launch {
            val list = runCatching { neteaseApi.fetchRecommendedPlaylists(neteaseCookies) }
                .getOrNull() ?: return@launch
            if (list.isNotEmpty()) _state.value = _state.value.copy(recommended = list)
        }
    }

    fun loadSquare(category: String) {
        if (_state.value.squareLoading && _state.value.squareCat == category) return
        _state.value = _state.value.copy(squareCat = category, squareLoading = true)
        scope.launch {
            val list = runCatching { neteaseApi.fetchCategoryPlaylists(neteaseCookies, category) }
                .getOrNull() ?: emptyList()
            _state.value = _state.value.copy(square = list, squareLoading = false)
        }
    }

    fun loadPlaylistTracks(playlist: AccountPlaylist) {
        val key = playlistKey(playlist)
        if (_state.value.playlistTracks.containsKey(key)) return
        if (_state.value.loadingPlaylistId != null) return
        _state.value = _state.value.copy(loadingPlaylistId = playlist.id, playlistError = null)
        scope.launch {
            try {
                val tracks = if (playlist.provider == QqPlaylistApi.PROVIDER) {
                    qqApi.fetchPlaylistTracks(
                        cookies = qqCookies,
                        playlistId = playlist.id,
                        fallbackName = playlist.name,
                    )
                } else {
                    neteaseApi.fetchPlaylistTracks(
                        cookies = neteaseCookies,
                        playlistId = playlist.id,
                        fallbackName = playlist.name,
                    )
                }
                _state.value = _state.value.copy(
                    loadingPlaylistId = null,
                    playlistTracks = _state.value.playlistTracks + (key to tracks),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                Log.w(TAG, "load playlist ${playlist.id} failed: ${t.message}")
                _state.value = _state.value.copy(
                    loadingPlaylistId = null,
                    playlistError = friendly(t),
                )
            }
        }
    }

    fun clearMessage() {
        _state.value = _state.value.copy(message = null)
    }

    fun clearPlaylistError() {
        _state.value = _state.value.copy(playlistError = null)
    }

    fun playlistTracks(playlist: AccountPlaylist): List<DemoTrack> =
        _state.value.playlistTracks[playlistKey(playlist)].orEmpty()

    private suspend fun refreshNeteasePlaylists(profile: AccountProfile) {
        runCatching {
            val list = neteaseApi.fetchPlaylists(neteaseCookies, profile.uid)
            savePlaylists(list)
            _state.value = _state.value.copy(
                playlists = _state.value.playlists.filterNot { it.provider == NeteaseApi.PROVIDER } + list,
            )
        }.onFailure {
            Log.w(TAG, "fetch playlists failed: ${it.message}")
            _state.value = _state.value.copy(error = "读取歌单失败：${friendly(it)}")
        }
    }

    private fun updateWaiting(url: String, status: String) {
        val current = _state.value.qr
        if (current is QrLoginState.Waiting && current.url == url) {
            _state.value = _state.value.copy(qr = QrLoginState.Waiting(url, status))
        }
    }

    private fun playlistKey(playlist: AccountPlaylist): String =
        "${playlist.provider}:${playlist.id}"

    private fun parseCookieImports(raw: String): Map<String, Map<String, String>> {
        val text = raw.trim()
        if (text.isBlank()) return emptyMap()
        val result = linkedMapOf<String, Map<String, String>>()
        runCatching { JSONObject(text) }.getOrNull()?.let { json ->
            json.optString("netease").trim().takeIf { it.isNotBlank() }?.let {
                result[NeteaseApi.PROVIDER] = parseCookieHeader(it)
            }
            json.optString("qq").trim().takeIf { it.isNotBlank() }?.let {
                result["qq"] = parseCookieHeader(it)
            }
            return result
        }
        val cookies = parseCookieHeader(text)
        if (cookies.containsKey("MUSIC_U")) {
            result[NeteaseApi.PROVIDER] = cookies
        } else if (cookies.containsKey("qm_keyst") || cookies.containsKey("qqmusic_key") ||
            cookies.containsKey("qqmusic_uin")
        ) {
            result["qq"] = cookies
        }
        return result
    }

    private fun parseCookieHeader(raw: String): Map<String, String> {
        val text = raw.trim().removePrefix("Cookie:").trim()
        val result = linkedMapOf<String, String>()
        text.split(';').forEach { part ->
            val index = part.indexOf('=')
            if (index <= 0) return@forEach
            val name = part.substring(0, index).trim()
            val value = part.substring(index + 1).trim()
            if (name.isNotBlank() && value.isNotBlank()) result[name] = value
        }
        return result
    }

    private fun restore() {
        loadCookies(NeteaseApi.PROVIDER)?.let { neteaseCookies.putAll(it) }
        loadCookies("qq")?.let { qqCookies.putAll(it) }
        val netease = readProfile(KEY_NETEASE_PROFILE)
        val qq = readProfile(KEY_QQ_PROFILE)
        val playlists = readPlaylists()
        _state.value = _state.value.copy(netease = netease, qq = qq, playlists = playlists)
    }

    private fun saveCookies(provider: String, cookies: Map<String, String>) {
        val json = JSONObject()
        cookies.forEach { (k, v) -> json.put(k, v) }
        val encrypted = encrypt(json.toString()) ?: return
        prefs.edit()
            .putString(if (provider == NeteaseApi.PROVIDER) KEY_NETEASE_COOKIES else KEY_QQ_COOKIES, encrypted)
            .apply()
    }

    private fun loadCookies(provider: String): Map<String, String>? {
        val key = if (provider == NeteaseApi.PROVIDER) KEY_NETEASE_COOKIES else KEY_QQ_COOKIES
        val stored = prefs.getString(key, null) ?: return null
        val plain = decrypt(stored) ?: run {
            prefs.edit().remove(key).apply()
            return null
        }
        return runCatching {
            val json = JSONObject(plain)
            buildMap {
                json.keys().forEach { k -> put(k, json.optString(k)) }
            }
        }.getOrNull()
    }

    private fun saveProfile(profile: AccountProfile) {
        val key = if (profile.provider == NeteaseApi.PROVIDER) KEY_NETEASE_PROFILE else KEY_QQ_PROFILE
        prefs.edit()
            .putString(
                key,
                JSONObject()
                    .put("provider", profile.provider)
                    .put("uid", profile.uid)
                    .put("nickname", profile.nickname)
                    .put("avatarUrl", profile.avatarUrl)
                    .toString(),
            )
            .apply()
    }

    private fun readProfile(key: String): AccountProfile? = runCatching {
        val raw = prefs.getString(key, null) ?: return@runCatching null
        val json = JSONObject(raw)
        AccountProfile(
            provider = json.optString("provider"),
            uid = json.optString("uid"),
            nickname = json.optString("nickname"),
            avatarUrl = json.optString("avatarUrl"),
        )
    }.getOrNull()

    private fun savePlaylists(playlists: List<AccountPlaylist>) {
        val array = JSONArray()
        playlists.forEach { playlist ->
            array.put(
                JSONObject()
                    .put("provider", playlist.provider)
                    .put("id", playlist.id)
                    .put("name", playlist.name)
                    .put("trackCount", playlist.trackCount)
                    .put("coverUrl", playlist.coverUrl)
            )
        }
        prefs.edit().putString(KEY_PLAYLISTS, array.toString()).apply()
    }

    private fun readPlaylists(): List<AccountPlaylist> = runCatching {
        val raw = prefs.getString(KEY_PLAYLISTS, null) ?: return@runCatching emptyList()
        val array = JSONArray(raw)
        (0 until array.length()).mapNotNull { i ->
            val json = array.optJSONObject(i) ?: return@mapNotNull null
            val id = json.optString("id")
            if (id.isBlank()) return@mapNotNull null
            AccountPlaylist(
                provider = json.optString("provider"),
                id = id,
                name = json.optString("name"),
                trackCount = json.optInt("trackCount", 0),
                coverUrl = json.optString("coverUrl"),
            )
        }
    }.getOrDefault(emptyList())

    private fun encrypt(plain: String): String? = runCatching {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" +
            Base64.encodeToString(encrypted, Base64.NO_WRAP)
    }.onFailure { Log.w(TAG, "encrypt account cookies failed", it) }.getOrNull()

    private fun decrypt(stored: String): String? = runCatching {
        val parts = stored.split(':', limit = 2)
        if (parts.size != 2) return null
        val iv = Base64.decode(parts[0], Base64.NO_WRAP)
        val data = Base64.decode(parts[1], Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
        cipher.doFinal(data).toString(Charsets.UTF_8)
    }.onFailure { Log.w(TAG, "decrypt account cookies failed", it) }.getOrNull()

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return generator.generateKey()
    }

    private fun friendly(t: Throwable): String {
        val message = t.message.orEmpty()
        return when {
            message.contains("HTTP 301", true) || message.contains("HTTP 302", true) ||
                message.contains("HTTP 401", true) || message.contains("HTTP 403", true) ->
                "登录已失效，请重新登录"
            message.contains("timed out", true) || message.contains("timeout", true) ->
                "连接超时，请检查网络"
            message.contains("Unable to resolve host", true) -> "无法连接网易云，请检查网络"
            message.isBlank() -> t.javaClass.simpleName
            else -> message.take(100)
        }
    }

    private companion object {
        const val TAG = "TvMusicAccount"
        const val KEY_NETEASE_COOKIES = "netease_cookies"
        const val KEY_QQ_COOKIES = "qq_cookies"
        const val KEY_NETEASE_PROFILE = "netease_profile"
        const val KEY_QQ_PROFILE = "qq_profile"
        const val KEY_PLAYLISTS = "playlists"
        const val KEY_ALIAS = "tvmusic_account"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val QR_POLL_MS = 1_500L
        const val QR_TIMEOUT_MS = 180_000L
    }
}
