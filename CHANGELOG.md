# Changelog

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
- Kept enterprise compatibility profiles opt-in because they require private application artifacts. Those artifacts are not part of the framework package or its standalone release workflow.

This is the first stable release.
