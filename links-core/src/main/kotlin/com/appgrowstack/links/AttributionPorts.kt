package com.appgrowstack.links

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/** What the reporter keeps between launches. */
public interface AttributionStorage {
    public var firstOpenAt: Long?
    public var openedUrl: String?
    /** Set once the source was decided (or can never be). Nothing is resolved after that. */
    public var attribution: Attribution?
    public var isFinished: Boolean
}

/** In memory, for tests and previews. */
public class InMemoryAttributionStorage(
    override var firstOpenAt: Long? = null,
    override var openedUrl: String? = null,
    override var attribution: Attribution? = null,
    override var isFinished: Boolean = false,
) : AttributionStorage

/** One JSON file, `<directory>/app_links/attribution.json`. Pass `context.filesDir`. */
public class FileAttributionStorage(directory: File) : AttributionStorage {
    @Serializable
    private data class Stored(
        val firstOpenAt: Long? = null,
        val openedUrl: String? = null,
        val attribution: Attribution? = null,
        val isFinished: Boolean = false,
    )

    private val file = File(File(directory, "app_links"), "attribution.json")
    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Any()

    private fun read(): Stored = file.takeIf { it.exists() }
        ?.let { runCatching { json.decodeFromString(Stored.serializer(), it.readText()) }.getOrNull() }
        ?: Stored()

    private fun update(change: (Stored) -> Stored): Unit = synchronized(lock) {
        val next = change(read())
        file.parentFile.mkdirs()
        val temp = File(file.parentFile, "attribution.json.tmp")
        temp.writeText(json.encodeToString(Stored.serializer(), next))
        if (!temp.renameTo(file)) file.writeText(temp.readText())
    }

    override var firstOpenAt: Long?
        get() = synchronized(lock) { read().firstOpenAt }
        set(value) = update { it.copy(firstOpenAt = value) }

    override var openedUrl: String?
        get() = synchronized(lock) { read().openedUrl }
        set(value) = update { it.copy(openedUrl = value) }

    override var attribution: Attribution?
        get() = synchronized(lock) { read().attribution }
        set(value) = update { it.copy(attribution = value) }

    override var isFinished: Boolean
        get() = synchronized(lock) { read().isFinished }
        set(value) = update { it.copy(isFinished = value) }
}

/** What the Play Install Referrer API answered. */
public sealed interface InstallReferrer {
    /** The `referrer` string (`utm_source=google-play&utm_medium=organic` when Play knows nothing). */
    public data class Found(val referrer: String) : InstallReferrer

    /** There is none and never will be (not installed from Play, the API is not supported). */
    public data object None : InstallReferrer

    /** Play could not answer now (the service disconnected or is unavailable). Tried again later. */
    public data object RetryLater : InstallReferrer
}

/**
 * Reads the Play Install Referrer. The app wraps Google's `installreferrer` library in it (this SDK
 * has no dependencies):
 *
 * ```kotlin
 * val referrer = InstallReferrerSource {
 *     suspendCancellableCoroutine { cont ->
 *         val client = InstallReferrerClient.newBuilder(context).build()
 *         client.startConnection(object : InstallReferrerStateListener {
 *             override fun onInstallReferrerSetupFinished(code: Int) {
 *                 val answer = when (code) {
 *                     InstallReferrerClient.InstallReferrerResponse.OK -> InstallReferrer.Found(client.installReferrer.installReferrer)
 *                     InstallReferrerClient.InstallReferrerResponse.FEATURE_NOT_SUPPORTED -> InstallReferrer.None
 *                     else -> InstallReferrer.RetryLater
 *                 }
 *                 client.endConnection()
 *                 if (cont.isActive) cont.resume(answer)
 *             }
 *             override fun onInstallReferrerServiceDisconnected() { if (cont.isActive) cont.resume(InstallReferrer.RetryLater) }
 *         })
 *     }
 * }
 * ```
 */
public fun interface InstallReferrerSource {
    public suspend fun read(): InstallReferrer

    public companion object {
        /** No Play Install Referrer (tests, a build outside Play). */
        public val None: InstallReferrerSource = InstallReferrerSource { InstallReferrer.None }
    }
}
