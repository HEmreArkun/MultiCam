package main

import (
	"archive/zip"
	"os"
	"path/filepath"
	"strings"
	"testing"
)

func TestEnsureConfigPersistsGeneratedCredentials(t *testing.T) {
	path := filepath.Join(t.TempDir(), "mediamtx.yml")
	camera1, viewer1, err := ensureConfig(path)
	if err != nil {
		t.Fatal(err)
	}
	if camera1 == "" || viewer1 == "" || camera1 == viewer1 {
		t.Fatal("beklenen benzersiz kimlik bilgileri oluşturulmadı")
	}
	camera2, viewer2, err := ensureConfig(path)
	if err != nil {
		t.Fatal(err)
	}
	if camera1 != camera2 || viewer1 != viewer2 {
		t.Fatal("kimlik bilgileri sonraki başlatmada değişti")
	}
	b, err := os.ReadFile(path)
	if err != nil {
		t.Fatal(err)
	}
	if !strings.Contains(string(b), camera1) || !strings.Contains(string(b), viewer1) {
		t.Fatal("kimlik bilgileri config dosyasına yazılmadı")
	}
}

func TestExtractMediaMTX(t *testing.T) {
	dir := t.TempDir()
	zipPath := filepath.Join(dir, "server.zip")
	f, err := os.Create(zipPath)
	if err != nil {
		t.Fatal(err)
	}
	zw := zip.NewWriter(f)
	entry, err := zw.Create("package/mediamtx.exe")
	if err != nil {
		t.Fatal(err)
	}
	if _, err := entry.Write([]byte("test-binary")); err != nil {
		t.Fatal(err)
	}
	if err := zw.Close(); err != nil {
		t.Fatal(err)
	}
	if err := f.Close(); err != nil {
		t.Fatal(err)
	}
	dest := filepath.Join(dir, "mediamtx.exe")
	if err := extractMediaMTX(zipPath, dest); err != nil {
		t.Fatal(err)
	}
	got, err := os.ReadFile(dest)
	if err != nil {
		t.Fatal(err)
	}
	if string(got) != "test-binary" {
		t.Fatalf("beklenmeyen içerik: %q", got)
	}
}
