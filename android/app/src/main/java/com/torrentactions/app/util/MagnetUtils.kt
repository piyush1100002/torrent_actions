package com.torrentactions.app.util

import java.net.URLDecoder
import java.nio.charset.StandardCharsets

object MagnetUtils {

    fun isMagnetOrTorrent(input: String?): Boolean {
        if (input.isNullOrBlank()) return false
        val trimmed = input.trim()
        return trimmed.startsWith("magnet:?", ignoreCase = true) ||
                trimmed.endsWith(".torrent", ignoreCase = true) ||
                trimmed.contains(".torrent?", ignoreCase = true)
    }

    fun extractMagnetFromText(text: String?): String? {
        if (text.isNullOrBlank()) return null
        val trimmed = text.trim()
        if (trimmed.startsWith("magnet:?", ignoreCase = true)) {
            return trimmed
        }
        val magnetRegex = Regex("""(magnet:\?[^\s"'<>]+)""", RegexOption.IGNORE_CASE)
        val match = magnetRegex.find(trimmed)
        if (match != null) {
            return match.groupValues[1]
        }
        val torrentUrlRegex = Regex("""(https?://[^\s"'<>]+\.torrent[^\s"'<>]*)""", RegexOption.IGNORE_CASE)
        val torrentMatch = torrentUrlRegex.find(trimmed)
        return torrentMatch?.groupValues?.get(1)
    }

    fun extractDisplayName(magnetUrl: String): String {
        if (!magnetUrl.startsWith("magnet:?", ignoreCase = true)) {
            // URL or raw string
            val lastSegment = magnetUrl.substringAfterLast("/").substringBefore("?")
            return if (lastSegment.isNotBlank()) lastSegment else "Torrent Download"
        }
        val query = magnetUrl.substringAfter("?")
        val params = query.split("&")
        for (param in params) {
            if (param.startsWith("dn=", ignoreCase = true)) {
                val encoded = param.substring(3)
                return try {
                    URLDecoder.decode(encoded, StandardCharsets.UTF_8.name())
                } catch (e: Exception) {
                    encoded.replace("+", " ")
                }
            }
        }
        val hash = extractInfoHash(magnetUrl)
        return if (hash != null) "Torrent (${hash.take(8)}...)" else "Unknown Torrent"
    }

    fun extractInfoHash(magnetUrl: String): String? {
        val regex = Regex("""btih:([0-9a-fA-F]{40}|[a-zA-Z2-7]{32})""", RegexOption.IGNORE_CASE)
        val match = regex.find(magnetUrl)
        return match?.groupValues?.get(1)?.lowercase()
    }

    fun cleanImdbId(input: String?): String? {
        if (input.isNullOrBlank()) return null
        val match = Regex("""tt\d+""", RegexOption.IGNORE_CASE).find(input.trim())
        return match?.value?.lowercase()
    }
}
