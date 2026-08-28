# Java Rust Dubbo Spring Boot

[English](README.md) | [Türkçe](README.tr.md)

Bu repo yalnızca public Java production kodunu ve doğrulanmış Windows/Linux
native artifact'larını içerir. Rust implementasyon kaynakları ayrı tutulur ve
bu repoda yayımlanmaz.

## Runtime Sınırı

- Java 21 ve Spring Boot 3 gereklidir.
- `@EnableDubbo`, `@DubboReference` ve `@DubboService` kullanılmaya devam eder.
- Client, provider ve Hessian codec kodları build sırasında üretilir.
- Veri katmanı resmi Dubbo, Netty, ZooKeeper, Curator veya Java Hessian runtime
  yerine paketlenmiş native library'yi kullanır.
- Provider adresi statik olarak veya Kubernetes Service DNS ile verilir.

## Maven

GitHub Packages, public repoya bağlı Maven package'ları için de kimlik doğrulama
ister. `read:packages` yetkili classic token'ı `~/.m2/settings.xml` içinde
tanımlayın:

```xml
<server>
  <id>github</id>
  <username>GITHUB_KULLANICI_ADINIZ</username>
  <password>${env.GITHUB_PACKAGES_TOKEN}</password>
</server>
```

Repository ve plugin repository adresi:

```xml
<repository>
  <id>github</id>
  <url>https://maven.pkg.github.com/esasmer-dou/java-rust-dubbo-spring-boot</url>
</repository>
```

Starter, code generator ve yalnızca çalışacağınız işletim sisteminin native
artifact'ını kullanın:

```xml
<properties>
  <java-rust-dubbo.version>0.1.1</java-rust-dubbo.version>
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

Windows x64 için Linux artifact'ı yerine
`java-rust-dubbo-native-windows-x64` kullanın. Maven compiler içinde
`java-rust-dubbo-codegen` annotation processor'ını tanımlayın.
`java-rust-dubbo-enhancer-maven-plugin:enhance` goal'unu build'e ekleyin.

## Bütünlük

`NATIVE_SHA256SUMS`, yayımlanan DLL, SO ve native SBOM dosyalarını sabitler. CI;
Rust kaynağını, internal testleri, benchmark dosyalarını ve private uyumluluk
yüzeylerini reddeder. Release içinde native binary, SBOM ve checksum bulunur.

## Lisans

Public Java kaynakları Apache-2.0 lisanslıdır. Native binary içindeki üçüncü
taraf bileşenler `THIRD_PARTY_NOTICES` ve CycloneDX SBOM dosyalarında listelenir.
