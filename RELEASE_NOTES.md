# Java Rust Dubbo Spring Boot 0.6.0

## What's New

Version `0.6.0` completes the large read-only response path on the consumer side without changing the declared `List<T>` service signature.

- `@DubboStreamed` opts one synchronous concrete `List<T>` method into incremental consumer decoding.
- Generated clients return a native-backed `DubboStreamingResult<T>` behind the existing `List<T>` declaration.
- A one-pass HTTP or export writer decodes and writes one DTO at a time instead of retaining the entire consumer object graph.
- Normal `List` operations remain compatible and materialize on demand.
- Native response ownership is released on completion, explicit close, decode failure, and client disconnect.
- The data flow remains one database cursor, one Dubbo request, and one Dubbo response. No chatty page loop is introduced.

Use this feature only for deliberately large, unpaged, read-only results. Keep pagination as the normal API design. The encoded response remains a bounded native payload until the writer completes, so payload and in-flight limits still matter.

```java
public interface CatalogRepository {
    @DubboStreamed
    List<CatalogRow> findAll(CatalogFilter filter);
}
```

Upgrade all framework artifacts together:

```xml
<properties>
  <java-rust-dubbo.version>0.6.0</java-rust-dubbo.version>
</properties>
```

Native ABI remains `3`. Windows x64, GLIBC 2.17-compatible Linux x64, and Apple Silicon macOS 11+ artifacts keep the existing native protocol surface.

## Yenilikler

`0.6.0`, büyük ve salt okunur sonuçların consumer tarafını tamamlar. Servis metodunun tanımlı `List<T>` imzası değişmez.

- `@DubboStreamed`, senkron ve somut bir `List<T>` metodunda artımlı consumer decode yolunu açar.
- Generated client, mevcut `List<T>` tanımının arkasında native response'a bağlı `DubboStreamingResult<T>` döndürür.
- Tek geçişli HTTP veya export writer, bütün consumer nesne grafiğini tutmak yerine her seferinde bir DTO decode edip yazar.
- Normal `List` işlemleri uyumluluk için çalışır ve gerektiğinde sonucu materialize eder.
- Native response sahipliği tamamlanma, açık `close`, decode hatası ve client bağlantısının kopması yollarında serbest bırakılır.
- Veri akışı tek database cursor, tek Dubbo isteği ve tek Dubbo cevabı olarak kalır. Tekrarlayan sayfa çağrıları eklenmez.

Bu özelliği yalnız bilinçli olarak büyük, sayfalamasız ve salt okunur sonuçlarda kullanın. Normal API tasarımında sayfalama kullanın. Encoded cevap writer tamamlanana kadar sınırlandırılmış native payload olarak kalır. Bu nedenle payload ve in-flight limitleri önemini korur.

```java
public interface CatalogRepository {
    @DubboStreamed
    List<CatalogRow> findAll(CatalogFilter filter);
}
```

Tüm framework artifact'larını birlikte yükseltin:

```xml
<properties>
  <java-rust-dubbo.version>0.6.0</java-rust-dubbo.version>
</properties>
```

Native ABI `3` olarak kalır. Windows x64, GLIBC 2.17 uyumlu Linux x64 ve Apple Silicon macOS 11+ artifact'larının native protokol yüzeyi değişmez.
