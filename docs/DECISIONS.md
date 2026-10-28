## Phase P8.3 — Quality-of-Life, Study Mode, SAF Export, and Wallpaper Controls

- **Wallpaper Gesture Controls:** FactsLiveWallpaperService.FactsWallpaperEngine supports double-tap gestures via both onTouchEvent and onCommand(WallpaperManager.COMMAND_TAP) (<400ms delta), advancing rotation immediately via rotationController.advance() and drawing the next card without waiting for the scheduled rotation interval.
- **Study Mode with Spaced Repetition Counters:**
  - Added incrementGotItCount and incrementAgainCount queries in FactDao and FactRepository.recordReview(factId: String, gotIt: Boolean).
  - Added StudyModeSheet bottom sheet to FactsScreen allowing sequential review of cards with "Got it" and "Review again" counters, updating review counts in Room and summarizing session results upon completion.
- **SAF Export:**
  - Added Storage Access Framework Markdown export (CreateDocument("text/markdown")) in FactsViewModel / FactsScreen to export cards with lesson titles, content, LaTeX blocks, tags, and review statistics.
- **Library Search, Sorting, and Batch Delete:**
  - LibraryViewModel and LibraryScreen support sorting by recent date (DATE_DESC), title (TITLE_ASC), and pipeline progress (PROGRESS).
  - Added multi-document selection mode with select all, individual checkboxes, and batch deletion that cleans up database entries, associated files, and cancels active WorkManager tasks.
- **PDF Share / View Integration:** Verified intent filter in AndroidManifest.xml handling ACTION_VIEW and ACTION_SEND for application/pdf, routed from MainActivity directly into the import pipeline.

## Phase P9.1 — Finish P8 Gaps

- **ZIP and Multi-page PDF Export (`CardExportService`):**
  - Added `CardExportService` with `CardExportFormat` supporting `ZIP` (PNG card images rendered via `CardBitmapRenderer`), `PDF` (multi-page document rendered with Android `PdfDocument` at 1080x1920 with crisp canvas draw and font rendering), and `MARKDOWN`.
  - Accessible directly through SAF `ACTION_CREATE_DOCUMENT` from `FactsScreen` and `CollectionsScreen`.
- **Full-Screen Study Route with Spaced Repetition (`AppRoute.Study`):**
  - Replaced the bottom sheet with a dedicated full-screen Compose route `StudyScreen` hosting a swipeable `HorizontalPager`.
  - Added 3D perspective flip cards: front renders the full stylized card (LaTeX formula, diagram, source badge); back shows detailed review history and spaced repetition schedule.
  - "Got it" schedules the card into expanding intervals: 1d, 3d, 7d, 14d, 30d. "Review again" reschedules the card in 10 minutes. Unreviewed cards are due immediately.
  - Room schema version 6 adds `next_review_due_epoch_ms` with index on `facts` table via `MIGRATION_5_6`.
  - Added smart filter and toggle for "Due for review" cards.
- **Quick Settings Tile and Launcher Shortcuts:**
  - Implemented `NextFactTileService` (`TileService`) enabling quick advance of the live wallpaper rotation directly from Android Quick Settings.
  - Added static launcher shortcuts (`shortcuts.xml`): "Next fact" (`com.factswallpaper.app.ACTION_NEXT_FACT`) and "Import PDF" (`com.factswallpaper.app.ACTION_IMPORT_PDF`).
- **Encrypted Backup Restore via File Picker:**
  - Added `restoreBackupFromUri(Uri, String)` in `BackupManager` allowing direct file restore of encrypted `.fwbk` archives chosen by the user via SAF `OpenDocument`. Wired into `AdvancedSettingsScreen`.

## Phase P9.2 — Wallpaper & Daily Use

- **Fact of the Day Notification (`FactOfTheDayScheduler` & `FactOfTheDayWorker`):**
  - Configurable daily notification with user-chosen delivery time (default 08:00).
  - Tapping notification launches the app directly into the fact details.
  - Notification action buttons: "Next" (advances rotation and updates notification text) and "Got it" (records successful spaced-repetition review and dismisses notification) handled in `FactNotificationReceiver`.
- **Quiet Hours & Pause Rotation:**
  - Time window (e.g. 22:00 to 07:00, with support for midnight-crossing intervals) during which wallpaper rotation is suppressed.
  - Instant "Pause rotation" master toggle to halt periodic wallpaper changes without losing rotation state.
- **Battery Saver Awareness:**
  - Checks `PowerManager.isPowerSaveMode` when enabled; automatically doubles rotation interval during low-power mode to minimize battery and CPU impact.
- **Home-Screen Widget (`FactAppWidgetProvider`):**
  - Built an Android `AppWidgetProvider` displaying current fact card text and formatted source citation.
  - Interactive "Next" button on widget advances rotation and broadcasts `ACTION_FACT_UPDATED` to update all active widgets and live wallpaper in sync.
- **Rotation Source Picker:**
  - Added `RotationSource` enum: `ALL` (all cards from active documents), `COLLECTIONS` (specific collections/folders marked for rotation), `FAVORITES` (favorite cards only), `DUE_FOR_REVIEW` (spaced repetition review cards).
  - Integrated into `RoomRotationPoolRepository`, `AppPreferences`, `WallpaperViewModel`, and `WallpaperScreen`.

## Phase P9.3 — Library, Cards and Generation

- **Card Actions (Long-Press and Context Menu on Cards):**
  - Added 8-action contextual menu and long-press handler across `FactListItemCard` and `FactsScreen`.
  - Actions: favorite/pin toggle, hide from / restore to wallpaper rotation (`excluded_from_rotation`), inline text edit and immediate re-render, single-card AI regeneration, clipboard copy, share rendered card image via Android Sharesheet (`FileProvider`), jump to source PDF page, and delete.
- **Built-in PDF Page Viewer (`PdfViewerScreen` & `PdfViewerViewModel`):**
  - Full-screen viewer using `PdfRenderer` with pinch-to-zoom (1x–5x) and double-tap zoom/reset gestures.
  - Opens directly at the card's referenced source page with an interactive bottom page slider and step controls.
- **Duplicate PDF Detection on Import:**
  - Integrated SHA-256 content hashing on imported files in `DocumentRepository` / `ImportPdfViewModel`.
  - When an identical PDF has already been imported, prompts user with a dialog to open the existing document or re-import under a unique name.
- **Background Pipeline Controls & What's Happening Activity:**
  - Global pause and resume for WorkManager document extraction and lesson generation jobs via `DocumentProcessingScheduler.pauseAll()` and `resumeAll()`.
  - Added per-document "Regenerate failed lessons only" in `OutlineScreen` and `DocumentProcessingScheduler`.
  - Created standalone `WhatsHappeningActivity` and Compose route (`WhatsHappeningScreen`) displaying all running, queued, paused, and failed jobs with retry, cancel, pause-all, and resume-all controls. Reachable from foreground notification and Settings.
- **Undo Snackbar for Deletions:**
  - Implemented 5-second soft delete with Undo action for documents (Library), cards (Facts), and collections/folders (Collections). If not undone within 5 seconds, hard deletion is committed to the database and filesystem.

## Phase P9.4 ? Settings Search, Model Picker, Haptics and Polish

- **Settings search:** `SettingsSearchCatalog` lists 13 searchable entries across the provider screen (routing, task routing, usage, profiles) and the advanced screen (theme, pipeline, OCR, prompts, diagnostics, ledger, storage, backup). The filter requires every whitespace-separated token to match the localised title, summary or keyword list, so extra words narrow the result. Provider results scroll the existing `LazyColumn` to the matching section via `ProviderSection`; advanced results deep-link through `AppRoute.AdvancedSettings(section)` and `AdvancedSection.lazyListIndex()`.
- **Model picker:** `ProviderDiagnosticsService.fetchModels` now takes an optional `apiKeyOverride` so an unsaved draft key can be tested without writing it to the keystore. It calls the provider-specific catalog endpoint (Gemini `/v1beta/models?pageSize=200`, Anthropic `/models?limit=100`, Azure `/openai/models?api-version=`, others `{base}/models`), tolerates `data`/`models`/`value` wrappers, strips `models/` prefixes for Gemini, filters non-`generateContent` Gemini models and sorts by id. The provider editor shows a scrollable, filterable result list and falls back to free text with the error message.
- **Haptics:** `LocalHapticsEnabled` is provided once at the app root from `AppPreferences.hapticsEnabled` (default true) and consumed through `rememberHapticFeedback()`. Favorite/hide on fact cards, Study "Got it"/"Again" and Wallpaper "next fact" trigger a `KEYBOARD_TAP` haptic; the Advanced screen exposes the toggle.
- **Predictive back:** `android:enableOnBackInvokedCallback="true"` is set in the manifest. Library selection mode registers a `BackHandler` that clears the selection instead of leaving the screen.
- **Library coach hint:** one-time dismissible "Import -> outline -> generate" card at the top of the Library list, persisted through `setLibraryCoachHintDismissed`. Existing undo-delete behaviour is untouched.
- **Empty states:** Library, Facts, Favorites (filtered Facts), Outline and What's Happening render icon + message + primary action. What's Happening gained an "Open Library" action; Facts already deep-linked to the Library.

## Phase P10.1 ? Page-tool calling and M2 verification

- **Native `get_pages` tool:** `AiRequest.pageTool` opts a request into a native function declaration and `AiResponse.requestedPageRange` carries the parsed call. OpenAI Chat Completions (OpenAI, Azure OpenAI, OpenRouter, DeepSeek, Qwen, custom OpenAI-compatible) and Gemini implement the wire format; Anthropic is intentionally excluded because it already uses a forced `emit_result` tool for structured output. Unsupported providers fall back to the chunked direct-pages pipeline with a warning.
- **One request per tool round:** `StructuredAiService.generateFactsWithPageTool` sends the lesson pages plus previously fetched pages in a single request, includes prior tool results/rejections in the prompt, and returns `FactsGenerationResult.pageRequest` when the model calls `get_pages`. `GenerationOrchestrator` owns the loop (max 3 calls, max 5 pages per call) and validates ranges. The prompt tells the model not to re-request pages already included.
- **Context safety:** if the merged page text would exceed 90% of the profile context window, the service falls back to the chunked pipeline instead of failing or truncating source material.
- **Schema + tool fallback:** while a page tool is active the canonical JSON schema is embedded in the system prompt and provider-native `response_format`/`responseSchema` is omitted. If a model rejects tool declarations (400 mentioning tools/functions), the provider client retries once without tools so generation still completes.
- **Setting:** `pageToolCallsEnabled` defaults to true ("auto") and is exposed as Advanced ? Background Pipeline ? AI page tools. Disabling it restores the previous direct-pages behaviour. Cost estimates are only charged to the first tool round; later rounds report zero estimated cost so the orchestrator does not double count.
- **Notifications:** `POST_NOTIFICATIONS` was already requested during onboarding and before OCR imports; enabling the daily fact notification in Wallpaper settings now also requests it on API 33+. Quiet hours are enforced by `WallpaperRotationController` and `WallpaperRotationWorker`; the fact-of-the-day notification keeps its own schedule.
- **Tile/widget/shortcuts:** `NextFactTileService` (BIND_QUICK_SETTINGS_TILE), `FactAppWidgetProvider` (next-fact + refresh actions), `FactNotificationReceiver` (next/got-it) and the `shortcuts.xml` next-fact/import entries were verified already present in the manifest.
- **TODO sweep:** no `TODO`, `FIXME`, `TODO()` or `NotImplementedError` remain in `app/`, `core/` or `feature/`; grep hits for `todo` are false positives from `toDomain` and `toDouble`.
