# Changelog

All notable changes to `mido-client` are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.2.0] - 2026-05-26

### ⚠️ Breaking Changes

- **Removed the 3-arg `MidoClientFactory.baseRestClient(String, EndpointConfig, Charset)` overload.**
  Callers must now use the 4-arg form that takes an explicit `ContentType`. The removed overload
  silently defaulted to JSON, which produced wrong `Content-Type` headers for XML or other channels.
  If you only consumed the library via `getOrCreateClient(...)`, no change is required.

### Changed

- **Custom interceptor instantiation now fails fast.** A class that cannot be loaded, lacks a public
  no-arg constructor, or does not implement `ClientHttpRequestInterceptor` causes the first
  `MidoClientFactory.getOrCreateClient(...)` call for that channel to throw `IllegalStateException`
  naming both the channel and the offending class. The previous behavior — logging a warning and
  silently dropping the interceptor — could leave production missing required behavior with no
  obvious symptom.
- **Channel names are normalized to lowercase (`Locale.ROOT`).** YAML keys, the internal cache, and
  channel lookup now all behave consistently regardless of the casing the user typed. Previously the
  cache key was lowercased while the channel lookup was case-sensitive, so
  `getOrCreateClient("Payment")` on a `payment:` channel threw `Unknown Channel` even though the
  cache key already matched.

### Fixed

- **Request body logging honors the request `Content-Type` charset, then the channel charset, before
  falling back to UTF-8.** Previously the request body was always decoded as UTF-8, producing
  mojibake for non-UTF-8 channels (e.g. an EUC-KR endpoint).
- **`toLowerCase()` invocations now pass `Locale.ROOT`,** removing the Turkish-locale dotless-i
  pitfall in the cache key.

### Added

- **Public API javadoc.** Entry-point classes (`MidoClientFactory`, `MidoClientProperties` and its
  nested config types, `BaseExternalApi`, `ChannelContext`, `MidoClientAutoConfiguration`) and all
  four enums (`TokenType`, `EndpointType`, `ContentType`, `LogLevel`) now ship full javadoc.
  Internal interceptor classes are marked as such, leaving room to relocate them later.
- **Validation regression tests for `BindValidationException` cases** (URL scheme, blank URL,
  non-positive timeouts, missing primary, blank header name/value, etc.).
- **New unit tests for `MidoLoggingInterceptor`** verifying charset selection behavior.
- **New unit tests covering the interceptor fail-fast contract** (`ClassNotFoundException`, wrong
  interface, no public no-arg constructor).

### Documentation

- **Removed the incorrect `@Autowired` field-injection advice for custom interceptors** in
  `README.md` and `README.ko.md`. Because the interceptor is created via
  `Class.forName(...).newInstance()`, the resulting object is not a Spring-managed bean; field
  injection does not apply even with `@Component`. The README now documents the two patterns that
  actually work (`static` fields, `ApplicationContextHolder` escape hatch) and the new fail-fast
  contract.

## [1.1.2] - 2026-05-21

Maven Central release. See git history for changes prior to 1.2.0.

[1.2.0]: https://github.com/skaca8/mido-client/compare/v1.1.2...v1.2.0
[1.1.2]: https://github.com/skaca8/mido-client/releases/tag/v1.1.2