# kotlin-app-links

Androidで入れた人がどこから来たかを推し量らずに決め、自分のドメインからアプリへリンクし、別のアプリのGoogle Playのページを、そのアプリが読めるreferrer付きで開く。

[English](./README.md)

[swift-app-links](https://github.com/appgrowstack/swift-app-links)のKotlinの側。どちらも同じ手がかりを同じ形で送り、値を同じように揃え、同じ短いリンクを読む。

![Kotlin](https://img.shields.io/badge/Kotlin-2.4-purple.svg)
![JVM](https://img.shields.io/badge/JVM-17-blue.svg)
![License](https://img.shields.io/badge/License-MIT-yellow.svg)

> 開発は非公開のモノリポでしていて、ここへは片方向にコピーしている。Issueは受ける。PRは受けない。

## できること

- **確かな手がかりだけ。** 最初の起動で使うのはPlay Install Referrerと、起動を開いたApp Linkだけ。IPアドレス・端末の指紋・タップの時刻・クリップボードは使わない。どちらも無ければ答えは `organic`
- **端末で決める。** `DeviceAttributionResolver` はreferrerのutmと短いリンクのcode、次に自分のドメインで開いたURLの短いリンクとutmを読む。Playの既定（`google-play`・`organic`）は手がかりにしない。サーバーに問い合わせるresolverに差し替えられる（`AttributionResolver`）
- **入れた人ごとに1回・決まるまでやり直す。** 最初の起動では開いたURLを少し待ってから決める。Playが答えない・通信できないときは次の起動でもう一度決める。最初の答えを持ち続けるので、後で開いたリンクで流入元は変わらない
- **自分のドメインの短いリンク。** `ShortLink` は `https://<domain>/l/<code>`（作った物）と `https://<domain>/go/<code>`（名前を付けた物）を作って読む。リンクを作るのはサーバーで、端末は作らない
- **Playの製品ページ。** `PlayStorePage` は `https://play.google.com/store/apps/details` と `market://details` を、相手のアプリのPlay Install Referrerに渡る `referrer` 付きで作って読む
- **Androidに依存しない。** `links-core` はKotlin/JVM（coroutinesとkotlinx.serialization）。Play Install Referrerはアプリが `InstallReferrerSource` で渡し、ページはアプリの `Intent` で開く

## はじめに

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

`com.android.installreferrer:installreferrer` を包む十数行は、`InstallReferrerSource` のドキュメントにある。

短いリンクとPlayのページ:

```kotlin
val link = ShortLink.of("gohobishukan.com", "spring-sale")!!    // https://gohobishukan.com/go/spring-sale
ShortLink.parse(incomingUrl)?.let { route(it.code) }

val page = PlayStorePage("com.example.reading", referrer = "utm_source=gohobi&utm_medium=cross_promo")
context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(page.marketUri)).setPackage("com.android.vending"))
```

## 入れ方

JitPack: `com.github.appgrowstack:kotlin-app-links:0.3.0`。

## 動く環境

- Kotlin 2.4、JVM 17

## ライセンス

MIT（[LICENSE](LICENSE)）。
