# kotlin-app-links

Know where an Android install came from without guessing, link to your app from your own domain, and
open another app's Google Play page with a referrer it can read.

The Kotlin side of [swift-app-links](https://github.com/appgrowstack/swift-app-links). Both send the
same clues in the same shape, normalize values the same way, and read the same short links.

![Kotlin](https://img.shields.io/badge/Kotlin-2.4-purple.svg)
![JVM](https://img.shields.io/badge/JVM-17-blue.svg)
![License](https://img.shields.io/badge/License-MIT-yellow.svg)

> Developed in a private monorepo and copied here one way. Issues are welcome; pull requests are
> not accepted.

## Features

- **Deterministic clues only.** The first launch uses the Play Install Referrer and the App Link that
  opened it. No IP address, device fingerprint, click timing or clipboard. With neither clue the
  answer is `organic`
- **Decided on the device.** `DeviceAttributionResolver` reads the referrer's utm and short link
  code, then the short link and utm of the opening URL on your domains. Play's own default
  (`google-play` / `organic`) is not a clue. A resolver that asks a server instead is a drop-in
  replacement (`AttributionResolver`)
- **Once per install, retried until decided.** The reporter waits briefly on the first launch for the
  opening URL, then resolves. Play not answering or offline: it is resolved again on the next launch.
  The first answer is kept, so a later link never rewrites the source
- **Short links on your own domain.** `ShortLink` builds and reads `https://<domain>/l/<code>`
  (generated) and `https://<domain>/go/<code>` (named). Links are made on a server, never by the
  device
- **Play product pages.** `PlayStorePage` builds and reads `https://play.google.com/store/apps/details`
  and `market://details` with the `referrer` the other app's Play Install Referrer hands over
- **No Android dependency.** `links-core` is Kotlin/JVM (coroutines and kotlinx.serialization). The
  app passes the Play Install Referrer through `InstallReferrerSource` and opens pages with its own
  `Intent`

## Quick Start

```kotlin
val attribution = AttributionReporter(
    installId = installId,                         // the id your analytics uses for this install
    appVersion = BuildConfig.VERSION_NAME,
    resolver = DeviceAttributionResolver(setOf("gohobishukan.com")),
    storage = FileAttributionStorage(context.filesDir),
    installReferrer = playInstallReferrer,         // InstallReferrerSource over Google's installreferrer library
)

// In the launcher activity: the App Link that opened the app.
intent?.data?.toString()?.let(attribution::noteOpenedUrl)

// Every launch; it resolves only until decided.
lifecycleScope.launch {
    attribution.reportIfNeeded()?.let { analytics.installAttributed(it) }
}
```

`InstallReferrerSource`'s documentation has the dozen lines that wrap
`com.android.installreferrer:installreferrer`.

Short links and Play pages:

```kotlin
val link = ShortLink.of("gohobishukan.com", "spring-sale")!!    // https://gohobishukan.com/go/spring-sale
ShortLink.parse(incomingUrl)?.let { route(it.code) }

val page = PlayStorePage("com.example.reading", referrer = "utm_source=gohobi&utm_medium=cross_promo")
context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(page.marketUri)).setPackage("com.android.vending"))
```

## Installation

JitPack: `com.github.appgrowstack:kotlin-app-links:0.3.0`.

## Requirements

- Kotlin 2.4, JVM 17

## License

MIT — see [LICENSE](LICENSE).
