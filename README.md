# Facts Wallpaper

An offline-first Android study app that turns PDFs into verified fact cards and
rotates them as a live or static wallpaper. AI extraction, outline detection,
fact generation, diagram generation, formula verification and template design
run through user-configured providers; everything generated is stored locally
and keeps working without a network.

## Features

- **Bring your own AI provider** — Gemini, Anthropic, OpenAI (Responses or Chat
  Completions), Azure OpenAI, OpenRouter, DeepSeek, Qwen and any
  OpenAI-compatible endpoint such as NVIDIA NIM or Ollama. Per-task routing,
  manual profile pinning, custom headers, raw JSON body overrides, reasoning
  controls and a model picker that fetches the live catalog.
- **On-device PDF pipeline** — import from the file picker, a share sheet or
  "Open with"; duplicate detection; embedded-text extraction; ML Kit OCR with
  optional Tesseract/PaddleOCR engines; page-range selection and route
  classification (text, scanned, mixed).
- **Outline → facts → cards** — bookmark-aware or chunked outline detection,
  editable/mergeable/splittable lessons, cancellable and resumable background
  generation with progress notifications, schema validation, one JSON repair
  pass, formula verification and diagram generation.
- **Vision mode** — optionally send rendered page images instead of extracted
  text for scanned or layout-heavy documents.
- **Curate and study** — search, filter, favorite, hide, edit, regenerate and
  export facts; Study Mode with swipeable cards, session counters and spaced
  repetition scheduling.
- **Wallpaper rotation** — live wallpaper service and static system/lock
  wallpaper export, manual FOTD/interval/battery-aware schedules, quiet hours,
  rotation pool from documents/collections/folders/tags, double-tap advance,
  Quick Settings tile and home-screen widget.
- **Rich card rendering** — built-in and AI-generated templates for text,
  formulas and diagrams, pre-rendered offline and cached.
- **Organization and export** — collections, folders, smart views and tags;
  Markdown, ZIP and PDF study exports through the Storage Access Framework.
- **Local-first settings** — encrypted API keys, usage/cost ledger, diagnostics
  logs, settings search, haptic toggle, theme/dynamic color, and encrypted
  daily/weekly backup to any SAF location including Google Drive.

## How PDFs reach the LLM

1. The PDF is copied into app storage and hashed for duplicate detection.
2. Text is extracted **on-device** page by page. Scanned pages are OCR'd
   locally (ML Kit, with optional Tesseract/PaddleOCR). No PDF bytes leave the
   device in text mode.
3. The outline stage sends bounded page ranges — never the whole book — and
   adapts chunk size to the selected model's context window. Long outlines are
   merged and deduplicated locally.
4. Generation sends only the selected lesson's pages. Providers that support
   native tool calling may request a missing page range through the `get_pages`
   tool, up to a bounded number of calls per job; other providers receive the
   lesson page text directly.
5. **Vision mode** renders the selected pages to bitmaps on-device and sends
   those images instead of text, for scanned or layout-critical documents.
6. The returned JSON is schema-validated, repaired once when needed, verified,
   then rendered into cards locally. API keys stay in the Android Keystore and
   request logs contain only redacted summaries.

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
- Backup destinations can be local or any document provider exposed by Android,
  including Google Drive, through the Storage Access Framework. Automatic
  backups are scheduled daily or weekly by WorkManager and may run later under
  Android's background-execution policy.

## How the AI pipeline works

PDF text extraction and OCR run on-device. The outline stage prefers embedded
PDF bookmarks; otherwise it makes bounded, chunked provider requests and merges
the results into chapter/lesson page ranges. After selecting lessons, the
generation worker sends each lesson's source pages to the configured fact
provider, verifies the returned facts, and repairs invalid content. Cards are
rendered offline with the bundled formula/diagram renderers, then saved in a
book collection and queued for pre-rendering. Generation and rendering can
resume through WorkManager. The current HTTP clients send the selected lesson
pages directly; provider-native `get_pages` tool calls are not yet wired.

### NVIDIA NIM and reasoning models

Add NVIDIA NIM as an **OpenAI-compatible** provider with base URL
`https://integrate.api.nvidia.com/v1`, the model's exact ID, and its API key.
Reasoning tokens consume the model's output-token budget, so a response may
contain little visible text or hit its length limit even when the prompt is
small. Keep the profile's max output tokens at an appropriate higher value
(default 8,192; minimum 1,024), and use the connection test's actionable
guidance if the model spends its budget thinking. Provider-specific reasoning
controls are under **Advanced parameters**; raw JSON body overrides are
deep-merged last and can be used for provider options such as
`{"reasoning_effort":"low"}` or disabling a provider's thinking template.
