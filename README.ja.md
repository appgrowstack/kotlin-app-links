[English](./README.md) | 日本語

# kotlin-app-links

Android で入れた人がどこから来たかを推し量らずに決め、自分のドメインの短いリンクでアプリへ案内し、別のアプリの Google Play のページを、そのアプリが読める referrer 付きで開く。

[swift-app-links](https://github.com/appgrowstack/swift-app-links) の Kotlin の側。どちらも同じ形で手がかりを送り、値を同じ形に揃え、同じ短いリンクを読む。

> 開発は非公開のモノリポでしていて、ここへは片方向にコピーしている。Issue は受ける。PR は受けない。

## できること

- **確かな手がかりだけ。** 最初の起動で使うのは Play Install Referrer と、起動を開いた App Link だけ。IP・端末の指紋・タップの時刻・クリップボードは使わない。どちらも無ければ `organic`
- **端末で決める。** `DeviceAttributionResolver` は referrer の utm と短いリンクの code、次に自分のドメインで開いた URL の短いリンクと utm を読む。Play の既定（`google-play`・`organic`）は手がかりにしない。サーバーに聞く resolver（`AttributionResolver`）に差し替えられる
- **入れた人ごとに1回。決まるまで次の起動で試す。** 最初の起動は開いた URL を少し待ってから決める。Play が答えない・通信できないときは次の起動でもう一度。最初の答えを残すので、後で開いたリンクで流入元は変わらない
- **自分のドメインの短いリンク。** `ShortLink` は `https://<ドメイン>/l/<code>`（作ったリンク）と `https://<ドメイン>/go/<code>`（名前を付けたリンク）を作り、読む。リンクを作るのはサーバーで、端末は作らない
- **Play の製品ページ。** `PlayStorePage` は `https://play.google.com/store/apps/details` と `market://details` を、相手のアプリの Play Install Referrer に渡る `referrer` 付きで作り、読む
- **Android に依存しない。** `links-core` は Kotlin/JVM（coroutines と kotlinx.serialization）。Play Install Referrer はアプリが `InstallReferrerSource` で渡し、ページはアプリの `Intent` で開く

使い方は [README.md](./README.md) の Quick Start。入れ方は JitPack の `com.github.appgrowstack:kotlin-app-links:<版>`。

## ライセンス

MIT（[LICENSE](LICENSE)）。
