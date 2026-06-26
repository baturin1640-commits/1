package com.autovideo.app

object PlaylistParser {
    private const val MAX_LINES = 100_000
    private const val MAX_CHANNELS = 10_000
    private const val MAX_WARNINGS = 50

    fun parse(text: String, baseUrl: String? = null): M3uParseResult {
        val channels = linkedMapOf<String, IptvChannel>()
        val warnings = mutableListOf<String>()
        var pending = PlaylistEntryMeta()
        var extended = false
        var lineNumber = 0

        for (rawLine in text.removePrefix("\uFEFF").lineSequence()) {
            lineNumber++
            if (lineNumber > MAX_LINES || channels.size >= MAX_CHANNELS) {
                addWarning(warnings, "Плейлист ограничен безопасным максимальным размером")
                break
            }

            val line = rawLine.trim().removePrefix("\uFEFF")
            when {
                line.isEmpty() -> Unit
                line.startsWith("#EXTM3U", ignoreCase = true) -> extended = true
                line.startsWith("#EXTINF", ignoreCase = true) -> {
                    pending = PlaylistLineParser.metadata(line)
                }
                line.startsWith("#") -> Unit
                else -> {
                    val streamUrl = OnlineUrlValidator.resolveSecureUrl(baseUrl, line)
                    if (streamUrl == null) {
                        addWarning(warnings, "Строка $lineNumber содержит неподдерживаемый адрес")
                    } else if (channels.containsKey(streamUrl)) {
                        addWarning(warnings, "Повторяющийся адрес потока пропущен")
                    } else {
                        channels[streamUrl] = IptvChannel(
                            id = pending.id,
                            name = pending.name.ifBlank { "Канал ${channels.size + 1}" }.take(180),
                            logoUrl = pending.logo?.let(OnlineUrlValidator::normalizeSecureUrl),
                            group = pending.group?.take(120),
                            streamUrl = streamUrl,
                        )
                    }
                    pending = PlaylistEntryMeta()
                }
            }
        }

        return M3uParseResult(
            channels = channels.values.toList(),
            warnings = warnings,
            extended = extended,
        )
    }

    private fun addWarning(target: MutableList<String>, message: String) {
        if (target.size < MAX_WARNINGS) target += message
    }
}
