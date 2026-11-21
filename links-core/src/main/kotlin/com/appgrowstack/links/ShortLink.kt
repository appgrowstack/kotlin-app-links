package com.appgrowstack.links

import java.net.URI

/**
 * A short link on your product's own domain: `https://<domain>/l/<code>` for a generated link
 * (8 letters and digits, at least one capital) and `https://<domain>/go/<code>` for a link you named
 * (`spring-sale`). Links are made on a server; the device only builds and reads them.
 */
public class ShortLink private constructor(public val domain: String, public val code: String) {
    public enum class Kind(public val path: String) {
        /** `/l/<code>`: generated. */
        GENERATED("l"),
        /** `/go/<code>`: named. */
        NAMED("go"),
    }

    public val kind: Kind get() = if (isGenerated(code)) Kind.GENERATED else Kind.NAMED

    public val url: String get() = "https://$domain/${kind.path}/$code"

    override fun equals(other: Any?): Boolean = other is ShortLink && other.domain == domain && other.code == code
    override fun hashCode(): Int = 31 * domain.hashCode() + code.hashCode()
    override fun toString(): String = url

    public companion object {
        /** `null` when the code has neither shape or the domain is empty. */
        public fun of(domain: String, code: String): ShortLink? =
            if ((isGenerated(code) || isNamed(code)) && domain.isNotEmpty()) ShortLink(domain.lowercase(), code) else null

        /**
         * Reads `https://<domain>/l/<code>` or `https://<domain>/go/<code>`. A leading `www.` is dropped.
         * The path and the code's shape must agree (`/go/` with a generated code is not a short link).
         */
        public fun parse(url: String): ShortLink? {
            val uri = runCatching { URI(url) }.getOrNull() ?: return null
            if (uri.scheme != "https") return null
            val host = uri.host?.lowercase()?.removePrefix("www.") ?: return null
            val parts = (uri.rawPath ?: "").split("/").filter { it.isNotEmpty() }
            if (parts.size != 2) return null
            val kind = Kind.entries.firstOrNull { it.path == parts[0] } ?: return null
            val code = parts[1]
            val ok = when (kind) {
                Kind.GENERATED -> isGenerated(code)
                Kind.NAMED -> isNamed(code) && !isGenerated(code)
            }
            return if (ok && host.isNotEmpty()) ShortLink(host, code) else null
        }

        internal fun isGenerated(code: String): Boolean =
            code.length == 8 && code.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' } && code.any { it in 'A'..'Z' }

        internal fun isNamed(code: String): Boolean {
            if (code.length !in 2..40) return false
            fun lowerOrDigit(c: Char) = c in 'a'..'z' || c in '0'..'9'
            return lowerOrDigit(code[0]) && code.drop(1).all { lowerOrDigit(it) || it == '-' }
        }
    }
}
