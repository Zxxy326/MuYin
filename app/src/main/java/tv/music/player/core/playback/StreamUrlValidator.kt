package tv.music.player.core.playback

import android.net.Uri

/** 在线直链可用性预过滤：只接受 http(s)，过滤加密容器与跨平台占位流（对齐上游 v1.7.2）。 */
object StreamUrlValidator {
    private val ENCRYPTED_EXTENSIONS = listOf(
        ".mflac", ".mflac0", ".mflac2", ".mgg", ".mgg0", ".mgg1", ".mggl", ".mmp4",
    )

    fun isUsable(source: String, url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val trimmed = url.trim()
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) return false

        val path = trimmed.substringBefore('?').substringBefore('#').lowercase()
        if (ENCRYPTED_EXTENSIONS.any { path.endsWith(it) }) return false

        if (source != "kw") {
            val host = runCatching { Uri.parse(trimmed).host }.getOrNull()?.lowercase().orEmpty()
            if (host == "kuwo.cn" || host.endsWith(".kuwo.cn") ||
                host == "kuwo.com" || host.endsWith(".kuwo.com")
            ) {
                return false
            }
        }
        return true
    }

    /** 代理/本地地址不写入持久化缓存，避免端口变化后播放失败。 */
    fun isCacheable(url: String): Boolean {
        val host = runCatching { Uri.parse(url).host }.getOrNull()?.lowercase().orEmpty()
        return host != "127.0.0.1" && host != "localhost" && host != "0.0.0.0"
    }
}