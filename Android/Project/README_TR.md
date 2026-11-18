# MultiCam Android — Kaynak proje ve APK derleme rehberi

Bu Android uygulaması telefonun kamerasını ve isteğe bağlı mikrofon sesini aynı Wi-Fi ağındaki MultiCam Windows alıcısına RTMP ile gönderir. Her telefon farklı yayın kanalı kullanır: `cam1`, `cam2`, `cam3`, `cam4`.

## Özellikler

- H.264 video: hedef 1920×1080, 30 FPS, 4 Mbps
- AAC ses: 44.1 kHz, stereo, hedef 128 kbps
- Ön/arka kamera değiştirme
- Yerel Wi-Fi üzerinden RTMP
- Dört ayrı telefon kanalı
- Kamera ve mikrofon izinleri

Gerçek 1080p performansı telefonun sensörüne, H.264 donanım kodlayıcısına ve Wi-Fi kalitesine bağlıdır. Bu ilk sürümde yayın boyunca uygulama ekranda açık tutulmalıdır; ekran kapalı/arka planda çalışma ayrı bir foreground-service geliştirmesidir.

## 1. Gereken geliştirme araçları

- Android Studio güncel kararlı sürüm
- JDK 17 veya üstü
- Android SDK Platform 36
- Android SDK Build-Tools 35.0.0 veya daha güncel uyumlu sürüm
- Gradle 8.13
- İnternet bağlantısı (ilk Gradle ve RootEncoder bağımlılıklarını indirmek için)

Projede Android Gradle Plugin 8.13.2 kullanılır. Bu sürümün Gradle 8.13 ve JDK 17 gereksinimleri Android'in resmi uyumluluk tablosunda belirtilmiştir.

## 2. APK oluşturma (Windows)

1. Bu `Project` klasörünü Android Studio ile aç.
2. Android Studio ilk kez açtığında **SDK Manager** bölümünden Android SDK Platform 36 ve Android SDK Build-Tools paketlerini kur.
3. Android Studio'da Gradle senkronizasyonunun tamamlanmasını bekle. İlk senkronizasyon RootEncoder kütüphanesini JitPack üzerinden indirebilir.
4. Proje klasöründeki `Build-APK.bat` dosyasına çift tıkla.
5. Derleme başarılı olursa APK burada oluşur:

   `app\build\outputs\apk\debug\app-debug.apk`

Alternatif olarak Android Studio'da **Build → Build Bundle(s) / APK(s) → Build APK(s)** menüsünü kullan.

`Build-APK.ps1`, Gradle 8.13 sistemde yoksa indirip açmayı dener. Android SDK'nın `ANDROID_HOME` veya `ANDROID_SDK_ROOT` ortam değişkeniyle tanımlanması ya da varsayılan `%LOCALAPPDATA%\Android\Sdk` konumunda bulunması gerekir.

## 3. Telefona yükleme

1. `app-debug.apk` dosyasını Android telefona gönder.
2. APK'yı aç ve Android'in istediği durumda bu kaynaktan uygulama yüklemeye izin ver.
3. MultiCam'i aç; kamera ve mikrofon izinlerini ver.
4. Telefon ile Windows bilgisayarın aynı Wi-Fi ağına bağlı olduğundan emin ol.

Debug APK'nın paket kimliği `com.multicam.live.debug` olur. Android Studio'da imzalı dağıtım paketi oluşturmak için **Build → Generate Signed Bundle / APK → APK** seçeneğini kullan.

## 4. Uygulamadaki bağlantı bilgileri

Windows uygulamasında gösterilen şu bilgileri gir:

- Sunucu: bilgisayarın yerel IPv4 adresi (ör. `192.168.1.20`)
- Port: `1935`
- Kullanıcı adı: `camera`
- Şifre: Windows programında **Telefon yayın şifresi** satırında gösterilir
- Kanal: her telefon için ayrı `cam1`, `cam2`, `cam3`, `cam4`

Mikrofon izni verilirse ses de aktarılır. Yayını başlatmadan önce doğru kanalın seçili olduğunu kontrol et.

## 5. OBS Studio'da görüntüyü açma

1. OBS Studio'yu aç.
2. Alt taraftaki **Kaynaklar (Sources)** panelinde **+** düğmesine tıkla.
3. **Medya Kaynağı (Media Source)** seç ve kaynağa `Telefon 1 - Cam1` gibi bir ad ver.
4. Özellikler penceresinde **Yerel Dosya (Local File)** işaretini kaldır.
5. Windows MultiCam uygulamasındaki `CAM 1` satırındaki OBS bağlantısını kopyala.
6. Bu bağlantıyı OBS'nin **Girdi (Input)** alanına yapıştır ve **Tamam** düğmesine bas.
7. Diğer telefonlar için yeni Medya Kaynakları ekle; `CAM 2`, `CAM 3`, `CAM 4` URL'lerini kullan.

OBS URL biçimi `rtmp://BILGISAYAR_IP:1935/cam1?user=viewer&pass=OBS_SIFRESI` şeklindedir. Hazır bağlantıyı Windows uygulamasından kopyalaman önerilir.

## 6. Sorun giderme

- Telefon bağlantı kuramıyorsa aynı Wi-Fi, doğru IP, Windows sunucu durumu ve TCP 1935 özel ağ güvenlik duvarı iznini kontrol et.
- OBS görüntü alamıyorsa Android'de yayının gerçekten başladığını ve OBS'de `Yerel Dosya` seçeneğinin kapalı olduğunu kontrol et.
- Kimlik doğrulama hatasında `camera` kullanıcı adı ve Windows uygulamasındaki telefon yayın şifresini yeniden kontrol et.
- 1080p hazırlanamıyorsa ilgili telefon bu kodlama profilini desteklemiyor olabilir; bu cihaz için daha düşük çözünürlük seçeneği eklenmelidir.

## Güvenlik

RTMP şifreli taşıma sağlamaz. Bu uygulamayı yalnızca güvendiğin yerel Wi-Fi ağında kullan; port 1935'i internete açma. Daha sonraki sürümde şifreli aktarım eklenmelidir.

## Bağımlılıklar

RootEncoder 2.8.1, Apache-2.0 lisanslı kütüphanedir. Resmi kaynak kodu ve sürüm bilgisi: https://github.com/pedroSG94/RootEncoder
