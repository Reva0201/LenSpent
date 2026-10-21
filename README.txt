LENSSPENT — PROYEK UTS IF570L LAB MOBILE APPLICATION PROGRAMMING
================================================================

Nama kelompok : Fantastic Four
Nama aplikasi : LensSpent
Package       : org.umn.lensspent
Teknologi     : Kotlin + Jetpack Compose (Material 3), Room, CameraX, ML Kit
minSdk 26 / targetSdk 35 / versionName 1.0


1. DESKRIPSI APLIKASI
---------------------
LensSpent adalah aplikasi pencatat pengeluaran untuk mahasiswa yang hidup dengan
anggaran bulanan. Ide utamanya satu: mencatat pengeluaran harus selesai dalam
hitungan detik. Arahkan kamera ke struk, aplikasi membaca struk itu di perangkat,
menebak nama toko, jumlah, dan kategori, lalu meminta pengguna mengonfirmasi.
Kalau struk tidak terbaca, transaksi bisa diketik manual. Semua data keuangan
tetap di ponsel.

Lingkup UTS adalah lapisan UI: lima jenis layar, tema Material 3 buatan sendiri,
manajemen state, navigasi dengan argumen, form dengan validasi, dan data dummy
yang realistis. Room, kamera, dan integrasi AI memang ada di proyek ini, tetapi
tidak satu pun dari ketiganya diperlukan untuk menunjukkan kriteria penilaian.


2. ANGGOTA KELOMPOK
-------------------
1. Muhammad Meccapareva   — 000001113059
2. Christian Kaliman      — 00000105460
3. Faturachman Al Kahfi   — 00000075344
4. Clarence Bradlee Gosal — 00000137487
5. Ezequiel Emeraldo      — 00000159857
6. Farell Timothy         — 00000108839


3. CARA KERJA KELOMPOK
----------------------
Pekerjaan dilakukan di satu repositori Git dengan pembagian per fitur. Setiap
anggota bekerja di branch sendiri, lalu digabung ke main setelah build dan
seluruh unit test lulus. Sebelum merge, minimal satu anggota lain membaca
perubahannya. Kami bertemu dua kali seminggu: pertemuan pertama untuk membagi
pekerjaan dan menyepakati tampilan, pertemuan kedua untuk menggabungkan hasil
dan memeriksa aplikasi langsung di emulator. Konflik di file bersama (tema,
navigasi, README) diselesaikan oleh pemilik file tersebut, bukan oleh yang
terakhir commit.

Aturan yang kami pegang: satu layar dikerjakan satu orang supaya tidak saling
menimpa, dan semua keputusan tampilan mengacu ke lapisan tema di
ui/theme/ supaya warna, huruf, dan radius tidak ditulis ulang di layar.


4. KONTRIBUSI MASING-MASING ANGGOTA
-----------------------------------
Muhammad Meccapareva (000001113059) — arsitektur dan mesin inti
  - Struktur aplikasi: AppContainer, AppViewModelProvider, pemisahan lapisan
    UI, ViewModel, repository, dan data.
  - Pipeline OCR di perangkat: pengambilan gambar (CameraScreen + ImageStorage),
    MlKitCaptureExtractor, OcrRows, ReceiptParser, ReceiptStructurer, dan
    StructuredJson untuk membaca merchant, total, tanggal, dan kategori dari
    struk.
  - Skema basis data lokal: entity, DAO, konverter, OfflineTransactionRepository,
    serta penghubung antara capture dan transaksi.
  - Layar utama: HomeScreen dan HomeViewModel (kartu anggaran, grafik mingguan,
    daftar transaksi).
  - Form input manual dan form edit: AddExpenseScreen dan AddExpenseViewModel,
    termasuk aturan validasi.
  - Penyemaian data contoh (DemoLedger) beserta flag build-nya.

Christian Kaliman (00000105460) — layar Trends
  - AnalyticsScreen dan AnalyticsViewModel: empat kompartemen Daily, Weekly,
    Yearly, dan Categories dalam HorizontalPager.
  - Integrasi SpendingBarChart, stepper tanggal, dan daftar seluruh transaksi.
  - Perhitungan total per kategori untuk kompartemen Categories.

Faturachman Al Kahfi (00000075344) — layar kamera
  - CameraScreen: tampilan viewfinder, tombol shutter, pemilih galeri, ganti
    lensa, panel tips, dan tampilan saat izin kamera ditolak.
  - Penyesuaian ukuran dan tata letak kontrol kamera agar tetap nyaman dipakai
    dengan satu tangan.

Clarence Bradlee Gosal (00000137487) — sistem desain dan komponen
  - ui/theme/: skema warna terang dan gelap, skala tipografi dengan font Plus
    Jakarta Sans, skala bentuk, dan token jarak/elevasi.
  - Komponen bersama: LensCard, CategoryBadge, SectionHeader, SegmentedTabs,
    CountUpAmount, dan deretan tile kategori.
  - Ikon launcher adaptif, warna latar jendela per tema, dan penyesuaian
    kontras ikon status bar.

Ezequiel Emeraldo (00000159857) — onboarding, pengaturan, dan AI Insights
  - OnboardingScreen dua langkah (penjelasan privasi, lalu nama dan anggaran)
    beserta view model dan validasinya.
  - SettingsScreen: profil, dialog anggaran, pilihan tema, dan ekspor CSV.
  - AiChatScreen dan AiChatViewModel untuk fitur Premium AI Insights.

Farell Timothy (00000108839) — pengujian dan dokumentasi
  - 123 unit test (validasi form, data contoh, agregasi, parser) dan pengujian
    instrumentasi untuk kartu konfirmasi penyimpanan.
  - Verifikasi data contoh langsung dari basis data perangkat.
  - README, daftar tangkapan layar, dan perekaman video demo.


5. LIMA LAYAR WAJIB
-------------------
1. LIST — HomeScreen.kt
   Dashboard dengan LazyColumn: sapaan, kartu anggaran bulanan, ringkasan
   minggu ini dan hari ini, grafik pengeluaran, dan daftar transaksi terbaru.
   Setiap baris bisa ditekan dan membuka layar detail.

2. DETAIL — TransactionDetailScreen.kt
   Dibuka dari daftar dengan id transaksi sebagai argumen navigasi. Menampilkan
   jumlah, merchant, kategori, tanggal, catatan, dan asal transaksi, dibaca dari
   basis data berdasarkan id. Bisa menghapus transaksi dengan dialog konfirmasi
   dan menampilkan keadaan "transaksi sudah tidak ada" bila barisnya hilang.

3. FORM/INPUT — AddExpenseScreen.kt
   Input manual: jumlah, merchant, kategori, tanggal, dan catatan. Setiap field
   memakai tipe keyboard yang sesuai, punya pesan kesalahan sendiri, dan tombol
   simpan tetap mati sampai seluruh isian valid. Form yang sama dipakai untuk
   mengubah transaksi yang sudah ada.

4. LAYAR FITUR NATIVE — CameraScreen.kt
   Antarmuka kamera: viewfinder, shutter, impor dari galeri, ganti lensa, dan
   panel tips. Keadaan izin ditolak juga dirancang sebagai layar, bukan crash.
   Aplikasi memakai kamera sungguhan; soal mengizinkan versi mockup, jadi ini
   melebihi ketentuan, bukan menyimpang darinya.

5. PILIHAN BEBAS — AnalyticsScreen.kt ("Trends")
   Kompartemen Daily, Weekly, Yearly, dan Categories: grafik batang, stepper
   tanggal, dan peringkat kategori dengan bar proporsi.

Layar pendukung (tidak dihitung dalam lima): OnboardingScreen, SettingsScreen,
dan AiChatScreen.


6. PETA RUBRIK
--------------
Implementasi layar (15)
  Lima jenis layar di atas, semuanya disusun dari Column/Row/Box/LazyColumn/
  FlowRow dengan Modifier dan weight, tanpa posisi hardcoded.

Tema UI (15)
  Seluruh tampilan mengambil warna, huruf, dan bentuk dari ui/theme/:
    Color.kt    skema Material 3 terang dan gelap, ditambah peran gradien merek
                agar mode gelap tidak menghasilkan teks putih di atas pastel
    Type.kt     Plus Jakarta Sans (lisensi OFL) sebagai variable font dengan
                empat bobot asli, dan skala 15 tingkat
    Shape.kt    radius 6/10/16/22/30 dp lewat MaterialTheme.shapes
    Dimens.kt   token Spacing/Elevation/Sizes
    Theme.kt    menerapkan warna, tipografi, dan bentuk sekaligus
  Selain itu: warna latar jendela per tema, ikon launcher sendiri, dan kontras
  ikon status bar yang mengikuti tema pilihan pengguna.

Manajemen state (15)
  Setiap layar punya ViewModel dengan StateFlow, dikumpulkan lewat
  collectAsStateWithLifecycle. Aturan turunan ditempel pada state (misalnya
  AddExpenseUiState.canSave), jadi tampilan tidak bisa berbeda pendapat dengan
  aturan penulisannya. Keadaan loading, saving, kosong, dan "data hilang"
  ditangani eksplisit. 123 unit test, 0 gagal.

Navigasi (15)
  NavHost + NavController dengan nama rute di AppRoutes. Dua argumen dikirim
  dan dipakai:
    review/{captureId}          Long — struk yang akan dikonfirmasi
    transaction/{transactionId} Long — transaksi yang ditampilkan
  Perilaku back stack diatur per rute, dan transisi layar memakai animasi
  singkat agar perpindahan terbaca sebagai perpindahan.

Form dan validasi (15)
  - Jumlah: keyboard angka, hanya angka yang diterima, nol ditolak, dan nilai
    yang terbaca ditampilkan kembali ("Saves as Rp 45.000") sambil diketik.
  - Merchant: wajib, kapitalisasi per kata, pesan kesalahan di bawah field.
  - Kategori: wajib dipilih, ada pesan "Choose one" bila belum.
  - Catatan: opsional, dihitung sampai 200 karakter.
  - Tombol simpan mati sampai semua aturan terpenuhi, dan menjelaskan alasannya.
  - Dialog anggaran dan field anggaran di onboarding memakai aturan yang sama.

Data dummy (15)
  data/demo/DemoLedger.kt berisi data class DemoExpense dan 18 transaksi yang
  realistis, mencakup 9 kategori, rentang empat minggu, dua transaksi di hari
  yang sama, sebagian dengan catatan dan sebagian tanpa. Data ini ditulis ke
  Room sekali saat instalasi pertama dan hanya bila buku besar masih kosong.
  Lima di antaranya ditandai sebagai hasil pindai dan benar-benar punya baris
  capture di basis data, sehingga layar detail menampilkan "From a receipt
  photo" berdasarkan relasi, bukan flag palsu. Seluruh data bisa dimatikan saat
  build dengan -PdemoLedger=false.

Dokumentasi dan kelengkapan (10)
  README ini, APK, proyek dalam zip, dan video demo sesuai naskah bagian 8.


7. CARA BUILD DAN MENJALANKAN
-----------------------------
Prasyarat: Android Studio dengan Android SDK 35 ke atas dan JDK (proyek ini
dibangun dengan JBR bawaan Android Studio).

  # APK debug
  gradlew.bat :app:assembleDebug

  # unit test
  gradlew.bat :app:testDebugUnitTest

  # build tanpa data contoh
  gradlew.bat :app:assembleDebug -PdemoLedger=false

APK yang dipasang ada di app/build/outputs/apk/debug/app-debug.apk, dan salinan
untuk penilaian ikut di dalam zip ini (LensSpent.apk).

Saat pertama kali dibuka, aplikasi meminta nama dan anggaran bulanan; keduanya
boleh dikosongkan. Data contoh ditulis pada peluncuran pertama itu, sehingga
dashboard, grafik, layar detail, dan seluruh kompartemen Trends langsung berisi
data.

Arsitektur singkat: UI (Compose) -> ViewModel (StateFlow) -> antarmuka
Repository -> Room/DataStore. Lapisan AI adalah satu-satunya bagian yang
mengirim data keluar perangkat, dan sifatnya opsional.


8. NASKAH VIDEO DEMO (MAKSIMAL 5 MENIT)
---------------------------------------
Rekam dari instalasi bersih agar data contoh terlihat.

0:00-0:20  Pembuka. Sebutkan nama aplikasi, kelompok, dan satu kalimat inti:
           "LensSpent mencatat struk dalam hitungan detik dan menjaga
           pengeluaran tetap di jalur anggaran bulanan."
0:20-0:40  ONBOARDING. Tunjukkan dua halaman: penjelasan privasi, lalu nama dan
           anggaran. Sebut tipe keyboard angka dan validasinya.
0:40-1:50  HOME (LIST). Kartu anggaran: total bulan ini, persentase, dan sisa
           anggaran. Ringkasan minggu dan hari ini, lalu grafik mingguan. Gulir
           ke daftar transaksi dan tekan satu baris.
1:50-2:20  DETAIL. Tunjukkan transaksi yang tadi ditekan: jumlah, kategori,
           tanggal, catatan, dan asal transaksi. Jelaskan bahwa layar ini dibuka
           dengan id transaksi, bukan data hardcoded. Kembali ke daftar.
2:20-3:20  FORM. Tekan "Add manually". Ketik jumlah yang salah (45rb) untuk
           menunjukkan pesan kesalahan dan tombol simpan yang mati. Perbaiki,
           tunjukkan nilai terbaca "Saves as Rp ...". Kosongkan merchant untuk
           menunjukkan field wajib. Pilih kategori, tunjukkan tombol simpan
           hidup, lalu simpan dan tunjukkan baris barunya di dashboard. Setelah
           itu buka baris tersebut, tekan ikon pensil, ubah jumlah, dan simpan
           untuk menunjukkan bahwa data benar-benar berubah.
3:20-3:50  LAYAR NATIVE. Tekan "Scan receipt": viewfinder, shutter, impor
           galeri, ganti lensa, panel tips, dan keadaan izin ditolak.
3:50-4:30  TRENDS (pilihan bebas). Empat kompartemen, stepper tanggal, grafik,
           dan peringkat kategori. Sebutkan bahwa angkanya sama dengan
           dashboard karena dibaca dari basis data yang sama.
4:30-4:50  TEMA. Pengaturan, lalu Light dan Dark, untuk membuktikan bahwa tema
           adalah skema warna penuh, bukan sekadar warna yang diganti.
4:50-5:00  Penutup. Satu kalimat tentang rencana berikutnya.


9. CATATAN UNTUK PENILAI
------------------------
- Data contoh adalah data contoh, bukan data pengguna. Data ini hanya ditulis ke
  buku besar yang masih kosong, hanya pada build ini, dan bisa dimatikan saat
  build.
- Foto struk, buku besar, dan pengaturan tinggal di perangkat. Hanya obrolan
  Premium AI Insights yang mengirim teks transaksi yang sudah dianonimkan.
- Build tanpa kunci API DeepSeek tetap bisa dipasang dan dijalankan; layar
  obrolan akan menyebutkan bahwa kuncinya belum diatur.
- Folder .git, build, .gradle, dan local.properties tidak disertakan dalam zip
  karena bersifat lokal mesin pengembang.


10. TANGKAPAN LAYAR
-------------------
Tangkapan layar ada di folder docs/screenshots/ dan diambil dari aplikasi yang
ada di zip ini. Keadaan yang perlu terlihat, beserta kriteria yang dibuktikan:

  Dashboard                 layar LIST: kartu anggaran, grafik mingguan, buku besar
  Detail transaksi          layar DETAIL yang dibuka dengan id transaksi
  Form kosong               layar FORM sebelum diisi
  Form dengan kesalahan     validasi menolak "45rb" (isError + teks pendukung)
  Form siap simpan          semua aturan terpenuhi, tombol simpan hidup
  Form saat keyboard muncul tombol simpan tetap terlihat di atas keyboard
  Konfirmasi struk          jalur kamera: hasil baca OCR bisa dikoreksi
  Kartu setelah tersimpan   konfirmasi bahwa baris benar-benar masuk
  Trends: Daily             ringkasan hari yang dipilih
  Trends: Weekly            grafik mingguan
  Trends: Categories        peringkat kategori dengan bar proporsi
  Pengaturan                profil, anggaran, tema, ekspor
  Kamera                    viewfinder, shutter, galeri, izin ditolak
  Onboarding                dua langkah peluncuran pertama
  Dashboard tema gelap      membuktikan tema adalah skema warna penuh

Pemetaan ke rubrik:
  Layar      dashboard, detail transaksi, form, kamera, trends
  Tema       dashboard dibandingkan dashboard tema gelap, ditambah pengaturan
  State      form kosong -> form dengan kesalahan -> form siap simpan
  Navigasi   detail transaksi dibuka dari baris daftar dengan argumen id
  Validasi   form dengan kesalahan
  Data       dashboard dan trends: Categories
