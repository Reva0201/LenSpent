# LensSpent

Aplikasi pencatat pengeluaran untuk mahasiswa yang hidup dengan anggaran bulanan.
Arahkan kamera ke struk, aplikasi membacanya di perangkat, lalu meminta konfirmasi.
Kalau struk tidak terbaca, transaksi bisa diketik manual. Semua data keuangan tetap
di ponsel.

Proyek UTS IF570L Lab Mobile Application Programming — Kelompok **Fantastic Four**.

| | |
| --- | --- |
| Nama aplikasi | LensSpent |
| Package | `org.umn.lensspent` |
| Bahasa | Kotlin + Jetpack Compose (Material 3) |
| minSdk / targetSdk | 26 / 35 |
| Versi | 1.0 |

## Tangkapan layar

Tangkapan layar tidak ditanam di dokumen ini. Versi terbaru ada di folder
`docs/screenshots/` dan diambil langsung dari aplikasi yang ada di zip ini.
Kalau folder itu kosong, jalankan aplikasi lalu ambil sendiri keadaan berikut:
dashboard, detail transaksi, form kosong, form dengan kesalahan validasi, form
siap simpan, konfirmasi struk, kartu setelah tersimpan, tiga kompartemen Trends,
pengaturan, kamera, onboarding dua langkah, dan dashboard dalam tema gelap.

## Fitur

- **Pindai struk di perangkat.** ML Kit membaca teks struk, parser menyusun merchant,
  total, tanggal, dan kategori. Tidak ada gambar yang diunggah.
- **Input manual dan edit.** Form yang sama dipakai untuk membuat dan mengubah
  transaksi, lengkap dengan validasi dan tipe keyboard yang sesuai.
- **Anggaran bulanan.** Kartu utama menampilkan total bulan berjalan, persentase
  terpakai, dan sisa anggaran.
- **Trends.** Kompartemen Daily, Weekly, Yearly, dan Categories; peringkat kategori
  menunjukkan ke mana uang pergi.
- **Data lokal.** Room menyimpan buku besar, DataStore menyimpan pengaturan. Tidak
  ada akun dan tidak ada server.
- **Tema sendiri.** Skema warna terang dan gelap, tipografi Plus Jakarta Sans, dan
  skala bentuk buatan sendiri.

## Arsitektur

```
UI (Compose)
   │  collectAsStateWithLifecycle
   ▼
ViewModel  ── StateFlow ──►  state turunan (canSave, budgetUsedFraction, …)
   │
   ▼
TransactionRepository (antarmuka)
   │
   ├── OfflineTransactionRepository ──► Room (SQLite)
   ├── UserPreferencesRepository   ──► DataStore
   └── AiInsightsRepository        ──► DeepSeek (opsional, satu-satunya jalur keluar)
```

Keputusan yang penting:

- **Satu sumber kebenaran.** Semua angka di dashboard dan Trends dibaca dari
  repository yang sama, jadi kartu anggaran dan grafik tidak bisa berbeda pendapat.
- **Aturan di dalam state.** Validasi form hidup di `AddExpenseUiState`, bukan di
  composable. Tombol simpan dan fungsi penyimpanan membaca aturan yang sama.
- **Tema sebagai satu-satunya sumber tampilan.** Warna, huruf, radius, dan jarak
  diambil dari `ui/theme/`; layar tidak menulis nilai-nilai itu sendiri.

## Tumpukan teknologi

| Bagian | Dipakai untuk |
| --- | --- |
| Jetpack Compose + Material 3 | Seluruh UI, tema, navigasi |
| Navigation Compose | `NavHost`, rute bernama, argumen bertipe |
| Room + KSP | Buku besar transaksi dan capture |
| DataStore | Nama, anggaran, preferensi tema |
| CameraX | Viewfinder, shutter, impor galeri |
| ML Kit Text Recognition | OCR struk di perangkat |
| Retrofit + kotlinx.serialization | Fitur Premium AI Insights (opsional) |
| Plus Jakarta Sans (OFL) | Tipografi aplikasi |

## Struktur proyek

```
app/src/main/java/org/umn/lensspent/
├── ui/
│   ├── theme/          Color, Type, Shape, Dimens, Theme
│   ├── components/     LensCard, CategoryBadge, SectionHeader,
│   │                   SegmentedTabs, CountUpAmount, tile kategori
│   ├── AppNavigation.kt
│   ├── onboarding/
│   └── settings/
├── data/
│   ├── demo/           DemoLedger + seeder
│   ├── extraction/     OCR rows, parser, structurer
│   ├── local/          entity, DAO, Room, DataStore
│   ├── remote/         klien DeepSeek
│   └── repository/
├── HomeScreen.kt       daftar transaksi + ringkasan bulan
├── TransactionDetailScreen.kt
├── AddExpenseScreen.kt form buat dan ubah
├── CameraScreen.kt
├── AnalyticsScreen.kt  Trends
└── AiChatScreen.kt
```

## Lima layar wajib

| Jenis | Berkas | Isi |
| --- | --- | --- |
| List | `HomeScreen.kt` | Ringkasan bulan, grafik mingguan, daftar transaksi |
| Detail | `TransactionDetailScreen.kt` | Satu transaksi penuh, dibuka dengan id |
| Form/Input | `AddExpenseScreen.kt` | Jumlah, merchant, kategori, tanggal, catatan |
| Fitur native | `CameraScreen.kt` | Viewfinder, shutter, galeri, izin ditolak |
| Pilihan bebas | `AnalyticsScreen.kt` | Empat kompartemen Trends |

Layar pendukung: onboarding, pengaturan, dan AI Insights.

## Peta rubrik UTS

| Kriteria | Bobot | Di mana |
| --- | --- | --- |
| Implementasi layar | 15 | Lima layar di atas, disusun dengan `LazyColumn`/`Column`/`Row`/`FlowRow` dan `Modifier`, tanpa posisi hardcoded |
| Tema UI | 15 | `ui/theme/`: skema warna terang dan gelap, skala tipografi 15 tingkat dengan Plus Jakarta Sans, skala bentuk, token jarak |
| Manajemen state | 15 | ViewModel + `StateFlow` per layar, state turunan, keadaan loading/saving/kosong/hilang |
| Navigasi | 15 | `NavHost` dengan dua argumen `Long` yang dipakai: `review/{captureId}` dan `transaction/{transactionId}` |
| Form dan validasi | 15 | Tipe keyboard per field, `isError` + teks pendukung, simpan terkunci sampai valid |
| Data dummy | 15 | `DemoLedger`: 18 transaksi, 9 kategori, empat minggu, ditulis ke Room saat instalasi pertama |
| Dokumentasi | 10 | README ini, APK, zip proyek, video demo |

## Menjalankan proyek

```powershell
# APK debug
gradlew.bat :app:assembleDebug

# unit test
gradlew.bat :app:testDebugUnitTest

# build tanpa data contoh
gradlew.bat :app:assembleDebug -PdemoLedger=false
```

APK ada di `app/build/outputs/apk/debug/app-debug.apk`.

Saat pertama dibuka, aplikasi meminta nama dan anggaran bulanan; keduanya boleh
dikosongkan. Data contoh ditulis pada peluncuran pertama itu, hanya jika buku besar
masih kosong, sehingga dashboard dan Trends langsung berisi data.

## Data contoh

`data/demo/DemoLedger.kt` berisi `DemoExpense` dan 18 transaksi realistis: sembilan
kategori, rentang empat minggu, dua transaksi di hari yang sama, sebagian dengan
catatan dan sebagian tanpa. Lima di antaranya ditandai sebagai hasil pindai dan
benar-benar punya baris `capture` di basis data, jadi layar detail menampilkan
"Asal: From a receipt photo" berdasarkan relasi, bukan flag palsu.

Data ini bisa dimatikan saat build dengan `-PdemoLedger=false`; secara default aktif
karena build yang dinilai harus langsung memperlihatkan layar berisi data.

## Pengujian

| Jenis | Jumlah | Isi |
| --- | --- | --- |
| Unit test | 123 | Validasi form, data contoh, agregasi pengeluaran, parser struk, preferensi |
| Instrumented | 2 | Kartu konfirmasi penyimpanan dirender di perangkat dan dibaca ulang |

```powershell
gradlew.bat :app:testDebugUnitTest
adb shell am instrument -w -e class org.umn.lensspent.SaveConfirmationDialogTest `
    org.umn.lensspent.test/androidx.test.runner.AndroidJUnitRunner
```

## Tim

| Nama | NIM | Bagian |
| --- | --- | --- |
| Muhammad Meccapareva | 000001113059 | Arsitektur, pipeline OCR dan kamera, Room, dashboard, form buat/ubah, tema, detail, onboarding, pengaturan, AI Insights, data contoh, video demo |
| Christian Kaliman | 00000105460 | Layar Trends: pager, grafik, kompartemen kategori |
| Faturachman Al Kahfi | 00000075344 | Layar kamera: viewfinder, shutter, galeri, izin |
| Clarence Bradlee Gosal | 00000137487 | Uji coba tampilan di beberapa ukuran layar, masukan desain |
| Ezequiel Emeraldo | 00000159857 | Bantuan aset dan pengecekan alur onboarding serta pengaturan |
| Farell Timothy | 00000108839 | Bantuan pengujian manual dan pencatatan temuan |

## Lisensi

Font **Plus Jakarta Sans** dipakai di bawah SIL Open Font License; salinan lisensinya
ada di `licenses/PlusJakartaSans-OFL.txt`.
