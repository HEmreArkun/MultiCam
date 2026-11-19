package main

import (
	"archive/zip"
	"crypto/rand"
	"crypto/sha256"
	_ "embed"
	"encoding/base64"
	"encoding/hex"
	"errors"
	"fmt"
	"io"
	"net"
	"net/http"
	"os"
	"os/exec"
	"path/filepath"
	"runtime"
	"strings"
	"time"
)

// MediaMTXVersion is pinned to a known release so the launcher does not
// silently change behavior when a new server version is published.
const MediaMTXVersion = "1.21.4"
const MediaMTXZipSHA256 = "835645b2700ffce4d2b6cb04bfa0aadce95335da6dfc0d7c2114e6da585734f8"

//go:embed mediamtx.template.yml
var configTemplate string

func main() {
	printHeader()
	if runtime.GOOS != "windows" || runtime.GOARCH != "amd64" {
		fatal("Bu derleme Windows 10/11 x64 içindir.")
	}

	exePath, err := os.Executable()
	if err != nil {
		fatal("Uygulama yolu belirlenemedi: %v", err)
	}
	dataDir := filepath.Join(filepath.Dir(exePath), "MultiCamData")
	if err := os.MkdirAll(dataDir, 0755); err != nil {
		fatal("Veri klasörü oluşturulamadı: %v", err)
	}

	configPath := filepath.Join(dataDir, "mediamtx.yml")
	cameraPass, viewerPass, err := ensureConfig(configPath)
	if err != nil {
		fatal("Sunucu ayarı hazırlanamadı: %v", err)
	}

	serverPath := filepath.Join(dataDir, "mediamtx.exe")
	if _, err := os.Stat(serverPath); errors.Is(err, os.ErrNotExist) {
		zipPath := filepath.Join(dataDir, "mediamtx_windows_amd64.zip")
		url := fmt.Sprintf("https://github.com/bluenviron/mediamtx/releases/download/v%s/mediamtx_v%s_windows_amd64.zip", MediaMTXVersion, MediaMTXVersion)
		fmt.Printf("MediaMTX %s ilk kullanım için indiriliyor...\n", MediaMTXVersion)
		if err := downloadFile(url, zipPath, MediaMTXZipSHA256); err != nil {
			fatal("MediaMTX indirilemedi: %v\nİnternet bağlantısını kontrol edip tekrar çalıştır.", err)
		}
		fmt.Println("Arşiv açılıyor...")
		if err := extractMediaMTX(zipPath, serverPath); err != nil {
			fatal("MediaMTX arşivi açılamadı: %v", err)
		}
		_ = os.Remove(zipPath)
	}

	printConnectionInfo(cameraPass, viewerPass)
	fmt.Println("\nSunucu başlatılıyor. Bu pencere açık kaldığı sürece kameralar bağlanabilir.")
	fmt.Println("Durdurmak için Ctrl+C kullan. İlk çalıştırmada Windows Güvenlik Duvarı sorarsa Özel ağlara izin ver.")
	fmt.Println("------------------------------------------------------------")

	cmd := exec.Command(serverPath, configPath)
	cmd.Dir = dataDir
	cmd.Stdin = os.Stdin
	cmd.Stdout = os.Stdout
	cmd.Stderr = os.Stderr
	if err := cmd.Run(); err != nil {
		fatal("MediaMTX kapandı veya başlatılamadı: %v", err)
	}
}

func printHeader() {
	fmt.Println("============================================================")
	fmt.Println("                 MULTICAM WINDOWS ALICISI")
	fmt.Printf("                 MediaMTX v%s  |  x64\n", MediaMTXVersion)
	fmt.Println("============================================================")
}

func fatal(format string, args ...any) {
	fmt.Fprintf(os.Stderr, "\nHATA: "+format+"\n", args...)
	fmt.Println("Çıkmak için Enter tuşuna bas.")
	var s string
	_, _ = fmt.Scanln(&s)
	os.Exit(1)
}

func newPassword() (string, error) {
	b := make([]byte, 24)
	if _, err := rand.Read(b); err != nil {
		return "", err
	}
	return base64.RawURLEncoding.EncodeToString(b), nil
}

func ensureConfig(path string) (string, string, error) {
	if b, err := os.ReadFile(path); err == nil {
		text := string(b)
		camera := valueAfterComment(text, "# MULTICAM_CAMERA_PASSWORD=")
		viewer := valueAfterComment(text, "# MULTICAM_VIEWER_PASSWORD=")
		if camera != "" && viewer != "" {
			return camera, viewer, nil
		}
	}

	camera, err := newPassword()
	if err != nil {
		return "", "", err
	}
	viewer, err := newPassword()
	if err != nil {
		return "", "", err
	}
	text := strings.ReplaceAll(configTemplate, "{{CAMERA_PASSWORD}}", camera)
	text = strings.ReplaceAll(text, "{{VIEWER_PASSWORD}}", viewer)
	text = "# MULTICAM_CAMERA_PASSWORD=" + camera + "\n# MULTICAM_VIEWER_PASSWORD=" + viewer + "\n" + text
	if err := os.WriteFile(path, []byte(text), 0600); err != nil {
		return "", "", err
	}
	return camera, viewer, nil
}

func valueAfterComment(text, prefix string) string {
	for _, line := range strings.Split(text, "\n") {
		line = strings.TrimSpace(line)
		if strings.HasPrefix(line, prefix) {
			return strings.TrimSpace(strings.TrimPrefix(line, prefix))
		}
	}
	return ""
}

func printConnectionInfo(cameraPass, viewerPass string) {
	ips := localIPv4s()
	fmt.Println("\nBİLGİSAYARIN YEREL IP ADRESLERİ:")
	if len(ips) == 0 {
		fmt.Println("  IPv4 adresi bulunamadı. Wi-Fi bağlantısını kontrol et.")
	}
	for _, ip := range ips {
		fmt.Printf("  %s\n", ip)
	}
	fmt.Println("\nANDROID UYGULAMASINDA KULLAN:")
	fmt.Println("  Sunucu IP       : Bu bilgisayarın yukarıdaki IPv4 adresi")
	fmt.Println("  Port            : 1935")
	fmt.Println("  Kullanıcı adı   : camera")
	fmt.Printf("  Şifre           : %s\n", cameraPass)
	fmt.Println("  Kamera kanalları: cam1, cam2, cam3, cam4")
	fmt.Println("\nOBS STUDIO MEDYA KAYNAĞI URL'LERİ:")
	for _, ip := range ips {
		fmt.Printf("  rtmp://%s:1935/cam1?user=viewer&pass=%s\n", ip, viewerPass)
		break // A single chosen LAN address is enough for OBS.
	}
	fmt.Println("  Diğer telefonlar için URL içindeki cam1 bölümünü cam2/cam3/cam4 yap.")
	fmt.Println("\nGÜVENLİK: Yalnızca güvendiğin yerel Wi-Fi ağında kullan; RTMP şifreli değildir.")
}

func localIPv4s() []string {
	interfaces, err := net.Interfaces()
	if err != nil {
		return nil
	}
	var result []string
	for _, iface := range interfaces {
		if iface.Flags&net.FlagUp == 0 || iface.Flags&net.FlagLoopback != 0 {
			continue
		}
		addrs, err := iface.Addrs()
		if err != nil {
			continue
		}
		for _, addr := range addrs {
			var ip net.IP
			switch v := addr.(type) {
			case *net.IPNet:
				ip = v.IP
			case *net.IPAddr:
				ip = v.IP
			}
			if ip == nil || ip.IsLoopback() || ip.To4() == nil || ip.IsLinkLocalUnicast() {
				continue
			}
			result = append(result, ip.To4().String())
		}
	}
	return result
}

func downloadFile(url, path, expectedSHA256 string) error {
	client := &http.Client{Timeout: 4 * time.Minute}
	resp, err := client.Get(url)
	if err != nil {
		return err
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		return fmt.Errorf("indirme sunucusu HTTP %s yanıtı verdi", resp.Status)
	}
	f, err := os.Create(path)
	if err != nil {
		return err
	}
	defer f.Close()
	hash := sha256.New()
	if _, err = io.Copy(io.MultiWriter(f, hash), resp.Body); err != nil {
		_ = os.Remove(path)
		return err
	}
	actual := hex.EncodeToString(hash.Sum(nil))
	if !strings.EqualFold(actual, expectedSHA256) {
		_ = os.Remove(path)
		return fmt.Errorf("SHA-256 doğrulaması başarısız; dosya beklenen resmi sürümle eşleşmiyor")
	}
	return nil
}

func extractMediaMTX(zipPath, destPath string) error {
	r, err := zip.OpenReader(zipPath)
	if err != nil {
		return err
	}
	defer r.Close()
	for _, f := range r.File {
		if strings.EqualFold(filepath.Base(f.Name), "mediamtx.exe") {
			rc, err := f.Open()
			if err != nil {
				return err
			}
			out, err := os.Create(destPath)
			if err != nil {
				rc.Close()
				return err
			}
			_, copyErr := io.Copy(out, rc)
			closeErr := out.Close()
			rc.Close()
			if copyErr != nil {
				return copyErr
			}
			if closeErr != nil {
				return closeErr
			}
			return os.Chmod(destPath, 0755)
		}
	}
	return fmt.Errorf("arşiv içinde mediamtx.exe bulunamadı")
}
