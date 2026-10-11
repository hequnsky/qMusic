package com.qmusic.ui.toplist

import com.qmusic.data.model.Toplist

/**
 * 排行榜筛选。
 *
 * 背景：网易云 /toplist 返回 63 个榜单，其中夹杂大量与「听歌」无关的运营榜
 * （车友榜、直播榜、派对潮音榜、星云榜、音乐合伙人榜、听歌识曲榜…）。
 * 首页只取前 8 个时，一旦顺序变化就会出现让人莫名其妙的卡片，
 * 用户反馈的「排行榜内容不对劲」即由此而来。
 *
 * 这里按关键词把非音乐类榜单过滤掉，保留真正的音乐榜单
 * （飙升 / 新歌 / 原创 / 热歌 / 说唱 / 电音 / 国风 / 摇滚 / 民谣 / ACG / 欧美 / 日韩…）。
 */
private val NON_MUSIC_KEYWORDS = listOf(
    "车友",       // 特斯拉/理想/比亚迪/蔚来/极氪/昊铂/埃安/吉利 车友爱听榜
    "KTV",
    "直播",
    "派对",
    "星云",
    "音乐合伙人",
    "听歌识曲",
    "潜力爆款",
    "蛋仔",
    "LOOK",
    "赏音",
    "实时分享",
    "潮流风向",
)

/** 过滤出音乐类榜单，同时剔除无封面、无名目的异常项 */
fun List<Toplist>.musicToplists(): List<Toplist> = filterNot { t ->
    t.name.isBlank() || NON_MUSIC_KEYWORDS.any { t.name.contains(it, ignoreCase = true) }
}
