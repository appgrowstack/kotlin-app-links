package com.appgrowstack.links

import java.net.URI

/**
 * Decides an install's source on the device, from deterministic clues only, in this order:
 *
 * 1. The Play Install Referrer: a short link's `code` gives its code, and `utm_source` /
 *    `utm_medium` / `utm_campaign` give the source. Play's own default (`google-play` / `organic`),
 *    `not_set` and an `invite` without a code are not clues.
 * 2. The URL that opened the first launch, when it is on one of [domains]: a short link
 *    (`/l/<code>`, `/go/<code>`) gives its code, and utm gives the source. A short link without utm
 *    is `short_link`.
 * 3. Otherwise `organic`.
 *
 * Values are lowercased and anything but letters, digits and `_` (and `-` in medium and campaign)
 * becomes `_`, the same as the server does.
 *
 * @param domains your product's own domains (`example.com`). URLs on other hosts are not clues.
 */
public class DeviceAttributionResolver(domains: Set<String>) : AttributionResolver {
    public val domains: Set<String> = domains.map { it.lowercase() }.toSet()

    override suspend fun resolve(request: AttributionRequest): AttributionResolution {
        request.playInstallReferrer?.let { fromReferrer(it) }?.let { return AttributionResolution.Decided(it) }
        request.openedUrl?.let { fromOpenedUrl(it) }?.let { return AttributionResolution.Decided(it) }
        return AttributionResolution.Decided(Attribution.Organic)
    }

    private fun fromReferrer(raw: String): Attribution? {
        val query = PlayStorePage.queryItems(raw)
        val code = query["code"]?.takeIf { ShortLink.isGenerated(it) || ShortLink.isNamed(it) || isInviteCode(it) }
        val utm = Utm.of(query)
        if (code != null) {
            return Attribution(Attribution.MatchedBy.PLAY_INSTALL_REFERRER, utm.source ?: "short_link", utm.medium, utm.campaign, code)
        }
        if (!utm.isClue) return null
        return Attribution(Attribution.MatchedBy.PLAY_INSTALL_REFERRER, utm.source!!, utm.medium, utm.campaign)
    }

    private fun fromOpenedUrl(raw: String): Attribution? {
        val uri = runCatching { URI(raw) }.getOrNull() ?: return null
        val host = uri.host?.lowercase()?.removePrefix("www.") ?: return null
        if (host !in domains) return null
        val utm = Utm.of(PlayStorePage.queryItems(uri.rawQuery))
        val link = ShortLink.parse("https://$host${uri.rawPath ?: ""}")
        if (link != null) {
            return Attribution(Attribution.MatchedBy.OPENED_URL, utm.source ?: "short_link", utm.medium, utm.campaign, link.code)
        }
        val source = utm.source ?: return null
        if (source == "not_set") return null
        return Attribution(Attribution.MatchedBy.OPENED_URL, source, utm.medium, utm.campaign)
    }

    private class Utm(val source: String?, val medium: String?, val campaign: String?) {
        /** A source that means something without a code. */
        val isClue: Boolean
            get() = source != null && source != "not_set" && source != "invite" && !(source == "google_play" && medium == "organic")

        companion object {
            fun of(query: Map<String, String>) = Utm(
                norm(query["utm_source"], 40, hyphen = false),
                norm(query["utm_medium"], 40, hyphen = true),
                norm(query["utm_campaign"], 64, hyphen = true),
            )
        }
    }

    public companion object {
        /** An invite link's code: 16 letters and digits with at least one capital. */
        private fun isInviteCode(code: String): Boolean =
            code.length == 16 && code.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' } && code.any { it in 'A'..'Z' }

        /** Lowercase; a run of characters that are not allowed becomes one `_`; `_` at the ends is dropped. */
        internal fun norm(value: String?, max: Int, hyphen: Boolean): String? {
            if (value.isNullOrEmpty()) return null
            val out = StringBuilder()
            var inRun = false
            for (c in value.lowercase()) {
                val ok = c in 'a'..'z' || c in '0'..'9' || c == '_' || (hyphen && c == '-')
                if (ok) {
                    out.append(c)
                    inRun = false
                } else if (!inRun) {
                    out.append('_')
                    inRun = true
                }
            }
            val cut = out.toString().trim('_').take(max)
            return cut.ifEmpty { null }
        }
    }
}
