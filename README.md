# Java Rust Dubbo Spring Boot

[English](README.md) | [Turkish](README.tr.md)

A reflection-free Dubbo consumer and provider for Spring Boot 3. Java keeps the business logic. A small Rust native data plane handles TCP connections, Dubbo framing, timeouts, heartbeats, backpressure, and provider I/O.

The public package contains the Java API and verified Windows/Linux native artifacts. Rust source is maintained in a separate private repository.

## Contents

- [Quick start](#quick-start)
- [Choose a profile](#choose-a-profile)
- [Critical runtime limits](#critical-runtime-limits)
- [Production recipes](#common-production-recipes)
- [Kubernetes without ZooKeeper](#kubernetes-without-zookeeper)
- [Supported contracts](#supported-contract-surface)
- [Configuration reference](#configuration-reference)
- [Safe tuning](#safe-tuning-order)
- [Troubleshooting](#troubleshooting)

## At A Glance

| You keep | This library replaces | Intentionally not included |
|---|---|---|
| Spring Boot, Java services, validation, repositories, DTOs | Official Dubbo runtime, Netty, Java Hessian, runtime proxies and reflection | ZooKeeper, Curator, metadata center, routers, generic invocation and callbacks |

```text
Spring bean
  -> generated typed client and Hessian codec
  -> JNI
  -> bounded Rust TCP data plane
  -> Dubbo provider
  -> generated Java dispatcher
  -> your Java service
```

Use this library when provider addresses are static or available through Kubernetes Service DNS. Use official Dubbo when registry governance or the omitted Dubbo features are required.

## Requirements

- Java 21
- Spring Boot 3; version 3.2.4 is verified
- Maven 3.9 or newer
- Windows x64 for local development, or Linux x64 with GLIBC 2.17 or newer
- A shared Java contract artifact used by both consumer and provider

Current release: `0.1.1`.

## Quick Start

### 1. Allow Maven To Read GitHub Packages

GitHub Packages requires authentication even when the repository is public. Create a classic GitHub token with `read:packages`. Keep it outside the project.

Add the server to `~/.m2/settings.xml`:

```xml
<settings xmlns="http://maven.apache.org/SETTINGS/1.2.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.2.0 https://maven.apache.org/xsd/settings-1.2.0.xsd">
  <servers>
    <server>
      <id>github</id>
      <username>YOUR_GITHUB_USERNAME</username>
      <password>${env.GITHUB_PACKAGES_TOKEN}</password>
    </server>
  </servers>
</settings>
```

Set the token only in the current shell:

```powershell
$env:GITHUB_PACKAGES_TOKEN='YOUR_CLASSIC_PAT'
```

```bash
export GITHUB_PACKAGES_TOKEN='YOUR_CLASSIC_PAT'
```

### 2. Add The Maven Configuration

Add the repository, starter, code generator, one native platform artifact, and build enhancer to your application POM:

```xml
<properties>
  <java-rust-dubbo.version>0.1.1</java-rust-dubbo.version>
</properties>

<repositories>
  <repository>
    <id>github</id>
    <url>https://maven.pkg.github.com/esasmer-dou/java-rust-dubbo-spring-boot</url>
  </repository>
</repositories>

<pluginRepositories>
  <pluginRepository>
    <id>github</id>
    <url>https://maven.pkg.github.com/esasmer-dou/java-rust-dubbo-spring-boot</url>
  </pluginRepository>
</pluginRepositories>

<dependencies>
  <dependency>
    <groupId>com.reactor</groupId>
    <artifactId>java-rust-dubbo-spring-boot-starter</artifactId>
    <version>${java-rust-dubbo.version}</version>
  </dependency>

  <dependency>
    <groupId>com.reactor</groupId>
    <artifactId>java-rust-dubbo-native-linux-x64</artifactId>
    <version>${java-rust-dubbo.version}</version>
    <scope>runtime</scope>
  </dependency>

  <dependency>
    <groupId>com.reactor</groupId>
    <artifactId>java-rust-dubbo-codegen</artifactId>
    <version>${java-rust-dubbo.version}</version>
    <scope>provided</scope>
    <optional>true</optional>
  </dependency>
</dependencies>

<build>
  <plugins>
    <plugin>
      <groupId>org.apache.maven.plugins</groupId>
      <artifactId>maven-compiler-plugin</artifactId>
      <version>3.13.0</version>
      <configuration>
        <release>21</release>
        <parameters>true</parameters>
        <annotationProcessorPaths>
          <path>
            <groupId>com.reactor</groupId>
            <artifactId>java-rust-dubbo-codegen</artifactId>
            <version>${java-rust-dubbo.version}</version>
          </path>
        </annotationProcessorPaths>
      </configuration>
    </plugin>

    <plugin>
      <groupId>com.reactor</groupId>
      <artifactId>java-rust-dubbo-enhancer-maven-plugin</artifactId>
      <version>${java-rust-dubbo.version}</version>
      <executions>
        <execution>
          <goals>
            <goal>enhance</goal>
          </goals>
        </execution>
      </executions>
    </plugin>
  </plugins>
</build>
```

For Windows x64, replace `java-rust-dubbo-native-linux-x64` with `java-rust-dubbo-native-windows-x64`. Add exactly one platform artifact to each deployment.

### 3. Define A Shared Contract

Place the interface and DTOs in a small shared JAR. Consumer and provider must use the same contract version and package names.

```java
package com.example.store.api;

public record StoreView(long id, String code, String name) {}
```

```java
package com.example.store.api;

public interface StoreQueryService {
    StoreView find(long id);
}
```

### 4. Create The Consumer

The familiar Dubbo annotations stay in place:

```java
package com.example.store.consumer;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@EnableDubbo
@SpringBootApplication
public class StoreConsumerApplication {
    public static void main(String[] args) {
        SpringApplication.run(StoreConsumerApplication.class, args);
    }
}
```

```java
package com.example.store.consumer;

import com.example.store.api.StoreQueryService;
import com.example.store.api.StoreView;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Service;

@Service
public final class StoreFacade {
    @DubboReference(check = true, group = "store", version = "1.0")
    private StoreQueryService storeQueryService;

    public StoreView find(long id) {
        return storeQueryService.find(id);
    }
}
```

`@DubboReference` fields must be instance fields, assignable, and typed as an interface. Do not mark them `static` or `final`. The Maven enhancer injects the generated client without runtime reflection.

Consumer configuration:

```properties
reactor.dubbo.profile=micro
reactor.dubbo.consumer.providers=127.0.0.1:20880
reactor.dubbo.consumer.timeout-ms=3000
```

### 5. Create The Provider

Business code remains a normal Java implementation:

```java
package com.example.store.provider;

import com.example.store.api.StoreQueryService;
import com.example.store.api.StoreView;
import org.apache.dubbo.config.annotation.DubboService;

@DubboService(
    interfaceClass = StoreQueryService.class,
    group = "store",
    version = "1.0",
    executor = "store-query")
public final class StoreQueryServiceImpl implements StoreQueryService {
    private final StoreRepository repository;

    public StoreQueryServiceImpl(StoreRepository repository) {
        this.repository = repository;
    }

    @Override
    public StoreView find(long id) {
        return repository.find(id);
    }
}
```

Provider configuration:

```properties
reactor.dubbo.profile=micro
reactor.dubbo.provider.enabled=true
reactor.dubbo.provider.port=20880
reactor.dubbo.provider.executors.store-query.max-concurrent=16
```

The generator creates the Spring bean registration, typed dispatcher, method IDs, and Hessian codecs during the build. No runtime classpath scan or Java proxy is created.

### 6. Build And Run

```bash
mvn -U clean package
java -jar target/your-application.jar
```

If Spring Boot Actuator is present, the library contributes `rustDubboHealthIndicator`. Check it through `/actuator/health`. The details include the selected profile, client readiness, provider readiness, and native metrics.

## Choose A Profile

Start with `micro`. Change profile only after measuring RSS, p99 latency, rejection count, CPU throttling, and downstream pool wait together.

| Profile | Use it for | Trade-off |
|---|---|---|
| `micro` | Low-traffic or memory-first pods | Smallest worker, queue, connection, and buffer budgets |
| `balanced` | Steady mixed traffic | More concurrency and smoother bursts with moderate RSS |
| `throughput` | Load-tested high-volume services | Highest concurrency; larger queues and retained memory |

An explicit property always overrides the selected profile.

<details>
<summary>Profile defaults</summary>

| Setting | `micro` | `balanced` | `throughput` |
|---|---:|---:|---:|
| Runtime I/O workers | 1 | 2 | 4 |
| Callback workers | 1 | 2 | 4 |
| Callback queue | 256 | 512 | 2048 |
| Native thread stack | 256 KiB | 256 KiB | 512 KiB |
| Connections per endpoint | 2 | 2 | 4 |
| Command queue per connection | 32 | 128 | 256 |
| Consumer max in-flight | 64 | 256 | 1024 |
| Retained request buffers | 16 | 32 | 64 |
| Largest retained buffer | 64 KiB | 128 KiB | 256 KiB |
| Provider I/O workers | 1 | 2 | 4 |
| Provider business workers | 4 | 8 | 16 |
| Provider queue | 64 | 256 | 1024 |
| Default provider concurrency | 16 | 128 | 512 |

</details>

## Critical Runtime Limits

The following values are the effective `micro` defaults unless an explicit property overrides them. Profile-dependent values grow in `balanced` and `throughput`.

| Limit | Default | What it protects |
|---|---:|---|
| Consumer RPC deadline | `3000 ms` | Total time spent waiting for an in-flight permit, command queue, and response |
| Consumer startup wait | `3000 ms` | Time allowed for required providers to become reachable |
| TCP connect attempt | `3000 ms` | Internal limit for one native connection attempt; reconnect continues after failure |
| Provider request deadline | `30000 ms` | Provider queue, Java dispatch, and response completion time |
| Provider drain wait | `10000 ms` | Graceful shutdown wait for active work |
| Consumer payload | `8388608` bytes (`8 MiB`) | One encoded request or response body |
| Provider payload | `8388608` bytes (`8 MiB`) | One incoming request or generated response body |
| Decoded collection | `100000` items | Object growth caused by a large `List`, `Set`, array, or `Map` |
| Connections per endpoint | `2` | Persistent native connections and Kubernetes pod distribution |
| Consumer max in-flight | `64` | Outstanding calls per generated client |
| Command queue | `32` per connection | Calls waiting for a native connection |
| Callback queue | `256` | Async completions waiting for a callback worker |
| Provider business workers | `4` | Java business dispatch threads |
| Provider default concurrency | `16` | Active calls sharing the default executor lane |
| Provider queue | `64` | Work waiting for Java business workers |
| Retained request buffers | `16`, at most `65536` bytes each | Reusable direct-buffer retention after calls complete |

### How Payload Limits Work

`max-payload-bytes` is a per-call hard ceiling. It is not reserved memory and it is not a total process memory limit.

- Consumer request buffers start at `1024` bytes and grow only when encoding needs more space.
- Provider response buffers also start at `1024` bytes and grow only when needed.
- Outbound requests are checked after Hessian2 encoding and before they enter the native command queue.
- Incoming frame length is checked before the complete body is allocated.
- Response growth stops at the configured provider limit.
- The limit applies to the encoded Dubbo body, including protocol and Hessian2 data. It may be slightly larger than the business DTO content.
- Consumer and provider limits should normally match. A provider limit may be lower when the service deliberately accepts smaller requests.

An `8 MiB` limit does **not** allocate `8 MiB` for every call. However, it permits a call to grow that large. Memory risk is therefore related to both payload and concurrency:

```text
possible active payload memory ~= max-payload-bytes x active large calls
```

Do not keep an `8 MiB` ceiling when valid payloads are below `256 KiB`. Lowering the limit reduces the maximum burst allocation and rejects invalid data earlier.

`reactor.dubbo.consumer.max-collection-items` is a separate object-allocation guard. The generated provider dispatcher uses the same codec limit, even though the property is under `consumer`. A small encoded payload can still create many Java objects, so tune both byte and collection limits.

Example for small JSON-like DTO contracts:

```properties
reactor.dubbo.consumer.max-payload-bytes=1048576
reactor.dubbo.provider.max-payload-bytes=1048576
reactor.dubbo.consumer.max-collection-items=5000
reactor.dubbo.consumer.max-in-flight=32
reactor.dubbo.consumer.retained-buffers=8
reactor.dubbo.consumer.max-retained-buffer-bytes=65536
```

There is currently no per-method payload override. Move unusually large contracts to a separate deployment or process when they require a very different memory budget.

### How Timeouts Work

The consumer timeout is one end-to-end RPC budget. Queue wait reduces the time left for the response. When the deadline expires, pending native state is removed and the invocation fails without automatic replay.

The provider timeout includes waiting in the provider business queue and waiting for the Java result. On expiry, Rust cancels the native response handle and returns a server-timeout response. Rust cannot safely interrupt a synchronous Java method that is blocked inside JDBC or an HTTP client. Those dependencies must have their own shorter timeouts.

A practical timeout chain leaves a small margin at every layer:

```properties
# Example when the inbound HTTP deadline is 3000 ms.
reactor.dubbo.provider.request-timeout-ms=2000
reactor.dubbo.consumer.timeout-ms=2500
```

In this example, database and outbound HTTP timeouts should be below `2000 ms`. Per-method `timeout` annotation values are not supported.

### Compression

The native wire path does not currently compress payloads. There is no Gzip, LZ4, Zstd, or compression negotiation. Frames use the supported Hessian2 encoding directly.

This avoids compression CPU, temporary buffers, and p99 latency on normal API payloads. For large data, prefer a smaller DTO, pagination, or a separate bulk-transfer design. Application-managed compressed `byte[]` is possible, but the application must enforce a maximum decompressed size and accept the extra CPU and allocation cost.

## Common Production Recipes

### Memory-First Consumer

Use this as a starting point for a small service with modest traffic and payloads below 1 MiB:

```properties
reactor.dubbo.profile=micro
reactor.dubbo.consumer.providers=store-provider:20880
reactor.dubbo.consumer.connections-per-endpoint=1
reactor.dubbo.consumer.max-in-flight=32
reactor.dubbo.consumer.command-queue-capacity=16
reactor.dubbo.consumer.retained-buffers=8
reactor.dubbo.consumer.max-payload-bytes=1048576
```

### Query And Command Provider

Keep database writes inside the real DB pool capacity. A larger Dubbo queue does not create database capacity.

```java
@DubboService(executor = "query")
final class QueryServiceImpl implements QueryService { /* business code */ }

@DubboService(executor = "command")
final class CommandServiceImpl implements CommandService { /* business code */ }
```

```properties
reactor.dubbo.profile=balanced
reactor.dubbo.provider.enabled=true
reactor.dubbo.provider.business-workers=8
reactor.dubbo.provider.queue-capacity=64
reactor.dubbo.provider.executors.query.max-concurrent=8
reactor.dubbo.provider.executors.command.max-concurrent=2
```

### One Large Response

Raise only the required limit. Keep collection count and retained buffers bounded. Prefer pagination for large lists.

```properties
reactor.dubbo.consumer.max-payload-bytes=16777216
reactor.dubbo.consumer.max-collection-items=20000
reactor.dubbo.consumer.max-retained-buffer-bytes=65536
```

## Kubernetes Without ZooKeeper

Expose the provider through a normal Kubernetes Service:

```yaml
apiVersion: v1
kind: Service
metadata:
  name: store-provider
spec:
  selector:
    app: store-provider
  ports:
    - name: dubbo
      port: 20880
      targetPort: 20880
```

Point the consumer to that Service DNS name:

```yaml
env:
  - name: REACTOR_DUBBO_PROFILE
    value: "micro"
  - name: REACTOR_DUBBO_CONSUMER_PROVIDERS
    value: "store-provider.platform.svc.cluster.local:20880"
```

Kubernetes balances new TCP connections. Existing persistent connections remain on the pod selected when they were opened. Increase `connections-per-endpoint` only when load tests show that more provider-pod distribution is needed. Use readiness probes, graceful shutdown, and a termination grace period longer than `reactor.dubbo.provider.drain-timeout-ms`.

## Supported Contract Surface

- Supported scalar types: Java primitives, boxed primitives, `String`, `BigDecimal`, `Date`, `LocalDate`, `LocalTime`, and `LocalDateTime`.
- Supported structures: enums, arrays, `byte[]`, `List`, `Set`, `Collection`, `Map`, nested records, and compatible Java beans.
- Records are the simplest DTO choice. A bean must have readable properties and either a compatible builder or a no-argument constructor with writable properties.
- Synchronous return values, `void`, and `CompletableFuture<T>` are supported.
- Collection and payload limits are checked while decoding. Keep both limits close to valid business sizes.

### `@DubboService` Parameters

| Parameter | Default | Meaning and recommended use |
|---|---:|---|
| `interfaceClass` | `void.class` | Type-safe service contract. Prefer `interfaceClass = StoreQueryService.class` in new code, especially when the implementation has multiple interfaces. |
| `interfaceName` | Empty | Fully qualified contract name as text. Use it only when source compatibility requires a string. Prefer `interfaceClass`; do not set both. |
| `group` | Empty | Logical namespace for services that share the same interface. The consumer value must match exactly. |
| `version` | Empty | Contract version used in service identity. The consumer value must match exactly. Deploy a new version when a contract change is not backward compatible. |
| `export` | `true` | Registers the generated dispatcher with the native provider. With `false`, Spring can still create the implementation bean, but the service is not reachable over Dubbo. |
| `executor` | Empty | Names a bounded concurrency lane. All methods and services using the same name share that lane's active-call limit. An empty name uses the global default lane. |
| `executes` | `-1` | Positive value hard-codes the lane limit and overrides configuration. Prefer `executor` plus a property so each environment can be tuned without rebuilding. |
| `async` | `false` | Accepted for source compatibility. Real non-blocking dispatch is determined by a `CompletableFuture<T>` method return type; `async = true` does not make a blocking method asynchronous. |

The provider service identity is the combination of interface name, `group`, and `version`. Consumer and provider must use the same three values.

If neither `interfaceClass` nor `interfaceName` is set, the implementation must implement exactly one interface. Explicit `interfaceClass` is clearer and safer for production code.

Named concurrency example:

```java
@DubboService(
    interfaceClass = StoreQueryService.class,
    group = "store",
    version = "1.0",
    executor = "store-query")
public final class StoreQueryServiceImpl implements StoreQueryService {
    // Business code stays in Java.
}
```

```properties
reactor.dubbo.provider.executors.store-query.max-concurrent=16
```

The value above limits the total active calls across every method sharing `store-query`; it does not create 16 threads per method. The global `reactor.dubbo.provider.business-workers` limit still applies. Reusing one executor name with conflicting `executes` values is rejected during provider startup.

Concurrency precedence is explicit:

1. `@DubboService(executes = N)` when `N > 0`.
2. `reactor.dubbo.provider.executors.<executor>.max-concurrent` for a named executor.
3. `reactor.dubbo.provider.default-max-concurrent` when no named value exists.

### `@DubboReference` Parameters

| Parameter | Default | Meaning and recommended use |
|---|---:|---|
| `interfaceClass`, `interfaceName` | Inferred from field | Optional contract validation. The annotated field must already be typed as the service interface. |
| `group`, `version` | Empty | Must match the provider identity exactly. |
| `check` | `true` | Includes this reference in startup readiness. Set `false` only when that provider is intentionally optional during startup. It does not enable retries. |

### Unsupported Annotation Parameters

Unsupported values are not silently ignored. Explicit use fails the build.

| Parameter | Use instead |
|---|---|
| `timeout` | `reactor.dubbo.consumer.timeout-ms` or `reactor.dubbo.provider.request-timeout-ms` |
| `connections` | `reactor.dubbo.consumer.connections-per-endpoint` |
| `payload` | Consumer/provider `max-payload-bytes` properties |
| `retries` | Explicit application retry for idempotent operations only; native calls are not replayed automatically |
| `registry` | `reactor.dubbo.consumer.providers` with a static address or Kubernetes Service DNS |
| `serialization` | The native protocol uses the supported Hessian2 subset |
| `protocol` | The native data plane uses classic `dubbo://` |
| `path`, `actives`, `cluster`, `loadbalance` | No annotation equivalent in the bounded native runtime |

## Failure And Capacity Model

- The consumer keeps persistent bounded connections and reconnects after connection loss.
- `startup-check=true` waits for required providers. `@DubboReference(check = false)` excludes only that reference from startup readiness.
- Every RPC has a deadline. Set the RPC timeout below the inbound HTTP timeout.
- Queues and in-flight calls are bounded. Under overload, rejection is safer than unbounded RSS and tail latency growth.
- Automatic business retries are not provided. Retry only idempotent operations and keep retries within the caller deadline.
- Provider shutdown stops accepting work and waits up to the configured drain timeout.

## Configuration Reference

Spring Boot relaxed binding applies. These three forms set the same value:

```properties
reactor.dubbo.consumer.max-in-flight=64
```

```text
-Dreactor.dubbo.consumer.max-in-flight=64
```

```yaml
- name: REACTOR_DUBBO_CONSUMER_MAX_IN_FLIGHT
  value: "64"
```

<details>
<summary>All runtime and consumer properties</summary>

| Property | `micro` default | Purpose |
|---|---:|---|
| `reactor.dubbo.enabled` | `true` | Starts or disables the generated Dubbo runtime |
| `reactor.dubbo.profile` | `micro` | Selects the starting resource preset |
| `reactor.dubbo.runtime.io-workers` | `1` | Shared native consumer I/O workers |
| `reactor.dubbo.runtime.callback-workers` | `1` | Async completion workers |
| `reactor.dubbo.runtime.callback-queue-capacity` | `256` | Bounded async completion queue |
| `reactor.dubbo.runtime.thread-stack-bytes` | `262144` | Stack per native runtime thread |
| `reactor.dubbo.consumer.providers` | `127.0.0.1:20880` | Comma-separated `host:port` endpoints |
| `reactor.dubbo.consumer.connections-per-endpoint` | `2` | Persistent connections per endpoint |
| `reactor.dubbo.consumer.command-queue-capacity` | `32` | Waiting calls per connection |
| `reactor.dubbo.consumer.max-in-flight` | `64` | Outstanding calls per generated client |
| `reactor.dubbo.consumer.heartbeat-interval-ms` | `30000` | Idle connection heartbeat; `0` disables it |
| `reactor.dubbo.consumer.timeout-ms` | `3000` | RPC deadline |
| `reactor.dubbo.consumer.max-payload-bytes` | `8388608` | Request/response hard limit |
| `reactor.dubbo.consumer.max-collection-items` | `100000` | Decoded collection item limit |
| `reactor.dubbo.consumer.initial-buffer-bytes` | `1024` | Initial request buffer size |
| `reactor.dubbo.consumer.retained-buffers` | `16` | Reusable request buffers; `0` minimizes retention |
| `reactor.dubbo.consumer.max-retained-buffer-bytes` | `65536` | Largest buffer kept for reuse |
| `reactor.dubbo.consumer.startup-check` | `true` | Waits for required providers at startup |
| `reactor.dubbo.consumer.startup-timeout-ms` | `3000` | Maximum startup readiness wait |

</details>

<details>
<summary>All provider properties</summary>

| Property | `micro` default | Purpose |
|---|---:|---|
| `reactor.dubbo.provider.enabled` | `false` | Starts the native provider listener |
| `reactor.dubbo.provider.port` | `20880` | Dubbo TCP port |
| `reactor.dubbo.provider.io-workers` | `1` | Dedicated provider I/O workers |
| `reactor.dubbo.provider.business-workers` | `4` | Maximum Java dispatch workers |
| `reactor.dubbo.provider.queue-capacity` | `64` | Bounded waiting provider work |
| `reactor.dubbo.provider.max-payload-bytes` | `8388608` | Provider payload hard limit |
| `reactor.dubbo.provider.request-timeout-ms` | `30000` | Provider execution deadline |
| `reactor.dubbo.provider.drain-timeout-ms` | `10000` | Graceful shutdown wait |
| `reactor.dubbo.provider.default-max-concurrent` | `16` | Fallback method concurrency |
| `reactor.dubbo.provider.executors.<name>.max-concurrent` | `16` | Named workload-lane concurrency |

`@DubboService(executes = N)` has the highest priority. Otherwise, `executor = "name"` uses the matching named property. If neither is present, the global default is used.

</details>

## Safe Tuning Order

1. Measure provider execution time, DB pool wait, CPU throttling, p99, rejection count, and RSS.
2. Fix slow business code and database access first.
3. Match provider executor limits to downstream capacity.
4. Match consumer `max-in-flight` to work the provider can finish before timeout.
5. Add connections only when socket saturation or Kubernetes distribution is proven.
6. Increase queues last. Queues store waiting work; they do not add capacity.

## Small Glossary

| Term | Meaning |
|---|---|
| RSS | Physical memory currently attributed to the process or container |
| p99 | Latency below which 99% of measured requests complete |
| In-flight | A request that started but has not completed yet |
| Backpressure | Rejecting or slowing new work when bounded capacity is full |
| Queue | Work waiting for an available worker or connection |
| Heartbeat | Small periodic message used to keep and verify an idle connection |
| Idempotent | Safe to repeat without applying the business effect twice |

## Troubleshooting

| Symptom | Check |
|---|---|
| Maven returns `401` | Token exists, has `read:packages`, and server ID is exactly `github` |
| Maven returns `403` or `404` | Token owner can access the repository and package |
| Native library cannot load | Exactly one correct Windows/Linux native artifact is present |
| Startup reports provider unavailable | Provider address, port, readiness, and startup timeout are correct |
| Build rejects an annotation option | Use only the supported annotation subset listed above |
| Generated client or injection is missing | Annotation processor and enhancer plugin both ran during `mvn package` |
| DTO decode fails | Consumer and provider use the same contract package, class names, and compatible fields |
| p99 and RSS rise under load | Inspect DB/provider capacity before increasing workers, connections, or queues |
| Traffic reaches too few provider pods | Measure and then raise `connections-per-endpoint` gradually |

## Integrity And License

Release assets include Windows/Linux native binaries, SHA-256 checksums, and CycloneDX SBOMs. `NATIVE_SHA256SUMS` pins the distributed artifacts. Third-party native components are listed in `THIRD_PARTY_NOTICES` and the SBOM files.

The public Java source is licensed under Apache License 2.0. See [Releases](https://github.com/esasmer-dou/java-rust-dubbo-spring-boot/releases) for published packages and native assets.
