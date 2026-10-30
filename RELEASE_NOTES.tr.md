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
