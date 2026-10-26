# Facts Wallpaper v2 — Phase P1 decisions

This document records the choices made while delivering Phase P1 (Foundation) of
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
  phase" snackbar (download/analyze, SQLCipher enable, backup export/import,
  AI suggestions and generation, static wallpaper apply, template creation).

## Live wallpaper

`:core:wallpaper` contains a real `WallpaperService` registered in the manifest,
with service metadata and a thumbnail. For P1 it draws a branded placeholder
canvas; card rendering, scheduling, double-tap advance, and the render cache are
Phase P5 work. The app launches the system live-wallpaper picker directly with a
fallback to the chooser, and shows a Toast if no picker is available.

## Deliberately empty modules

`:core:security`, `:core:pdf`, `:core:ocr`, `:core:ai`, `:core:render`, and
`:core:backup` exist and are wired into the app but contain no fake
implementations. Their features (API-key storage, PDF parsing, OCR engines, AI
client/routing, card rendering, backup/export) land in later phases. No screen
pretends these features work.

## Known gaps and honest limitations

- Import screens validate input and preserve the selected URI, but PDF
  download/copy/hash inspection and analysis are not implemented yet; the
  actions report that they are next-phase work.
- No built-in templates, lessons, facts, collections, or provider profiles are
  seeded. First runs correctly show empty states; templates appear after later
  phases seed them or the user creates custom ones.
- `Document.lessonCount` and `Lesson.factCount` are denormalized counters and
  can go stale; this phase does not install SQL triggers. Counters fetched by
  dedicated aggregate queries (collections, tags, pool) are live.
- The rotation pool is evaluated in memory from Room flows. At large libraries
  this should become a paginated/limited query; P1 favors correctness and
  testability.
- Static wallpaper apply, share/export, backups, encrypted databases, and the
  AI pipeline are visible as explicit next-phase actions only.
- Instrumented/Compose UI tests and screenshot tests are not part of P1; the
  current suite is focused JVM/Robolectric coverage.

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
- `:core:ui`: source-reference formatting and metric/byte/token/cost/percent
  formatting, including locale-stable output.
- `:app`: onboarding-vs-library start routing and all 11 type-safe routes,
  including optional arguments.


## Phase P3: AI Layer, Outline, Facts, Provider Settings & Keystore

### Architecture & Parallel Work Boundaries
- To honor parallel-work isolation without waiting for P2 code to be merged, :core:data introduces DocumentTextRepository implemented by RoomDocumentTextRepository, which queries the existing Room database schema (imported pages/chunks) for document content.
- StructuredAiService and ProviderDiagnosticsService are defined as clean interfaces with default implementations (DefaultStructuredAiService, DefaultProviderDiagnosticsService) bound via Hilt.
- Json binding: Reused the singleton Json provided by :core:datastore rather than binding a duplicate in :core:ai.

### Security (F23)
- ApiKeyStore backed by AndroidKeystoreSecretCipher using Android Keystore AES/GCM/NoPadding (256-bit key alias acts_wallpaper_api_key_v1). Fresh 12-byte IV per encryption with authenticated tag.
- SharedPreferences store only SHA-256 hashed profile IDs (pi_key_ + base64url(sha256(profileId))). Keys are never stored in plaintext or Room.
- LogRedactor scrubs known credential prefixes and registered keys from all loggable output.

### AI Wire Contracts & Routing (Section 6 & 11)
- Ktor HTTP client with OkHttp engine, configurable timeouts, and full multi-format wire support: Gemini (generateContent API with contents, generationConfig, schema specification), OpenAI (chat completions & response format), Anthropic (messages API), and OpenAI-compatible providers (OpenRouter, DeepSeek, Qwen, Azure, custom).
- Schema adapter handles dialect translations across providers (Gemini OpenAPI subset, OpenAI json_schema, Anthropic prompt-based tool schemas).
- Reasoning models (e.g. o1, o3, deepseek-reasoner, 
1): system prompts mapped to user messages where required; temperature suppressed; reasoning tokens parsed and billed.
- AiRetryPolicy: exponential backoff (2s, 4s, 8s) for 429/5xx, honoring Retry-After. 400/401/403 are non-retryable.
- Semantic TextChunker segments document text respecting page/paragraph/sentence boundaries with configurable token targets and overlap.
- Fact deduplication uses normalized token overlap / Jaccard similarity threshold (0.75) against existing facts in the lesson/document.
- Token cost ledger persisted to Room i_usage_logs table via AiCostEstimator with per-model pricing catalog.

### Durable Generation Jobs (WorkManager)
- OutlineGenerationWorker and FactsGenerationWorker implemented as @HiltWorker durable jobs with progress tracking and retry support.
- Registered via HiltWorkerFactory in FactsWallpaperApplication.
