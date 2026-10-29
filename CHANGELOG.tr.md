# Değişiklik Kaydı

## 0.8.0 - 2026-10-29

- İsteğe bağlı REST adaptörü, desteklenen Hessian listelerini consumer tarafında satır DTO'ları oluşturmadan JSON'a yazar. Java'nın satırları işlemesi gereken durumlarda normal DTO yolu korunur.
- Yanıt kapasitesi yetersiz olduğunda aynı native yanıtla tekrar yazılır; RPC ve iş mantığı yeniden çalıştırılmaz.
- Yanıt handle'ları, callback kuyruğu ve provider buffer sahipliği sınırlıdır. İptal ve timeout, çalışan Java kodunun kullandığı belleği erken serbest bırakmaz.
- Düz Java için başlangıçta oluşturulan istemciler eklendi. Reflection ve runtime proxy gerekmez.
- Linux x64, Windows x64 ve macOS ARM64 native dosyaları aynı kaynak sürümünden hazırlanır. Native ABI `3` korunur.

## 0.7.0 - 2026-10-16

- Tek geçişli consumer yalnız kök listeyi ve o anki öğenin referanslarını tutar. Öğeler arası referans gereken sonuçlar normal List ile okunur. Tek geçişli modda tüm DTO grafiğini gizlice tutmak yerine açık hata verilir.
- Kesik gelen değişken uzunluklu listeler başarılı kısmi sonuç olarak gösterilmez; açık codec hatası verilir.

- Hessian2 kesirli double değerleri ve tipli nesnelerin `Q` referans sırası düzeltildi.
- Servis için `@DubboService(timeout=...)`, metot için `@DubboMethod(timeoutMs=...)` eklendi. Süreye kuyruk ve async tamamlanma dahildir.
- Timeout sonrasında Java tamamlanana kadar native buffer ve concurrency izni korunur. Süresi dolan kuyruk işi başlatılmaz.
- İsteğe bağlı Glowroot provider adaptörü için başlangıçta bağlanan telemetri arayüzü eklendi. Core runtime agent, Apache Dubbo veya reflection bağımlılığı almaz.
- Native ABI `3` korunur. Yeni metot timeout'u aynı sürümün native dosyasını gerektirir.


## 0.6.0 - 2026-09-30

- Senkron ve somut `List<T>` contract metotları için build sırasında doğrulanan `@DubboStreamed` annotation'ı eklendi.
- Public Java metot imzası değişmeden, tek geçişli response writer'lar için native response'a bağlı `DubboStreamingResult<T>` eklendi.
- Consumer tarafında bütün Java sonuç grafiğini tutmak yerine her seferinde bir DTO decode edilip yazılır.
- Native response sahipliği listenin sonunda, açık `close`, decode hatası ve HTTP client bağlantısının kopması yollarında deterministik olarak bırakılır.
- Streaming kullanmayan çağrılar için normal `List` davranışı ihtiyaç anında materialize edilerek korunur.
- Tek database sorgusu ve tek Dubbo istek/cevap akışı korunur. Tekrarlayan offset sayfalama protokolü eklenmez.
- Native ABI `3`, Dubbo wire uyumluluğu, provider davranışı ve annotation eklenmeyen metotlar değişmez.

## 0.5.0 - 2026-09-29

- Public `List<T>` servis imzalarını koruyan, isteğe bağlı `DubboStreamingList<T>` provider sözleşmesi eklendi.
- Sayfalamasız database satırları, provider'da tam liste oluşturmadan generated Hessian codec üzerinden büyüyebilen native response buffer'a aktarılır. Request argümanındaki gereksiz kopya da kaldırıldı.
- Hessian listesi için sonradan tamamlanan uzunluk alanı ve derinlik, koleksiyon ve payload sınırlarını koruyan değer atlama desteği eklendi.
- Codec context, JDBC ve native response kaynakları başarı, business exception, encoding hatası, timeout ve bağlantı kopması yollarında deterministik olarak serbest bırakılır.
- Generated client'lar, Spring annotation'ları, DTO sözleşmeleri, Dubbo wire uyumluluğu, GLIBC 2.17 desteği ve native ABI `3` korundu.

## 0.4.1 - 2026-09-07

- Provider business exception mesajı, bildirilen exception tipi ve sınırlandırılmış cause zinciri Rust ve Apache Dubbo consumer/provider kombinasyonlarında korunur.
- Rust provider'daki senkron ve asenkron hatalar genel servis hatasına çevrilmek yerine standart Dubbo business-exception cevabı olarak kodlanır.
- Reflection kullanmadan consumer tarafındaki hata bilgisini okumak için `DubboRemoteBusinessException` eklendi. Başarılı çağrı davranışı ve native ABI `3` değişmedi.
- Uzak exception mesajı, tip adı ve cause derinliği sınırlandırıldı. Rust provider wire payload'ında uzak stack trace taşınmaz.

## 0.4.0 - 2026-09-03

- Apple Silicon Mac bilgisayarlar için ayrı `java-rust-dubbo-native-macos-aarch64` Maven artifact'ı eklendi.
- Java API'si ve native ABI `3` değişmeden `librust_dubbo-macos-aarch64.dylib` macOS ARM64 JNI kütüphanesi yüklenir.
- Apple Silicon için desteklenen en düşük sürüm olan `macOS 11.0`, native deployment target olarak sabitlendi.
- Mach-O mimarisi, minimum macOS sürümü, sistem bağımlılıkları, ad-hoc code signing, JNI yükleme ve ABI release gate'leri eklendi.

## 0.3.1 - 2026-08-30

- Annotation processor source seviyesi, yalnızca Java 21'de bulunan `SourceVersion.RELEASE_21` alanına bağlanmak yerine aktif compiler modelinden alınır.
- Java 21 ile build edilen projelerde NetBeans 17'nin geçerli `@DubboReference` sınıflarını parsing hatası olarak göstermesi engellenir.
- Generated contract'lar, kalıcı annotation'lar, native ABI `3`, wire davranışı ve runtime performance özellikleri değişmez.

## 0.3.0 - 2026-08-30

- `DubboReference`, `DubboService` ve `EnableDubbo` için kalıcı `com.reactor.rust.dubbo.annotation` package'ı eklendi.
- Starter'ın transitif annotation artifact'ından Apache package sınıfları çıkarıldı. Böylece Apache ve Rust birlikte kullanılan geçiş uygulamalarında duplicate class riski kaldırıldı.
- Eski import'ların geçici kullanımı için isteğe bağlı `java-rust-dubbo-apache-compat-annotations` artifact'ı ile açık compiler/enhancer uyumluluk seçenekleri eklendi.
- Codegen ve bytecode enhancement varsayılan olarak yalnızca kalıcı annotation'ları işler. Uyumluluk açıkça etkinleştirilmezse resmî Apache annotation'larına dokunulmaz.
- Kalıcı API, eski import uyumluluğu ve mixed-mode izolasyonu için ayrı build gate'leri eklendi.
- Native ABI `3`, Dubbo wire davranışı, Java business API'leri ve bağımsız Rust runtime değişmedi.

## 0.2.1 - 2026-08-30

- Koşul veya profil ile kapatılan Spring bean'leri için native client açmadan generated consumer factory kaydı eklendi.
- Aktif generated reference'lar için strict route ve readiness kontrolü korundu.
- Kapalı ve açık koşullu reference davranışı için unit ve native entegrasyon testleri eklendi.

## 0.2.0 - 2026-08-29

- Birden fazla provider uygulamasına bağlanan consumer'lar için tam `interface + group + version` anahtarıyla servis route desteği eklendi.
- Her servis için ayrı endpoint, connection, queue, in-flight, timeout, payload, collection ve buffer retention bütçesi tanımlayan isimli route property'leri eklendi.
- Global consumer property'leri geriye uyumlu fallback olarak korundu. `require-explicit-routes` ile strict startup doğrulaması eklendi.
- Health-aware native connection seçimi eklendi. Kopuk socket'ler atlanır, queue'sunda hemen yer olan connection'a öncelik verilir ve reconnect bekleme süresi mevcut RPC deadline içinde kalır.
- `clientConnectionWaits`, `clientConnectionWaitTimeouts` metrikleri ve route'a özel `unreadyClients` health detayı eklendi.
- Gerçek iki-provider TCP entegrasyon testi, strict/fallback property testleri ve kopuk replica testi eklendi. Windows/Linux native artifact'ları ABI `3` değişmeden yeniden üretildi.
- Çoklu provider deployment'ları için iki dilli mimari, migration, tuning, operasyon ve public kullanım dokümanları genişletildi.

## 0.1.0 - 2026-08-28

- Kaynak kod uyumlu `@EnableDubbo`, `@DubboReference` ve `@DubboService` annotation'ları eklendi.
- Derleme zamanında üretilen tip güvenli istemciler, provider dispatcher'ları, Spring bean tanımları, kararlı kimlikler ve Hessian codec'leri eklendi.
- Runtime reflection veya proxy üretmeden private alan enjeksiyonu yapan build-time enhancement eklendi.
- Kalıcı bağlantılar, request-ID demultiplexing, yeniden bağlanma, heartbeat, deadline ve sınırlı backpressure kullanan bağımsız Rust Tokio consumer data-plane eklendi.
- Sınırlı business worker'lar, kuyruklar, isimlendirilmiş concurrency lane'leri, async completion ve graceful drain kullanan bağımsız Rust Tokio provider data-plane eklendi.
- Native-owned response handle'ları, deterministik serbest bırakma, limitli direct request-buffer yeniden kullanımı ve native leak metrikleri eklendi.
- ABI sürümü `3` kontrolüyle Windows x64 ve GLIBC 2.17 uyumlu Linux x64 platform artifact'ları eklendi.
- Spring Boot profilleri, readiness health entegrasyonu, configuration metadata, NMC derleme uyumluluğu gate'leri, resmi Dubbo wire interoperability testleri, bozuk frame testleri, provider restart testleri, yük testleri ve soak testleri eklendi.
- Sıfır hatayı varsayılan benchmark gate'i olarak korurken provider reject korelasyonu ve açıkça etkinleştirilen overload hata bütçesi modu eklendi.
- Production frame decode ve provider lane invariant panic yolları sınırlı hata yönetimiyle değiştirildi.
- Custom ve resmi Spring Boot yapılarını karşılaştıran kabul edilmiş HTTP matrisi ile throughput, p99, RSS, anonymous memory, startup ve paket boyutu raporu eklendi.
- İki dilli mimari, migration, tuning, operasyon ve performans dokümantasyonu eklendi.

- Tekrar üretilebilir Windows MSVC ve GLIBC 2.17 Linux native release job'ları eklendi.
- GitHub Packages yayını, source JAR'lar, Java/Rust SBOM dosyaları, checksum dosyası ve boş Maven cache consumer gate'i eklendi.
- CI, pinlenmiş GitHub Actions, Dependabot, stable release notları ve kalıcı release kanıtı eklendi.
- 10.000 çağrılık Spring Boot container RPC gate'i otomatikleştirildi. Eski snapshot artifact'inin yanlışlıkla image'a girmesini önlemek için benchmark image'ları sürümden bağımsız `target/app.jar` kullanır.
- NMC uyumluluk profili private `com.zenia` artifact'lerine ihtiyaç duyduğu için isteğe bağlı bırakıldı. Bu artifact'ler framework package'ına veya bağımsız release workflow'una girmez.

Bu sürüm ilk stable release'tir.
