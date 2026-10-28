# Spec Compliance Audit (v2.4.0)

Audited against `Facts Wallpaper Complete Feature Specification.md` at the end of M3.
Statuses are deliberately strict: **Done** means the behaviour is implemented and wired
into the app; **Partial** means a visible part is missing or reduced; **Missing** means
the spec item is not implemented. Files listed are the primary implementation, not an
exhaustive list.

## F1–F23 Feature Specification

| Spec section | Requirement | Status | File(s) / notes |
| --- | --- | --- | --- |
| F1 | 5-step onboarding: welcome/hero preview, BYOK provider + key + test, first PDF, theme/template, launch wallpaper; skip paths; Keystore badge | Done | `feature/onboarding/OnboardingScreen.kt`, `OnboardingViewModel.kt`; all 8 provider presets, local picker + HTTPS URL shortcut, live-preview card, `ACTION_CHANGE_LIVE_WALLPAPER` |
| F2 | SAF local import with SHA-256 copy, HTTPS-only URL import with magic-byte + 100 MB cap, duplicate sheet (open/replace/copy) | Done | `feature/library/ImportPdfScreen.kt`, `ImportPdfViewModel.kt`; `core/pdf/PdfFileStore.kt`, `PdfUrlDownloader.kt`, `PdfUrlPolicy.kt` |
| F3 | Inspection metadata, deterministic route classification, cost preview with Local/OCR vs Direct PDF toggle | Done | `core/pdf/PdfAnalyzer.kt`, `PdfRouteClassifier.kt`; `feature/library/ImportPdfScreen.kt` (`CostPreview`, direct-PDF switch) |
| F4 | Page-by-page text extraction with page provenance and optional page range | Done | `core/pdf/PdfBoxDocumentReader.kt`, `PdfTextExtractionProcessor.kt`, `PdfPageRange.kt`; range fields in `ImportPdfScreen.kt` |
| F5 | Checkpointed local OCR, exact interrupted-page restart, ML Kit English + multilingual packs, SHA-256 pack download, compiled `.txt` handoff, >20-page advisory | Done | `core/ocr/*`; OCR manager now exposed in Advanced Settings with progress/install/delete; language catalogue covers Latin, Chinese, Japanese, Korean, Devanagari (ML Kit) plus downloadable Arabic/Russian/Ukrainian Tesseract packs |
| F6 | Hierarchical outline generation, curation (select, edit, merge, split, add/delete), density + formula/diagram toggles, retry failed | Done | `feature/outline/OutlineScreen.kt`, `OutlineViewModel.kt`, `OutlineLessonPolicy.kt`; `core/ai/StructuredAiService.kt` |
| F7 | Strict fact extraction schema, semantic chunking with overlap + similarity dedupe, one JSON-repair pass | Done | `core/ai/AiSchemas.kt`, `TextChunker.kt`, `StructuredAiService.kt`; word-bigram Jaccard dedupe at the documented 0.85 threshold |
| F8 | Offline KaTeX, two-tier formula/diagram verification, failed items fall back to TEXT + "Needs Review" badge, Advanced re-verify action | Done | `core/render/verify/*`, `core/render/diagram/DiagramValidator.kt`; `core/ui/component/FactListItemCard.kt`; Advanced Settings "Re-verify all formulas & diagrams" iterates every fact and reports counts |
| F9 | Mermaid, TikZ, SVG and HTML canvas diagrams, CSP sandbox, PNG cache | Done | `core/render/diagram/OfflineWebViewDiagramRenderer.kt`, `DiagramValidator.kt` (HTML_CANVAS markup is validated and rendered as a diagram source) |
| F10 | Fact filters (document/lesson/search/status/tag), active + favorite toggles, inline edit with preview, guided + one-tap regenerate, provenance modes | Done | `feature/facts/FactsScreen.kt`, `FactsViewModel.kt`; `core/ui/component/FactListItemCard.kt` |
| F11 | Smart views, user folders, tags, AI-suggested collections, rotation pool builder + live counter | Done | `feature/collections/CollectionsScreen.kt`, `CollectionsViewModel.kt`; `core/data/repository/RoomRotationPoolRepository.kt` |
| F12 | 8 built-in templates with the specified visual archetypes | Done | `core/render/template/BuiltInTemplates.kt`, `CardBitmapRenderer.kt` |
| F13 | Duplicate/customise templates (fonts, scale, alignment, padding, radius, border, background, accent, footer) and AI template generator with preview | Partial | `feature/templates/TemplatesScreen.kt`, `TemplateDraft.kt`, `TemplateAiGenerator.kt`; native JSON customisation and AI generation work. Custom templates using `TemplateEngineKind.HTML_CANVAS` are stored and validated but rendered only through the native DSL fallback, because the rasterizer has no WebView-template pipeline. |
| F14 | Deterministic cache key, proactive pre-render queue, last-known-good fallback | Done | `core/render/cache/RenderCacheKey.kt`, `CardRenderCache.kt`; `CardPreRenderWorker.kt`, `CardPreRenderEngine.kt` |
| F15 | Live wallpaper service with configurable clock-safe inset, static export to home/lock/both, periodic static updates | Done | `core/wallpaper/FactsLiveWallpaperService.kt`, `StaticWallpaperExporter.kt`, `WallpaperRotationScheduler.kt`; `core/render/export/CardExportService.kt` |
| F16 | On-unlock, interval and manual modes; cooldown, sequential + no-repeat shuffle ordering, quiet hours, battery-saver interval | Done | `core/wallpaper/WallpaperRotationController.kt`, `RotationReducer.kt`, `WallpaperRotationWorker.kt`; tile, widget, shortcut, notification and double-tap advance paths |
| F17 | 8 first-class providers, multiple profiles, connection test, task overrides | Done | `core/ai/ProviderPresets.kt`, `core/ai/client/*`, `feature/settings/ProviderSettingsScreen.kt`; Azure uses its deployment URL + `api-version`, custom compatible profiles support headers/raw JSON |
| F18 | Capability-aware router with CHEAPEST/BALANCED/HIGHEST_QUALITY/MANUAL, direct-PDF and JSON-capability requirements | Done | `core/ai/AiRouter.kt`, `StructuredAiService.kt`; task overrides persisted in DataStore |
| F19 | Pre-flight estimator, post-flight ledger with token/cost parsing, dashboard by today/30 days/all time/provider/document | Done | `core/ai/CostEstimator.kt`, `AiCallService.kt`; `feature/settings/UsageBreakdown.kt` now shared by Provider and Advanced settings (includes token totals per profile/PDF) |
| F20 | Light/dark/AMOLED/dynamic themes, independent app vs wallpaper theme | Done | `core/designsystem/theme/*`; app theme default is now `DARK` per §5.4 while wallpaper default stays `AMOLED`; `SYSTEM` is retained as an extra option |
| F21 | AES-256 `.fwbk` backup, manifest + every required table, secrets stripped, path-traversal guard, transactional restore, cache rebuild, PDF re-link | Done | `core/backup/BackupManagerImpl.kt`, `SecretScrubber.kt`; M3 adds `PdfRelinkService` plus Library "Source PDF not on device → Re-link PDF" with SHA-256 verification |
| F22 | Prompt inspector, per-task prompt suffix with reset, storage breakdown + clear/compact/re-verify, encrypted DB toggle | Done | `feature/settings/AdvancedSettingsScreen.kt`, `AdvancedSettingsViewModel.kt`; `core/security/DatabaseEncryptionStore.kt`, `core/database/DatabaseEncryptionMigrator.kt`; OCR language pack manager added in M3 |
| F23 | No telemetry/backend, Keystore AES-GCM API keys, log redaction | Done | `core/security/AndroidKeystoreApiKeyStore.kt`, `SecretCipher.kt`, `LogRedactor.kt`; manifest has no analytics/crash SDKs |

## Section 5 — Data model and persistence

| Spec section | Requirement | Status | File(s) / notes |
| --- | --- | --- | --- |
| 5.1 | Core enumerations | Done | `core/model/Enums.kt`, `Document.kt`, `Fact.kt`, `Provider.kt`, `Collection.kt` |
| 5.2 | 17 Room entities, WAL, no destructive fallback | Done | `core/database/entity/*` (17 `@Entity` classes); `FactsDatabase.kt` registers migrations 1→6 with no destructive fallback |
| 5.3 | Required DAOs incl. rotation, usage, cache | Done | `core/database/dao/*`; repository wrappers in `core/data/repository/*` |
| 5.4 | DataStore preference schema | Done | `core/datastore/AppPreferences.kt`, `DataStoreSettingsRepository.kt`; all required keys present, plus later M1–M2 preferences; `appThemeMode` default corrected to `DARK` |

## Section 6 — AI contracts

| Spec section | Requirement | Status | File(s) / notes |
| --- | --- | --- | --- |
| 6.1 | Universal `AiClient` request/response model | Done | `core/ai/AiClient.kt`, `AiCallService.kt`, `AiPorts.kt` |
| 6.2 | October 2026 provider presets | Done | `core/ai/ProviderPresets.kt`; model IDs are data, editable in the profile editor |
| 6.3 | Provider wire contracts for Gemini, Anthropic, OpenAI Responses/Chat, Azure, compatible | Done | `core/ai/client/GeminiClient.kt`, `AnthropicClient.kt`, `OpenAiClient.kt`, `OpenAiCompatibleClients.kt`, `ChatCompletionsSupport.kt`; image blocks, tool calls and reasoning fields covered by `WireFormatTest` |
| 6.4 | Router scoring formula | Done | `core/ai/AiRouter.kt`; `AiRouterTest` |
| 6.5 | Semantic chunker and fact dedupe | Done | `core/ai/TextChunker.kt`; page/paragraph boundaries, 600-char overlap, bounded aggregation, Jaccard similarity ≥0.85 |
| 6.6 | Verbatim schemas/prompts for all six tasks incl. one repair pass | Done | `core/ai/AiSchemas.kt`, `StructuredAiService.kt`, `SchemaValidator.kt`, `SchemaAdapter.kt`; `JsonRepairServiceTest` |

## Section 7 — Rendering

| Spec section | Requirement | Status | File(s) / notes |
| --- | --- | --- | --- |
| 7.1 | Native JSON template DSL | Done | `core/render/template/CardTemplateSpec.kt`, `CardTemplateCodec.kt` |
| 7.2 | Exact configuration of the 8 built-ins | Done | `core/render/template/BuiltInTemplates.kt` |
| 7.3 | Offline WebView LaTeX/Mermaid/TikZ rasterizer | Done | `core/render/diagram/OfflineWebViewDiagramRenderer.kt`; bundled assets only |
| 7.4 | Deterministic sanitizer | Done | `core/render/diagram/DiagramValidator.kt`; entity decoding, script/event rejection, balanced delimiter checks |
| 7.5 | Bitmap rasterizer + content-addressed cache | Done | `core/render/card/CardBitmapRenderer.kt`, `cache/RenderCacheKey.kt`, `cache/CardRenderCache.kt` |

## Section 8 — Wallpaper engine

| Spec section | Requirement | Status | File(s) / notes |
| --- | --- | --- | --- |
| 8.1 | Live wallpaper service, insets, draw loop | Done | `core/wallpaper/FactsLiveWallpaperService.kt`, `core/wallpaper/src/main/AndroidManifest.xml` |
| 8.2 | Rotation controller and cooldown rules | Done | `core/wallpaper/WallpaperRotationController.kt`, `RotationReducer.kt`; unit coverage extended in M4 |

## Section 9 — Design system and UI

| Spec section | Requirement | Status | File(s) / notes |
| --- | --- | --- | --- |
| 9.1 | Scholar's Slate palettes and semantic fact colors | Done | `core/designsystem/theme/Color.kt`, `Theme.kt` |
| 9.2 | Typography, shapes, spacing, motion tokens | Done | `core/designsystem/theme/Type.kt`, `Shape.kt`, `Spacing.kt`, `Motion.kt` |
| 9.3 | 16 required shared components | Done | `core/ui/component/*` (17 components incl. `MetricPill`, `SourceBadge`, `StepProgressHeader`, `DocumentCard`, `FactListItemCard`, preview boxes, state panels) |
| 9.4 | 11-route screen blueprint + navigation contract | Done | `app/navigation/AppRoute.kt`, `AppNavHost.kt`; all canonical destinations plus Study and PDF viewer |

## Section 10 — Testing and CI

| Spec section | Requirement | Status | File(s) / notes |
| --- | --- | --- | --- |
| 10.1 | Unit/integration tests for models, pricing, chunking, reducer, AI, repositories, DB, ViewModels | Done | See `docs/TESTING.md` for the current suite (M4 adds Study, rotation controller and scheduler coverage) |
| 10.2 | Roborazzi screenshot tests and Compose UI tests | Partial | Robolectric Compose tests exist (`LibraryScreenTest`, `FactsScreenTest`); no Roborazzi dependency/goldens. Goldens need a recorded reference set and a stable host renderer, so they are tracked as remaining work rather than faked with empty assertions. |
| 10.3 | CI workflow with secret-leak gate | Done | `.github/workflows/android-ci.yml`; builds, unit tests, lint and a secret scan |

## Section 11 — Cross-cutting rules

| Spec section | Requirement | Status | File(s) / notes |
| --- | --- | --- | --- |
| 11.1 | Model IDs are data | Done | `ProviderPresets.kt`; profile editor overrides model and base URL |
| 11.2 | Provider schema dialect adapter | Done | `core/ai/SchemaAdapter.kt`, `SchemaValidatorTest` |
| 11.3 | Reasoning/thinking model controls | Done | `ProviderProfile.reasoningEffort`, raw request JSON overrides; `RequestParameterOverrides.kt` |
| 11.4 | Direct-PDF limits | Done | `PdfRouteClassifier.kt`, `PdfDirectPdfProcessor.kt`, block reasons surfaced in import UI |
| 11.5 | AI error taxonomy, retries, durable jobs | Done | `AiException.kt`, `AiRetryPolicy.kt`, `WorkManager` workers |
| 11.6 | Precise rotation pool resolution | Done | `RoomRotationPoolRepository.kt`; favorites/due/collections/documents modes |
| 11.7 | Wallpaper platform constraints | Done | Foreground service type, `BIND_WALLPAPER` in core module manifest, static flags for home/lock/both |
| 11.8 | Rendering threading rules | Done | Renderers use `FactsDispatchers.io`/default; no WebView on wallpaper surface |
| 11.9 | Manifest permissions | Done | `app/src/main/AndroidManifest.xml`; only INTERNET, network state, wallpaper, notifications, foreground data-sync; `allowBackup=false`, cleartext disabled; no unused storage/location/contacts permissions |
| 11.10 | Accessibility, localisation, 48 dp targets | Done | String resources for all user-visible text (no `context.getString` in composables), `SettingSwitchRow` minimum 48 dp, content descriptions on icon actions; Compose semantics/lint checked by `lintDebug` |
| 11.11 | Canonical navigation arguments | Done | `AppRoute.kt` matches spec arguments (documentId, lessonId, tag, status, dueOnly, templateId, factId, initialPage) |
| 11.12 | Whole-app definition of done | Partial | Functional flows, encrypted DB, backup/restore and release build are complete; real device validation of live wallpaper, SQLCipher migration and OCR remains environment-dependent (see below) |

## Deliberate limitations and follow-ups

1. **Roborazzi goldens (10.2).** Not added. The harness has no recorded golden
   baseline; generating one locally would make screenshot tests non-deterministic on
   other machines. Existing Robolectric Compose tests assert real semantics instead.
2. **HTML_CANVAS custom templates (F13).** The engine kind is accepted by the data model
   and sanitizer, but the bitmap rasterizer only executes `NATIVE_JSON`. Rendering
   arbitrary HTML templates would require a second templating pipeline and a separate
   security review; the UI therefore falls back to the native DSL.
3. **Cleartext LAN endpoints (F17/11.9).** `usesCleartextTraffic=false` is intentional:
   enabling `http://` for local Ollama/vLLM would weaken every profile. Users can point
   a custom profile at an HTTPS reverse proxy or Tailscale HTTPS endpoint; a runtime
   per-host cleartext allowlist is not possible with Android's static network security
   config without a global opt-in.
4. **Post-restore API-key prompt (F21).** Keys are never exported. The provider screen
   shows which profiles need a key and offers Test Connection, but there is no separate
   blocking prompt after restore. This is a UX choice, not a data gap.
5. **Robolectric vs device-only surfaces.** SQLCipher (`net.zetetic` native library),
   `PdfRenderer`, live wallpaper surfaces and ML Kit OCR cannot execute on the JVM; they
   are covered by manual emulator/device checks in `docs/TESTING.md`.

## Changes made during this audit (M3)

- Real SQLCipher mode: Keystore-wrapped passphrase, pre-Room migration, integrity check
  and honest fallback on failure; the Advanced Settings toggle now reflects the
  database format that is actually on disk.
- Advanced "Re-verify all formulas & diagrams" with cost warning and result counts.
- Usage breakdown by provider profile and PDF in both Settings screens.
- OCR language pack manager in Advanced Settings (download progress, checksum
  verification, install/delete).
- "Needs review" badge on failed formula/diagram facts.
- Source-PDF re-link for restored documents (SAF picker, SHA-256 check).
- Onboarding exposes all eight provider presets and a direct HTTPS URL shortcut.
- App theme default aligned with §5.4 (`DARK`).
