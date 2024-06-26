package com.lustyflix.streamverse.extractors

import com.lustyflix.streamverse.SubtitleFile
import com.lustyflix.streamverse.app
import com.lustyflix.streamverse.utils.ExtractorApi
import com.lustyflix.streamverse.utils.ExtractorLink
import com.lustyflix.streamverse.utils.Qualities

open class Mvidoo : ExtractorApi() {
    override val name = "Mvidoo"
    override val mainUrl = "https://mvidoo.com"
    override val requiresReferer = true

    private fun String.decodeHex(): String {
        require(length % 2 == 0) { "Must have an even length" }
        return String(
            chunked(2)
                .map { it.toInt(16).toByte() }
                .toByteArray()
        )
    }

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val document = app.get(url, referer = referer).text
        val data = Regex("""\{var\s*[^\s]+\s*=\s*(\[[^]]+])""").find(document)?.groupValues?.get(1)
            ?.removeSurrounding("[", "]")?.replace("\"", "")?.replace("\\x", "")?.split(",")?.map { it.decodeHex() }?.reversed()?.joinToString("") ?: return
        Regex("source\\s*src=\"([^\"]+)").find(data)?.groupValues?.get(1)?.let { link ->
            callback.invoke(
                ExtractorLink(
                    this.name,
                    this.name,
                    link,
                    "$mainUrl/",
                    Qualities.Unknown.value,
                    headers = mapOf(
                        "Range" to "bytes=0-"
                    )
                )
            )
        }
    }
}