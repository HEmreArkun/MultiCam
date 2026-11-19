# MultiCam Windows Alıcısı

`MultiCamWindows.exe`, Windows 10/11 x64 içindir. İlk çalıştırmada MediaMTX v1.21.4 sunucusunu resmi GitHub sürümünden indirir, `MultiCamData` klasörüne açar ve yerel RTMP alıcısını başlatır. İlk çalıştırma için internet gerekir; telefonların yayını için sonrasında yalnızca aynı yerel Wi-Fi ağı yeterlidir.

## Kullanım

1. ZIP arşivini bilgisayarda normal bir klasöre çıkar.
2. `MultiCamWindows.exe` dosyasını çalıştır.
3. İlk açılışta Windows Güvenlik Duvarı sorarsa yalnızca Özel ağlarda erişime izin ver.
4. Ekrandaki IPv4 adresini, kullanıcı adını ve şifreyi Android uygulamasına gir. Telefon kanalları `cam1`–`cam4` olmalı.
5. OBS içinde her telefon için bir `Medya Kaynağı` ekle, `Yerel dosya` seçeneğini kapat ve konsolda gösterilen RTMP URL'sini gir. `cam1` değerini diğer telefonlar için `cam2`, `cam3` veya `cam4` yap.
6. Alıcıyı kapatmak için konsol penceresinde Ctrl+C kullan.

Ayar ve sunucu dosyaları `MultiCamData` klasöründe tutulur. Kimlik bilgileri ilk açılışta rastgele üretilir ve sonraki başlatmalarda korunur. Şifreyi kaybedersen `MultiCamData\mediamtx.yml` dosyasını yedekledikten sonra silip programı yeniden çalıştırarak yeni şifre üretebilirsin.

## Önemli

RTMP şifreli değildir. Port 1935'i internete açma; yalnızca güvendiğin yerel ağda kullan. Bu Windows `.exe` bir başlatıcıdır; MediaMTX sunucu bileşenini ilk çalıştırmada indirir.
