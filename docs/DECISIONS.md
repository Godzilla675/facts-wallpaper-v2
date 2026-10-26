# Facts Wallpaper v2 — Phase P1 & P2 decisions

This document records the choices made while delivering Phase P1 (Foundation) and
Phase P2 (PDF Import, Inspection, Extraction & OCR) of
`Facts Wallpaper Complete Feature Specification.md`. The specification remains the
authority; where this document and the spec disagree, the spec wins.

## Scope delivered

Phase P1 establishes a compiling, tested, installable foundation:

- 23 Gradle modules: the 21 modules named by the spec plus `:core:common` and
  `:core:data` for shared dispatchers/qualifiers and repository implementations.
- Convention plugins and a version catalog; no module repeats SDK/compiler setup.
- `:core:model` with the domain models and pure filter/count types.
- Room database with all 17 entities, 9 DAOs, foreign keys, indices, and an
  exported version-1 schema.
- DataStore-backed global preferences.
- Design system and 16 shared Compose components, including the adaptive app
  scaffold, empty state, cards, badges, previews, and settings rows.
- Room/DataStore-backed repositories used directly by feature ViewModels.
- `:app` with Hilt, the 11 type-safe destinations, bottom-tab state restoration,
  manifest entry points, and PDF share/view intent handling.
- One real `@HiltViewModel` + `StateFlow<UiState>` per feature screen.
- Focused JVM/Robolectric unit tests for filters, converters, database behavior,
  preference mapping, mappers, rotation-pool semantics, formatters, start routing,
  and route serialization.

Phase P2 delivers `:core:pdf`, `:core:ocr`, durable WorkManager jobs, and the
complete `:feature:library` + `import_pdf` workflow:

- `:core:pdf`:
  - Multi-source import (`DefaultPdfImportRepository`, `PdfFileStore`,
    `HttpPdfUrlDownloader`, `PdfUrlPolicy`): SAF picker (`ACTION_OPEN_DOCUMENT`),
    share/view intents, and strict HTTPS-only URL download with redirect scheme
    verification, MIME/magic-byte (`%PDF-`) checks, and a 100 MB streaming cap.
  - Single-pass copy into app-private staging storage with SHA-256 hashing
    (`PdfHashing`, `DuplicateHashDetector`) and duplicate policy handling
    (`OPEN_EXISTING`, `REPLACE_EXISTING`, `IMPORT_AS_COPY`, `ASK`).
  - Local PDF inspection (`PdfBoxAnalyzer`, `PdfBoxDocumentReader`) using
    `com.tom-roush:pdfbox-android:2.0.27.0` with per-page `PDFTextStripper`
    character counting, password-protected PDF detection, and deterministic
    processing-route classification & cost estimation (`PdfRouteClassifier`,
    `PdfCostEstimator`, `PdfPageRange`, `PageRangeInput`).
  - Local text extraction (`PdfTextExtractionProcessor`), direct-PDF sub-PDF
    building (`PdfDirectPdfProcessor`, `PdfSubPdfBuilder`), and `PdfRenderer`
    page rasterization (`PdfRendererPageRenderer`, 2.5x scale capped at 2560 px
    longest edge, white opaque background).
- `:core:ocr`:
  - Bundled ML Kit Text Recognition v2 (`MlKitOcrEngine` for Latin, Chinese,
    Devanagari, Japanese, Korean) and downloadable checksum-verified Tesseract 5
    LSTM (`TesseractOcrEngine` via `cz.adaptech.tesseract4android:tesseract4android:4.7.0`
    and `DefaultOcrModelManager` for `ara`, `rus`, `ukr` from `tessdata_fast`
    pinned commit `65727574dfcd264acbb0c3e07860e4e9e9b22185`).
  - Page-by-page checkpointed OCR (`DefaultOcrPipeline`, `OcrCheckpointResolver`,
    `DefaultOcrTextFileCompiler`) storing `ocrLastStartedPage` before rasterizing
    and atomically committing `ocrLastCompletedPage` + `DocumentChunk` in Room,
    restarting any interrupted page from scratch while skipping completed pages
    and preserving >= 40-char digital text pages unless `forceFullLocalOcr` is set.
  - Deterministic `.txt` compilation (`filesDir/ocr_text/{docId}.txt`) with
    stable `# Document` / `# Pages` / `# OCR Model` headers and
    `--- [PAGE N] ---` delimiters.
- Durable WorkManager jobs (`PdfProcessingWorker`, `DocumentProcessingScheduler`,
  `PdfNotifications`, `OcrControlReceiver`) using `@HiltWorker` and foreground
  `dataSync` notifications with Pause/Cancel actions.
- `:feature:library` (`LibraryScreen`, `LibraryViewModel`, `ImportPdfScreen`,
  `ImportPdfViewModel`) wired end-to-end with live progress, duplicate dialog,
  20-page OCR override gate, language pack download manager, pause/resume/retry
  controls, rotation toggle, and cascade delete.

The build gate is:

```powershell
.\gw.ps1 assembleDebug testDebugUnitTest --console=plain
```

`:core:model` is a pure JVM module, so its tests use the plain `test` task:

```powershell
.\gw.ps1 :core:model:test --console=plain
```

## Toolchain

| Component | Version |
| --- | --- |
| Android Gradle Plugin | 8.13.2 |
| Gradle wrapper | 8.14.3 |
| Kotlin / KSP | 2.2.21 / 2.2.21-2.0.5 |
| Hilt | 2.58 |
| Compose BOM | 2025.12.01 |
| Navigation Compose | 2.9.6 |
| Room | 2.8.4 |
| DataStore | 1.1.7 |
| PDFBox-Android | 2.0.27.0 |
| ML Kit Text Recognition | 16.0.1 (Latin + Chinese/Devanagari/Japanese/Korean 16.0.1) |
| Tesseract4Android | 4.7.0 |
| compileSdk / targetSdk / minSdk | 36 / 36 / 33 |
| JVM target | 17 |

Hilt stays on 2.58 deliberately: Hilt 2.59+ refuses to apply with AGP below
9.0.0, while this project deliberately stays on AGP 8.13.2 because it is the
last AGP 8.x line that supports compileSdk 36 without an AGP 9 migration.

`gw.ps1` is the only supported Gradle entry point on this machine. It repairs
`JAVA_HOME`, `GRADLE_USER_HOME`, and the Android SDK variables, which otherwise
point at missing locations.

## Architecture

Dependency direction is `app -> feature -> core:data -> core:database/datastore
-> core:model`. Feature modules never touch Room entities or DataStore keys.
`:core:common` holds dispatchers/qualifiers; `:core:data` holds repository
interfaces and implementations. `:core:model` is a pure Kotlin/JVM module so its
filter and range logic can be tested without Android.

Every screen has a Hilt ViewModel that exposes immutable UI state through
`StateFlow`. One-shot user-visible events use `FeatureViewModel`'s
`UiMessageEffect`/`UiMessage` channel, with all copy in module `strings.xml`
files. No production screen contains sample or placeholder content; empty data
is represented by real empty states.

The app scaffold renders five tabs (Library, Facts, Collections, Wallpaper,
Settings). Top-level navigation uses `popUpTo(Library)`, `launchSingleTop`, and
`restoreState` so each tab keeps its back stack. Detail screens hide the bottom
bar. `AppNavHost` reacts to a shared PDF URI by navigating to the import screen;
the URI is reported back as consumed exactly once.

The start destination is captured from the first preferences emission with
`take(1)`. Completing onboarding writes `onboardingCompleted = true` and
navigates to the library; the captured start destination prevents the NavHost
from being recreated underneath that navigation.

## Persistence

### Room

- Database name `facts_wallpaper.db`, version 1, schema exported to
  `core/database/schemas`.
- Enum-like columns are stored as their stable enum names (`String`), matching
  the schema in the spec. `FactsConverters` converts only aggregate/typed
  projections.
- `MIGRATIONS` is intentionally empty for version 1. There is no destructive
  fallback; future schema changes must add a migration.
- Foreign keys encode the real lifecycle: deleting a document cascades its
  settings, chunks, lessons, facts, and tag/collection/folder cross-references;
  deleting a lesson sets `facts.lessonId` to null; deleting a fact cascades its
  tags and cross-references.
- `PdfDocumentDao.commitOcrPage` and `insertDocumentWithSettings` are
  transactional, so OCR checkpoints and settings cannot be half-written.
- AI diagnostics logs are capped at the newest 50 rows via
  `insertLogAndPrune`.
- Normalized tag insertion trims, lowercases, drops blanks, and deduplicates.
- `FactDao.observeAllActiveOrdered` defines canonical rotation order: document
  import time, lesson `sortOrder` (nulls last), then fact creation time.

### DataStore

`DataStoreSettingsRepository` is the single writer for global preferences.
Two map-valued preferences (per-task provider overrides and per-task prompt
suffixes) are stored as JSON text but exposed as `Map<AiTask, String>`.
Preferences: onboarding completion, app theme, wallpaper theme, routing
strategy, task profile overrides, prompt suffixes, default OCR page limit,
advanced OCR engines, pre-render queue size, encrypted database flag, AI
diagnostics log, and double-tap wallpaper advance.

Numeric limits are clamped when written and when read (`1..500` OCR pages,
`1..100` pre-render items). Unknown enum names fall back to the documented
defaults instead of crashing after a downgrade or manual data edit.

### Rotation pool semantics

`RoomRotationPoolRepository` is the single source of truth used by the library
banner, collections summary, wallpaper settings, and (later) the renderer:

1. Start with active facts only.
2. If at least one accepted, rotation-enabled collection or rotation-enabled
   folder exists, use the union of facts explicitly assigned to those sources.
   In this mode document rotation flags are intentionally ignored: choosing a
   collection/folder is an explicit override.
3. Otherwise use active facts whose document has `includedInRotation = true`.
4. Apply `onlyFavoritesInPool` last.
5. Keep canonical sequential order; random order is applied by the wallpaper
   engine at read time, not by the pool query.

## UI and design system

- Theme modes: light, dark, AMOLED, and dynamic. App and wallpaper theme modes
  are independent and persisted.
- Shared components live in `core/ui/component`: adaptive scaffold, section
  cards, setting switches, metric pills, document/fact rows, empty state,
  inline banner, step progress, template/fact/diagram/formula previews, source
  badge, provider profile row, phone wallpaper mock-up, and lesson selector.
- `SourceReferenceFormatter` implements the `Book · Lesson · p. 42` rules from
  spec 9.3, including compact/page-only/hidden modes; all copy used inside it is
  parameterized for localization.
- Later-phase actions remain enabled where the spec expects an action. Instead
  of silently doing nothing, they show a precise "not yet available in this
  phase" snackbar (SQLCipher enable, backup export/import, AI suggestions and
  generation, static wallpaper apply, template creation).

## Live wallpaper

`:core:wallpaper` contains a real `WallpaperService` registered in the manifest,
with service metadata and a thumbnail. For P1 it draws a branded placeholder
canvas; card rendering, scheduling, double-tap advance, and the render cache are
Phase P5 work. The app launches the system live-wallpaper picker directly with a
fallback to the chooser, and shows a Toast if no picker is available.

## Deliberately empty modules

`:core:security`, `:core:ai`, `:core:render`, and `:core:backup` exist and are
wired into the app but contain no fake implementations. Their features (API-key
storage, AI client/routing, card rendering, backup/export) land in later phases.
No screen pretends these features work.

## Testing notes

- `:core:model`: `FactFilter` matching/status/combination behavior and density
  bounds; extraction-status parsing.
- `:core:database`: converter round-trips, document/settings CRUD, OCR page
  checkpoint transaction, tag normalization/cascades, document/lesson delete
  behavior, wallpaper singleton, diagnostics pruning, cross-reference cascades,
  and canonical active-fact ordering.
- `:core:datastore`: defaults, every preference round-trip, enum fallback,
  incremental map updates, clear-on-blank, and numeric clamping. The repository
  is tested through a real DataStore update path backed by an in-memory
  `DataStore<Preferences>` so the test is deterministic on Windows.
- `:core:data`: tag CSV parsing, every domain/entity mapper round-trip, unknown
  enum fallbacks, and rotation-pool integration against an in-memory Room
  database (documents, lessons, favorites, collections, folders, summaries).
- `:core:pdf`: SHA-256 hashing & `DuplicateHashDetector`, page-range parsing &
  clamping, deterministic route classification & token/cost estimation, URL
  validation & `HttpPdfUrlDownloader` streaming limits, and PDFBox synthetic
  PDF inspection/extraction/sub-PDF building.
- `:core:ocr`: checkpoint/resume resolution, language catalog & SHA-256
  manifest verification, deterministic `.txt` rendering, and `DefaultOcrPipeline`
  checkpointed execution/interrupted-page restart/digital-text preservation.
- `:feature:library`: `LibraryViewModel` search/delete/pause/resume and
  `ImportPdfUiState` route/override/language readiness rules.
- `:core:ui`: source-reference formatting and metric/byte/token/cost/percent
  formatting, including locale-stable output.
- `:app`: onboarding-vs-library start routing and all 11 type-safe routes,
  including optional arguments.

## Phase P4 — Card Rendering, Templates & Collections

- **Bundled offline renderer assets (`:core:render`)**: KaTeX (`katex.min.js`, `katex.min.css`, and all 19 `.woff2` font files), Mermaid (`mermaid.min.js`), and TikZJax (`tikzjax.js`, `tex.wasm`, `tex-core.gz`, `fonts.css`) are bundled under `core/render/src/main/assets/renderer/` and served strictly through `WebViewAssetLoader` (`https://appassets.androidplatform.net/assets/renderer/*`) with all external/network loads blocked (`403 Blocked`). If TikZ WASM compilation times out on a constrained WebView, `tikz.html` falls back to a clean inline SVG diagram stage as permitted by spec §7.3.
- **Two-tier formula/diagram verification (`FormulaVerificationService`)**: Tier 1 runs `DiagramValidator` deterministically in `:core:render`. Tier 2 is exposed via the optional Hilt `@Multibinds Set<FormulaAiVerifier>` port so `:core:ai` (P3) can contribute its AI verifier without coupling `:core:render` to P3 internals during parallel development.
- **Template AI generation & Smart Collections during parallel work**: `TemplateAiGenerator` and `CollectionsViewModel.generateAiCollections` provide deterministic local implementations backed by real Room data and validated by `CardTemplateCodec` so `:feature:templates` and `:feature:collections` work completely in isolation from P3 (`:core:ai`) and offline.