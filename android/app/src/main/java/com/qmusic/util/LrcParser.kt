package com.qmusic.util

import com.qmusic.data.model.LyricLine

/** 解析 LRC 歌词为按时间排序的歌词行 */
object LrcParser {

    private val timeRegex = Regex("""\[(\d{1,2}):(\d{1,2})(?:[.:](\d{1,3}))?]""")

    /**
     * 「制作信息」行（作词 / 作曲 / 编曲 / OP / SP …）。
     *
     * 网易云等多音源会把这类信息**也带上 [00:00.00] 时间戳**放在歌词最前面，
     * 于是它们会被当成普通歌词渲染：开头白白占掉两行，
     * 而且因为时间戳是 0，播放到第一句之前 indexOfLast 会一直命中它们、
     * 把「作曲 : xxx」高亮成"正在唱的那句"。这里直接剔除。
     */
    private val creditRegex = Regex(
        """^[\[\(【]?\s*(作词|作曲|编曲|填词|词|曲|制作人|出品|监制|混音|母带|录音|配唱|和声|合声|吉他|贝斯|鼓|键盘|弦乐|文案|统筹|发行|OP|SP|Lyricist|Composer|Arranger|Producer|Vocals|Guitar|Bass|Drums|Piano|Strings|Mixed|Mastered|Recorded|Music|Lyrics)\s*[:：]""",
        RegexOption.IGNORE_CASE,
    )

    fun parse(lrc: String): List<LyricLine> {
        if (lrc.isBlank()) return emptyList()
        val result = mutableListOf<LyricLine>()
        lrc.lineSequence().forEach { raw ->
            val line = raw.trim()
            if (line.isEmpty()) return@forEach
            val matches = timeRegex.findAll(line).toList()
            if (matches.isEmpty()) return@forEach
            val content = line.substring(matches.last().range.last + 1).trim()
            if (content.isEmpty()) return@forEach
            if (creditRegex.containsMatchIn(content)) return@forEach
            for (m in matches) {
                val min = m.groupValues[1].toIntOrNull() ?: 0
                val sec = m.groupValues[2].toIntOrNull() ?: 0
                val frac = m.groupValues[3].padEnd(3, '0').take(3).toIntOrNull() ?: 0
                val timeMs = min * 60_000L + sec * 1_000L + frac
                result.add(LyricLine(timeMs, content))
            }
        }
        return result.sortedBy { it.timeMs }
    }
}