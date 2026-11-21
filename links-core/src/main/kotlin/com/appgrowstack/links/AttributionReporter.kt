package com.appgrowstack.links

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

/**
 * Reports where this install came from, once, on the first launch.
 *
 * Only deterministic clues are used: the Play Install Referrer, and the App Link that opened the
 * first launch (hand it to [noteOpenedUrl] from the activity's intent). Nothing is inferred from the
 * IP address, the device or the time of a click, and the clipboard is never read. With neither clue
 * the answer is `organic`.
 *
 * The resolver decides ([DeviceAttributionResolver] on the device, or a server). When it asks to try
 * later (offline, Play not answering), the clues are resolved again on a later launch. A URL is only
 * taken during the first launch, so a link opened weeks later never rewrites where the install came
 * from.
 *
 * @param installId the id your analytics uses for this install (lowercase UUID).
 * @param appVersion `versionName`, `x.y.z`.
 * @param clock epoch milliseconds.
 */
public class AttributionReporter(
    private val installId: String,
    private val appVersion: String,
    private val resolver: AttributionResolver,
    private val storage: AttributionStorage,
    private val installReferrer: InstallReferrerSource = InstallReferrerSource.None,
    private val urlGraceMillis: Long = DEFAULT_URL_GRACE_MILLIS,
    clock: () -> Long = System::currentTimeMillis,
    private val sleep: suspend (Long) -> Unit = { delay(it) },
) {
    public companion object {
        /** How long the first launch waits for the opening URL before reporting without it. */
        public const val DEFAULT_URL_GRACE_MILLIS: Long = 3_000
    }

    /** `true` when this process is the install's first launch (nothing was stored before it). */
    public val isFirstLaunch: Boolean

    private val urlNoted = CompletableDeferred<Unit>()
    private val inFlight = Mutex()

    init {
        if (storage.firstOpenAt == null) {
            storage.firstOpenAt = clock()
            isFirstLaunch = true
        } else {
            isFirstLaunch = false
        }
    }

    /** The decided source, once there is one. */
    public val attribution: Attribution? get() = storage.attribution

    /** Hand every URL that opens the app. Only the first `https` URL of the first launch is kept. */
    public fun noteOpenedUrl(url: String) {
        if (!isFirstLaunch || storage.isFinished || storage.openedUrl != null) return
        if (!url.startsWith("https://") || url.toByteArray(Charsets.UTF_8).size > 2048) return
        storage.openedUrl = url
        urlNoted.complete(Unit)
    }

    /**
     * Resolves the source if it has not been decided yet. Returns it, or `null` when it will be tried
     * again on a later launch (offline, Play not answering, server busy) or can never be decided (the
     * clues were rejected).
     */
    public suspend fun reportIfNeeded(): Attribution? {
        if (storage.isFinished) return storage.attribution
        if (!inFlight.tryLock()) return null
        try {
            if (isFirstLaunch && storage.openedUrl == null) {
                coroutineScope {
                    val timer = launch {
                        sleep(urlGraceMillis)
                        urlNoted.complete(Unit)
                    }
                    urlNoted.await()
                    timer.cancel()
                }
            }
            val referrer = when (val read = installReferrer.read()) {
                is InstallReferrer.Found -> read.referrer.take(1024)
                InstallReferrer.None -> null
                InstallReferrer.RetryLater -> return null
            }
            val request = AttributionRequest(
                installId = installId,
                appVersion = appVersion,
                firstOpenAt = storage.firstOpenAt ?: 0,
                playInstallReferrer = referrer,
                openedUrl = storage.openedUrl,
            )
            return when (val answer = resolver.resolve(request)) {
                is AttributionResolution.Decided -> {
                    storage.attribution = answer.attribution
                    storage.isFinished = true
                    answer.attribution
                }
                AttributionResolution.Rejected -> {
                    // The clues will never pass; resolving them again on every launch would only add noise.
                    storage.isFinished = true
                    null
                }
                AttributionResolution.RetryLater -> null
            }
        } finally {
            inFlight.unlock()
        }
    }
}
