# LensSpent security policy

This file records the privacy and security decisions that are easy to undo by accident. Each one is
enforced by code or by a test; the test name is given so a reviewer can see the rule, not just read
about it.

## No API key is shipped in the app

An APK is not a secret store. Anything compiled into `BuildConfig`, a resource or a Gradle property is
readable from the artifact, and a leaked provider key cannot be rotated in builds already installed.

- The app has **no** provider credential. `BuildConfig` carries only `AI_PROXY_BASE_URL`, a
  non-secret https address of a backend the project controls (`AppContainer`, `AiBackendConfig`).
- The backend owns the provider key, the rate limit and (if it needs one) authentication of the app.
- A build with no proxy URL is a normal, fully working build: every feature except AI Insights works
  offline, and the chat says the feature is off.

Enforced by: `DeepSeekApiClientTest.no provider credential is compiled into the app`,
`DeepSeekApiClientTest.the build script never reads or embeds a provider key`,
`ReleaseConfigurationTest.the build script carries no provider credential`.

If a key was ever committed or shipped, rotate it at the provider. Removing it from the working tree
does not un-leak it.

## AI Insights is opt-in and off by default

Three independent gates, all consulted on **every** request (`AiAccess`, re-read by
`AiInsightsRepository.ask`):

1. a secure backend is configured for the build,
2. the user has not switched AI networking off in Settings,
3. the user accepted the in-app disclosure.

Enforced by: `AiAccessTest`, `AiInsightsRepositoryTest.a build with no backend never makes a request`,
`...turning AI off stops the next request even with consent on record`,
`...no request is made before the disclosure is accepted`, `...access is re-evaluated on every question`.

## What the AI request contains

The request has two parts: the question the user typed, and the ledger data. The ledger part is
monthly aggregates by category only - `month`, `totalRp`, and per category `category`, `totalRp`,
`count`. Never transmitted: receipt images, OCR text, notes, merchant names, individual transactions,
dates finer than the month, usernames, database ids, capture ids.

The question is free text the user wrote, so it can contain anything they chose to type; that is why
it is named explicitly in the disclosure rather than covered by "only aggregates".

Enforced by: `AiInsightsRepositoryTest.only monthly category totals leave the phone` (hand-written
expected JSON) and `...merchants, notes, ids and exact timestamps never appear in the request`.

## Receipt images

Written to app-private internal storage (`filesDir/receipts`), never the shared gallery, so no other
app, media scanner or photo-backup service can read them. No storage permission is used.

Retention: the image is deleted on confirm, on discard, and when the transaction it became is
deleted. A startup sweep removes files with no capture row, which closes the crash/cancel window
between writing the file and recording the capture. `ImageStorage.privateFileFor` refuses any path
outside the app's own receipt directory, so deletion can never reach a gallery item.

Enforced by: `ReceiptRetentionTest`, `SensitiveLoggingTest`, `BackupConfigurationTest`.

## Logging

`AppLog` is the only logger, and it is silent unless `BuildConfig.DEBUG`. OCR text, merchant names,
amounts, notes, CSVs and URIs are never logged. `printStackTrace()` is not used.

Enforced by: `SensitiveLoggingTest` (static scan of `app/src/main`).

## Backup and device transfer

The ledger, DataStore preferences and receipt images are excluded from cloud backup **and** from
device transfer, on API 31+ (`data_extraction_rules.xml`) and below (`backup_rules.xml`), with
`android:allowBackup="false"` as the manifest default. A user who wants their history on another
device uses the explicit CSV export.

Enforced by: `BackupConfigurationTest`.

## CSV export

Two steps: a confirmation that names the complete ledger and the receiving app, then the share
sheet. The CSV is built in memory, never logged, never written to shared storage by the app, and the
pending value is cleared if the chooser fails or is dismissed.

Enforced by: `TransactionsCsvTest` / `CsvExportFlowTest`, `SensitiveLoggingTest.CSV content is never
logged or persisted`.

## Permissions

`CAMERA` is requested only when the camera screen opens, and denial offers the system photo picker and
manual entry instead. `INTERNET` is used only by the optional AI backend. No storage permissions.
Gallery selection uses `ActivityResultContracts.PickVisualMedia`.

## Release builds

R8 and resource shrinking are enabled for release; keep rules live in `src/main/keepRules` and name
only the reflection-sensitive entry points (serialization, Retrofit, Room, ML Kit, manifest classes).
No signing material is checked in.

Enforced by: `ReleaseConfigurationTest`.
