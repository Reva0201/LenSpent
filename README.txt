LENSSPENT — IF570L MID EXAM PROJECT
====================================

Group name : <GROUP_NAME>
App name   : LensSpent
Package    : org.umn.lensspent
Language   : Kotlin + Jetpack Compose (Material 3)
Min SDK 26 / Target SDK 35


1. WHAT THE APP IS
------------------
LensSpent is an offline-first expense tracker for students living on a monthly budget.
The core idea is that logging a purchase should take seconds: point the camera at the
receipt, and the app reads it on-device, guesses the merchant, the amount and the
category, and asks you to confirm. Anything the camera cannot read can be typed in by
hand. Everything the app knows about your money stays on the phone.

The midterm scope is the UI layer: five kinds of screen, a custom Material 3 theme,
real state management, navigation with an argument, forms with validation, and a
realistic dummy dataset. Room, the camera pipeline and the AI tier all exist in the
project, but none of them is required to demonstrate any rubric criterion.


2. GROUP MEMBERS
----------------
<FULL_NAME_1> — <NIM_1>
<FULL_NAME_2> — <NIM_2>
<FULL_NAME_3> — <NIM_3>


3. HOW THE GROUP WORKED
-----------------------
<TO BE CONFIRMED BY THE GROUP: describe the working system in 3-6 sentences. Say how
often you met, how you split the screens, how changes were reviewed before they went
into the shared branch, and how you handled merge conflicts. Be specific — this part is
graded on clarity, and "we divided the work evenly" scores nothing.>

Version control: one Git repository, work done on branches, merged into main after a
build + test run passed.


4. INDIVIDUAL CONTRIBUTIONS
---------------------------
<MEMBER_1>: <specific screens / files / features they personally implemented>
<MEMBER_2>: <specific screens / files / features they personally implemented>
<MEMBER_3>: <specific screens / files / features they personally implemented>

Suggestion: name files, not feelings. "HomeScreen.kt + HomeViewModel.kt: dashboard
layout, budget hero, week chart wiring" is specific and checkable.


5. THE FIVE REQUIRED SCREENS
----------------------------
1. LIST — HomeScreen.kt
   Dashboard built on a LazyColumn: greeting header, monthly budget hero, week-over-week
   quick stats, bar chart, and the recent transactions list (one card, divided rows).
   Each row is a tap target that opens the detail screen.

2. DETAIL — TransactionDetailScreen.kt
   Opened from the list with the transaction id passed as a navigation argument. Shows
   the amount, merchant, category, date, note and how the row was created, read from the
   database by id. Deletes the expense behind a confirmation dialog. Renders a distinct
   "no longer in your ledger" state if the row is gone.

3. FORM / INPUT — AddExpenseScreen.kt
   Manual entry: amount, merchant, category chips, date stepper, optional note with a
   character counter. Correct keyboard type per field, per-field validation with
   isError + supporting text, and Save disabled until the form is valid with a line
   explaining what is missing.

4. NATIVE-FEATURE SCREEN — CameraScreen.kt
   Camera viewfinder UI: shutter, gallery import, lens flip, framing tips panel. Built on
   CameraX, and it degrades gracefully: the permission-denied state is a designed screen,
   not a crash. The screen is a UI mockup (the exam allows dummy behaviour); no OCR result
   is required to demonstrate it.

5. FREE CHOICE — AnalyticsScreen.kt ("Trends")
   Daily / Weekly / Yearly / Categories compartments in a HorizontalPager, date
   steppers, the shared bar chart, a ranked category breakdown with share bars,
   and the full transaction list beneath.

Supporting screens (not counted in the five): OnboardingScreen (agreement + profile),
AiChatScreen (premium AI insights), SettingsScreen (name, budget, theme, CSV export).


6. RUBRIC MAP
-------------
Screen implementation (15)
  Five distinct screen types above, all built from Column/Row/Box/LazyColumn/FlowRow with
  Modifier chaining and weights — no hardcoded positioning.

UI theming (15)
  ui/theme/ is the design system, and every screen reads from it:
    Color.kt   full light + dark Material 3 colour schemes (34 roles each), plus the
               brand gradient as its own role so dark mode cannot produce white-on-pastel
    Type.kt    Plus Jakarta Sans (OFL, bundled) as a variable font with four real weights,
               and a 15-step type scale — display through label
    Shape.kt   6 / 10 / 16 / 22 / 30 dp radii, applied through MaterialTheme.shapes
    Dimens.kt  Spacing / Elevation / Sizes tokens (4 dp grid)
    Theme.kt   applies colour scheme, typography and shapes; dynamic colour is off so the
               brand palette is what ships
  Also: window background per theme (no white flash on cold start), a custom adaptive
  launcher icon, and system-bar icon contrast that follows a user-forced theme.

State management (15)
  Every screen has a ViewModel exposing StateFlow; screens collect with
  collectAsStateWithLifecycle. State holders are data classes with derived properties
  (e.g. AddExpenseUiState.canSave, HomeUiState.budgetUsedFraction) so the UI cannot
  disagree with the rule. Loading / missing / saving / validation states are explicit;
  transient UI state is remembered locally where it belongs (focus, dialogs, pager).
  Screen-level and form-level behaviour is unit tested: 123 unit tests, 0 failures.

Navigation (15)
  NavHost + NavController with named routes in AppRoutes. Two arguments are passed and
  consumed:
    review/{captureId}      Long  — receipt capture to review
    transaction/{transactionId} Long — expense to show on the detail screen
  Back-stack behaviour is handled explicitly: onboarding is popped when finished, and
  the review/detail screens pop back to Home rather than to an intermediate screen.

Form input and validation (15)
  - Amount: numeric keyboard, parsed by parseRupiahAmount; rejects letters with
    "Use digits only, for example 45000", rejects zero, and echoes the parsed value
    ("Saves as Rp 45.000") while typing.
  - Merchant: required, words capitalisation, inline error when blank.
  - Category: required selection, chips, "Choose one" error when missing.
  - Note: optional, 200-character counter that turns into an error past the limit.
  - Save is disabled until every rule passes, and states why it is disabled.
  - Budget dialog and onboarding budget field reuse the same parse + error rules.

Dummy data (15)
  data/demo/DemoLedger.kt: a DemoExpense data class and 18 realistic expenses covering
  all 9 categories, amounts from Rp 25.000 to Rp 449.000, spread across four weeks, two
  on the same day (so the Daily tab has more than one bar), some with notes and some
  without. Seeded once on first launch into an empty ledger only, behind the
  DEMO_LEDGER build flag, so the same source tree can produce an honest empty build
  with -PdemoLedger=false. DemoLedgerTest asserts the data stays varied and valid.

Documentation and deliverables (10)
  This README, the APK, the zipped project, and a demo video script in section 8.


7. BUILDING AND RUNNING
-----------------------
Requirements: Android Studio with the Android SDK 35+ and a JDK (the project was built
with Android Studio's bundled JBR).

  # debug APK
  gradlew.bat :app:assembleDebug

  # unit tests
  gradlew.bat :app:testDebugUnitTest

  # debug build WITHOUT the sample ledger
  gradlew.bat :app:assembleDebug -PdemoLedger=false

The APK to install is app/build/outputs/apk/debug/app-debug.apk.
On first launch you are asked for a name and a monthly budget; both may be left blank.
The sample ledger is written on that first launch, so the dashboard, the chart, the
detail screen and all three analytics tabs have data immediately.

Architecture in one line: UI (Compose screens) -> ViewModel (StateFlow) -> Repository
interface -> Room / DataStore. The AI tier is the only collaborator that leaves the
device, and it is optional.


8. DEMO VIDEO SCRIPT (MAX 5 MINUTES)
------------------------------------
Record on a freshly installed APK so the sample data is visible.

0:00-0:20  Intro. Name the app and the group. "LensSpent logs a receipt in seconds and
           keeps spending on a monthly budget."
0:20-0:40  ONBOARDING. Show the two pages: the privacy disclosure, then name and budget.
           Point out the numeric keyboard and the validation on the budget field.
0:40-1:50  HOME (LIST). Hero card: spent this month, percentage of budget, what is left.
           Quick stats, then the week chart. Scroll to Recent Transactions and tap a row.
1:50-2:20  DETAIL. Show the row that was tapped: amount, category, date, note, and how it
           was created. Point out that the screen was opened with the transaction id —
           the content is not hardcoded. Back to the list.
2:20-3:20  FORM. Tap "Add manually". Type an invalid amount (e.g. 45rb) and show the red
           field + "Use digits only". Fix it, show the live "Saves as Rp ..." echo. Leave
           the merchant blank to show the required-field error. Pick a category and show
           Save becoming enabled. Save, and show the new row on the dashboard.
3:20-3:50  NATIVE SCREEN. Tap "Scan receipt": viewfinder, shutter, gallery import, lens
           flip, framing tips, and the permission-denied state.
3:50-4:30  TRENDS (free choice). Daily/Weekly/Yearly/Categories compartments, step through
           dates, note that the numbers match the dashboard, then show the ranked category
           breakdown.
4:30-4:50  THEME. Settings -> Light / Dark. Show the same screens in dark mode to prove
           the theme is a full colour scheme, not a tint.
4:50-5:00  Close. One line on what you would build next (edit/edit-existing, real OCR).

Screenshots for the report are in docs/screenshots/, in the order they appear in the
video script:

  onboarding.png            first launch, step 1 (brand band + privacy promises)
  profile.png               first launch, step 2 (name + budget, both optional)
  home.png                  LIST screen: monthly hero, week chart, ledger
  detail.png                DETAIL screen opened with a transaction id
  form-empty.png            FORM screen, empty
  form-error.png            FORM screen, numeric validation rejecting "45rb"
  form-ready.png            FORM screen, valid and ready to save
  form-keyboard.png         FORM screen, save bar riding above the IME
  confirm-expense.png       receipt confirmation (camera path)
  saved-dialog.png          saved confirmation card
  trends-daily.png          free-choice screen, Daily compartment
  trends-weekly.png         free-choice screen, Weekly chart
  trends-categories.png     free-choice screen, ranked category breakdown
  settings.png              profile, budget, theme, export
  camera.png                native-feature screen: viewfinder, shutter, gallery
  home-dark.png             dark theme
  saved-dialog.png          confirmation card after a save

Where each rubric criterion is visible:

  Screens          home.png, detail.png, form-*.png, camera.png, trends-*.png
  Theming          home.png vs home-dark.png, settings.png
  State            form-empty -> form-error -> form-ready, saved-dialog.png,
                   trends-daily.png (two stat cards filled from stored rows)
  Navigation       detail.png was opened from a list row with an id argument;
                   form-ready.png is the edit route for that same row
  Validation       form-error.png (isError + supporting text, Save disabled)
  Dummy data       home.png, trends-categories.png, all-transactions list



9. NOTES FOR THE GRADER
-----------------------
- The demo ledger is sample data, not the user's. It is written only into an empty
  ledger, only in this build, and can be turned off at build time (-PdemoLedger=false).
- Receipt photos, the ledger and the settings all stay on the device. Only the Premium AI
  Insights chat sends anything out, and it sends anonymised transaction text.
- A build without a DeepSeek API key still installs and runs; the chat screen says the key
  is missing instead of failing a call.
