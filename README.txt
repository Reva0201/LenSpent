README.txt
LensSpent — Proyek UTS IF570L Lab Mobile Application Programming
Kelompok Fantastic Four


==========================================================
1. DESKRIPSI SINGKAT APLIKASI
==========================================================
LensSpent adalah aplikasi pencatat pengeluaran untuk mahasiswa yang hidup
dengan anggaran bulanan. Ide utamanya satu: mencatat pengeluaran harus selesai
dalam hitungan detik. Pengguna mengarahkan kamera ke struk, aplikasi membaca
struk itu di perangkat, menebak nama toko, jumlah, dan kategori, lalu meminta
pengguna mengonfirmasi. Kalau struk tidak terbaca, transaksi bisa diketik
manual. Semua data keuangan tetap di ponsel.

Lima jenis layar yang diwajibkan sudah ada di aplikasi ini:

  List        HomeScreen.kt            ringkasan bulan, grafik mingguan,
                                       daftar transaksi terbaru
  Detail      TransactionDetailScreen  satu transaksi penuh, dibuka dengan id
  Form/Input  AddExpenseScreen.kt      jumlah, merchant, kategori, tanggal,
                                       catatan; dipakai untuk menambah dan
                                       mengubah transaksi
  Fitur       CameraScreen.kt          viewfinder, shutter, impor galeri, ganti
  native                               lensa, panel tips, izin ditolak
  Pilihan     AnalyticsScreen.kt       empat kompartemen: Daily, Weekly,
  bebas       ("Trends")               Yearly, Categories

Layar pendukung: onboarding dua langkah, pengaturan, dan AI Insights.

Aplikasi memakai tema Material 3 buatan sendiri (skema warna terang dan gelap,
tipografi Plus Jakarta Sans, skala bentuk, token jarak), navigasi dengan
argumen, validasi form dengan pesan kesalahan di tiap field, dan data contoh
yang disimpan ke basis data lokal. Tidak ada akun dan tidak ada server: semua
data transaksi tinggal di perangkat, jadi tidak ada kredensial yang perlu
disimpan dan tidak ada sesi yang perlu dijaga. Kunci layar ponsel adalah batas
keamanan yang sebenarnya.

APK yang dipasang ada di dalam zip ini dengan nama LensSpent.apk.


==========================================================
2. NAMA LENGKAP SELURUH ANGGOTA KELOMPOK
==========================================================
1. Muhammad Meccapareva    — 000001113059
2. Christian Kaliman       — 00000105460
3. Faturachman Al Kahfi    — 00000075344
4. Clarence Bradlee Gosal  — 00000137487
5. Ezequiel Emeraldo       — 00000159857
6. Farell Timothy          — 00000108839


==========================================================
3. PEMBAGIAN TUGAS DAN SISTEM KERJA KELOMPOK
==========================================================
Pembagian tugas

  Pekerjaan dibagi per bagian aplikasi, bukan per jenis pekerjaan. Satu layar
  atau satu lapisan dikerjakan satu orang sampai selesai, supaya tidak ada dua
  orang menulis file yang sama pada waktu yang sama. Bagian yang menjadi
  fondasi (struktur aplikasi, basis data, tema) dikerjakan lebih dulu, karena
  layar lain bergantung padanya.

  Muhammad Meccapareva mengambil bagian terbesar: arsitektur aplikasi, pipeline
  OCR dan kamera, skema basis data, layar utama, form tambah/ubah, lapisan
  tema, komponen bersama, layar detail, onboarding, pengaturan, dan perekaman
  video demo.

  Christian Kaliman mengerjakan layar Trends beserta view model-nya.
  Faturachman Al Kahfi mengerjakan layar kamera dan penyesuaian tata letaknya.
  Clarence Bradlee Gosal, Ezequiel Emeraldo, dan Farell Timothy membantu pada
  tahap uji coba: mencoba aplikasi di beberapa ukuran layar, mengecek alur
  onboarding dan pengaturan, mencatat temuan, serta memberi masukan tampilan.

Sistem kerja kelompok

  - Satu repositori Git. Setiap anggota bekerja di branch sendiri, lalu
    digabung ke main setelah build dan seluruh unit test lulus.
  - Sebelum merge, minimal satu anggota lain membaca perubahannya.
  - Dua pertemuan per minggu: pertemuan pertama membagi pekerjaan dan
    menyepakati tampilan, pertemuan kedua menggabungkan hasil dan memeriksa
    aplikasi langsung di emulator.
  - Konflik di file bersama (tema, navigasi, README) diselesaikan oleh pemilik
    file tersebut, bukan oleh orang yang terakhir commit.
  - Semua keputusan tampilan mengacu ke lapisan tema di ui/theme/, supaya
    warna, huruf, dan radius tidak ditulis ulang di masing-masing layar.
  - Sebelum menyerahkan hasil, aplikasi dijalankan dari instalasi bersih dan
    seluruh alur dicoba ulang: tambah transaksi, ubah, hapus, pindai struk, dan
    buka semua kompartemen Trends.


==========================================================
4. TUGAS SPESIFIK DAN KONTRIBUSI MASING-MASING ANGGOTA
==========================================================
Muhammad Meccapareva (000001113059)
  - Struktur aplikasi: AppContainer, AppViewModelProvider, pemisahan lapisan
    UI, ViewModel, repository, dan data.
  - Pipeline OCR di perangkat: pengambilan gambar (CameraScreen + ImageStorage),
    MlKitCaptureExtractor, OcrRows, ReceiptParser, ReceiptStructurer, dan
    StructuredJson untuk membaca merchant, total, tanggal, dan kategori.
  - Skema basis data lokal: entity, DAO, konverter, OfflineTransactionRepository,
    serta penghubung antara capture dan transaksi.
  - HomeScreen dan HomeViewModel: kartu anggaran, ringkasan minggu dan hari ini,
    grafik, daftar transaksi.
  - AddExpenseScreen dan AddExpenseViewModel: form tambah dan ubah, termasuk
    seluruh aturan validasi dan tipe keyboard.
  - TransactionDetailScreen: layar detail dengan argumen id, hapus dengan
    konfirmasi, keadaan "transaksi sudah tidak ada".
  - Lapisan tema (ui/theme/): skema warna terang dan gelap, tipografi Plus
    Jakarta Sans, skala bentuk, token jarak, palet kategori, ikon launcher.
  - Komponen bersama: LensCard, CategoryBadge, SectionHeader, SegmentedTabs,
    CountUpAmount, tile kategori.
  - OnboardingScreen, SettingsScreen, dan AiChatScreen beserta view model-nya.
  - DemoLedger dan penyemaian data contoh beserta flag build-nya.
  - Perekaman video demo.

Christian Kaliman (00000105460)
  - AnalyticsScreen dan AnalyticsViewModel: empat kompartemen Daily, Weekly,
    Yearly, dan Categories dalam HorizontalPager.
  - Integrasi SpendingBarChart, stepper tanggal, dan daftar seluruh transaksi.
  - Perhitungan total per kategori untuk kompartemen Categories.

Faturachman Al Kahfi (00000075344)
  - CameraScreen: viewfinder, tombol shutter, pemilih galeri, ganti lensa,
    panel tips, dan tampilan saat izin kamera ditolak.
  - Penyesuaian ukuran dan tata letak kontrol kamera agar nyaman dipakai dengan
    satu tangan.

Clarence Bradlee Gosal (00000137487)
  - Uji coba tampilan di beberapa ukuran layar dan pencatatan bagian yang perlu
    diperbaiki.

Ezequiel Emeraldo (00000159857)
  - Bantuan menyiapkan aset dan pengecekan alur onboarding serta pengaturan
    saat uji coba.

Farell Timothy (00000108839)
  - Bantuan menjalankan pengujian manual dan pencatatan temuan.


==========================================================
5. KETERANGAN TAMBAHAN UNTUK PENILAI
==========================================================
Arsitektur
  UI (Compose) -> ViewModel (StateFlow) -> antarmuka TransactionRepository ->
  Room (SQLite) dan DataStore. Lapisan AI adalah satu-satunya bagian yang
  mengirim data keluar perangkat, dan sifatnya opsional.

Yang membuktikan tiap kriteria rubrik
  Layar       Kelima jenis layar di atas, disusun dengan LazyColumn, Column,
              Row, Box, dan FlowRow beserta Modifier; tidak ada posisi
              hardcoded.
  Tema        ui/theme/: dua skema warna lengkap, skala tipografi 15 tingkat,
              skala bentuk, token jarak. Ikon launcher sendiri, warna latar
              jendela mengikuti tema, kontras ikon status bar mengikuti tema
              pilihan pengguna.
  State       ViewModel dan StateFlow di setiap layar, state turunan
              (canSave, budgetUsedFraction, amountError), keadaan loading,
              saving, kosong, dan "data hilang".
  Navigasi    NavHost dengan rute bernama. Dua argumen dikirim dan dipakai:
              review/{captureId} dan transaction/{transactionId}.
  Validasi    Keyboard angka untuk jumlah, kapitalisasi kata untuk merchant,
              isError dan teks pendukung di tiap field, tombol simpan terkunci
              sampai semua aturan terpenuhi.
  Data        DemoLedger: 18 transaksi, 9 kategori, rentang empat minggu,
              ditulis ke Room saat instalasi pertama dan hanya bila buku besar
              masih kosong. Lima di antaranya punya baris capture sungguhan.
  Dokumen     README ini, README.md, APK, zip proyek, dan video demo.

Pengujian
  123 unit test, 0 gagal. 2 pengujian instrumentasi untuk kartu konfirmasi
  penyimpanan. Data contoh juga diverifikasi langsung dari basis data perangkat.

Cara build
  gradlew.bat :app:assembleDebug
  gradlew.bat :app:testDebugUnitTest
  gradlew.bat :app:assembleDebug -PdemoLedger=false    (tanpa data contoh)

Catatan
  - Data contoh bisa dimatikan saat build; build yang dinilai memakainya supaya
    layar langsung berisi data.
  - Build tanpa kunci API DeepSeek tetap bisa dipasang dan dijalankan; layar
    obrolan akan menyebutkan bahwa kuncinya belum diatur. APK yang disertakan
    dibangun tanpa kunci.
  - Folder .git, build, .gradle, dan local.properties tidak disertakan karena
    bersifat lokal mesin pengembang.
