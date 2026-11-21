package com.appgrowstack.links

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ShortLinkTest {
    @Test
    fun buildsGeneratedAndNamedLinks() {
        assertEquals("https://gohobishukan.com/l/Ab12Cd34", ShortLink.of("gohobishukan.com", "Ab12Cd34")?.url)
        assertEquals("https://gohobishukan.com/go/spring-sale", ShortLink.of("GohobiShukan.com", "spring-sale")?.url)
        assertEquals(ShortLink.Kind.GENERATED, ShortLink.of("gohobishukan.com", "Ab12Cd34")?.kind)
        assertEquals(ShortLink.Kind.NAMED, ShortLink.of("gohobishukan.com", "abcdefgh")?.kind)
    }

    @Test
    fun rejectsCodesOfNeitherShape() {
        assertNull(ShortLink.of("gohobishukan.com", "Bad_Code"))
        assertNull(ShortLink.of("gohobishukan.com", "-leading"))
        assertNull(ShortLink.of("gohobishukan.com", "x"))
        assertNull(ShortLink.of("", "spring-sale"))
    }

    @Test
    fun readsLinksBackFromUrls() {
        assertEquals(ShortLink.of("gohobishukan.com", "Ab12Cd34"), ShortLink.parse("https://www.gohobishukan.com/l/Ab12Cd34"))
        assertEquals("spring-sale", ShortLink.parse("https://gohobishukan.com/go/spring-sale/")?.code)
        assertNull(ShortLink.parse("https://gohobishukan.com/go/Ab12Cd34"), "a generated code under /go/")
        assertNull(ShortLink.parse("https://gohobishukan.com/l/spring-sale"), "a named code under /l/")
        assertNull(ShortLink.parse("http://gohobishukan.com/l/Ab12Cd34"))
        assertNull(ShortLink.parse("https://gohobishukan.com/articles/x"))
    }
}

class PlayStorePageTest {
    @Test
    fun readsPlayUrlsAndMarketUris() {
        assertEquals(
            PlayStorePage("com.kyoichi.readingmemory", "utm_source=gohobi&utm_campaign=after streak"),
            PlayStorePage.parse("https://play.google.com/store/apps/details?id=com.kyoichi.readingmemory&referrer=utm_source%3Dgohobi%26utm_campaign%3Dafter%20streak"),
        )
        assertEquals(PlayStorePage("com.example.app"), PlayStorePage.parse("market://details?id=com.example.app"))
        assertNull(PlayStorePage.parse("https://apps.apple.com/app/id6443590440"))
        assertNull(PlayStorePage.parse("https://play.google.com/store/apps/details?id=not a package"))
        assertNull(PlayStorePage.parse("https://play.google.com/store/apps/details"))
    }

    @Test
    fun roundTripsTheUrl() {
        val page = PlayStorePage("com.kyoichi.readingmemory", "utm_source=gohobi&utm_medium=cross_promo")
        assertEquals("https://play.google.com/store/apps/details?id=com.kyoichi.readingmemory&referrer=utm_source%3Dgohobi%26utm_medium%3Dcross_promo", page.url)
        assertEquals("market://details?id=com.kyoichi.readingmemory&referrer=utm_source%3Dgohobi%26utm_medium%3Dcross_promo", page.marketUri)
        assertEquals(page, PlayStorePage.parse(page.url))
        assertEquals(page, PlayStorePage.parse(page.marketUri))
    }
}

private class FakeResolver(vararg answers: AttributionResolution) : AttributionResolver {
    private val queue = ArrayDeque(answers.toList())
    val sent = mutableListOf<AttributionRequest>()

    override suspend fun resolve(request: AttributionRequest): AttributionResolution {
        sent += request
        return queue.removeFirstOrNull() ?: AttributionResolution.RetryLater
    }
}

private val organic = Attribution.Organic
private val link = Attribution(Attribution.MatchedBy.OPENED_URL, "x_post", campaign = "launch", code = "Ab12Cd34")
private const val INSTALL = "3f2a9c1e-0b7d-4e55-9a10-7c3d2b1a0f99"
private const val FIRST_OPEN = 1_800_000_000_000L

private fun reporter(
    storage: AttributionStorage,
    resolver: AttributionResolver,
    referrer: InstallReferrer = InstallReferrer.None,
    firstOpenAt: Long? = null,
    maxAttempts: Int = AttributionReporter.DEFAULT_MAX_ATTEMPTS,
) = AttributionReporter(
    installId = INSTALL, appVersion = "3.3.0", resolver = resolver, storage = storage,
    installReferrer = { referrer }, firstOpenAt = firstOpenAt, urlGraceMillis = 1, maxAttempts = maxAttempts,
    clock = { FIRST_OPEN }, sleep = {},
)

class AttributionReporterTest {
    @Test
    fun firstLaunchSendsTheOpeningUrlAndTheReferrer() = runTest {
        val storage = InMemoryAttributionStorage()
        val resolver = FakeResolver(AttributionResolution.Decided(link))
        val r = reporter(storage, resolver, InstallReferrer.Found("utm_source=x&code=Ab12Cd34"))
        assertTrue(r.isFirstLaunch)
        r.noteOpenedUrl("https://gohobishukan.com/l/Ab12Cd34")
        r.noteOpenedUrl("https://gohobishukan.com/l/Zz99Zz99") // only the first one counts
        assertEquals(link, r.reportIfNeeded())
        assertEquals(
            listOf(AttributionRequest(INSTALL, "android", "3.3.0", FIRST_OPEN, "utm_source=x&code=Ab12Cd34", "https://gohobishukan.com/l/Ab12Cd34")),
            resolver.sent,
        )
        assertTrue(storage.isFinished)
    }

    @Test
    fun theBodyIsTheContractsShape() {
        val json = Json { encodeDefaults = true; explicitNulls = false }
        val body = json.encodeToString(AttributionRequest.serializer(), AttributionRequest(INSTALL, appVersion = "3.3.0", firstOpenAt = 1, openedUrl = "https://a.example/l/Ab12Cd34"))
        val obj = Json.parseToJsonElement(body).jsonObject
        assertEquals(setOf("install_id", "platform", "app_version", "first_open_at", "opened_url"), obj.keys)
        assertEquals("android", obj.getValue("platform").jsonPrimitive.content)
        val answer = Json { ignoreUnknownKeys = true }.decodeFromString(
            Attribution.serializer(),
            """{"matched_by":"play_install_referrer","source":"youtube","medium":null,"campaign":"v12","code":null,"ad":null}""",
        )
        assertEquals(Attribution(Attribution.MatchedBy.PLAY_INSTALL_REFERRER, "youtube", campaign = "v12"), answer)
    }

    @Test
    fun withoutAUrlItWaitsBrieflyThenReportsOrganic() = runTest {
        val resolver = FakeResolver(AttributionResolution.Decided(organic))
        assertEquals(Attribution.MatchedBy.ORGANIC, reporter(InMemoryAttributionStorage(), resolver).reportIfNeeded()?.matchedBy)
        assertNull(resolver.sent.single().openedUrl)
        assertNull(resolver.sent.single().playInstallReferrer)
    }

    @Test
    fun playNotAnsweringWaitsForALaterLaunch() = runTest {
        val storage = InMemoryAttributionStorage()
        val resolver = FakeResolver(AttributionResolution.Decided(organic))
        assertNull(reporter(storage, resolver, InstallReferrer.RetryLater).reportIfNeeded())
        assertTrue(resolver.sent.isEmpty())
        assertFalse(storage.isFinished)
        assertEquals(organic, reporter(storage, resolver).reportIfNeeded())
    }

    @Test
    fun retriesOnALaterLaunchUntilAnswered() = runTest {
        val storage = InMemoryAttributionStorage()
        val resolver = FakeResolver(AttributionResolution.RetryLater, AttributionResolution.RetryLater, AttributionResolution.Decided(link))
        val first = reporter(storage, resolver)
        first.noteOpenedUrl("https://gohobishukan.com/l/Ab12Cd34")
        assertNull(first.reportIfNeeded())
        assertFalse(storage.isFinished)

        // Second launch: not the first any more, so a new URL is ignored, but the stored one is resent.
        val second = reporter(storage, resolver)
        assertFalse(second.isFirstLaunch)
        second.noteOpenedUrl("https://gohobishukan.com/go/later")
        assertNull(second.reportIfNeeded())
        assertEquals("Ab12Cd34", second.reportIfNeeded()?.code)
        assertEquals(List(3) { "https://gohobishukan.com/l/Ab12Cd34" }, resolver.sent.map { it.openedUrl })
        assertEquals(setOf(FIRST_OPEN), resolver.sent.map { it.firstOpenAt }.toSet())

        // Answered: nothing more is sent.
        assertEquals("Ab12Cd34", reporter(storage, resolver).reportIfNeeded()?.code)
        assertEquals(3, resolver.sent.size)
    }

    @Test
    fun aRejectedBodyIsNotSentAgain() = runTest {
        val storage = InMemoryAttributionStorage()
        val resolver = FakeResolver(AttributionResolution.Rejected)
        assertNull(reporter(storage, resolver).reportIfNeeded())
        assertTrue(storage.isFinished)
        assertNull(reporter(storage, resolver).reportIfNeeded())
        assertEquals(1, resolver.sent.size)
    }

    @Test
    fun givesUpAfterMaxAttemptsOfTryLater() = runTest {
        val storage = InMemoryAttributionStorage()
        val resolver = FakeResolver(AttributionResolution.RetryLater, AttributionResolution.RetryLater, AttributionResolution.RetryLater, AttributionResolution.Decided(link))
        assertNull(reporter(storage, resolver, maxAttempts = 3).reportIfNeeded())
        assertNull(reporter(storage, resolver, maxAttempts = 3).reportIfNeeded())
        assertFalse(storage.isFinished)
        assertNull(reporter(storage, resolver, maxAttempts = 3).reportIfNeeded())
        assertTrue(storage.isFinished)
        assertEquals(3, storage.attempts)
        // Finished without a source: nothing more is sent.
        assertNull(reporter(storage, resolver, maxAttempts = 3).reportIfNeeded())
        assertEquals(3, resolver.sent.size)
    }

    @Test
    fun theFirstOpenDateTheAppKnewIsSentForAPersonWhoUpdated() = runTest {
        val storage = InMemoryAttributionStorage()
        val resolver = FakeResolver(AttributionResolution.Decided(organic))
        reporter(storage, resolver, firstOpenAt = 1_700_000_000_000).reportIfNeeded()
        assertEquals(1_700_000_000_000, resolver.sent.single().firstOpenAt)
        assertEquals(1_700_000_000_000, storage.firstOpenAt)

        // A later date than the stored one does not move it.
        val other = InMemoryAttributionStorage(firstOpenAt = 1_600_000_000_000)
        reporter(other, FakeResolver(), firstOpenAt = 1_700_000_000_000)
        assertEquals(1_600_000_000_000, other.firstOpenAt)
    }

    @Test
    fun ignoresUrlsThatAreNotHttps() {
        val storage = InMemoryAttributionStorage()
        reporter(storage, FakeResolver()).noteOpenedUrl("gohobi://pair/abc")
        assertNull(storage.openedUrl)
    }

    @Test
    fun theFileStorageKeepsEverythingBetweenLaunches() = runTest {
        val directory = Files.createTempDirectory("links").toFile()
        val resolver = FakeResolver(AttributionResolution.Decided(link))
        val first = reporter(FileAttributionStorage(directory), resolver)
        first.noteOpenedUrl("https://gohobishukan.com/l/Ab12Cd34")
        assertEquals(link, first.reportIfNeeded())

        val stored = FileAttributionStorage(directory)
        assertEquals(FIRST_OPEN, stored.firstOpenAt)
        // Decided: the opening URL (it may carry an invite code) is not kept
        assertNull(stored.openedUrl)
        assertEquals(link, stored.attribution)
        assertTrue(stored.isFinished)
        assertFalse(reporter(stored, resolver).isFirstLaunch)
    }
}

private fun request(url: String? = null, referrer: String? = null) =
    AttributionRequest(INSTALL, appVersion = "3.3.0", firstOpenAt = 1, playInstallReferrer = referrer, openedUrl = url)

class DeviceAttributionResolverTest {
    private val resolver = DeviceAttributionResolver(setOf("gohobishukan.com"))
    private suspend fun decided(request: AttributionRequest) = (resolver.resolve(request) as AttributionResolution.Decided).attribution

    @Test
    fun theReferrerGivesTheUtmAndTheCode() = runTest {
        assertEquals(
            Attribution(Attribution.MatchedBy.PLAY_INSTALL_REFERRER, "youtube", "video", "v12"),
            decided(request(referrer = "utm_source=YouTube&utm_medium=video&utm_campaign=V12")),
        )
        assertEquals(
            Attribution(Attribution.MatchedBy.PLAY_INSTALL_REFERRER, "short_link", code = "Ab12Cd34"),
            decided(request(referrer = "code=Ab12Cd34")),
        )
        assertEquals(
            Attribution(Attribution.MatchedBy.PLAY_INSTALL_REFERRER, "invite", code = "Ab12Cd34Ef56Gh78"),
            decided(request(referrer = "utm_source=invite&code=Ab12Cd34Ef56Gh78")),
        )
    }

    @Test
    fun playsDefaultAndEmptyReferrersAreNotClues() = runTest {
        assertEquals(organic, decided(request(referrer = "utm_source=google-play&utm_medium=organic")))
        assertEquals(organic, decided(request(referrer = "utm_source=(not%20set)&utm_medium=(not%20set)")))
        assertEquals(organic, decided(request(referrer = "utm_source=invite")))
        assertEquals(organic, decided(request(referrer = "")))
        // Not a clue: the opening URL is next.
        assertEquals(
            Attribution(Attribution.MatchedBy.OPENED_URL, "short_link", code = "spring-sale"),
            decided(request(url = "https://gohobishukan.com/go/spring-sale", referrer = "utm_source=google-play&utm_medium=organic")),
        )
    }

    @Test
    fun theReferrerComesBeforeTheOpeningUrl() = runTest {
        assertEquals(
            Attribution.MatchedBy.PLAY_INSTALL_REFERRER,
            decided(request(url = "https://gohobishukan.com/l/Ab12Cd34", referrer = "utm_source=x")).matchedBy,
        )
    }

    @Test
    fun aShortLinkOnTheDomainGivesItsCodeAndUtm() = runTest {
        assertEquals(
            Attribution(Attribution.MatchedBy.OPENED_URL, "x_post", campaign = "launch-1", code = "Ab12Cd34"),
            decided(request(url = "https://www.gohobishukan.com/l/Ab12Cd34?utm_source=X%20Post&utm_campaign=Launch-1")),
        )
        assertEquals(
            Attribution(Attribution.MatchedBy.OPENED_URL, "short_link", code = "spring-sale"),
            decided(request(url = "https://gohobishukan.com/go/spring-sale")),
        )
    }

    @Test
    fun utmOnTheDomainIsTheSourceAndOtherHostsAreNotClues() = runTest {
        assertEquals(
            Attribution(Attribution.MatchedBy.OPENED_URL, "newsletter", "email"),
            decided(request(url = "https://gohobishukan.com/help/?utm_source=newsletter&utm_medium=Email")),
        )
        assertEquals(organic, decided(request(url = "https://example.com/l/Ab12Cd34?utm_source=x")))
        assertEquals(organic, decided(request(url = "https://gohobishukan.com/help/")))
    }

    @Test
    fun valuesAreNormalizedLikeTheServer() {
        assertEquals("x_post", DeviceAttributionResolver.norm("  X Post!! ", 40, hyphen = false))
        assertEquals("a__b", DeviceAttributionResolver.norm("a_ b", 40, hyphen = false))
        assertEquals("spring-sale", DeviceAttributionResolver.norm("Spring-Sale", 40, hyphen = true))
        assertEquals("spring_sale", DeviceAttributionResolver.norm("Spring-Sale", 40, hyphen = false))
        assertNull(DeviceAttributionResolver.norm("!!!", 40, hyphen = false))
    }
}
