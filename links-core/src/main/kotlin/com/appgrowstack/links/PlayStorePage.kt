package com.appgrowstack.links

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * A Google Play product page: the package name and the referrer handed to the installed app through
 * the Play Install Referrer (`utm_source=…&utm_campaign=…`), so the other app can tell where its
 * install came from.
 *
 * The app opens it (this SDK has no Android dependency), for example as the body of
 * kotlin-app-campaign's `open_store_page` action:
 *
 * ```kotlin
 * val page = PlayStorePage("com.example.reading", referrer = "utm_source=gohobi&utm_medium=cross_promo")
 * context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(page.marketUri)).setPackage("com.android.vending"))
 * ```
 */
public data class PlayStorePage(val packageName: String, val referrer: String? = null) {
    /** `https://play.google.com/store/apps/details?id=<package>&referrer=<referrer>`. */
    public val url: String get() = "https://play.google.com/store/apps/details?" + query()

    /** `market://details?id=<package>&referrer=<referrer>`, for the Play Store app. */
    public val marketUri: String get() = "market://details?" + query()

    private fun query(): String =
        "id=" + encode(packageName) + (referrer?.let { "&referrer=" + encode(it) } ?: "")

    public companion object {
        private val packagePattern = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+$")

        /**
         * Reads `https://play.google.com/store/apps/details?id=…&referrer=…` and
         * `market://details?id=…&referrer=…`. `null` for anything else.
         */
        public fun parse(url: String): PlayStorePage? {
            val uri = runCatching { URI(url) }.getOrNull() ?: return null
            val isWeb = uri.scheme == "https" && uri.host?.lowercase() == "play.google.com" && uri.rawPath == "/store/apps/details"
            val isMarket = uri.scheme == "market" && uri.host == "details"
            if (!isWeb && !isMarket) return null
            val query = queryItems(uri.rawQuery)
            val id = query["id"]?.takeIf { packagePattern.matches(it) } ?: return null
            return PlayStorePage(id, query["referrer"]?.takeIf { it.isNotEmpty() })
        }

        /** `a=1&b=x%20y` as a map of the decoded values; the first of a repeated name wins. */
        internal fun queryItems(raw: String?): Map<String, String> {
            if (raw.isNullOrEmpty()) return emptyMap()
            val out = LinkedHashMap<String, String>()
            for (part in raw.split("&")) {
                if (part.isEmpty()) continue
                val eq = part.indexOf('=')
                val name = decode(if (eq < 0) part else part.substring(0, eq))
                val value = if (eq < 0) "" else decode(part.substring(eq + 1))
                if (name.isNotEmpty()) out.putIfAbsent(name, value)
            }
            return out
        }

        private fun decode(text: String): String = runCatching { URLDecoder.decode(text, Charsets.UTF_8) }.getOrDefault(text)
        private fun encode(text: String): String = URLEncoder.encode(text, Charsets.UTF_8).replace("+", "%20")
    }
}
