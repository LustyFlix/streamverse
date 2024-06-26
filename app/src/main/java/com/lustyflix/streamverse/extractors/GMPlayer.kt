package com.lustyflix.streamverse.extractors

import com.lustyflix.streamverse.app
import com.lustyflix.streamverse.utils.ExtractorApi
import com.lustyflix.streamverse.utils.ExtractorLink
import com.lustyflix.streamverse.utils.Qualities

open class GMPlayer : ExtractorApi() {
    override val name = "GM Player"
    override val mainUrl = "https://gmplayer.xyz"
    override val requiresReferer = true

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val ref = referer ?: return null
        val id = url.substringAfter("/video/").substringBefore("/")

        val m3u8 = app.post(
            "$mainUrl/player/index.php?data=$id&do=getVideo",
            mapOf(
                "accept" to "*/*",
                "referer" to ref,
                "x-requested-with" to "XMLHttpRequest",
                "origin" to mainUrl
            ),
            data = mapOf("hash" to id, "r" to ref)
        ).parsed<GmResponse>().videoSource ?: return null

        return listOf(
            ExtractorLink(
                this.name,
                this.name,
                m3u8,
                ref,
                Qualities.Unknown.value,
                headers = mapOf("accept" to "*/*"),
                isM3u8 = true
            )
        )
    }

    private data class GmResponse(
        val videoSource: String? = null
    )
}