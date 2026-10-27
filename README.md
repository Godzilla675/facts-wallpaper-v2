# Facts Wallpaper

An offline-first Android study app that turns PDFs into verified fact cards and
rotates them as a live or static wallpaper. AI extraction, outline detection,
fact generation, diagram generation, formula verification and template design
run through user-configured providers; everything generated is stored locally
and keeps working without a network.

## Requirements

- Android Studio with the Android SDK for API 36 (`compileSdk`/`targetSdk` 36,
  `minSdk` 33).
- JDK 17 (Android Studio's bundled JBR works).
- Windows PowerShell, macOS or Linux. The repository includes a wrapper that
  pins the local JDK, Gradle home and Android SDK for this machine.

## Build and test

On Windows, always use the repository wrapper:

```powershell
.\gw.ps1 assembleDebug
.\gw.ps1 testDebugUnitTest
.\gw.ps1 lintDebug
```

Run the full gate before committing:

```powershell
.\gw.ps1 assembleDebug testDebugUnitTest lintDebug --console=plain
```

On macOS/Linux use the Gradle wrapper directly:

```bash
./gradlew assembleDebug testDebugUnitTest lintDebug
```

`gw.ps1` sets `JAVA_HOME`, `GRADLE_USER_HOME`, `ANDROID_HOME` and
`ANDROID_SDK_ROOT`, then forwards every argument to `gradlew.bat`. It also
supports `.\gw.ps1 --stop` to stop the Gradle daemon and free disk/memory.

## Architecture

The app is a multi-module Gradle project using Hilt, Room, DataStore,
WorkManager and Jetpack Compose:

| Module | Responsibility |
| --- | --- |
| `:app` | Application shell, navigation graph, Hilt entry points, share/view PDF intents |
| `:core:model` | Shared immutable domain models and enums |
| `:core:common` | `AppResult`/`AppError`, dispatchers, small shared utilities |
| `:core:database` | Room entities, DAOs, converters, migrations and cascade rules |
| `:core:data` | Repository implementations over Room/DataStore |
| `:core:security` | Android Keystore-backed API key storage and log redaction |
| `:core:pdf` | PDF import, hashing, route classification, text extraction, OCR hand-off and sub-PDF building |
| `:core:ocr` | ML Kit/Tesseract/PaddleOCR fallback pipeline and page gates |
| `:core:ai` | Provider clients, schema adaptation, routing, retries, chunking, JSON repair, cost ledger |
| `:core:render` | Card rendering, template codecs, validation, cache keys and pre-render scheduling |
| `:core:wallpaper` | Live wallpaper service, rotation reducer, static wallpaper export and scheduling |
| `:core:backup` | AES-256 encrypted `.fwbk` export/restore |
| `:core:ui` | Shared Compose components, theme helpers and `UiMessage` plumbing |
| `:core:designsystem` | Color, type, spacing and theme definitions |
| `:feature:onboarding` | Five-step first-run setup |
| `:feature:library` | Document library, import and route/OCR state |
| `:feature:outline` | Lesson detection, editing, merge/split and generation jobs |
| `:feature:facts` | Fact curation, search, filtering, editing and regeneration |
| `:feature:collections` | Smart views, folders, collections and rotation pool membership |
| `:feature:templates` | Template gallery, editor, AI template generation and card preview |
| `:feature:wallpaper` | Wallpaper mode, rotation controls, static export and diagnostics |
| `:feature:settings` | Provider profiles, routing, usage ledger, advanced settings and backup/restore |

Each feature screen observes a `StateFlow<UiState>` exposed by a Hilt
`ViewModel`; ViewModels read Room/DataStore through repository interfaces.
AI output is schema-validated locally and may use at most one JSON repair pass.
The live wallpaper never performs network, AI, PDF or WebView work.

## Testing and CI

- Unit and integration tests live in each module's `src/test` directory and use
  JUnit4, Coroutines Test, Truth and Robolectric.
- Compose UI tests use the Robolectric Compose rule (see
  `feature/library/src/test/.../LibraryScreenTest.kt`).
- `.github/workflows/android-ci.yml` runs a secret-leak gate over tracked files,
  then `lintDebug testDebugUnitTest assembleDebug`.
- Architecture decisions and deviations are recorded in
  [`docs/DECISIONS.md`](docs/DECISIONS.md).

## Security notes

- Provider API keys are encrypted with AES-GCM through the Android Keystore and
  are never written to Room, logs or backups.
- Request logs store only redacted summaries and are opt-in.
- Backups are passphrase-protected AES-256 archives; restore validates archive
  paths and versions before replacing local data.
