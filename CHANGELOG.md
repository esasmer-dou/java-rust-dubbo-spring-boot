# Changelog

## 0.8.0 - 2026-10-29

- Connect direct Hessian-to-JSON lists to an optional Rust-Java REST owned-body
  adapter. Support concrete nested lists without consumer row DTO construction;
  preserve correct finite numeric formatting, UTF-8 escaping and explicit schema failures.
- Keep the native wire across one exact-size HTTP buffer retry. Do not replay
  the RPC. Close on success, malformed input, cancellation and abandoned retries.
- Add startup-only plain-Java generated client assembly without Spring or proxies.
- Replace provider output and pending-completion maps with fixed generational
  atomic slots. Timeout does not revoke a live Java borrow; wire/response credit
  follows the actual callback and outbound body. Native ABI remains 3.
- Bound retained native consumer responses to 256 generational handles and 32 MiB,
  without payload copies or a per-response registry mutex. Failed JNI exposure
  releases ownership. Reject capacity without RPC replay; expose limits and
  rejection metrics. The new direct path checks response policy 1 before dispatch.
- Move the sole asynchronous callback reference onto the bounded attached callback
  lane. Its admission permit covers RPC, queued and executing work; I/O workers do
  not invoke Java or attach for normal callback cleanup. Normal overload fails
  before dispatch; an impossible internal queue invariant is terminal, not a retry.
- Write native provider headers and body through a bounded vectored-write cursor;
  preserve partial writes, one deadline, body ownership and connection serialization.
- Resolve the exact provider JNI dispatcher method at startup and retain its
  owning global object. Dispatch with six stack arguments instead of reparsing
  the signature, resolving the method and allocating an argument Vec per RPC.
  Java business signatures and native ABI stay unchanged.
- Reuse one connection-local primitive invocation header plan (at most 1 KiB of
  metadata) instead of rebuilding fixed service/method/attachment graphs per RPC.
  Learn only from scalar wire arguments, not just a primitive method signature;
  never replace the learned plan on metadata churn. Validate every argument and
  exact metadata match; use the full bounded parser
  for object graphs or changed metadata. Share the immutable execution-lane name
  instead of cloning its String on each dispatch. Do not cache business responses.
- Ship coordinated Linux x64, Windows x64 and macOS ARM64 native artifacts.
  Use the native JAR matching this release; native ABI remains 3.

## 0.7.0 - 2026-10-16

- Keep one-pass consumer reference retention bounded to the root and current item. Cross-item references require normal List materialization; they fail explicitly in one-pass mode instead of retaining the full DTO graph.
- Reject truncated variable-length streaming lists instead of reporting a successful partial result.

- Correct standard Hessian2 integer-thousandth double decoding and use full-width fractional double output for mixed-peer compatibility.
- Keep typed/generated object, list, map, enum, array, and temporal reference slots aligned with dynamic `Q` references; preserve mutable cyclic objects such as Throwable causes.
- Add build-time `@DubboService(timeout=...)` and per-implementation-method `@DubboMethod(timeoutMs=...)` deadlines, including queue wait and asynchronous completion.
- Keep native response buffers and admission permits alive until actual business completion after a network timeout. Do not start expired queued work or replay timed-out writes.
- Expose a startup-bound provider telemetry SPI for the optional Glowroot native-Dubbo adapter, without introducing an agent, Apache Dubbo, Netty, or reflection dependency into the runtime.
- Preserve native ABI `3` and the original JNI registration entry point. Method deadlines require a rebuilt native library with the additive timeout entry point.

## 0.6.0 - 2026-09-30

- Add compile-time `@DubboStreamed` validation for synchronous concrete `List<T>` contract methods.
- Keep the public Java method signature unchanged while exposing a native-backed `DubboStreamingResult<T>` to one-pass response writers.
- Decode and write one consumer DTO at a time instead of retaining the full Java result graph.
- Release native response ownership deterministically on exhaustion, explicit close, decode failure, and HTTP client disconnect.
- Preserve regular `List` semantics through on-demand materialization for non-streaming callers.
- Keep one database query and one Dubbo request/response; no chatty offset-pagination protocol is introduced.
- Preserve native ABI `3`, Dubbo wire compatibility, provider behavior, and existing non-annotated methods.

## 0.5.0 - 2026-09-29

- Add the opt-in `DubboStreamingList<T>` provider contract while preserving public `List<T>` service signatures.
- Stream unpaged database rows directly through generated Hessian codecs into growable native response buffers, avoiding a fully materialized provider list and redundant request-argument copies.
- Add deferred Hessian list framing and value skipping with bounded depth, collection, and payload checks.
- Detach codec contexts and release JDBC/native response resources deterministically on success, business failure, encoding failure, timeout, and disconnect paths.
- Preserve generated clients, Spring annotations, DTO contracts, Dubbo wire compatibility, GLIBC 2.17 support, and native ABI `3`.

## 0.4.1 - 2026-09-07

- Preserve provider business exception messages, reported exception types, and bounded cause chains across Rust and Apache Dubbo consumer/provider combinations.
- Encode synchronous and asynchronous Rust-provider failures as standard Dubbo business-exception responses instead of generic service errors.
- Add `DubboRemoteBusinessException` for reflection-free consumer-side error inspection, while keeping success-path behavior and native ABI `3` unchanged.
- Bound remote exception messages, type names, and cause depth; omit remote stack traces from Rust-provider wire payloads.

## 0.4.0 - 2026-09-03

- Add a dedicated `java-rust-dubbo-native-macos-aarch64` Maven artifact for Apple Silicon Macs.
- Load the macOS ARM64 JNI library from `librust_dubbo-macos-aarch64.dylib` without changing the Java API or native ABI `3`.
- Build with `MACOSX_DEPLOYMENT_TARGET=11.0`, the earliest supported Apple Silicon macOS target.
- Add Mach-O architecture, deployment-target, system-dependency, ad-hoc code-signing, JNI-load, and ABI release gates.

## 0.3.1 - 2026-08-30

- Resolve the annotation processor source level from the active compiler model instead of linking to the Java 21-only `SourceVersion.RELEASE_21` enum field.
- Prevent NetBeans 17 IDE indexing from marking valid `@DubboReference` classes as parsing errors when the project builds with Java 21.
- Preserve generated contracts, canonical annotations, native ABI `3`, wire behavior, and runtime performance characteristics.

## 0.3.0 - 2026-08-30

- Added the canonical `com.reactor.rust.dubbo.annotation` package for `DubboReference`, `DubboService`, and `EnableDubbo`.
- Removed Apache-package classes from the starter's transitive annotation artifact, preventing duplicate classes in mixed Apache/Rust migrations.
- Added the optional `java-rust-dubbo-apache-compat-annotations` artifact and explicit compiler/enhancer compatibility switches for temporary old-import migration.
- Made codegen and bytecode enhancement canonical-only by default. Official Apache annotations remain untouched unless compatibility is deliberately enabled.
- Added canonical, compatibility, and mixed-mode isolation build gates.
- Preserved native ABI `3`, Dubbo wire behavior, Java business APIs, and the dedicated Rust runtime.

## 0.2.1 - 2026-08-30

- Register generated consumer factories without opening native clients for Spring beans disabled by a condition or profile.
- Preserve strict route and readiness validation for every active generated reference.
- Add unit and native integration coverage for disabled and enabled conditional references.

## 0.2.0 - 2026-08-29

- Added exact service routing by `interface + group + version` for consumers connected to multiple provider applications.
- Added named route configuration with per-service endpoint, connection, queue, in-flight, timeout, payload, collection, and buffer-retention budgets.
- Preserved the global consumer properties as a backward-compatible fallback and added strict startup validation through `require-explicit-routes`.
- Added health-aware native connection selection. Disconnected sockets are skipped, immediate queue capacity is preferred, and reconnect waiting remains inside the original RPC deadline.
- Added `clientConnectionWaits`, `clientConnectionWaitTimeouts`, and route-specific `unreadyClients` health details.
- Added real two-provider TCP integration coverage, strict/fallback configuration tests, disconnected-replica tests, and rebuilt Windows/Linux native artifacts without changing native ABI `3`.
- Expanded bilingual architecture, migration, tuning, operations, and public usage documentation for multi-provider deployments.

## 0.1.0 - 2026-08-28

- Added source-compatible `@EnableDubbo`, `@DubboReference`, and `@DubboService` annotations.
- Added compile-time generated typed clients, provider dispatchers, Spring bean definitions, stable IDs, and Hessian codecs.
- Added build-time private-field enhancement without runtime reflection or proxy generation.
- Added a dedicated Rust Tokio consumer data plane with persistent connections, request-ID demultiplexing, reconnect, heartbeat, deadlines, and bounded backpressure.
- Added a dedicated Rust Tokio provider data plane with bounded business workers, queues, named concurrency lanes, async completion, and graceful drain.
- Added native-owned response handles, deterministic release, capped direct request-buffer reuse, and native leak metrics.
- Added Windows x64 and GLIBC 2.17-compatible Linux x64 platform artifacts with ABI version `3` verification.
- Added Spring Boot profiles, readiness health integration, configuration metadata, NMC compile-compatibility gates, official Dubbo wire interoperability tests, malformed-frame tests, provider restart tests, load tests, and soak tests.
- Added provider-side reject correlation and an explicit overload error-budget mode while preserving zero errors as the default benchmark gate.
- Replaced production frame-decoding and provider-lane invariant panic paths with bounded error handling.
- Added an accepted custom-versus-official Spring Boot HTTP matrix and a detailed throughput, p99, RSS, anonymous-memory, startup, and package-size comparison report.
- Added bilingual architecture, migration, tuning, operations, and performance documentation.

- Added reproducible Windows MSVC and GLIBC 2.17 Linux native release jobs.
- Added GitHub Packages publication, source JARs, Java/Rust SBOMs, checksums, and an empty-cache consumer gate.
- Added CI, pinned GitHub Actions, Dependabot, stable release notes, and durable release evidence.
- Added an automated 10,000-call Spring Boot container RPC gate. Benchmark images now consume a version-independent `target/app.jar` to prevent stale snapshot artifacts.
- Kept the NMC compatibility profile opt-in because it requires private `com.zenia` artifacts. Those artifacts are not part of the framework package or its standalone release workflow.

This is the first stable release.
