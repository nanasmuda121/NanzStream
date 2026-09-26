# 🎬 NanzStream — Kumpulan Streaming & Baca Komik (Native Kotlin)

Aplikasi Android Native modern berbasis **Kotlin + Jetpack Compose** dengan desain **100% Monochrome Glassmorphism** elegan. Menghadirkan portal hiburan lengkap dalam satu aplikasi (All-in-One):
- 🎭 **Drama Korea & Asia** (Drakor.id)
- 📖 **Komik & Manga Reader** (Manga UP dengan dekripsi otomatis AES-256)
- ⚔️ **Anime Subtitle Indonesia** (Samehadaku)
- 🐉 **Donghua / Animasi 3D China** (Anichin)
- 📺 **Live TV Indonesia & Mancanegara** (CubMu 80+ Saluran HD)
- 🎬 **Serial & Film VOD** (CubMu)

---

## ✨ Fitur Unggulan

### 1. 💎 Tampilan 100% Monochrome Glassmorphism
- Antarmuka bertema gelap murni (*Obsidian Charcoal*) dipadukan dengan efek kaca buram (*Frosted Glassmorphism*).
- Border halus tipis 1dp dengan gradasi pencahayaan ambient.
- Tipografi kontras tinggi yang jernih dan nyaman di mata untuk sesi menonton dan membaca maraton.

### 2. ⚡ Kategori Lengkap di Landing Screen
- **Pills Filter Kategori Interaktif**: Semua, Drama, Komik, Anime, Donghua, Live TV, dan VOD.
- **Hero Spotlight Carousel**: Menampilkan tayangan trending terpopuler dengan sinopsis dan tombol akses cepat.
- **Lanjutkan Nonton & Baca**: Otomatis menyimpan progres menit tontonan dan chapter komik terakhir di penyimpanan lokal perangkat.
- **Koleksi / Watchlist**: Fitur bookmark untuk menyimpan judul favorit Anda.

### 3. 🎥 Pemutar Video Sinematik (Media3 ExoPlayer + WebView Sandboxed)
- Mendukung direct stream **HLS (.m3u8)** dan **MP4**.
- Sandboxed WebView untuk memutar server embed pihak ketiga (OK.ru, Bunny, Dailymotion, Rubyvid, dll) dengan filter iklan popup.
- Pilihan server streaming dinamis (Lite, Fast, Max, Embed).
- Navigasi cepat episode (Episode Selanjutnya & Sebelumnya).

### 4. 📖 Pembaca Komik Cepat (Manga, Manhwa & Manhua - Bacakomik)
- **Mode Vertikal**: Gulir vertikal berkelanjutan (*continuous vertical scroll*).
- **Mode Per Halaman**: Membaca santai halaman demi halaman.
- **Engine Pembaca Terpadu**: Mengambil langsung chapter dan gambar komik dari Bacakomik dengan perlindungan referer anti-blokir.

### 5. 📺 Siaran Langsung 80+ Channel TV
- Trans TV, Trans7, CNN Indonesia, CNBC Indonesia, SCTV, Indosiar, tvN Movies, dll.
- Pemutaran live HLS instan dengan daftar channel yang dikelompokkan berdasarkan genre (TV Nasional, Berita, Film, Hiburan).

---

## 🛠️ Informasi Teknis & Konfigurasi

- **Nama Aplikasi**: NanzStream
- **Package Name**: `com.nanzstream.nanas`
- **Versi**: `1.3.16` (Version Code: 46)
- **Min SDK**: Android 7.0 (API 24)
- **Target SDK**: Android 15 (API 35)
- **UI Framework**: Android Jetpack Compose + Material 3
- **Media Engine**: AndroidX Media3 (ExoPlayer 1.4.1 + HLS)
- **Image Loader**: Coil Compose 2.7.0
- **Network**: OkHttp 4.12.0 + IPv4 First Priority

---

## 🔑 Embedded Keystore (Siap Build Release)

Keystore resmi telah disertakan di repositori (`nanzstream-release.jks`) sehingga build GitHub Actions akan langsung menghasilkan **APK Universal Release yang telah ditandatangani (Signed)** dan siap langsung diinstal di semua jenis HP Android:
- **File**: `nanzstream-release.jks`
- **Keystore Password**: `nanzstream123`
- **Key Alias**: `nanzstream`
- **Key Password**: `nanzstream123`
- **Masa Berlaku**: 10.000 hari (~27 tahun)

---

## 🚀 Build Otomatis di GitHub Actions

Repositori ini sudah dilengkapi workflow CI/CD otomatis di `.github/workflows/build.yml`.

### Cara Mendapatkan APK:
1. Push repositori ini ke GitHub.
2. Buka tab **Actions** di GitHub.
3. Klik workflow **Build NanzStream Universal Release APK**.
4. Klik tombol **Run workflow** (atau otomatis jalan setiap kali Anda melakukan push ke branch `main`/`master`).
5. Tunggu proses kompilasi selesai (~2-3 menit).
6. Unduh file **`NanzStream-v1.0.0-universal-release.apk`** dari bagian **Artifacts**!

---

## ☁️ Deploy Scraper API ke Vercel (Opsional)

Folder `/api` dapat langsung di-deploy ke Vercel jika Anda ingin memiliki backend sendiri:
```bash
cd api
vercel --prod
```
Setelah deploy, Anda dapat memasukkan URL API Anda di menu **Setelan / Pengaturan** di dalam aplikasi NanzStream.
