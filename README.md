# Java Rust Dubbo Spring Boot

[English](README.md) | [Turkish](README.tr.md)

This repository contains the public Java production surface and the verified
Windows/Linux native artifacts. The Rust implementation source is maintained
separately and is not part of this repository.

## Runtime Boundary

- Java 21 and Spring Boot 3 are required.
- `@EnableDubbo`, `@DubboReference` and `@DubboService` remain available.
- Clients, providers and Hessian codecs are generated at build time.
- The data plane uses the packaged native library instead of the official
  Dubbo, Netty, ZooKeeper, Curator or Java Hessian runtimes.
- Provider discovery is static or Kubernetes Service DNS based.

## Maven

GitHub Packages requires authentication even for packages connected to a
public repository. Configure a classic personal access token with
`read:packages` in `~/.m2/settings.xml`:

```xml
<server>
  <id>github</id>
  <username>YOUR_GITHUB_USERNAME</username>
  <password>${env.GITHUB_PACKAGES_TOKEN}</password>
</server>
```

Add the repository and plugin repository:

```xml
<repository>
  <id>github</id>
  <url>https://maven.pkg.github.com/esasmer-dou/java-rust-dubbo-spring-boot</url>
</repository>
```

Use the starter, code generator and exactly one native platform artifact:

```xml
<properties>
  <java-rust-dubbo.version>0.1.0</java-rust-dubbo.version>
</properties>

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
</dependencies>
```

Replace the Linux artifact with `java-rust-dubbo-native-windows-x64` for local
Windows x64 runs. Configure `java-rust-dubbo-codegen` as an annotation processor
and run `java-rust-dubbo-enhancer-maven-plugin:enhance` during the Maven build.

## Integrity

`NATIVE_SHA256SUMS` pins the distributed DLL, SO and native SBOM files. CI
rejects Rust source, internal tests, benchmarks and private compatibility
surfaces. Release assets include the native binaries, SBOMs and checksums.

## License

The public Java source is Apache-2.0 licensed. Native binaries include
third-party components listed in `THIRD_PARTY_NOTICES` and the CycloneDX SBOMs.
