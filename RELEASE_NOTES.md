## What's New

Version `0.5.0` adds an opt-in low-retention provider path for large, read-only JDBC results.

- `DubboStreamingList<T>` lets generated provider code encode rows directly into the Dubbo response instead of first building a second full Java collection.
- Existing service contracts still return `List<T>`. Consumer code, Spring annotations, generated method signatures, and configuration keys remain compatible.
- Cursor, statement, connection, and native response resources are closed deterministically on success and failure.
- Request argument encoding no longer creates a second Rust `Vec`.
- Provider business exceptions continue to preserve the remote message, reported type, and bounded cause chain.

Use the streaming path only for provider-side, single-pass queries that cannot be paginated. Consumers still materialize the final list, so payload and collection limits remain mandatory.

Upgrade every framework artifact to `0.5.0` together and include exactly one native platform artifact. Windows x64, Linux x64 with GLIBC 2.17, and Apple Silicon macOS 11+ binaries were rebuilt and verified from the same tag. Native ABI remains `3`.

## Yenilikler

`0.5.0`, büyük ve salt okunur JDBC sonuçları için isteğe bağlı, düşük memory tutan bir provider yolu ekler.

- `DubboStreamingList<T>`, generated provider kodunun ikinci bir büyük Java collection oluşturmadan satırları doğrudan Dubbo response içine yazmasını sağlar.
- Mevcut service kontratları yine `List<T>` döner. Consumer kodu, Spring annotation'ları, generated metot imzaları ve property adları uyumludur.
- Cursor, statement, connection ve native response kaynakları başarıda ve hatada kesin olarak kapatılır.
- Request argümanları encode edilirken ikinci bir Rust `Vec` artık oluşturulmaz.
- Provider business exception mesajı, bildirilen tipi ve sınırlandırılmış cause zinciri korunmaya devam eder.

Streaming yolunu yalnızca provider tarafındaki tek geçişli ve pagination uygulanamayan sorgularda kullanın. Consumer son listeyi yine memory içinde oluşturur. Bu nedenle payload ve collection limitlerini mutlaka sınırlı tutun.

Tüm framework artifact'larını birlikte `0.5.0` sürümüne yükseltin ve yalnızca bir native platform artifact'ı ekleyin. Windows x64, GLIBC 2.17 uyumlu Linux x64 ve macOS 11+ Apple Silicon binary'leri aynı tag kaynak kodundan yeniden üretildi ve doğrulandı. Native ABI `3` olarak kalır.
