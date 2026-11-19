# LensSpent — Materi 10 Topik Project (versi BACKUP, sebelum UI overhaul)

Dokumen ini menjelaskan kesepuluh topik berdasarkan **kode versi backup**, yaitu:

```
C:\Users\sepsurei\AndroidStudioProjects\LensSpent-backups\LensSpent-20261021-235259.zip
```

Isi zip itu = commit `95699a6` (Phase 2) + pekerjaan yang belum di-commit. Ciri versi ini:

- Tema masih **Material 3 dasar**: `Color.kt` hanya 6 warna, `Type.kt` hanya override `bodyLarge`,
  tidak ada `Shape.kt`, tidak ada `Dimens.kt`, tidak ada `ui/components/LensComponents.kt`.
- Belum ada `TransactionDetailScreen` / `AddExpenseScreen` / `CategoryPalette`.
- Dashboard masih `ElevatedCard` + `PrimaryTabRow` (belum ada segmented pill, count-up, dock, dsb).

> **Penting kalau ditanya "kodenya kok beda?"**
> Jawab: "Yang saya jelaskan adalah snapshot backup 21 Oktober, sebelum UI overhaul. Setelah itu tema
> dan beberapa layar saya rombak, tetapi arsitektur data (Room, DataStore, MVVM, Navigation) tidak
> berubah." Arsitektur memang tidak berubah — yang berubah hanya lapisan tampilan.

---

## 0. Gambaran besar aplikasi (30 detik pembuka)

LensSpent = aplikasi pencatat pengeluaran dari **foto struk**.

Alur utama:

```
Camera / Photo Picker  ->  CaptureEntity (Room)
        ->  ML Kit OCR (on-device)  ->  ReceiptParser (fast path)
        ->  ExpenseDraft  ->  layar Review (user mengoreksi)
        ->  TransactionEntity (Room)  ->  Dashboard / Analytics
```

Semua offline. Satu-satunya fitur yang keluar dari HP: **AI Insights** (DeepSeek), dan itu opsional.

Struktur folder:

```
org.umn.lensspent/
├─ MainActivity.kt            # root: baca preferensi, pasang tema, pilih start destination
├─ *Screen.kt / *ViewModel.kt # fitur per layar (MVVM)
├─ data/
│  ├─ local/                  # Room: entity, dao, database + DataStore preferensi
│  ├─ repository/             # TransactionRepository + implementasi offline
│  ├─ extraction/             # ML Kit + parser struk
│  ├─ remote/                 # DeepSeek (Retrofit + kotlinx.serialization)
│  └─ model/                  # model domain (Transaction, Capture, Category)
└─ ui/                        # navigasi, onboarding, settings, theme, markdown
```

---

## 1. Compose Layout — "jelaskan Composable function yang menghasilkan komponen UI tertentu"

### Contoh terbaik untuk dibawa: `StepperHeader` (paling mudah dijelaskan)

File: `app/src/main/java/org/umn/lensspent/StepperHeader.kt`

```kotlin
@Composable
internal fun StepperHeader(
    label: String,
    nextEnabled: Boolean,
    onStep: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(HeaderRowHeight),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = { onStep(-1) }) { /* panah kiri */ }
        Text(text = label, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, ...)
        IconButton(onClick = { onStep(1) }, enabled = nextEnabled) { /* panah kanan */ }
    }
}
```

Poin yang harus disebut:

1. **Parameter = kontrak komponen.** `label` (apa yang ditulis), `nextEnabled` (boleh maju atau tidak),
   `onStep` (callback). Komponen ini **tidak tahu** tanggal milik layar mana, tidak menyimpan state,
   dan tidak memanggil ViewModel. Ini namanya **state hoisting**: state naik ke pemanggil.
2. **`Row` + `Arrangement.SpaceBetween`** menaruh dua tombol di tepi; `Modifier.weight(1f)` pada `Text`
   membuat label mengisi sisa ruang dan tetap satu baris (`maxLines = 1`), jadi label panjang tidak
   mendorong tombol keluar layar.
3. **`height(HeaderRowHeight)` (48.dp)** dipakai bersama oleh `HeaderSlot` dan `LabelHeader`, sehingga
   tinggi header selalu sama. Efeknya: saat user menekan panah tanggal, chart di bawahnya **tidak
   bergeser naik-turun**. Ini alasan desain yang bagus untuk disebut.
4. **Dipakai di dua layar**: `AnalyticsScreen` (step hari/minggu) dan `ExpenseReviewScreen` (step tanggal
   struk). Satu implementasi, dua pemakai — menghindari dua perilaku tanggal yang berbeda.
5. `internal` = hanya terlihat di dalam modul `app` (bukan API publik), tetapi tetap bisa diuji.

### Contoh kedua (kalau diminta yang lebih "berat"): `SpendingBarChart`

File: `app/src/main/java/org/umn/lensspent/SpendingBarChart.kt`

- Signature: `fun SpendingBarChart(data: List<Float>, labels: List<String>, modifier: Modifier = Modifier)`.
- Isinya `Column` → `Box` (gesture + `Canvas`) → `Row` (label sumbu X).
- **Kenapa `Canvas`, bukan 7 `Box`?** Grid line, label sumbu, dan bar digambar dalam satu pass
  `Canvas`. Animasi tinggi bar dibaca di dalam `DrawScope`, sehingga perubahan tinggi hanya
  meng-invalidate **drawing**, bukan composition/layout.
- **`modifier` sebagai parameter terakhir dengan default** = konvensi Compose; pemanggil bisa
  menambahkan `padding`/`fillMaxWidth` tanpa mengubah komponen.
- Sumbu Y: `axisGutter` dihitung dari lebar label terukur (`TextMeasurer`), bukan angka hardcode —
  jadi label "Rp 1,7Jt" yang lebih panjang tidak terpotong.
- Interaksi: satu `pointerInput` memetakan posisi tap ke indeks bar; tap pada bar yang sama
  membatalkan pilihan. Tooltip (`BarTooltip`) membaca `State<Float>` animasi di scope-nya sendiri,
  jadi hanya pill itu yang recompose.

### Contoh ketiga: `HomeScreen` (layar)

File: `app/src/main/java/org/umn/lensspent/HomeScreen.kt`

- `Scaffold` dengan `bottomBar = BottomAppBar` dan `floatingActionButton` FAB kamera di tengah.
- FAB digeser `offset(y = 50.dp)` supaya "mendarat" di atas bottom bar (bukan mengambang lepas).
- Isi: `LazyColumn` dengan `contentPadding` + `verticalArrangement = Arrangement.spacedBy(20.dp)`.
  Item: greeting → kartu budget → kartu AI → chart → header "Recent Transactions" → list.
- `items(uiState.recentTransactions, key = { it.id })` — `key` mencegah baris "salah identitas"
  saat daftar berubah (tanpa key, Compose mencocokkan berdasarkan posisi).
- Empty state bukan `Text` kosong, tapi kartu yang mengajari langkah berikutnya ("Capture a receipt").

**Kalimat penutup topik ini:** "Composable di project ini selalu: (1) menerima state lewat parameter,
(2) mengirim aksi lewat lambda, (3) menerima `Modifier` dengan default, dan (4) tidak menyentuh database
langsung."

---

## 2. UI Theming — color, typography, shape

File: `ui/theme/Color.kt`, `ui/theme/Theme.kt`, `ui/theme/Type.kt`, `res/values/themes.xml`

### Color (versi backup: sangat sederhana — ini justru mudah dijelaskan)

```kotlin
// Color.kt — 6 token saja
val Blue80 = Color(0xFF9ECAFF)      // primary, dark
val BlueGrey80 = Color(0xFFBAC8DB)  // secondary, dark
val Cyan80 = Color(0xFFBCEBEB)      // tertiary, dark
val Blue40 = Color(0xFF0061A4)      // primary, light
val BlueGrey40 = Color(0xFF535F70)  // secondary, light
val Cyan40 = Color(0xFF006A6A)      // tertiary, light
```

Lalu dua `ColorScheme` lengkap di `Theme.kt`:

```kotlin
private val DarkColorScheme = darkColorScheme(primary = Blue80, onPrimary = ..., background = ..., ...)
private val LightColorScheme = lightColorScheme(primary = Blue40, ...)
```

Poin:

- **Token, bukan hex inline.** Layar memakai `MaterialTheme.colorScheme.primary`, bukan `Color(0xFF...)`.
  Kalau warna berubah, satu tempat yang diubah.
- Pasangan `primary` / `onPrimary` / `primaryContainer` / `onPrimaryContainer` = kontrak kontras
  Material 3: teks di atas `primary` wajib `onPrimary`. Ini alasan kita tidak asal memilih warna.
- `background` = `surface` di skema ini (0xFF1A1C1E dark, 0xFFFDFCFF light), sehingga `Surface` di root
  dan `Scaffold` menghasilkan warna yang sama — tidak ada "sambungan" warna yang kelihatan.

### Typography

```kotlin
val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.5.sp
    )
)
```

- Hanya `bodyLarge` yang di-override; sisanya memakai default Material 3.
- Argumen: satu skala tipografi untuk seluruh app lebih konsisten daripada `fontSize` acak per layar.
  Layar memakai peran (`headlineLarge` untuk nominal, `titleMedium` untuk judul bagian, `bodySmall`
  untuk keterangan), bukan angka sp langsung.
- Catatan jujur yang boleh disampaikan: di snapshot backup, skala tipografi masih tipis; pengembangan
  berikutnya (setelah backup) menambah `Type.kt` yang lebih lengkap.

### Shape

- Di versi backup **tidak ada `Shape.kt`**: aplikasi memakai `MaterialTheme.shapes.small/medium`
  bawaan Material 3, dipakai konsisten untuk kartu, chip, dan tooltip.
- `modifier.clip(MaterialTheme.shapes.medium)` pada baris transaksi dan preview gambar.
- Di root, `LazyRow` chip kategori memakai `FilterChip` (bentuk dari M3), bukan `RoundedCornerShape(50)`
  buatan sendiri.

### Bagian yang paling "menjual" di topik ini: pemilihan tema

```kotlin
// MainActivity.kt
val stored by preferences.preferences.collectAsStateWithLifecycle(initialValue = null)
val darkTheme = themeIsDark(
    preference = stored?.themePreference ?: ThemePreference.Default,
    systemInDarkTheme = isSystemInDarkTheme()
)
LensSpentTheme(darkTheme = darkTheme, dynamicColor = false) { ... }

internal fun themeIsDark(preference: ThemePreference, systemInDarkTheme: Boolean): Boolean =
    when (preference) {
        ThemePreference.SYSTEM -> systemInDarkTheme
        ThemePreference.LIGHT  -> false
        ThemePreference.DARK   -> true
    }
```

Empat argumen yang bisa disebut:

1. `themeIsDark` **pure function** → bisa diuji tanpa device (`ThemeSelectionTest`).
2. `dynamicColor = false` → warna brand konsisten; kalau `true`, Android 12+ akan memakai wallpaper
   user dan identitas warna app hilang.
3. `enableEdgeToEdge()` + `SystemBarStyle.auto(..., detectDarkMode = { darkTheme })` → warna ikon status
   bar mengikuti tema **yang sedang digambar app**, bukan mode malam HP. Tanpa ini, app yang dipaksa
   dark di HP light akan punya ikon hitam di atas latar gelap.
4. Tema disimpan di **DataStore** (`theme_preference` sebagai string `SYSTEM`/`LIGHT`/`DARK`), bukan enum
   ordinal — supaya urutan enum bisa berubah tanpa merusak data lama.

`res/values/themes.xml` hanya `Theme.Material3.DayNight.NoActionBar` + `windowBackground` — jembatan
antara window Android dan Compose, karena `setContent` menggambar sendiri isinya.

---

## 3. State — di mana, kapan, dan kenapa

Topik ini paling rapi kalau dijawab dengan **tiga tingkat state**. Itu struktur jawaban yang kuat.

### Tingkat 1 — State milik ViewModel (state aplikasi / bertahan lintas rotasi)

| ViewModel | State | Kenapa di sini |
|---|---|---|
| `HomeViewModel` | `StateFlow<HomeUiState>` (`combine` 4 flow) | Diturunkan dari database; harus tetap benar setelah rotasi |
| `AnalyticsViewModel` | `dayOffset`, `weekOffset` (`MutableStateFlow`) + `uiState` | Offset tanggal harus **selamat dari rotasi**; kalau disimpan di Composable, user balik ke hari ini |
| `ExpenseReviewViewModel` | `uiState: StateFlow<ExpenseReviewUiState?>` + `finished` + `savedExpense` | Mesin status (Processing → Editing → saved); proses OCR tidak boleh diulang saat rotasi |
| `CameraViewModel` | `pendingCaptureId: StateFlow<Long?>` | Event navigasi harus selamat rotasi, lalu dikonsumsi sekali |
| `OnboardingViewModel` | `step`, `username`, `budgetText`, `isFinished` | Isian user tidak boleh hilang saat keyboard/rotasi |
| `AiChatViewModel` | `messages`, `draft`, `isAwaitingReply` | Transkrip chat harus tetap ada saat pindah layar/rotasi |
| `SettingsViewModel` | `uiState` = snapshot DataStore + `pendingCsvExport` | Layar tidak menyimpan salinan setting (anti-drift) |

Contoh `StateFlow` turunan (paling penting — sebut yang ini):

```kotlin
// HomeViewModel.kt
val uiState: StateFlow<HomeUiState> = combine(
    repository.observeTransactions(RecentTransactionLimit),
    repository.observeTransactionsBetween(weekStartMillis, weekEndMillis),
    repository.observeSumBetween(monthStartMillis, nextMonthStartMillis),
    preferences.monthlyBudgetIdr
) { recent, week, monthTotal, budget ->
    HomeUiState(isLoading = false, recentTransactions = recent, weekSeries = ..., ...)
}.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5_000),
    initialValue = HomeUiState()
)
```

Kenapa begini:

- **Satu sumber kebenaran**: UI tidak menghitung uang sendiri; semua turunan dihitung sekali di VM.
- **`combine`**: 4 sumber (3 query Room + 1 preferensi) → 1 state. Menambah transaksi otomatis
  memperbarui dashboard karena Room `Flow` memancar ulang.
- **`WhileSubscribed(5_000)`**: berhenti mengamati 5 detik setelah layar ditinggalkan, lalu mulai lagi
  dari Room. Hemat kerja, data tetap benar.
- **Field turunan, bukan field tersimpan**: `hasBudget`, `budgetUsedFraction`, `budgetUsedPercent`
  adalah `get()` di data class → mustahil ada state "persen 40% tapi total 0".

Contoh state mesin (sealed interface):

```kotlin
sealed interface ExpenseReviewUiState {
    val captureId: Long
    data class Processing(override val captureId: Long) : ExpenseReviewUiState
    data class Unavailable(override val captureId: Long) : ExpenseReviewUiState
    data class Editing(override val captureId: Long, val merchant: String, val amountText: String,
                       val selectedCategoryId: Long?, val isSaving: Boolean = false, ...) : ExpenseReviewUiState
}
```

Kenapa `sealed interface` lebih baik dari `data class` dengan 6 boolean: mustahil membangun state
"sedang memproses tapi tombol save aktif". `when` di layar juga wajib lengkap (exhaustive).

### Tingkat 2 — State lokal di Composable (state UI murni, hilang saat layar ditutup)

| Lokasi | State | Kenapa lokal |
|---|---|---|
| `HomeScreen` | `remember { greeting }` | Turunan jam saat masuk layar; tidak perlu disimpan |
| `CameraScreen` | `lensFacing`, `showCaptureTips`, `latestThumbnailUri`, `thumbnail` | Murni soal tampilan kamera; kalau layar ditutup tidak ada gunanya |
| `SpendingBarChart` | `selectedBarIndex by remember(data)` | Tooltip bar; di-`remember(data)` supaya reset saat data berganti |
| `AnalyticsScreen` | `pagerState` | Posisi tab Daily/Weekly/Yearly |
| `AiChatScreen` | `listState` | Posisi scroll daftar chat |
| `ExpenseReviewScreen` | `preview: ImageBitmap?` | Hasil decode gambar untuk ditampilkan |

Batasannya jelas: **state yang harus benar setelah rotasi → ViewModel; state yang hanya "sedang dilihat"
→ Composable.**

### Tingkat 3 — State persisten (sumber kebenaran)

- **Room** untuk data pengeluaran (`transactions`, `categories`, `captures`).
- **DataStore Preferences** untuk pilihan app (`has_completed_onboarding`, `username`,
  `theme_preference`, `monthly_budget_idr`).
- `preferences` mengembalikan `UserPreferences?` — **null = belum terbaca**, bukan "belum onboarding".
  Ini yang mencegah user lama melihat onboarding lagi setiap membuka app (`launchRoute`).

### Kesalahan yang sengaja dihindari (bahan diskusi bagus)

1. **Event sebagai callback langsung** → `CameraViewModel` memakai `StateFlow<Long?>` + `consumePendingCapture()`
   supaya navigasi tidak terpanggil dua kali saat rotasi.
2. **`remember` untuk data penting** → offset tanggal analytics ada di VM, bukan di composable.
3. **Dua salinan state** → `SettingsScreen` tidak menyimpan `themePreference` sendiri; ia membaca
   DataStore via VM, jadi yang tampil = yang tersimpan.
4. **`start()` idempoten** di `ExpenseReviewViewModel` (`if (startedCaptureId == captureId) return`)
   supaya recomposition/rotasi tidak menjalankan OCR ulang.

---

## 4. Navigation — implementasi di project

File: `ui/AppNavigation.kt`, `MainActivity.kt` (penentu rute awal)

### 1) Rute dinamai di satu tempat

```kotlin
object AppRoutes {
    const val Onboarding = "onboarding"
    const val Home = "home"
    const val Camera = "camera"
    const val Analytics = "analytics"
    const val AiInsights = "ai-insights"
    const val Settings = "settings"
    const val Review = "review/{captureId}"

    fun review(captureId: Long): String = "review/$captureId"
}
```

Kenapa: `popBackStack("home")` dengan string literal akan "membusuk" diam-diam kalau ada yang rename
layar. Dengan konstanta, compiler yang mengingatkan. Rute ber-argumen punya **satu builder** (`review(id)`),
jadi bentuknya tidak pernah berbeda antar pemanggil.

### 2) Graph

```kotlin
NavHost(navController, startDestination) {
    composable(AppRoutes.Onboarding) { OnboardingScreen(onFinished = {
        navController.navigate(AppRoutes.Home) { popUpTo(AppRoutes.Onboarding) { inclusive = true } }
    }) }
    composable(AppRoutes.Home) { HomeScreen(...) }
    composable(AppRoutes.Camera) { CameraScreen(onBack = { navController.popBackStack() },
                                                 onNavigateToReview = { navController.navigate(AppRoutes.review(it)) }) }
    composable(AppRoutes.Review,
               arguments = listOf(navArgument("captureId") { type = NavType.LongType })) { entry ->
        val captureId = entry.arguments?.getLong("captureId") ?: return@composable
        ExpenseReviewScreen(captureId = captureId,
            onFinished = { navController.popBackStack(route = AppRoutes.Home, inclusive = false) })
    }
    composable(AppRoutes.Analytics) { AnalyticsScreen(onNavigateBack = { navController.popBackStack() }) }
    composable(AppRoutes.AiInsights) { AiChatScreen(onNavigateBack = { navController.popBackStack() }) }
    composable(AppRoutes.Settings) { SettingsScreen(onNavigateBack = { navController.popBackStack() }) }
}
```

Tiga keputusan yang layak dijelaskan:

1. **`popUpTo(Onboarding) { inclusive = true }`** → setelah onboarding selesai, layar onboarding
   **dibuang dari back stack**, bukan ditumpuk. Kalau tidak, tombol Back dari dashboard akan
   mengembalikan user ke halaman selamat datang yang tidak ada gunanya.
2. **`popBackStack(route = Home, inclusive = false)`** setelah struk disimpan → kembali ke dashboard,
   melewati kamera, sehingga baris yang baru disimpan langsung terlihat.
3. **Argumen bertipe `NavType.LongType`** → `captureId` bukan string yang di-parse manual; salah tipe
   langsung ketahuan. Kalau argumen hilang/invalid, layar tidak crash: `?: return@composable`.

### 3) Start destination ditentukan sekali, di root

```kotlin
// MainActivity.kt
val stored by preferences.preferences.collectAsStateWithLifecycle(initialValue = null)
val startRoute = launchRoute(stored)
if (stored != null && startRoute != null) {
    LensSpentNavHost(startDestination = startRoute, username = stored.username)
}

// AppNavigation.kt
internal fun launchRoute(stored: UserPreferences?): String? = when {
    stored == null -> null
    stored.hasCompletedOnboarding -> AppRoutes.Home
    else -> AppRoutes.Onboarding
}
```

Ini bagian paling "berbobot" di topik Navigation:

- **`null` berarti "belum tahu", bukan "belum onboarding".** DataStore asinkron; sebelum hasil bacaan
  datang, app memegang nilai default yang **identik** dengan "fresh install". Kalau langsung memutuskan,
  user lama akan dilempar ke onboarding setiap cold start — padahal nama dan temanya masih ada di disk.
- Karena itu `NavHost` **baru dibuat setelah store menjawab**; selama beberapa milidetik yang tampil
  hanya `Surface` berwarna `background`. Menunggu satu frame lebih murah daripada salah rute.
- `username` juga dikirim dari root, sehingga `HomeScreen` tidak perlu punya state preferensi sendiri.

### 4) Navigasi sebagai event dari ViewModel

`CameraScreen` tidak memanggil navigasi dari callback kamera. Alurnya:

```
shutter -> CameraViewModel.onImageCaptured(uri, source)
        -> repository.recordCapture(...) -> _pendingCaptureId.value = id
CameraScreen: LaunchedEffect(pendingCaptureId) { consume; onNavigateToReview(id) }
```

Argumennya: callback kamera bisa terpanggil saat activity sedang re-create; state + `consume` membuat
navigasi terjadi tepat sekali dan tetap benar.

**Topik terkait yang bisa disebut:** tidak ada deep link, tidak ada bottom navigation — struktur app
adalah satu dashboard dengan layar-layar yang di-push di atasnya. Itu keputusan sadar: alur utamanya
"foto → konfirmasi → dashboard", jadi tab bar akan menambah jalur tanpa menambah kemampuan.

---

## 5. MVVM — implementasi dan argumen pendekatannya

### Struktur

```
View (Composable)          -> hanya menggambar state + mengirim aksi
ViewModel                  -> memegang state, memanggil repository, menjalankan coroutine
Model/Repository           -> Room + DataStore + ML Kit + Retrofit
```

Contoh pemisahan pada satu layar (`ExpenseReviewScreen` + `ExpenseReviewViewModel`):

| Lapisan | Isi |
|---|---|
| View | `when (val state = uiState) { Processing -> ...; Unavailable -> ...; Editing -> ... }` |
| ViewModel | `start(captureId)`, `onMerchantChange`, `onAmountChange`, `onCategorySelected`, `onDayStep`, `save()`, `discard()`, `acknowledgeSaved()` |
| Repository | `findCapture`, `addTransaction`, `updateCaptureStatus`, `observeCategories` |

### Wiring: manual DI, tanpa Hilt

```kotlin
// LensSpentApplication.kt
class LensSpentApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}

// AppContainer.kt
class AppContainer(context: Context) {
    private val applicationContext = context.applicationContext
    val userPreferencesRepository by lazy { UserPreferencesRepository(applicationContext) }
    private val database by lazy { Room.databaseBuilder(...).build() }
    val transactionRepository: TransactionRepository by lazy {
        OfflineTransactionRepository(database.transactionDao(), database.categoryDao(), database.captureDao())
    }
    val captureExtractor: CaptureExtractor by lazy { MlKitCaptureExtractor(applicationContext, transactionRepository) }
    val aiInsightsRepository by lazy { AiInsightsRepository(transactionRepository, DeepSeekApiClient.create(), ...) }
}

// AppViewModelProvider.kt
object AppViewModelProvider {
    val Factory = viewModelFactory {
        initializer { HomeViewModel(application().container.transactionRepository,
                                    application().container.userPreferencesRepository) }
        initializer { ExpenseReviewViewModel(application().container.transactionRepository,
                                             application().container.captureExtractor) }
        // ... satu initializer per ViewModel
    }
}
```

### Argumen pendekatan (ini yang dinilai)

1. **Testability.** `HomeViewModel(repository, preferences, clock = Clock.systemDefaultZone())` — clock
   dan repository adalah parameter. Di unit test, repository diganti `FakeTransactionRepository` /
   `StubTransactionRepository` (ada di `src/test`), dan waktu dibekukan. Tanpa DI ini, fungsi yang
   bergantung pada "hari ini" tidak bisa diuji.
2. **`Clock`/`ZoneId` diinjeksi, bukan `LocalDate.now()` di dalam.** Logika minggu/bulan/tanggal jadi
   deterministik; `SpendingAggregator`, `steppedDate`, `confirmationOf` semuanya pure function.
3. **Pemisahan tanggung jawab yang bisa ditunjuk.** `Repository` adalah **batas data**: layar tidak
   pernah menyentuh Room. Kalau nanti agregasi pindah dari Kotlin ke SQL, atau database dienkripsi,
   yang berubah hanya `data/`.
4. **ViewModel tidak menyimpan `Context`.** Yang butuh context (`MlKitCaptureExtractor`, DataStore)
   dibuat di container dan disuntikkan — tidak ada activity leak.
5. **`Application` sebagai composition root**, satu `container` lazy: DB baru dibuka saat pertama kali
   ada yang membaca data.
6. **Kenapa bukan Hilt?** Argumen yang jujur dan kuat: project sebesar ini punya ~7 ViewModel dan 1
   container; `AppViewModelProvider.Factory` + `lazy` cukup, tanpa annotation processor, tanpa menambah
   waktu build, dan alur data tetap bisa dibaca dari atas ke bawah. Hilt masuk akal ketika modul dan
   scope-nya sudah banyak. (Sebutkan juga: kalau ditanya "kekurangannya?", jawab — boilerplate
   `initializer` bertambah linear terhadap jumlah ViewModel, dan tidak ada scoping otomatis.)

### Bukti bahwa MVVM-nya "hidup", bukan sekadar folder

- Semua layar punya pasangan `*Screen.kt` + `*ViewModel.kt`, kecuali layar murni tampilan.
- Layar tidak punya logika bisnis: `canSave`, `canStepForward`, `budgetUsedPercent`, `canSend` hidup di
  state class, dan ada test untuk beberapa di antaranya.
- Callback ViewModel berupa *command* (`save()`, `discard()`), bukan setter state dari layar.

---

## 6. Ktor, Kotlinx Serialization, Coil — mana yang dipakai?

Jawaban singkat dan jujur (ini penting, jangan mengarang):

| Library | Dipakai? | Bukti / pengganti |
|---|---|---|
| **Ktor** | **Tidak** | HTTP client = **Retrofit + OkHttp** (`data/remote/DeepSeekApiClient.kt`) |
| **kotlinx.serialization** | **Ya** | `@Serializable` pada request/response DeepSeek + encode ledger ke JSON |
| **Coil** | **Tidak** | Gambar didecode manual dengan `BitmapFactory` + `inSampleSize` (`ImageDecoding.kt`) |

### kotlinx.serialization — bagaimana dipakai

Plugin di `app/build.gradle.kts` & `gradle/libs.versions.toml`:

```
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
kotlinx-serialization-json = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version.ref = "kotlinxSerialization" }
```

Tiga pemakaian nyata:

1. **Wire format DeepSeek** (`DeepSeekChat.kt`) — OpenAI-compatible:

```kotlin
@Serializable data class ChatCompletionRequest(
    val model: String, val messages: List<ChatMessage>,
    val stream: Boolean = false, val temperature: Double = 0.3
)
@Serializable data class ChatMessage(val role: String, val content: String)
@Serializable data class ChatCompletionResponse(
    val choices: List<ChatChoice> = emptyList(), val usage: ChatUsage? = null
)
@Serializable data class ChatChoice(
    @SerialName("finish_reason") val finishReason: String? = null, ...
)
```

`@SerialName` dipakai karena nama di JSON (`finish_reason`, `prompt_tokens`) memakai snake_case
sementara Kotlin memakai camelCase.

2. **Konfigurasi `Json`** (`DeepSeekApiClient.kt`):

```kotlin
val Json = Json {
    ignoreUnknownKeys = true   // field baru dari server tidak memecahkan build lama
    encodeDefaults = true      // temperature & stream tetap dikirim walau sama dengan default
}
```

`encodeDefaults` itu bukan detail sepele: tanpa itu, `temperature = 0.3` **tidak dikirim**, dan model
memakai default server — perilaku app berubah tanpa satu baris kode berubah.

3. **Payload ledger ke model** (`AiInsightsRepository.kt`):

```kotlin
@Serializable internal data class InsightTransaction(
    val date: String, val merchant: String, val category: String, val amountRp: Long
)
json.encodeToString(rows)   // 50 transaksi terakhir, 4 field saja
```

Kelas ini sengaja **terpisah** dari `TransactionEntity`: yang dikirim ke internet hanya 4 field;
`id`, `note`, `captureId`, `createdAt` tetap di HP. Ini juga jawaban bagus untuk topik privasi/Auth.

Konverter Retrofit: `Json.asConverterFactory("application/json".toMediaType())`.

### Ktor — kalau diminta "kenapa tidak Ktor?"

Alasan yang bisa dipertahankan: hanya ada **satu endpoint** (`POST /chat/completions`). Retrofit
memberi deklarasi interface + converter yang type-safe dengan biaya paling kecil; OkHttp sudah menjadi
transpor di bawahnya, jadi kebutuhan (timeout, interceptor header) tercakup. Ktor masuk akal kalau
butuh multiplatform (iOS/desktop) atau WebSocket/SSE. Fitur streaming memang tidak dipakai
(`stream = false`) karena layar menunggu satu jawaban utuh.

### Coil — kalau diminta "kenapa tidak Coil?"

Kebutuhan gambar di app ini bukan "load URL dari internet", tetapi:

- decode **file lokal** (`content://`) dengan ukuran target yang diketahui (thumbnail 60.dp, preview 200.dp);
- **downsampling** supaya 12 MP JPEG (~48 MB ARGB_8888) tidak pernah masuk memori;
- caching tidak dibutuhkan karena gambar berasal dari MediaStore (sudah ada di disk).

`ImageDecoding.kt` melakukan itu dalam ~30 baris:

```kotlin
val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
// hitung sampleSize pangkat 2 ...
BitmapFactory.Options().apply { inSampleSize = sampleSize; inPreferredConfig = Bitmap.Config.RGB_565 }
```

Poin bonus: **decode selalu di `Dispatchers.IO`** (`withContext(Dispatchers.IO)` di `CameraScreen` dan
`ExpenseReviewScreen`), karena dua kali baca stream tidak boleh di main thread.

### Tabel perbandingan tiga library (topik ini selalu ditanya satu paket)

| Aspek | Ktor | kotlinx.serialization | Coil |
|---|---|---|---|
| Perannya | HTTP client (transport) | JSON codec (serialisasi) | Image loader |
| Dipakai di project? | **Tidak** | **Ya** | **Tidak** |
| Penggantinya di project | Retrofit + OkHttp | — (ini yang dipakai) | `BitmapFactory` manual |
| Alternatif lain di ekosistem | OkHttp langsung, Volley | Moshi, Gson | Glide, Picasso, Coil |
| Alasan utama | Cuma 1 endpoint, Retrofit lebih ringkas | Butuh typed JSON untuk DeepSeek | Gambar lokal, bukan URL |

---

### 6.4 Ktor — penjelasan lengkap (walaupun di project tidak dipakai)

**Apa itu Ktor.** Framework Kotlin-first dari JetBrains, punya dua sisi: **Ktor Server** dan
**Ktor Client**. Yang relevan untuk Android adalah Ktor Client: HTTP client berbasis coroutine
(semua fungsinya `suspend`), dengan arsitektur **engine + plugin**.

- **Engine** = mesin transport yang bisa diganti: `OkHttp`, `Android`, `CIO`, `Darwin` (iOS), `Js`.
  Artinya Ktor tidak mengunci kamu ke satu library jaringan — `ktor-client-okhttp` bahkan memakai
  OkHttp di bawahnya.
- **Plugin** (`install(...)`) = potongan kemampuan yang dipasang deklaratif:
  `ContentNegotiation` (JSON), `Logging`, `Auth` (bearer/basic), `HttpTimeout`, `WebSockets`,
  `SSE`, `Retry`, `HttpCache`, `DefaultRequest`.
- Mendukung **Kotlin Multiplatform**, jadi satu kode jaringan bisa dipakai di Android, iOS, desktop,
  dan JS.

**Kalau Ktor dipasang di LensSpent, bentuknya begini** (bandingkan dengan `DeepSeekApiClient` yang ada):

```kotlin
// app/build.gradle.kts
implementation("io.ktor:ktor-client-core:$ktor")
implementation("io.ktor:ktor-client-okhttp:$ktor")                    // engine
implementation("io.ktor:ktor-client-content-negotiation:$ktor")
implementation("io.ktor:ktor-serialization-kotlinx-json:$ktor")

// data/remote/DeepSeekKtorClient.kt
private val client = HttpClient(OkHttp) {
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true; encodeDefaults = true })
    }
    install(HttpTimeout) {
        connectTimeoutMillis = 20_000
        requestTimeoutMillis = 120_000   // sama seperti readTimeout Retrofit
    }
    install(Logging) { level = LogLevel.INFO }   // hati-hati: jangan sampai log header Authorization

    defaultRequest {
        url("https://api.deepseek.com/")
        header("Authorization", "Bearer ${DeepSeekApiClient.ApiKey}")
        contentType(ContentType.Application.Json)
    }
}

suspend fun chatCompletion(request: ChatCompletionRequest): ChatCompletionResponse =
    client.post("chat/completions") { setBody(request) }.body()
```

**Apa yang berubah dibanding implementasi sekarang:**

| Hal | Retrofit (sekarang) | Kalau Ktor |
|---|---|---|
| Deklarasi endpoint | `interface DeepSeekApi { @POST(...) suspend fun ... }` | panggilan `client.post("chat/completions")` langsung |
| Header auth | `Interceptor` OkHttp | `defaultRequest { header(...) }` atau plugin `Auth` |
| JSON | `Json.asConverterFactory(...)` | `install(ContentNegotiation) { json(...) }` |
| Timeout | `OkHttpClient.Builder().readTimeout(...)` | `install(HttpTimeout) { ... }` |
| Streaming | `@Streaming` + `ResponseBody` | `client.preparePost(...).execute { it.bodyAsChannel() }` — jauh lebih nyaman |
| Multiplatform | tidak bisa (Retrofit JVM-only) | bisa (engine per platform) |
| Test | MockWebServer, atau interface `DeepSeekApi` di-fake | `MockEngine` bawaan, sangat mudah |

**Kenapa akhirnya tidak dipakai di project ini (argumen yang bisa dipertahankan):**

1. **Satu endpoint saja.** Nilai utama Ktor (plugin, engine abstraksi, multiplatform) baru terasa
   saat ada banyak endpoint, banyak format, atau target selain Android. Untuk satu `POST` JSON,
   Retrofit + OkHttp lebih ringkas dan lebih dikenal dosen/reviewer.
2. **Retrofit sudah menyediakan bentuk yang type-safe.** Interface `DeepSeekApi` justru berguna untuk
   testing (`AiInsightsRepositoryTest` memberikan fake `DeepSeekApi`), dan `converter-kotlinx-serialization`
   menghubungkan keduanya tanpa kode tambahan.
3. **Tidak ada kebutuhan multiplatform.** Project ini Android-only.
4. **Tidak ada streaming.** `ChatCompletionRequest.stream = false`; jawaban ditunggu utuh, jadi
   keunggulan Ktor di SSE/WebSocket belum terpakai.

**Kalimat jawaban siap pakai:**

> "Ktor itu HTTP client Kotlin dengan arsitektur engine + plugin, dan dia multi-platform. Di project
> ini saya tidak memakainya karena hanya ada satu endpoint REST, jadi Retrofit + OkHttp lebih ringkas,
> dan interface Retrofit-nya justru memudahkan saya membuat fake saat unit test. Ktor akan saya pilih
> kalau butuh streaming token per token (SSE), WebSocket, atau kalau aplikasinya juga mau jalan di iOS."

**Pertanyaan lanjutan yang mungkin muncul:**

- *"Bedanya Ktor dan OkHttp?"* → OkHttp = library transport (level rendah: connection pool, interceptor,
  HTTP/2); Ktor = framework di atas engine, bisa memakai OkHttp sebagai engine-nya.
- *"Kalau mau ganti ke Ktor, berapa file yang berubah?"* → hanya `data/remote/DeepSeekApiClient.kt`
  (+ `DeepSeekChat.kt` bagian interface). `AiInsightsRepository`, ViewModel, dan UI tidak tersentuh,
  karena mereka bergantung pada `DeepSeekApi`, bukan pada Retrofit. **Ini bukti nilai repository
  pattern / dependency inversion** — sebut ini, nilainya tinggi.

---

### 6.5 Coil — penjelasan lengkap (walaupun di project tidak dipakai)

**Apa itu Coil** (`io.coil-kt:coil-compose`). Image loading library untuk Android yang ditulis dengan
Kotlin dan **coroutine-first**, dibuat khusus untuk Compose (`AsyncImage`, `SubcomposeAsyncImage`,
`rememberAsyncImagePainter`). Nama Coil = *Co*routine *I*mage *L*oader.

Yang dia tangani otomatis:

- decode + **downsampling sesuai ukuran target** (`size()` / ukuran constraint composable);
- **memory cache** (LRU) dan **disk cache**;
- pembatalan request saat composable keluar layar (tidak ada decode yang jalan untuk gambar yang
  sudah tidak terlihat);
- `placeholder`, `error`, `crossfade`, transformasi (blur, circle crop);
- sumber apa pun: URL HTTP, `File`, `content://` URI, resource, `Bitmap`.

**Kalau Coil dipasang di LensSpent, dua tempat ini yang berubah:**

```kotlin
// 1) Thumbnail galeri di CameraScreen — ganti Image(bitmap = thumbnail) + decodeSampledBitmap
AsyncImage(
    model = ImageRequest.Builder(LocalContext.current)
        .data(latestThumbnailUri)
        .size(120)                 // px; Coil yang menghitung inSampleSize
        .crossfade(true)
        .build(),
    contentDescription = "Choose a photo",
    contentScale = ContentScale.Crop,
    modifier = Modifier.fillMaxSize()
)

// 2) Preview struk di ExpenseReviewScreen — ganti LaunchedEffect + decodeSampledBitmap
AsyncImage(
    model = state.imageUri,
    contentDescription = "Captured receipt",
    contentScale = ContentScale.Crop,
    modifier = Modifier.fillMaxWidth().height(200.dp)
)
```

Konsekuensinya: `ImageDecoding.kt` (35 baris) **dihapus**, `LaunchedEffect` + `withContext(IO)` di dua
layar **dihilangkan**, dan dua state lokal (`thumbnail`, `preview`) **hilang** karena Coil menyimpan
hasilnya di cache internal. Itu pengurangan kode yang nyata.

**Kenapa akhirnya tidak dipakai (argumen jujur, bukan pembenaran):**

1. **Tidak ada gambar dari jaringan.** Semua gambar berasal dari MediaStore/`content://` lokal, jadi
   fitur andalan Coil (disk cache HTTP, header, retry) tidak terpakai.
2. **Kebutuhannya sudah tercakup 30 baris kode.** `ImageDecoding.kt` melakukan tepat dua hal yang
   dibutuhkan: baca bounds → hitung `inSampleSize` → decode `RGB_565`. Tidak ada kasus lain.
3. **Satu dependensi tambahan** untuk dua pemakaian; project ini sengaja menjaga jumlah dependensi
   (tidak ada Hilt, tidak ada Coil) agar alur data mudah dibaca dari atas ke bawah.
4. **Ada alasan teknis spesifik:** OCR butuh **gambar resolusi penuh** untuk ML Kit
   (`InputImage.fromFilePath` membaca file aslinya, bukan hasil cache Coil). Jadi pipeline OCR tidak
   akan pernah memakai Coil — hanya UI preview yang memakainya, dan itu porsi kecil.

**Keterbatasan pendekatan manual yang harus diakui** (ini yang membuat jawaban terlihat dewasa):

- tidak ada memory cache → preview yang sama didecode ulang setiap kali layar dibuka;
- pembatalan hanya mengandalkan `LaunchedEffect` (cukup untuk 1 gambar per layar, tapi tidak untuk
  grid/`LazyColumn` berisi banyak gambar);
- tidak ada `placeholder`/`crossfade` bawaan.

Artinya: **kalau nanti ada fitur galeri struk (grid banyak gambar), Coil adalah pilihan yang tepat.**
Itu jawaban yang menunjukkan kamu tahu *kapan* library itu layak.

**Kalimat jawaban siap pakai:**

> "Coil itu image loader coroutine-first untuk Compose: dia menangani downsampling, memory/disk cache,
> dan pembatalan request otomatis. Di project ini saya tidak memakainya karena semua gambar lokal dan
> cuma dua tempat, jadi saya decode manual dengan `BitmapFactory` + `inSampleSize` di `Dispatchers.IO`
> — 30 baris, tanpa dependensi. Kalau nanti ada galeri struk berisi banyak gambar, saya akan pindah ke
> Coil karena pembatalan dan cache-nya jadi penting di `LazyColumn`."

---

## 7. Native Feature — implementasi di project

Topik ini kuat karena ada empat fitur native yang benar-benar dipakai.

### a) CameraX (kamera in-app)

`CameraScreen.kt`:

```kotlin
val cameraController = remember {
    LifecycleCameraController(context).apply {
        setImageCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
    }
}
DisposableEffect(lifecycleOwner) {
    cameraController.bindToLifecycle(lifecycleOwner)
    onDispose { cameraController.unbind() }
}
AndroidView(factory = { ctx -> PreviewView(ctx).apply { controller = cameraController } })
```

- `LifecycleCameraController` mengikat kamera ke lifecycle → kamera **otomatis lepas** saat layar
  di-pop. Ini alasan `DisposableEffect` + `unbind()`.
- `PreviewView` dimasukkan lewat `AndroidView` karena itu View klasik, bukan Composable.
- `CAPTURE_MODE_MINIMIZE_LATENCY` dipilih karena user memotret struk beruntun; kecepatan lebih penting
  daripada resolusi maksimum.
- Ganti lensa cukup mengubah `cameraController.cameraSelector` (`LaunchedEffect(lensFacing)`), bukan
  membangun ulang kamera.

### b) ML Kit OCR on-device

`data/extraction/MlKitCaptureExtractor.kt`:

```kotlin
val image = InputImage.fromFilePath(context, Uri.parse(capture.sourceUri))
val recognized = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    .process(image)
    .await()          // coroutines-play-services: Task -> suspend
val lines = recognized.textBlocks.flatMap { it.lines }
```

Argumen:

- **Bundled model** (`com.google.mlkit:text-recognition:16.0.1`) → tidak perlu download model saat
  pertama dipakai, dan **teks struk tidak keluar dari HP**.
- `.await()` (kotlinx-coroutines-play-services) mengubah `Task` menjadi `suspend`, jadi seluruh
  pipeline tetap satu alur `suspend` yang bisa dites.
- Bounding box tiap baris (`OcrLine(left, top, bottom, right)`) dipakai untuk mengurutkan baris —
  urutan block dari OCR tidak selalu sama dengan urutan bacaan struk.
- `modelVersion` disimpan di tabel `captures`, jadi "baca ulang semua capture dengan model lebih baru"
  cukup satu query.

### c) MediaStore + Photo Picker (penyimpanan gambar)

`ImageStorage.kt`:

```kotlin
// tulis ke galeri lewat ContentResolver
val values = ContentValues().apply {
    put(DISPLAY_NAME, "IMG_$timestamp.jpg"); put(MIME_TYPE, "image/jpeg")
    put(RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/LensSpent")
    put(IS_PENDING, 1)       // API 29+: sembunyikan sampai selesai ditulis
}
...
// setelah selesai
put(IS_PENDING, 0)
```

- `IS_PENDING` = **transaksi dua fase** di MediaStore: galeri/indexer tidak melihat file setengah jadi.
- `PickVisualMedia` (Android Photo Picker) dipakai untuk ambil gambar dari galeri — **tanpa izin
  storage** di Android 13+.
- Grant dari picker hanya berlaku selama proses hidup, jadi gambarnya **disalin** ke folder app
  (`importToGallery`) sebelum dicatat sebagai capture. Kalau penyalinan gagal, baris MediaStore
  di-rollback (`contentResolver.delete`) supaya tidak ada frame kosong permanen di galeri.

### d) Permission (Accompanist)

`HomeScreen.kt`:

```kotlin
val permissionsList = if (SDK_INT >= TIRAMISU) listOf(CAMERA, READ_MEDIA_IMAGES)
                      else if (SDK_INT >= Q)      listOf(CAMERA, READ_EXTERNAL_STORAGE)
                      else                        listOf(CAMERA, READ_EXTERNAL_STORAGE, WRITE_EXTERNAL_STORAGE)
val multiplePermissionsState = rememberMultiplePermissionsState(permissionsList)
```

- Daftar izin **berbeda per versi Android**; `WRITE_EXTERNAL_STORAGE` hanya `< Q`.
- Pola UX: FAB kamera **meminta izin dulu**; kalau sudah granted baru navigasi.
- `CameraScreen` punya layar "permission required" sendiri, jadi user tidak menemukan viewfinder hitam.

### e) Share sheet untuk export CSV

`SettingsViewModel.exportTransactions()` membuat CSV, `SettingsScreen` mengirimnya lewat
`Intent.createChooser(...)`. Tidak perlu izin storage karena file ditulis oleh app penerima.

### f) Edge-to-edge

`enableEdgeToEdge()` di `MainActivity`, lalu tiap layar memakai `statusBarsPadding()` /
`navigationBarsPadding()` / `TopAppBarDefaults.windowInsets` / `imePadding()`.

---

## 8. Database — koneksi Android ke database dan proses yang terjadi

### a) Apa yang dipakai

- **Room** (SQLite) untuk data ledger. Bukan server, bukan cloud: **database lokal di perangkat**.
- **DataStore Preferences** untuk setting.
- **Tidak ada** koneksi JDBC/MySQL/Postgres/Firebase. Ini penting dikatakan lebih dulu, karena
  pertanyaan "koneksi ke database" sering dibayangkan sebagai server.

### b) Skema (versi 1)

```
categories                        transactions                       captures
----------                        ------------                       --------
id (PK, autoGenerate)             id (PK)                            id (PK)
name (UNIQUE index)               merchant                           sourceUri (UNIQUE index)
iconKey                           amount      (Long, Rupiah utuh)     source      (CAMERA|PICKER)
colorHex                          categoryId  (FK -> categories.id)   capturedAt
sortOrder                         occurredAt  (epoch millis)         status      (CAPTURED|OCR_DONE|...)
isUserDefined                     note        (nullable)             ocrText     (nullable)
                                  captureId   (nullable)             structuredJson (nullable)
                                  createdAt                          modelVersion  (nullable)
indices: categoryId, occurredAt
FK: onDelete = RESTRICT
```

Keputusan yang bisa dijelaskan:

1. **`amount` = `Long`, bukan `Float`/`Double`.** IDR tidak punya sen, dan Float kehilangan presisi di
   atas ~16 juta — masih di dalam rentang total bulanan.
2. **`onDelete = RESTRICT`** pada FK: kategori yang masih dipakai transaksi tidak boleh dihapus, supaya
   tidak ada baris yatim.
3. **`Index("categoryId")` dan `Index("occurredAt")`**: filter dan urutan yang paling sering dipakai.
4. **`createdAt` terpisah dari `occurredAt`**: `occurredAt` = tanggal belanja (bisa diedit user),
   `createdAt` = kapan dicatat (audit). Karena itu `updateFields` **tidak** menulis ulang seluruh baris.
5. **`captures.modelVersion`**: jejak versi model yang menghasilkan `structuredJson`.
6. **Enum disimpan sebagai nama** (`Converters.kt`), bukan ordinal — menambah/mengurutkan ulang enum
   tidak menafsirkan ulang baris lama.
7. **`exportSchema = true` + `room.schemaLocation`** → skema JSON per versi masuk repo, jadi migrasi
   berikutnya bisa direview di diff.
8. **Tanpa `fallbackToDestructiveMigration()`** → kalau migrasi lupa dibuat, build gagal keras di
   development, bukan diam-diam menghapus riwayat keuangan user.

### c) Proses koneksi (urutan nyata saat app dibuka)

```
Application.onCreate()
   └─ applicationScope.launch { container.transactionRepository.seedIfEmpty() }
        └─ AppContainer.database (by lazy)
             └─ Room.databaseBuilder(context, LensSpentDatabase::class.java, "lensspent.db").build()
                  └─ file SQLite dibuat/dibuka di /data/data/org.umn.lensspent/databases/
                       └─ CategoryDao.count() == 0 ? insertAll(DefaultCategories) : no-op
```

Lalu saat layar membaca data:

```
HomeViewModel
   └─ repository.observeTransactions(limit) ─┐
      repository.observeTransactionsBetween ─┼─ combine(...) -> HomeUiState
      repository.observeSumBetween ──────────┤
      preferences.monthlyBudgetIdr ──────────┘
             │
             └─ TransactionDao (Flow) ── Room menjalankan query di background, memancarkan ulang
                                          setiap kali tabel berubah
```

Poin yang wajib disebut:

- **Room memakai executor sendiri**; `Flow` dari DAO tidak boleh dan tidak perlu dijalankan di main
  thread. Tidak ada kode yang membuka koneksi manual — `databaseBuilder` yang mengelola.
- **`Flow` = query hidup.** Menyimpan satu transaksi memicu re-emisi otomatis; dashboard, chart, dan
  total bulan berubah tanpa `refresh()` manual. Tidak ada cache kedua yang bisa basi.
- **Join kategori dilakukan di memori** (`withCategories`), bukan SQL JOIN: taksonomi hanya 9 baris, dan
  hasilnya tidak pernah keluar setengah jadi. Ada fallback `UncategorisedCategory` bila id tidak
  ketemu (lebih baik daripada menyembunyikan uang).
- **Menulis**: `addTransaction` meng-`insert` `TransactionEntity` di `viewModelScope`; setelah itu
  `updateCaptureStatus(captureId, CONFIRMED)` menautkan capture ke transaksi.
- **`suspend` di DAO** = Room memindahkan eksekusi ke thread internal; `Flow` untuk baca berulang,
  `suspend` untuk tulis sekali.

### d) DataStore (database "kedua" yang sering ditanya)

- File `user_preferences` di `files/datastore/`, bukan tabel SQL.
- `Context.userPreferencesDataStore` dibuat sebagai **delegate top-level** → dijamin satu instance per
  nama file; dua instance bisa saling menimpa.
- Baca: `dataStore.data` (`Flow<Preferences>`), `catch { if (IOException) emit(emptyPreferences()) }`
  supaya file korup tidak membuat app crash sebelum frame pertama.
- Tulis: `dataStore.edit { }` — transaksi atomik. `saveOnboardingProfile()` menulis nama + budget +
  flag selesai **dalam satu edit**, supaya tidak ada jendela "onboarding selesai tapi nama belum
  tersimpan".

---

## 9. Auth — mekanisme autentikasi di project

### Jawaban inti (jujur dan jelas)

> "Aplikasi ini **tidak punya akun pengguna**: tidak ada login, registrasi, sesi, atau token user.
> Semua data tersimpan lokal di perangkat, jadi tidak ada identitas yang perlu diautentikasi.
> Yang ada adalah **autentikasi ke layanan pihak ketiga (DeepSeek)** untuk fitur AI Insights."

Sebutkan ini lebih dulu supaya tidak terkesan mengarang. Setelah itu jelaskan yang benar-benar ada:

### a) API key DeepSeek (satu-satunya autentikasi)

Alur key, dari build sampai header:

```kotlin
// app/build.gradle.kts — key tidak pernah di-commit
val deepSeekApiKey = providers.gradleProperty("deepseekApiKey")
    .orElse(providers.environmentVariable("DEEPSEEK_API_KEY"))
    .orElse(providers.provider { /* baca deepseek.api.key dari local.properties */ })

buildConfigField("String", "DEEPSEEK_API_KEY", "\"${deepSeekApiKey.get()}\"")
```

```kotlin
// DeepSeekApiClient.kt
val ApiKey: String = BuildConfig.DEEPSEEK_API_KEY
val isConfigured: Boolean = ApiKey.isNotBlank()

internal fun httpClient(apiKey: String = ApiKey) = OkHttpClient.Builder()
    .connectTimeout(20, TimeUnit.SECONDS)
    .readTimeout(120, TimeUnit.SECONDS)
    .addInterceptor(BearerAuthInterceptor(apiKey))
    .build()

private class BearerAuthInterceptor(private val apiKey: String) : Interceptor {
    override fun intercept(chain: Interceptor.Chain) = chain.proceed(
        chain.request().newBuilder()
            .header("Authorization", "Bearer $apiKey")
            .header("Accept", "application/json")
            .build()
    )
}
```

Poin yang dinilai:

1. **Key disuntik saat build**, bukan disimpan di source code. Tiga sumber berurutan: Gradle property →
   environment variable (CI) → `local.properties` (sudah di-`.gitignore`).
2. **Interceptor** = satu tempat yang menempelkan header. Tidak ada call site yang bisa lupa
   menambahkan `Authorization`, dan key tidak pernah masuk URL atau log.
3. **Build tanpa key tetap jalan.** `isConfigured = false` → `AiInsightsRepository.ask()` mengembalikan
   `AiInsightsResult.Failure(MissingApiKeyMessage)`, dan chat menampilkan kalimat itu. Fitur lain
   (offline) tidak terpengaruh.
4. **Kegagalan auth dipetakan ke bahasa manusia** (`httpFailureReason`): 401/403 → "key ditolak",
   402 → "kredit habis", 429 → "rate limit", 5xx → "server bermasalah". Tidak ada stack trace di UI.
5. **Keterbatasan yang harus diakui** (ini menunjukkan pemahaman, bukan kelemahan):
   karena tidak ada backend, key ikut ter-embed di APK dan bisa diekstrak. Solusi yang benar =
   proxy backend yang memegang key; itu di luar cakupan project. Yang sudah dilakukan: key tidak
   di repo, dan payload yang dikirim dibatasi 4 field per transaksi.

### b) "Auth" versi lokal: gerbang onboarding

- `hasCompletedOnboarding` di DataStore menentukan rute awal (`launchRoute`).
- `OnboardingScreen` halaman pertama = **disclosure** penggunaan data, dan tombol setuju adalah satu-
  satunya jalan masuk. Yang disimpan bukan "consent flag" terpisah, tapi fakta bahwa onboarding selesai.
- Ini bukan autentikasi (tidak memverifikasi identitas), tapi ia berfungsi sebagai **gerbang akses**
  pertama. Sebut sebagai "gate", jangan sebut "login".

### c) Izin Android

`CAMERA`, `READ_MEDIA_IMAGES`/`READ_EXTERNAL_STORAGE`, `INTERNET` — ini **otorisasi platform**, bukan
autentikasi. Kalau ditanya bedanya: autentikasi = "siapa kamu", otorisasi = "boleh melakukan apa".

### d) Autentikasi secara umum — dan bagaimana bentuknya kalau LensSpent punya akun

Bagian ini penting karena topik "Auth" selalu memancing pertanyaan "jadi kalau ada login, bagaimana?".

**Tiga hal berbeda yang sering tertukar:**

| Istilah | Pertanyaan yang dijawab | Contoh di LensSpent |
|---|---|---|
| **Autentikasi** | "Siapa kamu?" | API key DeepSeek (server memverifikasi identitas build/app) |
| **Otorisasi** | "Boleh melakukan apa?" | permission `CAMERA`, `INTERNET`; tier "Premium AI Insights" |
| **Identifikasi** | "Ini data milik siapa?" | `hasCompletedOnboarding` + `username` (lokal, tanpa verifikasi) |

**Pola autentikasi Android yang umum, dan mana yang cocok untuk app seperti ini:**

| Pola | Cara kerja | Cocok untuk LensSpent? |
|---|---|---|
| **API key statis** (dipakai sekarang) | Header `Authorization: Bearer <key>` di setiap request | Ya untuk satu layanan pihak ketiga; **tidak aman** untuk banyak user karena key tidak bisa dicabut per user |
| **Session cookie / token** | Login → server kirim token → disimpan → dikirim di setiap request | Ya, kalau nanti ada backend sendiri |
| **OAuth 2.0 / OIDC** (Google Sign-In) | User login lewat Google → app dapat **ID token** (JWT) → dikirim ke backend → backend verifikasi tanda tangan | Paling masuk akal untuk app konsumen: tidak perlu simpan password |
| **Firebase Authentication** | SDK menyediakan `signInWithCredential`, refresh token otomatis, `currentUser` | Paling cepat untuk prototyping; menambah dependensi Google Play Services |
| **Biometric / device credential** | `BiometricPrompt` membuka kunci data lokal, **tanpa server** | Cocok sebagai lapisan privasi lokal ("kunci app"), bukan pengganti login |

**Kalau LensSpent punya akun, alurnya akan seperti ini** (sketsa, belum ada di project):

```kotlin
// 1) Login -> token
val credential = GoogleAuthProvider.getCredential(idToken, null)
auth.signInWithCredential(credential).await()

// 2) Token disimpan aman, BUKAN di DataStore biasa
val masterKey = MasterKey.Builder(context).setKeyScheme(AES256_GCM).build()
EncryptedSharedPreferences.create(context, "auth", masterKey, ...)

// 3) Setiap request membawa token (interceptor yang sama seperti sekarang)
.addInterceptor { chain ->
    val token = tokenStore.accessToken          // refresh dulu kalau kedaluwarsa
    chain.proceed(chain.request().newBuilder()
        .header("Authorization", "Bearer $token").build())
}

// 4) Data lokal di-tag pemiliknya, atau dihapus saat logout
data class TransactionEntity(..., val ownerId: String)
```

Poin yang harus disebut kalau sampai ke sini:

1. **Jangan simpan token di `DataStore` biasa atau `SharedPreferences` polos.** Pakai
   `EncryptedSharedPreferences` (Jetpack Security) atau Keystore.
2. **Token punya masa berlaku.** Butuh refresh token + penanganan 401 (mis. `Authenticator` OkHttp).
3. **Struktur sekarang sudah siap.** Semua request lewat `BearerAuthInterceptor`, jadi mengganti
   "API key build" menjadi "token user" hanya mengubah **sumber string** di interceptor itu, bukan
   seluruh app.
4. **Kalau ada akun, data lokal jadi multi-user.** `lensspent.db` sekarang mengasumsikan satu pemilik;
   menambah login berarti memisahkan data per user (kolom `ownerId` atau database per akun) dan
   memutuskan apa yang terjadi saat logout.
5. **Auth bukan pengganti enkripsi.** Login tidak mengenkripsi SQLite. Kalau tujuan privasi, yang
   dibutuhkan SQLCipher atau setidaknya kunci aplikasi via biometrik.

**Kalimat jawaban siap pakai:**

> "Di project ini tidak ada login, karena data tidak pernah meninggalkan perangkat — jadi tidak ada
> identitas yang perlu diverifikasi. Autentikasi yang ada hanya ke DeepSeek, berupa API key yang
> ditempel lewat interceptor. Kalau saya harus menambahkan akun, saya akan pakai OAuth/OIDC lewat
> Google Sign-In, menyimpan token di EncryptedSharedPreferences, dan menukar sumber string di
> interceptor yang sudah ada — jadi perubahan terkonsentrasi di satu tempat. Tapi itu juga menuntut
> data lokal dipisah per pemilik, dan itu keputusan desain yang tidak boleh diambil setengah-setengah."

---

## 10. Background Process — ada atau tidak?

### Jawaban inti

> "Tidak ada background process terjadwal: **tanpa WorkManager, tanpa foreground service, tanpa
> BroadcastReceiver, tanpa AlarmManager**. Semua pekerjaan berjalan di dalam scope yang terikat
> lifecycle."

Lalu jelaskan mekanisme yang **memang ada**, karena itu yang membuat aplikasi tetap terasa "di
belakang layar":

### a) `applicationScope` untuk pekerjaan yang harus selesai sendiri

```kotlin
// LensSpentApplication.kt
private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

override fun onCreate() {
    super.onCreate()
    applicationScope.launch { container.transactionRepository.seedIfEmpty() }
}
```

- Scope ini **bertahan melewati satu layar** (milik proses), jadi seeding kategori tidak dibatalkan
  saat layar pertama ditutup.
- `SupervisorJob` → satu kegagalan tidak membunuh seluruh scope.
- `Dispatchers.IO` → tidak ada I/O di main thread.
- Komentar di file itu menyebut "later for background pipeline jobs" — jadi desainnya sudah menyiapkan
  tempat, implementasinya belum ada. Ini jawaban yang bagus untuk "kenapa belum ada WorkManager?".

### b) `viewModelScope` untuk pekerjaan per layar

Semua operasi berat dipicu user dan berjalan di scope ViewModel:

```kotlin
// ExpenseReviewViewModel
viewModelScope.launch {
    val capture = repository.findCapture(captureId)   // Room
    val draft = extractor.extract(capture)            // ML Kit OCR (suspend)
    _uiState.value = ExpenseReviewUiState.Editing(...)
}
```

- Hasilnya tetap benar saat rotasi karena state ada di ViewModel, bukan di composable.
- Saat layar benar-benar ditinggalkan, scope dibatalkan — tidak ada pekerjaan "hantu".
- **Batal ≠ gagal**: `MlKitCaptureExtractor` menangkap `CancellationException` lalu me-*rethrow*,
  dan hanya `Exception` lain yang diubah menjadi `ExpenseDraft()` kosong.

### c) Room `Flow` = pembaruan "di belakang layar" tanpa polling

`SharingStarted.WhileSubscribed(5_000)` membuat query berhenti 5 detik setelah layar tidak diamati dan
mulai lagi saat kembali. Ini pengganti yang lebih baik daripada service periodik: tidak ada wakeup,
tidak ada baterai terpakai saat app tidak dipakai.

### d) Operasi I/O eksplisit di `Dispatchers.IO`

- decode gambar (`decodeSampledBitmap`) — dipanggil di `withContext(Dispatchers.IO)`;
- MediaStore query & copy (`ImageStorage`) — seluruh fungsi `suspend` dengan `withContext(IO)`;
- Retrofit `suspend fun` — OkHttp menjalankan call di thread sendiri, hasil dikembalikan ke coroutine.

### e) Kalau ditanya "kapan WorkManager seharusnya dipakai di app ini?"

Jawaban konkret: (1) mengunggah/menyinkronkan ledger saat offline lalu online lagi, (2) menjalankan
ulang OCR massal untuk capture lama dengan model baru (`modelVersion` sudah ada untuk ini),
(3) retry export/backup. Ketiganya **belum ada** karena app ini offline-first dan semua aksinya dipicu
user.

### f) WorkManager — penjelasan lengkap (walaupun tidak dipakai)

**Apa itu.** `androidx.work:work-runtime-ktx`. API resmi Android untuk **pekerjaan yang harus tetap
dijalankan walau app sudah ditutup atau perangkat restart**. WorkManager memilih sendiri mekanisme
yang tersedia: `JobScheduler` (API 23+), `AlarmManager` + `BroadcastReceiver` (API lama), atau langsung
jalan saat app foreground.

**Konsep intinya:**

| Konsep | Arti |
|---|---|
| `Worker` / `CoroutineWorker` | Unit pekerjaan; `CoroutineWorker.doWork()` adalah `suspend` |
| `WorkRequest` | Permintaan kerja: `OneTimeWorkRequest` atau `PeriodicWorkRequest` |
| `Constraints` | Syarat jalan: `NetworkType.CONNECTED`, `requiresCharging`, `requiresBatteryNotLow` |
| `WorkManager.enqueueUniqueWork` | Menjamin tidak ada duplikat (mis. `KEEP` / `REPLACE`) |
| `Result.retry()` / `success()` / `failure()` | Hasil; `retry()` + `setBackoffCriteria` = backoff eksponensial otomatis |
| `beginUniqueWork` / chaining | Rantai pekerjaan berurutan (`.then()`) |
| `WorkInfo` (Flow) | Status untuk ditampilkan di UI |
| `ForegroundInfo` | Untuk kerja panjang yang butuh notifikasi progres |
| `@HiltWorker` | Integrasi dengan Hilt (tidak dipakai di project ini) |

**Kalau WorkManager dipasang di LensSpent, bentuknya begini.** Contoh paling masuk akal: **re-OCR
capture lama dengan model baru** — `captures.modelVersion` sudah ada persis untuk ini.

```kotlin
// data/work/ReceiptReExtractionWorker.kt
class ReceiptReExtractionWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as LensSpentApplication).container
        val stale = container.transactionRepository.capturesOlderThan(OcrModelVersion)

        return try {
            stale.forEachIndexed { index, capture ->
                setProgress(workDataOf("done" to index, "total" to stale.size))
                container.captureExtractor.extract(capture)   // pipeline yang sama, tanpa UI
            }
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // WorkManager memberi backoff eksponensial; tidak perlu retry manual
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}

// di Application / AppContainer
WorkManager.getInstance(context).enqueueUniquePeriodicWork(
    "receipt-re-extraction",
    ExistingPeriodicWorkPolicy.KEEP,
    PeriodicWorkRequestBuilder<ReceiptReExtractionWorker>(1, TimeUnit.DAYS)
        .setConstraints(Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)   // OCR on-device
            .setRequiresBatteryNotLow(true)                     // OCR berat
            .build())
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
        .build()
)
```

**Kenapa tidak dipakai sekarang — tiga argumen:**

1. **Tidak ada pekerjaan yang perlu bertahan melewati app.** Ekstraksi struk terjadi **saat user
   menekan Save di layar Review**, yaitu saat app foreground. Kalau user keluar, layar Review tetap ada
   di back stack dan prosesnya dibatalkan — itu perilaku yang benar, bukan bug.
2. **Semua data sudah aman tanpa worker.** Menyimpan transaksi adalah `insert` Room yang cepat
   (milidetik). Tidak ada upload yang bisa gagal di tengah jalan.
3. **WorkManager punya biaya nyata:** dependensi tambahan, `WorkerFactory` (apalagi kalau pakai Hilt),
   dan debugging jadi asinkron — sulit dilacak. Menambahkannya tanpa ada pekerjaan nyata = kompleksitas
   tanpa manfaat.

**Bandingkan dengan yang sekarang dipakai (ini inti jawabannya):**

| Kebutuhan | Solusi di project | Solusi kalau pakai WorkManager |
|---|---|---|
| Seed kategori sekali di awal | `applicationScope.launch { seedIfEmpty() }` | `OneTimeWorkRequest` + `enqueueUniqueWork` |
| OCR struk | `viewModelScope` di layar Review | `OneTimeWorkRequest` (tapi user harus menunggu notifikasi — UX lebih buruk) |
| Data berubah → UI ikut | Room `Flow` + `WhileSubscribed(5s)` | `WorkInfo` Flow (lebih rumit, tidak perlu) |
| Retry saat gagal | user menekan Save lagi (input tetap utuh) | `Result.retry()` + backoff |

**Kelemahan pendekatan sekarang yang jujur diakui:** kalau proses app dibunuh OS saat OCR berjalan,
user harus membuka capture itu lagi. Capture row-nya sudah ada di Room (`status = CAPTURED`), jadi
tidak ada data yang hilang — hanya pekerjaannya yang perlu diulang.

**Kalimat jawaban siap pakai:**

> "WorkManager itu API untuk pekerjaan yang harus tetap jalan walau app ditutup atau HP restart, dengan
> constraint jaringan/baterai dan backoff otomatis. Di project ini belum ada karena semua kerja berat —
> OCR — dipicu user dan selesai saat app terbuka, dan penyimpanan ke Room cuma milidetik. Yang saya
> pakai adalah `applicationScope` dengan `SupervisorJob + Dispatchers.IO` untuk seeding, dan
> `viewModelScope` per layar. Kalau nanti ada sinkronisasi atau re-OCR massal, di situ WorkManager baru
> masuk — dan tabel `captures` sudah menyimpan `modelVersion` supaya pekerjaan seperti itu mungkin."

**Pertanyaan lanjutan yang mungkin muncul:**

- *"Bedanya WorkManager, Service, dan coroutine scope?"* → coroutine scope mati bersama proses/app;
  Service adalah komponen Android yang bisa jalan tanpa UI tapi mudah dibunuh dan harus dikelola
  manual; WorkManager adalah abstraksi yang menjamin eksekusi + constraint + persistensi.
- *"Foreground service untuk apa?"* → kerja panjang yang harus terlihat user (mis. merekam audio).
  OCR struk berlangsung < 5 detik, jadi foreground service berlebihan (dan wajib `POST_NOTIFICATIONS`
  di Android 13+).
- *"Kenapa `Dispatchers.IO` di applicationScope?"* → seeding menyentuh disk (SQLite) dan tidak boleh
  di main thread; `SupervisorJob` memastikan satu kegagalan tidak mematikan scope proses.

---

## 11. Strategi memilih 2 topik (harus beda dengan anggota lain)

Topik yang **paling banyak bukti kode** dan paling aman untuk dikuasai:

| Prioritas | Topik | Alasan |
|---|---|---|
| 1 | **Database** | Room + DataStore + alur query; banyak keputusan desain yang bisa dijelaskan (Long vs Float, FK RESTRICT, `Flow`, tanpa destructive migration) |
| 2 | **MVVM** | Ada bukti berlapis: VM + Factory + Container + fake repository + clock injection |
| 3 | **State** | Bisa dijawab dengan 3 tingkat (VM / Composable / persisten) — strukturnya rapi |
| 4 | **Navigation** | Sedikit file, tapi ada argumen kuat: `launchRoute` null-handling, `popUpTo`, argumen bertipe |
| 5 | **Native Feature** | CameraX + ML Kit + MediaStore + permission: paling "berisi" |
| 6 | **Compose Layout** | Mudah, tapi pastikan menyebut recomposition & state hoisting, bukan cuma "ini Row" |
| 7 | **UI Theming** | Di backup masih tipis; jujur bilang begitu, lalu jelaskan `themeIsDark` + `dynamicColor=false` |
| 8 | **Kotlinx Serialization** | Jelas dan sempit: `@Serializable`, `@SerialName`, `encodeDefaults` |
| 9 | **Auth** | Jawab "tidak ada akun", lalu API key + Bearer + onboarding gate |
| 10 | **Background Process / Ktor / Coil** | Jawab "tidak dipakai", jelaskan penggantinya + alasannya |

Isi tabel ini saat pembagian kelompok:

| Anggota | Topik 1 | Topik 2 |
|---|---|---|
| Saya | | |
| | | |
| | | |
| | | |

**Kombinasi yang saling menguatkan kalau harus memilih:**

- **Database + State** → satu cerita utuh: "data dari Room mengalir sebagai Flow ke StateFlow di
  ViewModel, lalu jadi UI". Kalau ditanya apa pun tentang alur data, jawabannya sudah nyambung.
- **MVVM + Navigation** → menjelaskan arsitektur (lapisan) dan bagaimana layar disusun/dihubungkan.
- **Native Feature + Compose Layout** → menjelaskan dari kamera sampai komponen UI yang menampilkannya.

Kombinasi yang **hindari**: Theming + Compose Layout (terlalu banyak tumpang tindih di bagian UI), dan
dua topik "tidak dipakai" (Ktor + Coil) — akan terasa kosong.

---

## 12. Persiapan demo (checklist)

1. **Buka project backup**, bukan versi sekarang:
   - extract `LensSpent-backups\LensSpent-20261021-235259.zip` ke folder baru, buka di Android Studio;
   - atau siapkan `git stash`/branch kalau tetap memakai repo utama. **Jangan** jelaskan kode yang
     tidak ada di layar yang sedang ditunjukkan.
2. Pastikan `local.properties` ada (`sdk.dir`), lalu **Sync + Build sekali sebelum kelas**. Jangan
   mengandalkan build pertama saat presentasi.
3. Siapkan **device/emulator** dengan:
   - satu foto struk di galeri (untuk demo photo picker),
   - data lama dihapus supaya bisa menunjukkan onboarding (`Settings > Apps > LensSpent > Clear data`).
4. Urutan demo yang aman (≤ 4 menit):
   - onboarding → dashboard kosong (empty state),
   - FAB kamera → permission → shutter/pilih galeri,
   - layar Review: koreksi nominal, step tanggal, pilih kategori, Save → dialog konfirmasi,
   - dashboard terisi (total bulan + chart),
   - Analytics: tab Daily/Weekly/Yearly, step tanggal,
   - Settings: ganti tema (efek langsung), export CSV.
5. Siapkan **satu file kode yang akan dibuka** per topik (jangan scroll mencari saat presentasi):
   - Database: `LensSpentDatabase.kt`, `TransactionEntity.kt`, `TransactionDao.kt`;
   - State: `HomeViewModel.kt`, `ExpenseReviewViewModel.kt`;
   - MVVM: `AppContainer.kt`, `AppViewModelProvider.kt`;
   - Navigation: `ui/AppNavigation.kt`, `MainActivity.kt`;
   - Layout: `SpendingBarChart.kt`, `StepperHeader.kt`;
   - Theming: `ui/theme/Theme.kt`, `MainActivity.kt`;
   - Serialization: `DeepSeekChat.kt`, `DeepSeekApiClient.kt`;
   - Native: `CameraScreen.kt`, `MlKitCaptureExtractor.kt`, `ImageStorage.kt`;
   - Auth: `DeepSeekApiClient.kt` + `app/build.gradle.kts`;
   - Background: `LensSpentApplication.kt`.
6. Kalau tidak bisa hadir: sesuai ketentuan, siapkan diri untuk **diskusi susulan individu minggu 13**
   dengan 2 topik yang sama. Materi ini sudah cukup untuk itu, asalkan kamu bisa menunjukkan file dan
   menjelaskannya tanpa membaca.

---

## 13. Ringkasan satu halaman (untuk dibaca 10 menit sebelum tampil)

| Topik | Inti jawaban |
|---|---|
| **Compose Layout** | `StepperHeader`/`SpendingBarChart`: parameter = kontrak, state hoisting, `Modifier` default, `Canvas` agar animasi hanya invalidate draw |
| **Theming** | 6 token warna → `darkColorScheme`/`lightColorScheme`; `themeIsDark` pure; `dynamicColor = false`; shape & tipografi dari M3 |
| **State** | 3 tingkat: ViewModel (`StateFlow`, `combine`, `stateIn`), Composable (`remember` untuk UI sementara), persisten (Room + DataStore) |
| **Navigation** | `AppRoutes` konstanta; `popUpTo(inclusive)`; argumen `NavType.LongType`; `launchRoute` mengembalikan `null` saat store belum terbaca |
| **MVVM** | Screen–ViewModel–Repository; manual DI (`AppContainer` + `viewModelFactory`); `Clock` diinjeksi agar bisa dites; alasan tidak pakai Hilt |
| **Ktor / Serialization / Coil** | Ktor ✗ (pakai Retrofit+OkHttp), Serialization ✓ (`@Serializable`, `@SerialName`, `encodeDefaults`), Coil ✗ (decode manual + `inSampleSize`) |
| **Native Feature** | CameraX + `LifecycleCameraController`, ML Kit OCR bundled, MediaStore `IS_PENDING`, Photo Picker, Accompanist permission, share sheet, edge-to-edge |
| **Database** | Room lokal (`lensspent.db`, 3 tabel, FK RESTRICT, index, `amount: Long`), DAO `Flow` = query hidup, DataStore untuk setting, tanpa destructive migration |
| **Auth** | Tidak ada akun user; satu-satunya auth = API key DeepSeek via `BuildConfig` + `Authorization: Bearer` interceptor; onboarding sebagai gerbang lokal |
| **Background Process** | Tidak ada worker/service; `applicationScope` (SupervisorJob + IO) untuk seeding, `viewModelScope` per layar, Room `Flow` + `WhileSubscribed(5s)` |

---

## 14. Pola menjawab topik yang "tidak dipakai"

Kalau kamu kebagian **Ktor**, **Coil**, atau **Background Process**, jawaban "tidak dipakai" saja akan
terasa kosong. Pakai kerangka 4 langkah ini — total ± 90 detik, dan selalu memuaskan penguji:

**Langkah 1 — Nyatakan statusnya tanpa bertele-tele (5 detik).**
> "Library ini tidak terpasang di project kami."

**Langkah 2 — Jelaskan apa itu dan konsep intinya (25 detik).**
Sebutkan perannya, satu-dua konsep kunci, dan satu istilah teknis yang tepat
(mis. untuk Ktor: *engine + plugin*, coroutine-based, multiplatform; untuk Coil: *coroutine image
loader*, memory/disk cache, downsampling otomatis; untuk WorkManager: *constraints + backoff +
persistensi lintas restart*).

**Langkah 3 — Tunjukkan apa yang dipakai sebagai gantinya, dengan file (30 detik).**
Ini bagian yang membuktikan kamu paham project, bukan cuma teori:
> "Sebagai gantinya kami memakai Retrofit + OkHttp di `DeepSeekApiClient.kt`, karena endpoint-nya cuma
> satu." / "Kami decode manual di `ImageDecoding.kt` dengan `inSampleSize`." / "Kami pakai
> `applicationScope` di `LensSpentApplication.kt` dan `viewModelScope` per layar."

**Langkah 4 — Beri alasan + kapan harus pindah (30 detik).**
Alasan harus spesifik ke project (bukan "karena lebih mudah"), lalu akhiri dengan kondisi pemicu:
> "Coil akan kami pakai kalau sudah ada galeri struk berisi banyak gambar, karena pembatalan request
> dan memory cache jadi penting di dalam `LazyColumn`."

**Kesalahan yang harus dihindari:**

| Jangan | Kenapa |
|---|---|
| "Tidak dipakai, Pak/Bu." lalu diam | Terlihat tidak menyiapkan diri; ini justru topik yang paling mudah dijelaskan baik |
| Mengarang bahwa library itu dipakai | Penguji bisa minta tunjukkan `build.gradle.kts` — langsung ketahuan |
| Menjelekkan library ("Coil jelek") | Yang benar: "tidak dibutuhkan pada skala project ini", bukan "library-nya buruk" |
| Menjelaskan teori panjang tanpa menunjuk file | Topik ini menilai pemahaman **project**, bukan hafalan dokumentasi |

**Satu kalimat penutup yang kuat (bisa dipakai untuk ketiga topik):**
> "Keputusan tidak memakai library adalah keputusan arsitektur juga — yang kami hindari adalah
> dependensi yang menambah permukaan tanpa menyelesaikan masalah yang benar-benar ada di project ini."
