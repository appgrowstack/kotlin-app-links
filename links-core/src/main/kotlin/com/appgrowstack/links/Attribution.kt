package com.appgrowstack.links

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** The clues collected on the first launch (the shape of `schema/links/attribution.schema.json`). */
@Serializable
public data class AttributionRequest(
    @SerialName("install_id") val installId: String,
    val platform: String = "android",
    @SerialName("app_version") val appVersion: String,
    /** Epoch milliseconds. */
    @SerialName("first_open_at") val firstOpenAt: Long,
    /** The Play Install Referrer's `referrer` string as it came (`utm_source=…&utm_campaign=…&code=…`). */
    @SerialName("play_install_referrer") val playInstallReferrer: String? = null,
    /** The App Link that opened the first launch. */
    @SerialName("opened_url") val openedUrl: String? = null,
    /** Apple platforms only; always `null` on Android. */
    @SerialName("apple_ads_token") val appleAdsToken: String? = null,
)

/** Where the install came from. Decided once and kept. */
@Serializable
public data class Attribution(
    @SerialName("matched_by") val matchedBy: MatchedBy,
    /** `organic` when nothing matched. */
    val source: String,
    val medium: String? = null,
    val campaign: String? = null,
    /** The short link's code, when the clue was one. */
    val code: String? = null,
) {
    @Serializable
    public enum class MatchedBy {
        @SerialName("play_install_referrer") PLAY_INSTALL_REFERRER,
        @SerialName("opened_url") OPENED_URL,
        @SerialName("apple_ads") APPLE_ADS,
        @SerialName("organic") ORGANIC,
    }

    public companion object {
        public val Organic: Attribution = Attribution(MatchedBy.ORGANIC, "organic")
    }
}

/** What deciding an install's source came to. */
public sealed interface AttributionResolution {
    /** The source is known. It is kept and nothing is decided again. */
    public data class Decided(val attribution: Attribution) : AttributionResolution

    /** Not now (offline, the server is busy). Tried again on a later launch. */
    public data object RetryLater : AttributionResolution

    /** It can never be decided from these clues (the request was rejected). Not tried again. */
    public data object Rejected : AttributionResolution
}

/**
 * Decides where an install came from, from the clues [AttributionReporter] collected.
 * [DeviceAttributionResolver] decides on the device; a server can decide instead.
 */
public fun interface AttributionResolver {
    public suspend fun resolve(request: AttributionRequest): AttributionResolution
}
