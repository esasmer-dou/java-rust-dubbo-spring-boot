# Changelog

## 0.5.0 - 2026-09-29

- Add the opt-in `DubboStreamingList<T>` provider response source for large, read-only JDBC results while preserving existing `List<T>` service signatures.
- Let generated provider dispatchers encode streaming rows directly into the native response buffer and deterministically close JDBC/native resources on success or failure.
- Add deferred Hessian list length support and remove the second Rust request-argument buffer allocation.
- Preserve reflection-free generated clients, Spring annotations, configuration keys, wire compatibility, and native ABI `3`.
- Rebuild and verify Windows x64, Linux x64 GLIBC 2.17, and Apple Silicon macOS 11+ native artifacts from the same tagged source.

## 0.4.1 - 2026-09-07

- Preserve provider business exception messages, reported exception types, and bounded cause chains across Rust and Apache Dubbo consumer/provider combinations.
- Encode synchronous and asynchronous Rust-provider failures as standard Dubbo business-exception responses instead of generic service errors.
- Add `DubboRemoteBusinessException` for reflection-free consumer-side error inspection.
- Bound remote messages and cause chains, omit remote stack traces, and keep unexpected codec/JNI failures classified as infrastructure errors.
- Rebuild and verify Windows x64, Linux x64 GLIBC 2.17, and Apple Silicon macOS 11+ native artifacts without changing native ABI `3`.

## 0.4.0 - 2026-09-03

- Added `java-rust-dubbo-native-macos-aarch64` for Apple Silicon macOS 11 and newer.
- Added automatic macOS ARM64 native selection without changing the Java API.
- Added release gates for Mach-O architecture, deployment target, dynamic dependencies, code signing, JNI loading, and native ABI `3`.
- Rebuilt and republished the Windows x64, Linux x64 GLIBC 2.17, and macOS ARM64 native artifacts from the same tagged source.

The Dubbo wire contract, Spring annotations, generated code model, configuration keys, and native ABI remain compatible with `0.3.1`.
