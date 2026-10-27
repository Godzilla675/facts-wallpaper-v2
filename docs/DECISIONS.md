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
