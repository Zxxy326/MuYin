package tv.music.player.core.playback

data class DemoTrack(
    val id: Int,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val colorSeed: Int,
    val coverUrl: String = "",
    val onlineSource: String = "",
    val onlineSongId: String = "",
    val onlineHash: String = "",
    val onlineAlbumMid: String = "",
    val onlineStrMediaMid: String = "",
    val onlineSongNumericId: Long = 0L,
    val localUri: String = "",
    val localSource: String = "",
) {
    val isOnline: Boolean get() = onlineSource.isNotBlank()
    val isLocal: Boolean get() = localUri.isNotBlank()
    val usesPlayer: Boolean get() = isOnline || isLocal
    val artworkUrl: String
        get() = coverUrl.ifBlank {
            when {
                onlineSource == "tx" && onlineAlbumMid.isNotBlank() ->
                    "https://y.gtimg.cn/music/photo_new/T002R300x300M000$onlineAlbumMid.jpg"
                else -> localUri.takeIf { it.startsWith("content://") || it.startsWith("file://") }.orEmpty()
            }
        }
}

data class LyricLine(val timeMs: Long, val text: String)

object DemoCatalog {

    val tracks: List<DemoTrack> = listOf(
        DemoTrack(1, "夜航星", "陈默", "深空", 4 * 60_000L + 12_000L, 11),
        DemoTrack(2, "晴天", "周杰伦", "叶惠美", 4 * 60_000L + 29_000L, 23),
        DemoTrack(3, "海阔天空", "Beyond", "乐与怒", 5 * 60_000L + 25_000L, 37),
        DemoTrack(4, "起风了", "买辣椒也用券", "起风了", 5 * 60_000L + 11_000L, 41),
        DemoTrack(5, "平凡之路", "朴树", "猎户星座", 5 * 60_000L + 2_000L, 53),
        DemoTrack(6, "稻香", "周杰伦", "魔杰座", 3 * 60_000L + 43_000L, 67),
        DemoTrack(7, "光年之外", "邓紫棋", "光年之外", 3 * 60_000L + 55_000L, 79),
        DemoTrack(8, "后来", "刘若英", "我等你", 5 * 60_000L + 41_000L, 83),
        DemoTrack(9, "演员", "薛之谦", "绅士", 4 * 60_000L + 21_000L, 97),
        DemoTrack(10, "泡沫", "邓紫棋", "Xposed", 4 * 60_000L + 18_000L, 101),
        DemoTrack(11, "夜曲", "周杰伦", "十一月的萧邦", 3 * 60_000L + 46_000L, 113),
        DemoTrack(12, "斑马斑马", "宋冬野", "安和桥北", 4 * 60_000L + 13_000L, 127),
    )

    val hotWords: List<String> = listOf("晴天", "海阔天空", "起风了", "夜航星", "平凡之路", "光年之外", "稻香", "演员")

    val charts: List<String> = listOf("QQ 热歌榜", "网易云飙升榜", "酷狗 TOP500", "原创精选榜")

    val lyrics: List<LyricLine> = listOf(
        LyricLine(0, "城市把夜色轻轻放下"),
        LyricLine(8_000, "风从窗边路过"),
        LyricLine(16_000, "我把耳机调到刚刚好"),
        LyricLine(24_000, "让世界安静一点"),
        LyricLine(32_000, "灯光在玻璃上摇晃"),
        LyricLine(40_000, "像一条温柔的河"),
        LyricLine(48_000, "如果此刻你在身边"),
        LyricLine(56_000, "会不会也听见这首歌"),
        LyricLine(64_000, "时间在耳边慢慢走"),
        LyricLine(72_000, "我们都没有回头"),
        LyricLine(80_000, "把心事交给旋律"),
        LyricLine(88_000, "明天再说以后"),
        LyricLine(96_000, "让晚风替我说"),
        LyricLine(104_000, "那些没说出口的"),
        LyricLine(112_000, "这一刻 只剩音乐"),
        LyricLine(120_000, "陪着我们"),
    )
}
