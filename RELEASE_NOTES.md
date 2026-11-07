# 0.8.2 Distribution Candidate

Prepared November 7, 2026. Not published; current-source correctness remains pending.

Align all ten supported production modules, processor, enhancer and native JARs
to `0.8.2`, then regenerate clients. Generated codecs support standard Hessian
character strings and typed direct JSON fields. Direct JSON remains one-pass;
close unused results. Buffer retries reuse the response without replaying the RPC.

`-Areactor.dubbo.nativeArguments=true` is an opt-in annotation-processor option,
not a runtime property. Its default is `false`: unset builds retain generated Java
Hessian request encoding. Matching natives are required when enabled; missing
exports fail explicitly. Existing JNI signatures and native ABI `3` are unchanged.
Java business logic and request guards remain mandatory; no caching or bypass.

P05 acceptance covers only measured Dubbo workloads. Full HTTP remains **13/15,
not PASS**. **R-P05-HTTP-001** remains an open small HTTP control-route RPS/p99 risk
without an established product root cause. No whole-plan, all-default, all-endpoint
or all-platform performance acceptance is claimed. Native-argument default-off
does not itself prove equivalence to the measured configuration.

Windows x64, Linux x64 and macOS ARM64 natives were rebuilt from clean source
`984c1925f6b6efe17c5324f7802ddf3b95128a45`, build `42348372720`, in compile-only
mode. Linux retains the GLIBC 2.17 ceiling. No native rebuild is needed for this
distribution snapshot. Build success is not runtime qualification: provenance
remains runtime-unverified and release-not-ready until the matching EmaTech gate
and release-owner authorization. Rust sources and internal tests remain private.

Native SBOM metadata is refreshed from the existing dependency inventory after
verifying the locked dependency graph is unchanged except workspace versions.
This is a metadata refresh, not a claim that SBOM generation or runtime tests ran
in the compile-only workflow. Public publication is deferred.

---

# 0.8.1

Release date: 2026-10-30.

## Generated Consumer Timeouts

The annotation processor now generates consumer wait budgets for interface
`@DubboMethod(timeoutMs = ...)` and field `@DubboReference(timeout = ...)`.
The first configured value wins: method > reference > route > global.
Synchronous, asynchronous, streamed and direct JSON consumers follow the same rule.

Method values must be positive. Canonical reference `0` inherits route/global
configuration; negative canonical values fail compilation. Apache-compatible
reference `-1` also means inheritance. References sharing interface/group/version
share a client and must use the same normalized reference timeout, including zero.
Conflicts fail compilation with both field names and values; different
groups or versions remain independent.

## Upgrade And Compatibility

Align all Spring Dubbo Maven modules, the annotation processor and enhancer plugin
to `0.8.1`, then rebuild the application to regenerate clients. Existing generated
clients keep working: old protected runtime overloads remain and delegate with a
zero method override, preserving their inherited timeout behavior.

Native ABI stays `3`. Packaged Windows DLL, Linux SO and macOS dylib bytes are
unchanged; their Maven coordinates advance to `0.8.1`. No native source or binary
change is part of this patch. Provider service/implementation deadlines are unchanged.

## Failure And Qualification Boundaries

Consumer timeout limits client waiting, not provider execution. It does not interrupt
or roll back running Java/DB work. Keep downstream deadlines, idempotency and explicit
failure handling; do not blindly retry a timed-out write. No automatic RPC replay is added.

For streamed/direct JSON results the budget covers full response-frame arrival,
not subsequent iterator consumption, projection or HTTP output. Resource ownership
remains bounded and must still be closed on failure or disconnect.
No new full-load, all-platform performance or zero-rejection guarantee is claimed.
Historical performance limitations and qualification evidence remain unchanged.

---

# 0.8.0

Release date: October 29, 2026.

## What Changed?

Typed direct JSON callbacks and the optional REST adapter can forward supported native results without building consumer row DTOs. One-pass streaming and bounded response ownership are explicit. Keep ordinary DTO decoding when Java must inspect or transform rows.

## How To Use It

1. Align your dependency versions with the release set below.
2. Rebuild the application. Use the native file packaged with the selected JAR; do not mix older DLL/SO files.
3. Choose the feature you need in the [usage guide](README.md). Optional features are not enabled automatically.
4. Validate under your own CPU, memory and traffic budgets. Do not blindly retry a timed-out write.

## Aligned Versions

REST/platform: 4.8.0; cache: 0.9.0; Dubbo: 0.9.0; Spring Dubbo: 0.8.0; agent: 0.7.1.
Sample applications: 0.8.0; shared model/interfaces: 0.5.0.
Native contracts are unchanged: REST 29, Dubbo 7, Spring Dubbo 3, Redis 6, Glowroot 6.

## Qualification Scope

Java business logic, reflection-free dispatch and bounded resources are preserved.
L1 is optional; a net memory reduction is not guaranteed for every workload.
Consumer warmup rejections remain a separate improvement item.
A fixed 2.695 ms writer p99 and zero consumer warmup rejections are not general product guarantees.
Measured correctness and error checks remain applicable. No new all-platform performance guarantee is claimed.

For GitHub Packages, match the Maven settings.xml server ID to the repository ID.
Use a token with read:packages and, when required, private repository access. SSH authenticates Git only.
