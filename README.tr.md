# Java Rust Dubbo Spring Boot

[English](README.md) | [Türkçe](README.tr.md)

Spring Boot 3 için reflection kullanmayan bir Dubbo consumer ve provider kütüphanesidir. İş mantığı Java'da kalır. Küçük bir Rust native veri düzlemi; TCP bağlantılarını, Dubbo paketlerini, zaman aşımlarını, heartbeat işlemlerini, backpressure kontrolünü ve provider iletişimini yönetir.

Public paket, Java API'sini ve doğrulanmış Windows/Linux native artifact'larını içerir. Rust kaynak kodu ayrı bir private repoda tutulur.

## İçindekiler

- [Hızlı başlangıç](#hızlı-başlangıç)
- [Profil seçimi](#profil-seçimi)
- [Production reçeteleri](#sık-kullanılan-production-reçeteleri)
- [ZooKeeper olmadan Kubernetes](#zookeeper-olmadan-kubernetes-kullanımı)
- [Desteklenen contract yapısı](#desteklenen-contract-yapısı)
- [Tüm property'ler](#tüm-propertyler)
- [Güvenli tuning](#güvenli-tuning-sırası)
- [Sorun giderme](#sorun-giderme)

## Kısaca Nasıl Çalışır?

| Sizde kalanlar | Kütüphanenin değiştirdikleri | Bilinçli olarak eklenmeyenler |
|---|---|---|
| Spring Boot, Java service'leri, validation, repository ve DTO'lar | Resmî Dubbo runtime, Netty, Java Hessian, çalışma anı proxy ve reflection işlemleri | ZooKeeper, Curator, metadata center, router, generic invocation ve callback |

```text
Spring bean
  -> build sırasında üretilen tip güvenli client ve Hessian codec
  -> JNI
  -> sınırları belli Rust TCP veri düzlemi
  -> Dubbo provider
  -> build sırasında üretilen Java dispatcher
  -> Java service'iniz
```

Provider adresleri sabitse veya Kubernetes Service DNS üzerinden erişilebiliyorsa bu kütüphaneyi kullanın. Registry yönetimi veya çıkarılmış Dubbo özellikleri gerekiyorsa resmî Dubbo'yu kullanın.

## Gereksinimler

- Java 21
- Spring Boot 3; sürüm 3.2.4 ile doğrulanmıştır
- Maven 3.9 veya üzeri
- Yerel geliştirme için Windows x64 veya GLIBC 2.17 ve üzeri Linux x64
- Consumer ve provider tarafından ortak kullanılan küçük bir Java contract JAR'ı

Güncel sürüm: `0.1.1`.

## Hızlı Başlangıç

### 1. Maven İçin GitHub Packages Erişimi Verin

Repo public olsa da GitHub Packages kimlik doğrulaması ister. `read:packages` yetkili classic GitHub token oluşturun. Token'ı proje dosyasına yazmayın.

`~/.m2/settings.xml` dosyasına şu server tanımını ekleyin:

```xml
<settings xmlns="http://maven.apache.org/SETTINGS/1.2.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.2.0 https://maven.apache.org/xsd/settings-1.2.0.xsd">
  <servers>
    <server>
      <id>github</id>
      <username>GITHUB_KULLANICI_ADINIZ</username>
      <password>${env.GITHUB_PACKAGES_TOKEN}</password>
    </server>
  </servers>
</settings>
```

Token'ı yalnızca kullandığınız terminal oturumuna verin:

```powershell
$env:GITHUB_PACKAGES_TOKEN='CLASSIC_PAT_DEGERINIZ'
```

```bash
export GITHUB_PACKAGES_TOKEN='CLASSIC_PAT_DEGERINIZ'
```

### 2. Maven Ayarlarını Ekleyin

Repository, starter, code generator, tek bir native platform artifact'ı ve build enhancer tanımını uygulamanızın POM dosyasına ekleyin:

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

Windows x64 kullanıyorsanız `java-rust-dubbo-native-linux-x64` yerine `java-rust-dubbo-native-windows-x64` yazın. Her deployment içinde yalnızca bir platform artifact'ı bulunsun.

### 3. Ortak Contract Oluşturun

Interface ve DTO'ları küçük bir ortak JAR içinde tutun. Consumer ve provider aynı contract sürümünü ve aynı package adlarını kullanmalıdır.

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

### 4. Consumer Oluşturun

Bildik Dubbo annotation kullanımı değişmez:

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

`@DubboReference` alanı bir interface tipinde olmalıdır. `static` veya `final` olmamalıdır. Maven enhancer, üretilen client'ı çalışma anında reflection kullanmadan bu alana bağlar.

Consumer ayarları:

```properties
reactor.dubbo.profile=micro
reactor.dubbo.consumer.providers=127.0.0.1:20880
reactor.dubbo.consumer.timeout-ms=3000
```

### 5. Provider Oluşturun

İş mantığı normal bir Java implementasyonu olarak kalır:

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

Provider ayarları:

```properties
reactor.dubbo.profile=micro
reactor.dubbo.provider.enabled=true
reactor.dubbo.provider.port=20880
reactor.dubbo.provider.executors.store-query.max-concurrent=16
```

Generator; Spring bean kaydını, tip güvenli dispatcher'ı, method ID'lerini ve Hessian codec'lerini build sırasında üretir. Çalışma anında classpath taraması veya Java proxy oluşturulmaz.

### 6. Build Alın ve Çalıştırın

```bash
mvn -U clean package
java -jar target/uygulamaniz.jar
```

Spring Boot Actuator varsa kütüphane `rustDubboHealthIndicator` bean'ini ekler. `/actuator/health` üzerinden kontrol edebilirsiniz. Detaylarda seçilen profil, consumer ve provider hazırlık durumu ile native metrikler bulunur.

## Profil Seçimi

`micro` ile başlayın. Profili yalnızca RSS, p99 latency, reject sayısı, CPU throttling ve alt sistem pool bekleme süresini birlikte ölçtükten sonra değiştirin.

| Profil | Hangi durumda kullanılır? | Etkisi |
|---|---|---|
| `micro` | Trafiği düşük veya memory öncelikli pod | En küçük worker, queue, connection ve buffer bütçesi |
| `balanced` | Düzenli ve karma trafik | Orta seviye RSS ile daha fazla paralellik |
| `throughput` | Yük testi yapılmış yüksek trafikli servis | En yüksek paralellik; daha büyük queue ve daha fazla tutulan memory |

Açıkça verilen bir property, profil değerinin üzerine yazılır.

<details>
<summary>Profil varsayılan değerleri</summary>

| Ayar | `micro` | `balanced` | `throughput` |
|---|---:|---:|---:|
| Runtime I/O worker | 1 | 2 | 4 |
| Callback worker | 1 | 2 | 4 |
| Callback queue | 256 | 512 | 2048 |
| Native thread stack | 256 KiB | 256 KiB | 512 KiB |
| Endpoint başına connection | 2 | 2 | 4 |
| Connection başına command queue | 32 | 128 | 256 |
| Consumer max in-flight | 64 | 256 | 1024 |
| Tutulan request buffer | 16 | 32 | 64 |
| Tutulan en büyük buffer | 64 KiB | 128 KiB | 256 KiB |
| Provider I/O worker | 1 | 2 | 4 |
| Provider business worker | 4 | 8 | 16 |
| Provider queue | 64 | 256 | 1024 |
| Varsayılan provider concurrency | 16 | 128 | 512 |

</details>

## Sık Kullanılan Production Reçeteleri

### Memory Öncelikli Consumer

Trafiği düşük ve payload boyutu 1 MiB altında olan küçük bir servis için başlangıç ayarı:

```properties
reactor.dubbo.profile=micro
reactor.dubbo.consumer.providers=store-provider:20880
reactor.dubbo.consumer.connections-per-endpoint=1
reactor.dubbo.consumer.max-in-flight=32
reactor.dubbo.consumer.command-queue-capacity=16
reactor.dubbo.consumer.retained-buffers=8
reactor.dubbo.consumer.max-payload-bytes=1048576
```

### Query ve Command Provider

Database yazma işlerini gerçek connection pool kapasitesi içinde tutun. Dubbo queue değerini büyütmek database kapasitesini artırmaz.

```java
@DubboService(executor = "query")
final class QueryServiceImpl implements QueryService { /* iş mantığı */ }

@DubboService(executor = "command")
final class CommandServiceImpl implements CommandService { /* iş mantığı */ }
```

```properties
reactor.dubbo.profile=balanced
reactor.dubbo.provider.enabled=true
reactor.dubbo.provider.business-workers=8
reactor.dubbo.provider.queue-capacity=64
reactor.dubbo.provider.executors.query.max-concurrent=8
reactor.dubbo.provider.executors.command.max-concurrent=2
```

### Tek Bir Büyük Response

Yalnızca gereken limiti artırın. Collection sayısını ve tutulan buffer boyutunu sınırlı bırakın. Büyük listelerde pagination tercih edin.

```properties
reactor.dubbo.consumer.max-payload-bytes=16777216
reactor.dubbo.consumer.max-collection-items=20000
reactor.dubbo.consumer.max-retained-buffer-bytes=65536
```

## ZooKeeper Olmadan Kubernetes Kullanımı

Provider'ı normal bir Kubernetes Service ile yayınlayın:

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

Consumer'a Service DNS adresini verin:

```yaml
env:
  - name: REACTOR_DUBBO_PROFILE
    value: "micro"
  - name: REACTOR_DUBBO_CONSUMER_PROVIDERS
    value: "store-provider.platform.svc.cluster.local:20880"
```

Kubernetes yalnızca yeni TCP bağlantılarını pod'lara dağıtır. Açılmış kalıcı bağlantı, seçildiği pod üzerinde kalır. `connections-per-endpoint` değerini ancak yük testi daha geniş pod dağılımına ihtiyaç olduğunu gösterirse artırın. Readiness probe kullanın. Kubernetes termination grace süresi, `reactor.dubbo.provider.drain-timeout-ms` değerinden uzun olsun.

## Desteklenen Contract Yapısı

- Scalar tipler: Java primitive tipleri, boxed primitive tipler, `String`, `BigDecimal`, `Date`, `LocalDate`, `LocalTime` ve `LocalDateTime`.
- Veri yapıları: enum, array, `byte[]`, `List`, `Set`, `Collection`, `Map`, iç içe record ve uyumlu Java bean.
- DTO için record en sade seçimdir. Bean kullanılırsa okunabilir property'ler ile uyumlu bir builder veya boş constructor ve yazılabilir property'ler gerekir.
- Senkron dönüş, `void` ve `CompletableFuture<T>` desteklenir.
- Decode sırasında collection ve payload limitleri kontrol edilir. Bu limitleri geçerli iş verisine yakın tutun.

### `@DubboService` Parametreleri

| Parametre | Varsayılan | Anlamı ve önerilen kullanım |
|---|---:|---|
| `interfaceClass` | `void.class` | Tip güvenli servis kontratıdır. Yeni kodda, özellikle implementasyon birden fazla interface içeriyorsa `interfaceClass = StoreQueryService.class` kullanımını tercih edin. |
| `interfaceName` | Boş | Contract interface'inin tam package adını metin olarak alır. Yalnızca kaynak uyumluluğu string gerektiriyorsa kullanın. Yeni kodda `interfaceClass` tercih edin ve ikisini birlikte vermeyin. |
| `group` | Boş | Aynı interface'i kullanan servisleri ayıran mantıksal alandır. Consumer değeri bire bir aynı olmalıdır. |
| `version` | Boş | Servis kimliğine katılan contract sürümüdür. Consumer değeri bire bir aynı olmalıdır. Geriye uyumsuz contract değişikliğinde yeni sürüm kullanın. |
| `export` | `true` | Generated dispatcher'ı native provider'a kaydeder. `false` olduğunda Spring implementasyon bean'ini oluşturabilir ancak servis Dubbo üzerinden çağrılamaz. |
| `executor` | Boş | Sınırlı concurrency hattına isim verir. Aynı ismi kullanan tüm metot ve servisler aktif çağrı limitini ortak kullanır. Boş isim global varsayılan hattı kullanır. |
| `executes` | `-1` | Pozitif değer concurrency limitini koda sabitler ve property değerinin önüne geçer. Ortama göre build almadan tuning yapabilmek için `executor` ve property kullanımını tercih edin. |
| `async` | `false` | Kaynak uyumluluğu için kabul edilir. Gerçek non-blocking çalışma, metodun `CompletableFuture<T>` döndürmesiyle belirlenir. `async = true`, bloklayan bir metodu kendiliğinden asenkron yapmaz. |

Provider servis kimliği; interface tam adı, `group` ve `version` değerlerinin birleşimidir. Consumer ve provider bu üç değeri aynı kullanmalıdır.

`interfaceClass` ve `interfaceName` verilmezse implementasyon tam olarak bir interface'i implement etmelidir. Production kodunda açık `interfaceClass` kullanımı daha anlaşılır ve güvenlidir.

İsimlendirilmiş concurrency örneği:

```java
@DubboService(
    interfaceClass = StoreQueryService.class,
    group = "store",
    version = "1.0",
    executor = "store-query")
public final class StoreQueryServiceImpl implements StoreQueryService {
    // İş mantığı Java'da kalır.
}
```

```properties
reactor.dubbo.provider.executors.store-query.max-concurrent=16
```

Bu değer, `store-query` hattını kullanan bütün metotların toplam aktif çağrı sayısını sınırlar. Her metot için ayrı ayrı 16 thread oluşturmaz. Global `reactor.dubbo.provider.business-workers` sınırı da uygulanmaya devam eder. Aynı executor adı farklı `executes` değerleriyle kullanılırsa provider başlangıcı reddedilir.

Concurrency öncelik sırası nettir:

1. `N > 0` olduğunda `@DubboService(executes = N)`.
2. İsimlendirilmiş executor için `reactor.dubbo.provider.executors.<executor>.max-concurrent`.
3. İsimlendirilmiş değer yoksa `reactor.dubbo.provider.default-max-concurrent`.

### `@DubboReference` Parametreleri

| Parametre | Varsayılan | Anlamı ve önerilen kullanım |
|---|---:|---|
| `interfaceClass`, `interfaceName` | Field tipinden bulunur | İsteğe bağlı contract doğrulamasıdır. Annotation eklenen field zaten servis interface'i tipinde olmalıdır. |
| `group`, `version` | Boş | Provider servis kimliği ile bire bir aynı olmalıdır. |
| `check` | `true` | Bu reference'ı başlangıç readiness kontrolüne dahil eder. Yalnızca provider başlangıçta bilinçli olarak opsiyonelse `false` yapın. Retry açmaz. |

### Desteklenmeyen Annotation Parametreleri

Desteklenmeyen değerler sessizce yok sayılmaz. Açıkça kullanılırsa build hata verir.

| Parametre | Yerine kullanılacak ayar |
|---|---|
| `timeout` | `reactor.dubbo.consumer.timeout-ms` veya `reactor.dubbo.provider.request-timeout-ms` |
| `connections` | `reactor.dubbo.consumer.connections-per-endpoint` |
| `payload` | Consumer/provider `max-payload-bytes` property'leri |
| `retries` | Yalnızca idempotent işlemler için uygulama seviyesinde açık retry; native çağrı otomatik tekrarlanmaz |
| `registry` | Statik adres veya Kubernetes Service DNS ile `reactor.dubbo.consumer.providers` |
| `serialization` | Native protokol desteklenen Hessian2 alt kümesini kullanır |
| `protocol` | Native veri düzlemi klasik `dubbo://` kullanır |
| `path`, `actives`, `cluster`, `loadbalance` | Sınırları belli native runtime içinde annotation karşılığı yoktur |

## Hata ve Kapasite Modeli

- Consumer, sayısı sınırlı kalıcı bağlantılar kullanır. Bağlantı koparsa yeniden bağlanır.
- `startup-check=true`, zorunlu provider'ları başlangıçta bekler. `@DubboReference(check = false)` yalnızca ilgili reference'ı bu kontrolden çıkarır.
- Her RPC çağrısının süresi sınırlıdır. RPC timeout değerini HTTP timeout değerinden küçük tutun.
- Queue ve in-flight çağrı sayıları sınırlıdır. Aşırı yükte kontrollü reject, sınırsız RSS ve p99 büyümesinden daha güvenlidir.
- Otomatik business retry yapılmaz. Yalnızca idempotent işlemleri, çağrının toplam süresi içinde kalacak şekilde tekrar deneyin.
- Provider kapanırken yeni iş almayı bırakır ve tanımlı drain timeout süresi boyunca çalışan işleri bekler.

## Tüm Property'ler

Spring Boot relaxed binding geçerlidir. Aşağıdaki üç kullanım aynı değeri verir:

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
<summary>Tüm runtime ve consumer property'leri</summary>

| Property | `micro` varsayılanı | Görevi |
|---|---:|---|
| `reactor.dubbo.enabled` | `true` | Üretilen Dubbo runtime'ını açar veya kapatır |
| `reactor.dubbo.profile` | `micro` | Başlangıç kaynak profilini seçer |
| `reactor.dubbo.runtime.io-workers` | `1` | Ortak native consumer I/O worker sayısı |
| `reactor.dubbo.runtime.callback-workers` | `1` | Asenkron sonuç worker sayısı |
| `reactor.dubbo.runtime.callback-queue-capacity` | `256` | Sınırlı asenkron sonuç queue boyutu |
| `reactor.dubbo.runtime.thread-stack-bytes` | `262144` | Her native runtime thread'inin stack boyutu |
| `reactor.dubbo.consumer.providers` | `127.0.0.1:20880` | Virgülle ayrılmış `host:port` adresleri |
| `reactor.dubbo.consumer.connections-per-endpoint` | `2` | Her endpoint için kalıcı connection sayısı |
| `reactor.dubbo.consumer.command-queue-capacity` | `32` | Her connection üzerinde bekleyebilen çağrı sayısı |
| `reactor.dubbo.consumer.max-in-flight` | `64` | Her generated client için devam eden çağrı sınırı |
| `reactor.dubbo.consumer.heartbeat-interval-ms` | `30000` | Boşta kalan bağlantının heartbeat aralığı; `0` kapatır |
| `reactor.dubbo.consumer.timeout-ms` | `3000` | RPC çağrı süresi sınırı |
| `reactor.dubbo.consumer.max-payload-bytes` | `8388608` | Request ve response için kesin boyut sınırı |
| `reactor.dubbo.consumer.max-collection-items` | `100000` | Decode edilen collection eleman sınırı |
| `reactor.dubbo.consumer.initial-buffer-bytes` | `1024` | İlk request buffer boyutu |
| `reactor.dubbo.consumer.retained-buffers` | `16` | Yeniden kullanılan request buffer sayısı; `0` en az tutulumdur |
| `reactor.dubbo.consumer.max-retained-buffer-bytes` | `65536` | Yeniden kullanım için tutulan en büyük buffer |
| `reactor.dubbo.consumer.startup-check` | `true` | Başlangıçta zorunlu provider'ları bekler |
| `reactor.dubbo.consumer.startup-timeout-ms` | `3000` | Başlangıç hazırlık kontrolünün süre sınırı |

</details>

<details>
<summary>Tüm provider property'leri</summary>

| Property | `micro` varsayılanı | Görevi |
|---|---:|---|
| `reactor.dubbo.provider.enabled` | `false` | Native provider listener'ını başlatır |
| `reactor.dubbo.provider.port` | `20880` | Dubbo TCP portu |
| `reactor.dubbo.provider.io-workers` | `1` | Provider'a özel I/O worker sayısı |
| `reactor.dubbo.provider.business-workers` | `4` | En fazla Java dispatcher worker sayısı |
| `reactor.dubbo.provider.queue-capacity` | `64` | Bekleyen provider işlerinin üst sınırı |
| `reactor.dubbo.provider.max-payload-bytes` | `8388608` | Provider payload boyut sınırı |
| `reactor.dubbo.provider.request-timeout-ms` | `30000` | Provider çalışma süresi sınırı |
| `reactor.dubbo.provider.drain-timeout-ms` | `10000` | Güvenli kapanış bekleme süresi |
| `reactor.dubbo.provider.default-max-concurrent` | `16` | Varsayılan method paralellik sınırı |
| `reactor.dubbo.provider.executors.<name>.max-concurrent` | `16` | İsimlendirilmiş iş grubunun paralellik sınırı |

`@DubboService(executes = N)` en yüksek önceliğe sahiptir. Bu değer yoksa `executor = "name"` ile eşleşen property kullanılır. İkisi de yoksa global varsayılan değer uygulanır.

</details>

## Güvenli Tuning Sırası

1. Provider çalışma süresini, database pool beklemesini, CPU throttling değerini, p99'u, reject sayısını ve RSS'i ölçün.
2. Önce yavaş iş mantığını ve database erişimini düzeltin.
3. Provider executor limitini gerçek alt sistem kapasitesiyle eşleştirin.
4. Consumer `max-in-flight` değerini provider'ın timeout süresinden önce bitirebileceği iş sayısına göre ayarlayın.
5. Connection sayısını yalnızca socket sınırı veya Kubernetes pod dağılımı problemi kanıtlanırsa artırın.
6. Queue değerlerini en son artırın. Queue yalnızca bekleyen işi tutar. Kapasite üretmez.

## Kısa Sözlük

| Terim | Anlamı |
|---|---|
| RSS | Process veya container tarafından kullanılan fiziksel memory miktarı |
| p99 | Ölçülen isteklerin yüzde 99'unun tamamlandığı en yüksek latency sınırı |
| In-flight | Başlamış ancak henüz tamamlanmamış çağrı |
| Backpressure | Sınırlı kapasite dolduğunda yeni işi yavaşlatma veya kontrollü reddetme |
| Queue | Uygun worker veya connection bekleyen işler |
| Heartbeat | Boştaki bağlantıyı açık tutan ve kontrol eden küçük periyodik mesaj |
| Idempotent | Tekrarlandığında iş etkisini ikinci kez oluşturmayan işlem |

## Sorun Giderme

| Belirti | Kontrol edin |
|---|---|
| Maven `401` döndürüyor | Token tanımlı, `read:packages` yetkili ve server ID tam olarak `github` olmalı |
| Maven `403` veya `404` döndürüyor | Token sahibinin repo ve package erişimi olmalı |
| Native library yüklenmiyor | İşletim sistemine uygun yalnızca bir native artifact bulunmalı |
| Başlangıçta provider bulunamıyor | Provider adresi, portu, readiness durumu ve startup timeout doğru olmalı |
| Build annotation alanını reddediyor | Yalnızca yukarıdaki desteklenen annotation alanlarını kullanın |
| Generated client veya injection oluşmuyor | Annotation processor ve enhancer plugin `mvn package` sırasında çalışmalı |
| DTO decode hatası oluşuyor | Consumer ve provider aynı contract package, class adı ve uyumlu alanları kullanmalı |
| Yük altında p99 ve RSS artıyor | Worker, connection veya queue artırmadan önce database ve provider kapasitesini inceleyin |
| Trafik az sayıda provider pod'una gidiyor | Ölçüm yaptıktan sonra `connections-per-endpoint` değerini kademeli artırın |

## Bütünlük ve Lisans

Release dosyalarında Windows/Linux native binary'leri, SHA-256 checksum ve CycloneDX SBOM bulunur. `NATIVE_SHA256SUMS`, dağıtılan artifact'ları doğrular. Native binary içindeki üçüncü taraf bileşenler `THIRD_PARTY_NOTICES` ve SBOM dosyalarında listelenir.

Public Java kaynakları Apache License 2.0 ile lisanslanmıştır. Yayınlanan package ve native dosyaları için [Releases](https://github.com/esasmer-dou/java-rust-dubbo-spring-boot/releases) sayfasını kullanın.
