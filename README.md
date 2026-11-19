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
**Farell Timothy (00000108839)**
- Menjalankan pengujian manual pada alur tambah, ubah, dan hapus transaksi, lalu mencatat temuan beserta langkah mengulanginya.
- Memeriksa data contoh yang muncul di dashboard dan Trends agar angkanya sesuai dengan isi basis data.
- Membantu memeriksa konsistensi teks dan format Rupiah di seluruh layar.
