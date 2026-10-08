package com.qmusic.util

import com.qmusic.data.model.LyricLine

/** 解析 LRC 歌词为按时间排序的歌词行 */
object LrcParser {

    private val timeRegex = Regex("""\[(\d{1,2}):(\d{1,2})(?:[.:](\d{1,3}))?]""")

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