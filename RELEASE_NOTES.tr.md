# 0.8.2 Dağıtım Adayı

Hazırlık: 7 Kasım 2026. Yayımlanmadı; güncel kaynak için doğruluk kabulü bekleniyor.

Desteklenen on üretim modülünü, processor, enhancer ve native JAR'ları `0.8.2`
sürümüne hizalayın; istemcileri yeniden üretin. Üretilmiş codec standart Hessian
karakter metnini ve tipli direct JSON alanlarını destekler. Direct JSON tek
geçişlidir; kullanılmayan sonucu kapatın. Buffer tekrar yazımı aynı yanıtı kullanır,
RPC'yi tekrarlamaz.

`-Areactor.dubbo.nativeArguments=true` isteğe bağlı annotation-processor seçeneğidir;
runtime ayarı değildir. Varsayılan `false` kalır; seçenek verilmezse üretilmiş Java
Hessian istek kodlaması korunur. Açıldığında eşleşen native zorunludur; eksik export
açık hata verir. Mevcut JNI imzaları ve ABI `3` değişmez. Java iş mantığı ve istek
guard'ları zorunludur; önbellek veya bypass eklenmez.

P05 kabulü yalnız ölçülen Dubbo iş yüklerini kapsar. Tam HTTP **13/15; PASS
değildir**. **R-P05-HTTP-001**, kanıtlanmış ürün kök nedeni olmadan açık kalan küçük
HTTP kontrol rotası RPS/p99 riskidir. Tüm plan, varsayılanlar, endpoint'ler veya
platformlar için performans kabulü iddia edilmez. Native argümanların kapalı olması
tek başına ölçülen yapılandırmayla eşdeğerlik kanıtlamaz.

Windows x64, Linux x64 ve macOS ARM64 native dosyaları temiz kaynak
`984c1925f6b6efe17c5324f7802ddf3b95128a45` üzerinden, `42348372720` numaralı
compile-only build ile yeniden üretildi. Linux GLIBC 2.17 sınırı korunur. Bu dağıtım
snapshot'ı için native rebuild gerekmez. Build başarısı runtime kabulü değildir.
Eşleşen EmaTech kabulü ve sürüm sahibi onayı gelene kadar provenance runtime
doğrulanmamış ve yayına hazır değil olarak kalır. Rust ve iç testler private kalır.

Native SBOM metadata'sı, kilitli bağımlılık grafiğinde yalnız workspace sürümlerinin
değiştiği doğrulandıktan sonra mevcut envanterden güncellenir. Compile-only workflow'da
SBOM üretildiği veya runtime testi çalıştığı iddia edilmez. Public yayın ertelenmiştir.

---

# 0.8.1

Yayın tarihi: 2026-10-30.

## Üretilmiş Consumer Zaman Aşımı

Annotation processor artık interface üzerindeki `@DubboMethod(timeoutMs = ...)`
ve field üzerindeki `@DubboReference(timeout = ...)` için consumer bekleme bütçesi
üretir. İlk tanımlanan değer geçerlidir: metot > reference > route > genel ayar.
Senkron, asenkron, streamed ve direct JSON istemcileri aynı kurala uyar.

Metot değeri pozitif olmalıdır. Canonical reference için `0` route/genel ayarı
kullanır; negatif değer derleme hatasıdır. Apache uyumlu reference için `-1` de
miras alma anlamına gelir. Interface/group/version aynıysa reference'lar tek
istemciyi paylaşır; sıfır dahil normalize edilmiş reference süreleri aynı olmalıdır.
Çakışmada derleme durur; mesaj iki alanı ve değerlerini gösterir.
Group veya version farklıysa istemciler bağımsızdır.

## Yükseltme Ve Uyumluluk

Tüm Spring Dubbo Maven modüllerini, annotation processor ve enhancer plugin'i
`0.8.1` sürümüne hizalayın. İstemcileri yeniden üretmek için uygulamayı derleyin.
Eski üretilmiş istemciler çalışmaya devam eder: protected runtime overload'ları
korunur ve sıfır metot override'ı ile delege ederek miras alınan süreyi kullanır.

Native ABI `3` kalır. Paketlenmiş Windows DLL, Linux SO ve macOS dylib baytları
değişmez; Maven koordinatları `0.8.1` olur. Bu yama native kaynak veya binary
değişikliği içermez. Provider servis/implementasyon deadline'ları değişmez.

## Hata Ve Kabul Sınırları

Consumer timeout'u provider çalışmasını değil client beklemesini sınırlar.
Çalışan Java/DB işini kesmez veya geri almaz. Alt bağımlılık sürelerini, idempotency
ve açık hata yönetimini koruyun; timeout alan yazmayı kontrolsüz tekrarlamayın.
Otomatik RPC tekrarı eklenmez.

Streamed/direct JSON sonuçlarda bütçe yanıt paketinin tamamı gelene kadardır;
sonraki iterator tüketimi, projection veya HTTP çıktısını kapsamaz. Kaynak sahipliği
sınırlı kalır; hata veya bağlantı kopmasında kaynaklar yine kapatılmalıdır.
Yeni bir tam-yük, tüm-platform performans veya sıfır-ret garantisi verilmez.
Tarihsel performans sınırlamaları ve kabul kanıtları değişmez.

---

# 0.8.0

Yayın tarihi: 29 Ekim 2026.

## Ne Değişti?

Tipli direct JSON callback ve isteğe bağlı REST adaptörü, desteklenen sonuçları consumer tarafında satır DTO'ları oluşturmadan aktarabilir. Tek geçişli okuma ve sınırlı yanıt sahipliği açıkça tanımlıdır. Java satırları inceleyecek veya dönüştürecekse normal DTO çözümlemesini kullanın.

## Nasıl Kullanırım?

1. Proje sürümünü ve bağımlılıklarını aşağıdaki hizalı sürümlere güncelleyin.
2. Uygulamayı yeniden derleyin. JAR içindeki native dosyayı kullanın; eski DLL/SO dosyasını karıştırmayın.
3. İhtiyacınız olan özelliği [kullanım rehberinden](README.md) seçin. İsteğe bağlı özellikler kendiliğinden açılmaz.
4. Kendi CPU, bellek ve trafik sınırlarınızda doğrulayın. Timeout alan yazma çağrısını körlemesine tekrar göndermeyin.

## Sürüm Eşleşmesi

REST/platform: 4.8.0; cache: 0.9.0; Dubbo: 0.9.0; Spring Dubbo: 0.8.0; agent: 0.7.1.
Örnek uygulamalar: 0.8.0; ortak model/arayüzler: 0.5.0.
Native sözleşmeler değişmedi: REST 29, Dubbo 7, Spring Dubbo 3, Redis 6, Glowroot 6.

## Kabul Kapsamı

Java iş mantığı, sıfır-reflection yaklaşımı ve sınırlı kaynak kullanımı korunur.
L1 isteğe bağlıdır; her iş yükünde net bellek düşüşü garanti edilmez.
Isınma sırasında consumer retleri ayrıca izlenecek bir iyileştirme alanıdır.
Writer için 2,695 ms mutlak p99 ve consumer için sıfır ısınma reti genel bir ürün garantisi değildir.
Ölçüm aşamasındaki doğruluk ve hata kontrolleri korunur. Yeni bir tüm-platform performans garantisi verilmez.

GitHub Packages indirmek için Maven settings.xml içindeki repository kimliği ile server kimliğini eşleştirin.
Token, read:packages yetkisine ve gerekiyorsa private repository erişimine sahip olmalıdır. SSH yalnız Git erişimini sağlar.
