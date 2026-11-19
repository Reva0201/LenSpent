# LensSpent

Aplikasi pencatat pengeluaran untuk mahasiswa yang hidup dengan anggaran bulanan.
Arahkan kamera ke struk, aplikasi membacanya di perangkat, lalu minta konfirmasi.
Kalau struk tidak terbaca, transaksi bisa diketik manual. Buku besar, struk, dan OCR
berjalan di perangkat: tidak ada akun, dan tidak ada server untuk buku besar. Satu-satunya
data yang bisa keluar dari ponsel adalah permintaan AI Insights yang sifatnya opsional.
Fitur itu mati secara bawaan, hanya berjalan setelah pengguna menyetujui pemberitahuan di
aplikasi, dan hanya mengirim pertanyaan yang diketik pengguna beserta total pengeluaran
bulanan per kategori. Kalau fitur itu tidak pernah dinyalakan, tidak ada data yang keluar
dari perangkat.

Proyek UTS IF570L Lab Mobile Application Programming - Kelompok **Fantastic Four**.

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

## Privasi dan keamanan

- Tidak ada kunci API yang ditanam di dalam aplikasi. AI Insights hanya bisa dipakai
  kalau build diberi alamat backend `https` saat build; tanpa itu fiturnya mati dan
  tidak ada permintaan yang dibuat.
- AI Insights opsional: ada dialog pemberitahuan yang harus disetujui sebelum
  pertanyaan pertama, dan ada sakelar "AI Insights network access" di Settings yang
  bisa dimatikan kapan saja. Saat dimatikan, tidak ada data yang dikirim.
- Yang dikirim adalah pertanyaan yang diketik pengguna dan total pengeluaran bulanan per
  kategori: bulan, total dalam IDR, nama kategori, dan jumlah transaksi. Tidak ada nama
  toko, catatan, gambar struk, teks OCR, tanggal lebih rinci dari bulan, nama pengguna,
  atau id basis data.
- Foto struk disimpan hanya di penyimpanan internal privat aplikasi, lalu dihapus
  setelah konfirmasi, pembatalan, atau penghapusan transaksi.
- Backup cloud dan pemindahan ke perangkat baru dinonaktifkan untuk buku besar,
  preferensi, dan gambar struk.
- Ekspor CSV hanya terjadi saat pengguna menekan tombol ekspor dan memilih sendiri
  tujuan berkasnya.
- Izin yang dipakai: kamera saja, dan hanya diminta saat kamera dibuka. Galeri memakai
  pemilih foto sistem. Tidak ada izin penyimpanan.

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

- Pekerjaan dibagi di awal minggu, lalu tiap anggota mengerjakan bagiannya di
  salinan proyek sendiri.
- Hasil digabungkan di satu salinan utama. Kalau ada file yang tersentuh dua
  orang, pemilik file itulah yang menggabungkannya, bukan orang yang terakhir
  menyimpan.
- Dua pertemuan per minggu: pertama membagi pekerjaan dan menyepakati tampilan,
  kedua menggabungkan hasil dan memeriksa aplikasi langsung di emulator.
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
- Uji coba tampilan di beberapa ukuran layar (kecil, sedang, dan tablet) untuk memastikan tata letak tidak terpotong.
- Memberi masukan desain pada layar utama dan layar Trends, termasuk warna kartu anggaran dan keterbacaan grafik.
- Mencatat bagian tampilan yang perlu diperbaiki beserta layar dan keadaannya.

**Ezequiel Emeraldo (00000159857)**
- Menyiapkan aset yang dipakai aplikasi: ikon, bahan tangkapan layar, dan contoh struk untuk uji coba.
- Mengecek alur onboarding dari instalasi bersih sampai dashboard, termasuk keadaan field yang dikosongkan.
- Mengecek layar pengaturan: dialog anggaran, pilihan tema, dan ekspor CSV.

**Farell Timothy (00000108839)**
- Menjalankan pengujian manual pada alur tambah, ubah, dan hapus transaksi, lalu mencatat temuan beserta langkah mengulanginya.
- Memeriksa data contoh yang muncul di dashboard dan Trends agar angkanya sesuai dengan isi basis data.
- Membantu memeriksa konsistensi teks dan format Rupiah di seluruh layar.
