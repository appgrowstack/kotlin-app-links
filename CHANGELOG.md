# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/).

## [Unreleased]

## [0.2.0] - 2026-11-22

### Added

- `links-core`: the Kotlin side of swift-app-links. `LinksSdk.NAME` and `LinksSdk.VERSION` for a connector to report.
- `AttributionReporter`: reports where an install came from once, on the first launch, from the
  Play Install Referrer (`InstallReferrerSource`, which the app wraps around Google's library) and
  the App Link that opened the first launch. It retries on later launches until the resolver
  decides, and keeps the answer (`FileAttributionStorage`, `InMemoryAttributionStorage`).
- `DeviceAttributionResolver`: decides the source on the device from the referrer's utm and short
  link code, then the opening URL's short link and utm on your domains. Play's default
  `google-play` / `organic` is not a clue. Values are normalized the same way as on a server.
- `ShortLink`: builds and reads `https://<domain>/l/<code>` and `https://<domain>/go/<code>`.
- `PlayStorePage`: builds and reads Google Play product page URLs and `market://` URIs with a
  `referrer`.
