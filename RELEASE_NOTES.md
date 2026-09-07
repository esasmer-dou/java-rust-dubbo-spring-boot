## What's New

Version `0.4.1` keeps provider business-exception details instead of replacing them with a generic framework error.

- Rust consumers receive `DubboRemoteBusinessException` with the provider message, reported exception type, and a bounded cause chain.
- Rust providers return the standard Dubbo business-exception response to Rust and supported Apache Dubbo consumers.
- Synchronous failures and failed `CompletableFuture` results follow the same contract.
- Remote stack traces are not transferred. Expected outcomes should use typed result records and stable error codes.

Upgrade every framework artifact to `0.4.1` together and include exactly one native platform artifact. Windows x64, Linux x64 with GLIBC 2.17, and Apple Silicon macOS 11+ binaries were rebuilt from the same tagged source. Spring annotations, generated method signatures, business code, properties, and native ABI `3` remain compatible.

## Yenilikler

`0.4.1`, provider business exception bilgisinin genel bir framework hatasıyla değiştirilmesini engeller.

- Rust consumer, provider mesajını, bildirilen exception tipini ve sınırlandırılmış cause zincirini içeren `DubboRemoteBusinessException` alır.
- Rust provider, Rust consumer ve desteklenen Apache Dubbo consumer için standart Dubbo business-exception cevabı döner.
- Senkron hatalar ve hatayla tamamlanan `CompletableFuture` sonuçları aynı sözleşmeyi kullanır.
- Uzak stack trace taşınmaz. Beklenen sonuçlarda tip güvenli result record ve sabit hata kodu kullanılmalıdır.

Tüm framework artifact'larını birlikte `0.4.1` sürümüne yükseltin ve yalnızca bir native platform artifact'ı ekleyin. Windows x64, GLIBC 2.17 uyumlu Linux x64 ve Apple Silicon macOS 11+ binary'leri aynı tag kaynak kodundan yeniden üretildi. Spring annotation'ları, generated metot imzaları, business kodu, property'ler ve native ABI `3` uyumluluğunu korur.
