# Java Rust Dubbo Spring Boot 0.7.0

## What's New

This release fixes Hessian2 interoperability and adds explicit provider method deadlines.
Business services remain in Java. The generated invocation and codec paths remain reflection-free.

```java
@DubboService(timeout = 1500)
public final class CatalogServiceImpl implements CatalogService {
    @Override
    @DubboMethod(timeoutMs = 300)
    public CatalogView find(long id) { return repository.find(id); }
}
```

`find` uses 300 ms; other methods inherit 1500 ms. Queue wait and asynchronous completion
consume the same deadline. Expired queued work is not started. A running database operation is
not canceled or rolled back by a network timeout. Keep JDBC deadlines and idempotency explicit.
Native buffers and admission permits remain owned until the actual Java completion.

Hessian2 integer-thousandth doubles now decode correctly. Fractional output uses standard
full-width doubles for mixed-peer compatibility. Typed/generated and dynamic reference tables
stay aligned across records, collections and temporal values. Unsupported immutable cycles
fail explicitly rather than using reflective construction.

One-pass consumers retain only the current item's reference window, not every completed DTO.
References within one item work. Cross-item references require normal List materialization before
opening the iterator; one-pass mode rejects them explicitly. No second parse or weak-reference
allocation is added. A 10,000-row regression test checks the decoder retains at most the root
list and current row instead of 10,001 object references.

The optional Glowroot 0.6.0 native-Dubbo adapter can record provider business completion as
`Dubbo Provider` transactions. The Dubbo runtime itself does not depend on Glowroot.

Upgrade all framework artifacts and the annotation processor to `0.7.0`; rebuild the application.
Use the matching Windows x64 DLL, Linux x64 GLIBC 2.17 SO, or macOS ARM64 dylib.
Native ABI remains `3`; the deadline entry point is additive. Previous streaming APIs remain supported.

## Yenilikler

Bu sürüm Hessian2 uyumluluğunu düzeltir. Provider metotlarına ayrı timeout tanımlamayı sağlar.
İş kodu Java'da kalır. Metot çağrısı ve codec kodu build sırasında üretilir. Reflection eklenmez.

Örnekte `find` için 300 ms, diğer metotlar için 1500 ms kullanılır. Kuyrukta bekleme ve async
tamamlanma bu süreye dahildir. Kuyrukta süresi dolan iş başlatılmaz. Başlamış DB işlemi ağ timeout'u
yüzünden durdurulmaz veya geri alınmaz. JDBC timeout'unu ve idempotency kuralını ayrıca belirleyin.
Java tamamlanana kadar native buffer ve concurrency izni korunur.

Kesirli double değerleri ve tarih/saat içeren sonuçlardaki nesne referansları düzeltildi.
Tek geçişli tüketim biten öğeleri bellekte tutmaz. Öğeler arası referans içeren sonucu normal
List olarak okuyun. Böyle bir referans tek geçişli modda açık hata verir. İkinci parse geçişi
veya öğe başına weak-reference allocation eklenmez.
İsteğe bağlı Glowroot 0.6.0 adaptörü provider işlemlerini `Dubbo Provider` türünde gösterir.
Dubbo runtime'a agent bağımlılığı eklenmez.

Tüm framework paketlerini ve annotation processor sürümünü `0.7.0` yapın. Uygulamayı yeniden
derleyin. Aynı sürümün DLL/SO/dylib dosyasını kullanın. Native ABI `3` korunur.
