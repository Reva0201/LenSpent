# LensSpent

Aplikasi pencatat pengeluaran untuk mahasiswa yang hidup dengan anggaran bulanan.
Arahkan kamera ke struk, aplikasi membacanya di perangkat, lalu minta konfirmasi.
Kalau struk tidak terbaca, transaksi bisa diketik manual. Semua data keuangan tetap
di ponsel: tidak ada akun dan tidak ada server.

Proyek UTS IF570L Lab Mobile Application Programming — Kelompok **Fantastic Four**.

## Deskripsi aplikasi

Lima jenis layar yang diwajibkan sudah ada:

| Jenis | Berkas | Isi |
| --- | --- | --- |
| List | `HomeScreen.kt` | Ringkasan bulan, grafik mingguan, daftar transaksi |
| Detail | `TransactionDetailScreen.kt` | Satu transaksi, dibuka dengan id |
| Form | `AddExpenseScreen.kt` | Tambah dan ubah transaksi, dengan validasi |
| Fitur native | `CameraScreen.kt` | Viewfinder, shutter, galeri, panel tips |
| Pilihan bebas | `AnalyticsScreen.kt` | Trends: Daily, Weekly, Yearly, Categories |

APK yang bisa dipasang ikut di dalam zip dengan nama `LensSpent.apk`.
Build ulang: `gradlew.bat :app:assembleDebug`

## Anggota kelompok

| Nama | NIM |
| --- | --- |
| Muhammad Meccapareva | 000001113059 |
| Christian Kaliman | 00000105460 |
| Faturachman Al Kahfi | 00000075344 |
| Clarence Bradlee Gosal | 00000137487 |
| Ezequiel Emeraldo | 00000159857 |
| Farell Timothy | 00000108839 |

## Pembagian tugas dan sistem kerja

Pekerjaan dibagi per bagian aplikasi, bukan per jenis pekerjaan. Satu layar atau
satu lapisan dikerjakan satu orang sampai selesai, supaya tidak ada dua orang
menulis file yang sama pada waktu yang sama. Bagian yang menjadi fondasi
(struktur aplikasi, basis data, tema) dikerjakan lebih dulu karena layar lain
bergantung padanya.

Sistem kerja:

- Satu repositori Git. Setiap anggota bekerja di branch sendiri, lalu digabung ke
  `main` setelah build dan seluruh unit test lulus.
- Sebelum merge, minimal satu anggota lain membaca perubahannya.
- Dua pertemuan per minggu: pertama membagi pekerjaan dan menyepakati tampilan,
  kedua menggabungkan hasil dan memeriksa aplikasi langsung di emulator.
- Konflik di file bersama (tema, navigasi, README) diselesaikan oleh pemilik file
  tersebut, bukan oleh orang yang terakhir commit.
- Semua keputusan tampilan mengacu ke lapisan tema di `ui/theme/` supaya warna,
  huruf, dan radius tidak ditulis ulang di masing-masing layar.
- Sebelum menyerahkan hasil, aplikasi dijalankan dari instalasi bersih dan seluruh
  alur dicoba ulang: tambah, ubah, hapus, pindai struk, dan buka semua
  kompartemen Trends.

## Kontribusi tiap anggota

**Muhammad Meccapareva (000001113059)**
- Struktur aplikasi: `AppContainer`, `AppViewModelProvider`, pemisahan lapisan UI,
  ViewModel, repository, dan data.
- Pipeline OCR di perangkat: `CameraScreen` dan `ImageStorage` untuk pengambilan
  gambar, `MlKitCaptureExtractor`, `OcrRows`, `ReceiptParser`, `ReceiptStructurer`,
  dan `StructuredJson`.
- Skema basis data lokal: entity, DAO, konverter, `OfflineTransactionRepository`,
  serta penghubung antara capture dan transaksi.
- `HomeScreen` dan `HomeViewModel`: kartu anggaran, ringkasan minggu dan hari ini,
  grafik, daftar transaksi.
- `AddExpenseScreen` dan `AddExpenseViewModel`: form tambah dan ubah beserta aturan
  validasi dan tipe keyboard.
- `TransactionDetailScreen`: detail dengan argumen id, hapus dengan konfirmasi,
  keadaan "transaksi sudah tidak ada".
- Lapisan tema `ui/theme/`: skema warna terang dan gelap, tipografi Plus Jakarta
  Sans, skala bentuk, token jarak, palet kategori, ikon launcher.
- Komponen bersama: `LensCard`, `CategoryBadge`, `SectionHeader`, `SegmentedTabs`,
  `CountUpAmount`, tile kategori.
- `OnboardingScreen`, `SettingsScreen`, `AiChatScreen` beserta view model-nya.
- `DemoLedger` dan penyemaian data contoh beserta flag build-nya.
- Perekaman video demo.

**Christian Kaliman (00000105460)**
- `AnalyticsScreen` dan `AnalyticsViewModel`: empat kompartemen Daily, Weekly,
  Yearly, Categories dalam `HorizontalPager`.
- Integrasi `SpendingBarChart`, stepper tanggal, dan daftar seluruh transaksi.
- Perhitungan total per kategori untuk kompartemen Categories.

**Faturachman Al Kahfi (00000075344)**
- `CameraScreen`: viewfinder, tombol shutter, pemilih galeri, ganti lensa, panel
  tips, dan tampilan saat izin kamera ditolak.
- Penyesuaian ukuran dan tata letak kontrol kamera agar nyaman dipakai dengan satu
  tangan.

**Clarence Bradlee Gosal (00000137487)**
- Uji coba tampilan di beberapa ukuran layar dan pencatatan bagian yang perlu
  diperbaiki.

**Ezequiel Emeraldo (00000159857)**
- Bantuan menyiapkan aset dan pengecekan alur onboarding serta pengaturan saat uji
  coba.

**Farell Timothy (00000108839)**
- Bantuan menjalankan pengujian manual dan pencatatan temuan.
