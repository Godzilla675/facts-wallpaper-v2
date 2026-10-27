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

## Phase P5 — Live Wallpaper, Rotation, Backup, Diagnostics, Onboarding

- **Invariant 6 Live Wallpaper Engine (`:core:wallpaper`)**: `FactsLiveWallpaperService` strictly never accesses network, AI, PDF, or WebView. Draws pre-rendered PNG bitmaps from `CardRenderCache` onto `SurfaceHolder` canvas within `< 16ms`, fitted/center-cropped, or renders a clean typographic fallback card ("Facts Wallpaper · Import a PDF to start studying") on `#0F1419` if the cache is empty on fresh install. Safely honors clock-safe (26%) and gesture-bar (8%) insets.
- **Cooldown & Rotation Engine (`RotationReducer`, `WallpaperRotationController`, `WallpaperRotationScheduler`)**: Enforces a minimum 15-second cooldown between unlock rotations (`Intent.ACTION_USER_PRESENT` via runtime non-exported receiver) so rapid screen toggles never skip unread study cards. Interval schedules advance on visibility and enqueue periodic WorkManager jobs (`WallpaperRotationWorker`, >= 15 min).
- **Static System & Lock Screen Wallpaper (`StaticWallpaperExporter`)**: Direct bitmap export to system home screen, lock screen, or both via `WallpaperManager.setBitmap` with `FLAG_SYSTEM` and `FLAG_LOCK`. The Wallpaper UI explicitly details OEM lock-screen live wallpaper constraints and directs users to static lock export when needed.
- **Encrypted Local Backup & Restore (`:core:backup`)**: `BackupManagerImpl` exports a single `.fwbk` archive encrypted with Zip4j AES-256 (`AesKeyStrength.KEY_STRENGTH_256`) using a user-supplied passphrase (>= 8 chars). Exports all 15 Room tables to JSON while strictly scrubbing any sensitive fields (`SecretScrubber.containsSecretKeyMarker`), excluding API keys and preserving token metrics (`inputTokens`, `outputTokens`, `maxContextTokens`). Strict path-traversal protection rejects suspicious archive entries (`..`, leading `/` or `\`, `:\`). Single-transaction SQLite restore with child-first deletion and parent-first insertion guarantees rollbacks on any failure, followed by cache clearing and pre-render rescheduling.
- **Advanced Diagnostics, Prompt Inspector & Storage Manager (`:feature:settings`)**: Power users can inspect individual AI request logs in an interactive dialog (latency, token metrics, prompt summary, response snippet, errors), view token ledger aggregations, compact SQLite databases (`PRAGMA wal_checkpoint(FULL); VACUUM;`), clear caches, and execute encrypted backups and restores.
- **Onboarding (`:feature:onboarding`)**: Completed 5-step first-launch guided flow saving provider selection, template selection, and theme to DataStore, setting `onboardingCompleted = true` and navigating to the library screen.

## Phase P3: AI Layer, Outline, Facts, Provider Settings & Keystore

### Architecture & Parallel Work Boundaries
- To honor parallel-work isolation without waiting for P2 code to be merged, :core:data introduces DocumentTextRepository implemented by RoomDocumentTextRepository, which queries the existing Room database schema (imported pages/chunks) for document content.
- StructuredAiService and ProviderDiagnosticsService are defined as clean interfaces with default implementations (DefaultStructuredAiService, DefaultProviderDiagnosticsService) bound via Hilt.
- Json binding: Reused the singleton Json provided by :core:datastore rather than binding a duplicate in :core:ai.

### Security (F23)
- ApiKeyStore backed by AndroidKeystoreSecretCipher using Android Keystore AES/GCM/NoPadding (256-bit key alias \facts_wallpaper_api_key_v1). Fresh 12-byte IV per encryption with authenticated tag.
- SharedPreferences store only SHA-256 hashed profile IDs (api_key_ + base64url(sha256(profileId))). Keys are never stored in plaintext or Room.
- LogRedactor scrubs known credential prefixes and registered keys from all loggable output.

### AI Wire Contracts & Routing (Section 6 & 11)
- Ktor HTTP client with OkHttp engine, configurable timeouts, and full multi-format wire support: Gemini (generateContent API with contents, generationConfig, schema specification), OpenAI (chat completions & response format), Anthropic (messages API), and OpenAI-compatible providers (OpenRouter, DeepSeek, Qwen, Azure, custom).
- Schema adapter handles dialect translations across providers (Gemini OpenAPI subset, OpenAI json_schema, Anthropic prompt-based tool schemas).
- Reasoning models (e.g. o1, o3, deepseek-reasoner, r1): system prompts mapped to user messages where required; temperature suppressed; reasoning tokens parsed and billed.
- AiRetryPolicy: exponential backoff (2s, 4s, 8s) for 429/5xx, honoring Retry-After. 400/401/403 are non-retryable.
- Semantic TextChunker segments document text respecting page/paragraph/sentence boundaries with configurable token targets and overlap.
- Fact deduplication uses normalized token overlap / Jaccard similarity threshold (0.75) against existing facts in the lesson/document.
- Token cost ledger persisted to Room ai_usage_logs table via AiCostEstimator with per-model pricing catalog.

### Durable Generation Jobs (WorkManager)
- OutlineGenerationWorker and FactsGenerationWorker implemented as @HiltWorker durable jobs with progress tracking and retry support.
- Registered via HiltWorkerFactory in FactsWallpaperApplication.

## Phase P6a — Cross-Phase Integration

### Real AI Template Generation (`:feature:templates`)
- Replaced the parallel-development `LocalTemplateAiGenerator` binding with `DefaultTemplateAiGenerator` backed by `StructuredAiService.generateTemplate` (using `AiTask.TEMPLATE_GENERATION` and JSON schema constrained generation).
- Output is validated using `CardTemplateCodec.validate(...)`. Unconfigured or failing providers raise `AiException`, which `TemplatesViewModel` traps and displays as a user-facing error dialog/snackbar without fallback to fake output.

### Formula Verification Tier-2 Binding (`:core:render`)
- Implemented `StructuredAiFormulaVerifier` in `:core:render:verify`, bound into `@IntoSet FormulaAiVerifier` within `RenderModule`.
- Verifies LaTeX formulas via `StructuredAiService.verifyFormula`. If network or provider credentials fail, it returns `null` so formula verification status remains `PENDING` rather than falsely passing or failing.

### AI-Suggested Collections (`:feature:collections`)
- Added `suggestCollections` API to `StructuredAiService` using structured JSON schema output and routed to `AiTask.FACTS`.
- `CollectionsViewModel.generateAiCollections()` sends active fact summaries to the AI provider, which clusters them into thematic collections. The ViewModel persists accepted collections with real references to the grouped facts.

### Invariant-6-Safe Live Wallpaper Canvas Text Fallback (`:core:wallpaper`)
- In `FactsLiveWallpaperService`, when the bitmap render cache does not yet contain a pre-rendered card for the current active fact, the engine retrieves the current `Fact` directly via `WallpaperRotationController.getCurrentFact()`.
- Renders the actual fact title, body, and source reference using Android `StaticLayout` directly onto the Canvas with clock and navigation bar insets honored, satisfying the spec fallback requirement without violating Invariant 6 (no network/AI/PDF/WebView). The generic branded message is reserved strictly for when zero facts exist in the database.

### End-to-End Pipeline & Cache Pre-Render Bridging
- Created `CardPreRenderNotifier` in `:core:common`, implemented by `CardPreRenderScheduler` in `:core:render`, and injected into `FactsGenerationWorker` and `FactsViewModel`. Generating facts automatically triggers background card pre-rendering for the rotation pool.
- `RoomDocumentTextRepository` in `:core:data` now delegates directly to `DocumentRepository.getChunks` and compiled text to eliminate duplication between P2 and P3 text access.
- `OutlineScreen` header now provides an active navigation button to open the `FactsScreen` whenever facts have been extracted for the document.
- Stubs and "not yet available" placeholder strings across onboarding and advanced settings have been replaced with real functional bindings and explanatory copy.

## Phase P6b — Hardening, Accessibility and CI

### String resources and localization
- Remaining hardcoded user-facing labels were moved to module string resources:
  advanced log detail metadata and empty values, provider API-key optional
  labels, fact source-reference mode chips, and the outline cost estimate.
- Technical exception text from provider diagnostics is still passed as the
  `%2$s` detail argument of a localized failure message. Fully localizing
  provider/network diagnostics would require a message-code layer in
  `:core:ai`; this is intentionally deferred rather than adding a second
  user-facing string system.

### Lint and manifests
- `PropertyEscape` is disabled in `:app` because the warning is raised only for
  the machine-generated, git-ignored `local.properties` SDK path, not for
  source code.
- `:core:pdf` now declares the WorkManager foreground `SystemForegroundService`
  with `foregroundServiceType="dataSync"` so the module lints independently.
- `:core:wallpaper` declares `SET_WALLPAPER` in its own manifest instead of
  relying on manifest merging from `:app`.

### Testing
- Added `JsonRepairServiceTest` covering the single repair pass, schema-bound
  prompt construction, `temperature = 0.0`, the 1,024-token floor, larger token
  budgets and `repairTriggered` log metadata.
- Compose UI coverage remains Robolectric-based (`LibraryScreenTest` plus
  feature ViewModel tests). Roborazzi golden screenshots and the full
  seven-flow Hilt end-to-end suite from spec §10.2 are not implemented; this is
  recorded as a remaining release-acceptance gap.

### CI
- Added `.github/workflows/android-ci.yml` with a tracked-file secret scanner
  for Google API keys, OpenAI-style keys and hardcoded bearer tokens, followed
  by `lintDebug testDebugUnitTest assembleDebug`.

## Phase P7.1 — Reasoning Model Output Budgets

- Provider profiles now persist a separate maximum output-token budget (default
  8,192; minimum 1,024), independent of context-window size. Structured requests
  start at half that budget and make one doubled-budget retry for truncated JSON
  when the profile permits it.
- Connection diagnostics request 1,024 output tokens and accept either normal
  response text or parsed reasoning text. Empty responses ending in `length`
  direct users to raise the output budget or disable thinking in advanced
  parameters.
- OpenAI-compatible responses keep `reasoning` fields and `<think>` blocks out
  of structured content while including reported reasoning tokens in usage.

## Phase P7.2 — Advanced Provider Parameters

- Room schema version 3 adds per-profile `temperature`, `topP`,
  `reasoningEffort`, and `rawRequestJson`; the existing `customHeadersJson`
  field continues to hold editable extra headers. Migration 2→3 preserves
  existing headers and supplies nullable typed settings plus an empty override
  object.
- Every HTTP request-body builder passes its provider-shaped body through the
  shared override helper. Typed values are mapped to OpenAI chat/Responses,
  Anthropic thinking/output configuration, and Gemini `thinkingConfig`; raw
  JSON objects are recursively merged last, with arrays/scalars replaced.
  Custom headers are applied to all HTTP provider clients, while required
  authentication headers take precedence.
- The provider editor exposes typed sampling/reasoning controls, editable
  header rows, and a live-validating monospace raw JSON editor with the four
  requested snippets. It flags credential-like values without persisting API
  keys in a separate store; the existing secure API-key field remains the
  supported place for credentials.
