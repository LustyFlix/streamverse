package com.lustyflix.streamverse.extractors

import com.lustyflix.streamverse.app
import com.lustyflix.streamverse.utils.ExtractorApi
import com.lustyflix.streamverse.utils.ExtractorLink
import com.lustyflix.streamverse.utils.M3u8Helper


open class PlayerVoxzer : ExtractorApi() {
    override var name = "Voxzer"
    override var mainUrl = "https://player.voxzer.org"
    override val requiresReferer = false

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val listurl = url.replace("/view/","/list/")
        val urltext = app.get(listurl, referer = url).text
        val m3u8regex = Regex("((https:|http:)\\/\\/.*\\.m3u8)")
        val sources = mutableListOf<ExtractorLink>()
        val listm3 = m3u8regex.find(urltext)?.value
        if (listm3?.contains("m3u8") == true)
            M3u8Helper.generateM3u8(
                name,
                listm3,
                url,
                headers = app.get(url).headers.toMap()
            ).forEach { link ->
                sources.add(link)
            }
        return sources
    }
}