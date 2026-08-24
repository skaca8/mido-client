# Changelog

All notable changes to `mido-client` are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [3.3.0] - 2026-08-24

### Added

- **`interceptors:` entries can now name a Spring bean.** Each entry is resolved as a bean name
  first, falling back to the previous class-name reflection. Interceptors that need dependencies —
  a `MeterRegistry` for per-supplier latency, a `CircuitBreakerRegistry` — can finally be ordinary
  beans, and the `static` field / `ApplicationContextHolder` workarounds the README used to
  recommend are gone.

  The property stays `List<String>` in the same YAML position. Keeping it there is the point: a
  channel's whole behavior remains visible in one block, and per-endpoint targeting plus list order
  as execution order are exactly what a `RestClientCustomizer`-style bean would scatter across code.

  Resolution order: a registered bean name; otherwise a loadable class name, using the sole bean of
  that type if there is exactly one and a reflective instance otherwise; otherwise
  `IllegalStateException` at startup. Two or more beans of the type falls back to reflection with a
  warning naming the candidates, so an application that already registers several keeps working.

  **Bean lookup is deferred to the first request, by design.** `getOrCreateClient` is routinely
  called from a consumer's constructor, so fetching an interceptor bean at build time would force it
  and its dependencies into existence mid-construction — turning ordinary wiring into a circular
  reference. The interceptor is wrapped in a delegate that resolves on first `intercept` and caches
  under double-checked locking. A regression test proves it: a `@Component` that calls
  `getOrCreateClient` in its constructor, with an interceptor bean that depends on that very
  component, starts cleanly; making the lookup eager fails that test.

  Startup validation covers what it can without instantiating anything: a bean name is checked with
  `containsBean` / `getType`, a class name is loaded and inspected. A bean that exists but cannot be
  *created* still surfaces on first request rather than at startup.

  **Upgrade note:** listing a class name whose class is also registered as a bean now resolves to
  that bean rather than a separate reflective instance. That is usually the fix you wanted, but it is
  a behavior change — list a non-bean class to keep the old behavior.

- **Startup warning for a `@ChannelName` class that is not a Spring bean.** The validator could only
  see bean definitions, so a manually instantiated adapter was never checked at all — its
  `@ChannelAction` methods are inert and nothing said so. The application's own auto-configuration
  packages are now scanned for the class-level annotation, and any match without a corresponding bean
  is reported. Abstract classes and interfaces are skipped, since `@ChannelName` is `@Inherited` and
  an abstract base declaring the channel can never be a bean. A warning rather than a failure:
  instantiating such a class deliberately and binding the context by hand is valid.

  Reflective invocations and objects wrapped in a proxy the library did not create remain
  undetectable at startup, and are now documented as such rather than left implied.

### Changed

- **`FailureType.DNS` and `FailureType.CONNECT` no longer claim the request "never left the
  client".** Verified against the JDK: `HttpClient` follows redirects for POST as well, and
  `307`/`308` re-send the original method and body, so a DNS or connect failure can occur on a later
  hop after an earlier host was already reached. `NOT_DELIVERED` in that case means "not delivered to
  the host that failed"; every host reached before it answered with a redirect instead of performing
  the operation. The `Delivery` values are unchanged — that reading holds whenever the server behaves
  — but the wording overstated it, and the classifier cannot see the redirect chain to know better.
  Documented in the javadoc and both READMEs.

## [3.2.1] - 2026-08-21

### Fixed

- **`FailureType.TLS` no longer claims the request was not delivered.** Its `Delivery` changes from
  `NOT_DELIVERED` to `UNKNOWN`. The classifier matched on `SSLException`, which covers a failed
  handshake (before the request is sent) *and* a protocol failure raised while reading the response
  (after it was sent and possibly processed). Labelling both `NOT_DELIVERED` invited exactly the
  duplicate that label is supposed to prevent. Distinguishing the handshake case was considered and
  rejected: a TLS failure does not succeed on retry either way, so the extra precision buys nothing.
  The enum's shape is unchanged, so this is source-compatible; only the meaning is now safe.

### Changed

- **The `FailureType` documentation no longer tells you to retry on `NOT_DELIVERED`.** The previous
  example (`if (delivery == NOT_DELIVERED) retry();`) conflated three separate things: what the
  transport observed, whether an operation may be re-run, and what makes re-running harmless. Only
  the first is answerable from an exception. A non-idempotent call needs an idempotency key
  regardless of the classification, because `UNKNOWN` is a frequent and unavoidable answer —
  `read-timeout-seconds` is a whole-exchange deadline, so `timeout` routinely means "processed, but
  the response was too slow". The README and javadoc now separate the three questions and show the
  place where the classifier genuinely pays off: a `Predicate<Throwable>` for declarative retry
  configuration (Resilience4j / Spring Retry), which needs no dependency beyond the JDK.

### Corrected

- **The rationale given in 3.0.0 for not wrapping exceptions was partly wrong.** That release
  argued, in the changelog and in the commit message for `FailureType`, that wrapping transport
  exceptions in a library type would break Resilience4j and Spring Retry exception matching. Stated
  that broadly, it is false. It holds only for wrapping *inside* the interceptor chain — where the
  logging interceptor sits, and where a Resilience4j interceptor placed outermost would indeed
  observe the wrapper instead of the original. Wrapping *outside* the whole chain avoids it entirely,
  and this library already has two places that could do so: `BaseExternalApi.withDefaultChannelAction`
  and `ChannelActionAspect`, both of which wrap the caller's invocation rather than sitting in the
  chain.

  The decision not to wrap stands, but on the narrower argument: wrapping changes the *identity* of
  the exception, so `catch (SocketTimeoutException ...)` and
  `retryOnException(e -> e instanceof HttpTimeoutException)` stop matching, even though a wrapper
  extending `ResourceAccessException` would keep `catch (RestClientException ...)` working. That is a
  real compatibility cost to impose as a library's default behavior, and it is the reason —
  not the Resilience4j claim.

  Opt-in wrapping behind a property was considered and rejected: it doubles the behavioral surface
  of every future change, which is the same reason the `client-type` switch was removed in 3.0.0.

## [3.2.0] - 2026-08-21

### Added

- **Startup validation of `@ChannelAction` usage, including self-invocation detection.** 3.1.0 shipped
  the annotations with their proxy limits documented; documentation does not stop anyone from hitting
  them. A `ChannelActionValidator` now inspects every bean carrying `@ChannelAction` once the
  singletons exist.

  Fatal, because the advice provably cannot apply: `@ChannelAction` on a class without
  `@ChannelName`, on a `private` or `static` method (never matched by the `@annotation` pointcut), or
  on a `final` method (CGLIB cannot override it). Previously the first of these surfaced on the first
  call and the other two never surfaced at all.

  Warned about, not fatal: a call to an annotated method from an unannotated method of the same class
  — the self-invocation that bypasses the proxy. Detected by reading the class bytes with the ASM
  already inside spring-core, so no dependency is added. The bytecode cannot prove the receiver is
  `this` rather than another instance of the same type, so failing startup would occasionally be
  wrong; a call from a method that is itself annotated is not reported, since the context is already
  bound. Any failure inside the scan degrades to "no findings" and never breaks startup.

  Also warned about: `@ChannelAction` used with no AspectJ runtime on the classpath, which previously
  gave no feedback at all.

  **Upgrade note:** an application that already has a `private`, `static`, or `final`
  `@ChannelAction` method will now fail to start. Those annotations were doing nothing, so the fix is
  to remove them or make the method proxyable — but the failure is new.

## [3.1.0] - 2026-08-21

### Added

- **Declarative `ChannelContext` binding via `@ChannelName` + `@ChannelAction`.** Annotate the class
  with the channel it talks to and the methods with the action, instead of wrapping every call in
  `withDefaultChannelAction("...", () -> ...)`. The action key is unchanged
  (`"<channel>.<action>"`); `@ChannelAction` with no value uses the method name.

  The two axes are separate annotations on purpose: the channel belongs to the class (which external
  system), the action to the method (which call). A single annotation carrying both would let the
  channel vary per method and break the "one class = one channel" invariant, so there is no
  method-level channel override — split the class instead.

  **aspectjweaver is not a new dependency.** It is declared `compileOnly`, and the aspect is
  registered behind `@ConditionalOnClass("org.aspectj.weaver.Advice")`. A consumer that adds
  `spring-boot-starter-aop` gets the annotations; one that does not sees them as inert and everything
  else behaves identically. Define your own `ChannelActionAspect` bean to replace the default.

  Known limits, documented rather than papered over: the advice is proxy-based, so it does not apply
  to self-invocation, `private`/`final` methods, or non-beans — the annotation being present is not
  proof that it took effect. That is the one way the declarative form is worse than the lambda, where
  a missing wrapper is visible in the code. `@ChannelAction` on a class without `@ChannelName` throws
  `IllegalStateException` naming the class and method rather than silently logging `unknown`.

  `BaseExternalApi` is **not** deprecated. It covers exactly the paths proxying cannot reach, and the
  README documents when to pick which.

## [3.0.0] - 2026-08-20

### ⚠️ Breaking Changes

- **The pluggable HTTP transport is gone; every channel now uses `JdkClientHttpRequestFactory`.**
  The `ClientType` enum, `mido-client.client-type`, and the per-endpoint `client-type` are
  **removed** — a configuration point with one possible value is not a configuration point. The
  `simple` transport (`SimpleClientHttpRequestFactory` over `HttpURLConnection`) reused connections
  through the JVM-global keep-alive cache, so every channel shared one pool; it directly contradicted
  the per-channel isolation this library exists to provide. `java.net.http.HttpClient` gives each
  channel/endpoint its own pool, plus HTTP/2.

  Removing the escape hatch is safe because the reason to keep one turned out not to exist: both
  transports honor `http.proxyHost` / `https.proxyHost`. mido-client never calls
  `HttpClient.Builder.proxy()`, and a builder that does not is documented to use
  `ProxySelector.getDefault()`, which reads those system properties. Earlier releases of this
  changelog and README claimed otherwise — that claim was wrong.

  **What to check when upgrading**: `read-timeout-seconds` changes meaning. On `simple` it was a
  socket idle timeout; it is now a whole-exchange deadline covering request send through response
  body consumption, surfacing as `HttpTimeoutException`. Size it against total expected call time.
  Remove any `client-type` keys from YAML — an unknown property is not rejected by default, but it no
  longer does anything. Code referencing `io.github.hyunjun.mido.constant.ClientType` no longer
  compiles.

### Added

- **`FailureType.classify(Throwable)` classifies a failed call without wrapping the exception.**
  Returns `DNS` / `TLS` / `CONNECT` / `TIMEOUT` / `CLIENT_ERROR` / `SERVER_ERROR` / `UNKNOWN`, each
  carrying a `Delivery` verdict (`NOT_DELIVERED` / `DELIVERED` / `UNKNOWN`) that answers the question
  a caller actually has: may a non-idempotent request be retried? mido-client deliberately does
  **not** introduce its own exception hierarchy — `RestClientException`, `SocketTimeoutException`,
  and `HttpTimeoutException` still propagate unchanged, so existing handlers and Resilience4j
  exception predicates keep working. The `[mido-client failure]` log line now carries
  `failureType` / `delivery` as well.
- **`log-max-body-bytes` (per endpoint, default `8192`) caps how much body reaches a log line.**
  The remainder becomes `...(truncated N bytes)` and is never materialized as a String. `0` disables
  the cap. Note this changes existing behavior: bodies over 8 KB are truncated in the log by
  default. Set `log-max-body-bytes: 0` to keep full bodies.
- **`log-body` (per endpoint, default `true`) keeps request/response bodies out of the logs.** Set it
  to `false` on endpoints carrying PII, card, or token data: the body is not read at all (omission,
  not masking) and the log line shows `body: (omitted)`, while status, elapsed time, and channel
  action are still recorded.
- **Startup validation of `charset` and `interceptors`.** `MidoClientFactory` now implements
  `InitializingBean` and fails the context with a message naming the channel and endpoint when a
  `charset` is unknown, or an `interceptors[]` entry cannot be loaded, does not implement
  `ClientHttpRequestInterceptor`, or has no public no-arg constructor. Previously these surfaced on
  the first request to the channel. Interceptor classes are loaded and inspected but **not
  instantiated**, so a constructor with side effects does not run twice.

### Fixed

- **A channel without a `secondary` endpoint no longer builds two identical clients.**
  `getOrCreateClient(name, SECONDARY)` fell back to the primary *configuration* but cached under the
  `-secondary` key, so the channel ended up with two identically configured `RestClient` instances —
  and, on the `jdk` transport, two `HttpClient` instances with two connection pools. The fallback is
  now applied before the cache lookup, so both calls return the same cached client.
- **`HttpClient` instances are now released on context shutdown.** `MidoClientFactory` implements
  `DisposableBean` and calls `HttpClient.shutdown()` on every client it built. `shutdown()` rather
  than `close()`, deliberately: it is non-blocking, so an in-flight request cannot stall context
  shutdown. Previously the instances were unreachable once handed to `JdkClientHttpRequestFactory`
  and lingered until GC — visible across devtools restarts and repeated test contexts. The tracking
  list holds `WeakReference`s so that a one-off client built through the public `baseRestClient(...)`
  and then dropped stays collectable; pinning it would keep its selector thread alive for the life of
  the JVM.
- **gzip request compression skips an empty body regardless of `min-size`.** With `min-size: 0` a
  bodyless `GET` was given `Content-Encoding: gzip` and a ~20-byte gzip header.
- **`RestClient`'s default message converters are no longer discarded.** `configureMessageConverters`
  called `converters.clear()` before registering String + Jackson, which removed
  `ByteArrayHttpMessageConverter`, `ResourceHttpMessageConverter`, and
  `AllEncompassingFormHttpMessageConverter` — so `body(byte[].class)`, resource downloads, and
  form/multipart uploads were impossible. Only the default `String` converter is now replaced (to
  apply the channel `charset`); everything else, Jackson included, is left in place. A consumer with
  `jackson-dataformat-xml` on the classpath now also gets POJO ↔ XML on `type: xml` channels.
- **A channel-declared header now overrides mido-client's own default.** Custom headers were added
  with `HttpHeaders.add`, so a channel declaring `Accept: application/json` sent
  `Accept: */*, application/json` — equal q-values, letting the server pick either. The first
  declaration of a name now uses `set`; repeated declarations of the same name still accumulate.
- **gzip request compression no longer drops `Content-Length`.** `MidoGzipRequestInterceptor` removed
  the header instead of updating it, leaving the transport without a body length, so
  `JdkClientHttpRequest` fell back to a length-less `BodyPublisher` and the request went out chunked.
  Servers that reject chunked request bodies answered `411`/`400`. The header is now set to the
  compressed body length.
- **Custom interceptors now resolve through the default class loader.** `Class.forName(String)` used
  the class loader that loaded mido-client, which cannot see consumer classes loaded by
  spring-boot-devtools' `RestartClassLoader` — an interceptor named in `interceptors:` failed with
  `ClassNotFoundException` under devtools. Lookup now goes through
  `ClassUtils.forName(name, ClassUtils.getDefaultClassLoader())`.

### Changed

- **Transport failures are now logged.** When the request fails before a response arrives (connect /
  read timeout, DNS, TLS), `MidoLoggingInterceptor` emits a `[mido-client failure]` line at `error`
  level carrying the channel action, method, URL, elapsed time, and exception type/message, then
  rethrows unchanged. Previously only the request line was logged, so failed calls left no duration
  or cause behind. The line honors `log: off` and the `console` / `file` / `all` destinations.
- **Response log severity now follows the status code.** 5xx logs at `error`, 4xx at `warn`,
  everything else at `info`; previously every line was `info`, so a failing channel was invisible to
  level-based alerting. `LogLevel` (`off` / `console` / `file` / `all`) remains what it always was —
  the destination, not the severity.
- **`BaseExternalApi.withDefaultChannelAction` logs a failed action at `debug` instead of `error`.**
  With 4xx/5xx and transport failures now logged at `warn`/`error` by the interceptor, and the
  exception still propagating to the caller, the `error` line was the third copy of the same failure.
- **A missing `MidoClientFileLog` logger is reported at startup.** When an endpoint uses `log: file`
  or `log: all` and no appender is attached to that logger, the lines silently went to root. A
  warning is now emitted. Logback only; on other SLF4J bindings the check is skipped rather than
  guessed at.
- **`Unknown Channel` errors now list the configured channel names**, so a typo or casing mistake is
  diagnosable from the message alone.

## [2.0.0] - 2026-07-02

### ⚠️ Breaking Changes

- **Java baseline raised to 25.** `ChannelContext` is now built on the final `java.lang.ScopedValue`
  API (JEP 506, Java 25). The library is compiled to Java 25 bytecode and can only be consumed on
  Java 25+.
- **Spring Boot baseline raised to 3.5.x** (Spring Framework 6.2); the library is built and tested
  against Spring Boot 3.5.16. Consumers need a Spring Framework 6.2 release whose bundled ASM can
  read the library's Java 25 bytecode — Spring Boot 3.2.x's ASM cannot.
- **`ChannelContext` API changed to a scoped model.** `setChannelAction(String)` and `clear()` are
  **removed** — the mutate-then-clear pair does not exist in the `ScopedValue` model. Replace them
  with the new scope-running methods:
  - `ChannelContext.callWithChannelAction(action, op)` — binds the action for the call and returns
    the operation's value. `op` is a `ScopedValue.CallableOp`, so checked exceptions thrown by the
    body propagate unchanged (no wrapping needed when migrating from the old try/finally pattern).
  - `ChannelContext.runWithChannelAction(action, runnable)` — void form.
  `getChannelAction()` and `isBound()` are unchanged. Code using `BaseExternalApi
  .withDefaultChannelAction(...)` needs no changes — its public signature is unchanged.

### Changed

- **`BaseExternalApi.withDefaultChannelAction(...)` now delegates context binding to
  `ChannelContext.callWithChannelAction(...)`.** Behavior is otherwise the same (debug log on entry,
  error log + rethrow on failure).

### Fixed

- **Nested channel actions no longer clobber the outer action.** The action is bound for the dynamic
  extent of the call and the mirrored MDC value is saved/restored, so an outer action is correctly
  visible again after a nested call returns. The previous `ThreadLocal` + `clear()` implementation
  wiped the outer context when an inner call finished.

## [1.3.0] - 2026-07-01

### Added

- **Pluggable HTTP transport via `client-type` (`simple` / `jdk`).** Choose the underlying request
  factory globally (`mido-client.client-type`) or per endpoint
  (`channels.<name>.<endpoint>.client-type`); an endpoint inherits the global value when unset.
  The default is `simple` (`SimpleClientHttpRequestFactory`, `HttpURLConnection`), so existing
  configurations keep their current behavior. `jdk` uses `JdkClientHttpRequestFactory`
  (`java.net.http.HttpClient`), giving each channel its own connection pool and HTTP/2 — the
  transport that actually realizes per-channel connection isolation. The JDK transport follows
  redirects (`Redirect.NORMAL`, which refuses HTTPS→HTTP downgrades) and applies the same
  `connect-timeout-seconds` / `read-timeout-seconds`.

### Changed

- **Removed duplicated default values between `MidoClientProperties` and `MidoClientFactory`.**
  Timeout and gzip defaults now live solely in the properties class; the factory-side `?:` fallbacks
  (a second, drift-prone source of truth) were removed. `read-timeout-seconds`,
  `connect-timeout-seconds`, `gzip.min-size`, and `gzip.max-decompressed-size` are now `@NotNull`, so
  an explicitly-null YAML value fails fast at startup instead of silently falling back to a default.
- **Internal: extracted a logging `emit()` helper** in `MidoLoggingInterceptor`, unifying the
  console/file logger selection across request and response logging. No behavior change.
- **Internal: replaced wildcard imports** (`java.util.*`, `java.nio.charset.*`) with explicit imports.

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

[3.3.0]: https://github.com/skaca8/mido-client/compare/v3.2.1...v3.3.0
[3.2.1]: https://github.com/skaca8/mido-client/compare/v3.2.0...v3.2.1
[3.2.0]: https://github.com/skaca8/mido-client/compare/v3.1.0...v3.2.0
[3.1.0]: https://github.com/skaca8/mido-client/compare/v3.0.0...v3.1.0
[3.0.0]: https://github.com/skaca8/mido-client/compare/v2.0.1...v3.0.0
[2.0.1]: https://github.com/skaca8/mido-client/compare/v2.0.0...v2.0.1
[2.0.0]: https://github.com/skaca8/mido-client/compare/v1.3.0...v2.0.0
[1.3.0]: https://github.com/skaca8/mido-client/compare/v1.2.0...v1.3.0
[1.2.0]: https://github.com/skaca8/mido-client/compare/v1.1.2...v1.2.0
[1.1.2]: https://github.com/skaca8/mido-client/releases/tag/v1.1.2