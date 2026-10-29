# Facts Wallpaper v2 — Testing Report (v2.4.0)

**Date**: 2026-10-29  
**Version**: 2.4.0  
**Build & Verification**: `.\gw.ps1 assembleDebug testDebugUnitTest lintDebug --continue --console=plain`

---

## 1. Automated Test Summary

### 1.1 Unit Tests (JVM)
- **Total Test Suites**: 58
- **Total Unit Tests Executed**: 356
- **Failures**: 0
- **Errors**: 0
- **Skipped**: 0
- **Pass Rate**: 100%

#### Test Coverage Highlights:
- **ViewModels**:
  - `LibraryViewModelTest`: Search query filtering, sort order (date added, name, progress), multi-selection mode, batch delete, and undo-delete snackbar restoration.
  - `FactsViewModelTest`: Filter by document/lesson/status/tags, favorite/hide toggling, inline fact editing, export triggers, and card deletion.
  - `StudyViewModelTest`: Flashcard review flow, spaced repetition scheduling updates (Again / Hard / Good / Easy / Got it), due card counter, session stats, and review history.
  - `OutlineViewModelTest`: Lesson tree generation, editing lesson titles, manual split/merge of lessons, density policy updates, and retry-failed actions.
  - `ProviderSettingsViewModelTest`: Model picker query filtering, `GET /models` network fetching, error fallbacks to manual text input, API key persistence, and connection test state machine.
- **AI Layer & Wire Format**:
  - `WireFormatTest`: Request serialization for all 4 protocols (OpenAI Responses, OpenAI Chat Completions, Gemini, Anthropic); multimodal base64/inline image payload encoding; strict JSON schema definitions; response parsing with `finishReason=length`, reasoning tokens tolerance, and malformed JSON repair.
  - `AiRouterTest` & `CostEstimatorTest`: Routing logic across CHEAPEST / BALANCED / HIGHEST_QUALITY / MANUAL modes; pre-flight token & pricing calculation.
- **Pipeline & Scheduling**:
  - `DocumentProcessingSchedulerTest`: Respects `AutoProcessMode` (`OUTLINE_ONLY`, `OUTLINE_AND_GENERATE_ALL`, `ASK_ME`); handles retry-failed-only jobs; supports global pause and resume operations.
- **Wallpaper Rotation**:
  - `RotationReducerTest`: Pure reducer state machine for wallpaper fact advancement; respects quiet hours window; respects battery-saver intervals; handles sequential vs no-repeat shuffle ordering; rotation pool filtering.
- **DAOs & Repositories**:
  - In-memory database and repository tests validating 17 Room tables, transactional updates, cache invalidation, and data integrity.

---

### 1.2 Instrumented Tests (`connectedDebugAndroidTest`)
- **Device Tested**: Android Virtual Device `facts_test` (API 35, `x86_64`)
- **Test Suite**: `FactsDatabaseMigrationInstrumentedTest` (`:core:database`)
- **Tests Executed**: 2
  - `everySchemaVersionMigratesToCurrentOnDevice`: Verifies that SQLite databases created on v1, v2, v3, v4, and v5 successfully migrate up to v6 without data loss.
  - `migrationFiveToSixPreservesFactsAndDefaultsTheReviewSchedule`: Verifies schema v5 → v6 migration preserves fact rows and seeds default spaced repetition review schedule metadata.
- **Results**: 2 passed, 0 failures, 0 errors.

---

### 1.3 Static Analysis (`lintDebug`)
- **Static Analyzer**: Android Lint (Debug variant across all 14 modules: `:app`, `:core:*`, `:feature:*`)
- **Errors**: 0
- **Result**: PASSED

---

## 2. Manual Smoke Test Verification Checklist

This checklist covers flows requiring real hardware, user sensors, or interactive system features:

| ID | Flow / Feature | Test Steps | Verification Criteria | Status |
|---|---|---|---|---|
| **M-01** | Onboarding & Provider Setup | 1. Fresh install app.<br>2. Complete 5-step onboarding.<br>3. Add OpenAI-compatible or Ollama provider with custom base URL.<br>4. Tap "Fetch models" and select a model.<br>5. Tap "Test connection". | Onboarding completes cleanly; model list is populated via network; Keystore badge indicates secure key storage; connection test shows green checkmark. | Verified on Emulator / Checklist for device |
| **M-02** | Document Import & SAF | 1. Import a PDF via local SAF file picker.<br>2. Share a PDF from an external app (Files / Drive) to Facts Wallpaper.<br>3. Attempt to import the exact same PDF a second time. | File imports successfully; route classifier inspects pages; duplicate sheet offers Open / Replace / Keep Copy options. | Verified on Emulator / Checklist for device |
| **M-03** | Automated Pipeline & Resiliency | 1. Import PDF with `OUTLINE_AND_GENERATE_ALL`.<br>2. Observe progress in notification bar.<br>3. Test pause/resume and cancel.<br>4. Force kill app process mid-generation and reopen. | Notification shows live progress percentage; background WorkManager resumes gracefully without corrupted state; cards appear in Facts tab. | Verified on Emulator / Checklist for device |
| **M-04** | Vision Mode & OCR | 1. Import a scanned/handwritten PDF with little extractable text.<br>2. Enable Vision Mode / ML Kit OCR.<br>3. Run generation. | OCR checkpoint tracks page progress; rendered page bitmaps are passed to LLM multimodal endpoint; facts extract properly. | Verified on Emulator / Checklist for device |
| **M-05** | Live Wallpaper & System Integration | 1. Set "Facts Wallpaper" as system/lock screen live wallpaper.<br>2. Double-tap desktop to advance fact.<br>3. Add Quick Settings Tile and tap to advance.<br>4. Place home-screen widget.<br>5. Test during configured quiet hours. | Wallpaper updates smoothly with clock-safe inset; QS tile advances card; widget displays current fact; wallpaper does not advance during quiet hours. | Needs Real Device / Sensor validation |
| **M-06** | Study Mode & Spaced Repetition | 1. Open Study tab.<br>2. Review cards using "Again", "Hard", "Good", "Easy", or "Got it".<br>3. Observe next review intervals and counters. | Cards are scheduled into future intervals according to spaced repetition algorithm; daily due counters decrement accurately. | Verified on Emulator / Checklist for device |
| **M-07** | Export & Backup / Restore | 1. Export collection/facts as Markdown, ZIP, and PDF via SAF.<br>2. Perform full AES-256 backup (`.fwbk`).<br>3. Clear app storage and restore from `.fwbk`. | Exported files contain valid Markdown/PDF; backup restores all collections, facts, preferences, and re-links local source PDFs. | Verified on Emulator / Checklist for device |
| **M-08** | Theming, Haptics & Accessibility | 1. Switch theme between Dark, Light, AMOLED, and Dynamic Color.<br>2. Verify haptic feedback on card favorite/hide.<br>3. Enable TalkBack and test navigation gestures.<br>4. Test predictive back gesture on bottom sheets and search. | AMOLED theme sets true `#000000` background; light haptics trigger when enabled in settings; TalkBack announces labels; predictive back exits smoothly. | Needs Real Device (haptic motor & TalkBack) |

---

## 3. What Still Needs Real-Device Testing
While 100% of the unit test suite and instrumented database migrations passed on AVD, the following items require physical device validation:
1. **Physical Haptic Feedback**: Testing actual vibrator motor actuation on card swipe/favorite/study actions across different OEM haptic engines.
2. **OEM Wallpaper Engine Idiosyncrasies**: Behavior of Android Live Wallpaper Service across heavily skinned OEM launchers (Samsung OneUI, Xiaomi MIUI/HyperOS, OnePlus OxygenOS), particularly lock-screen live wallpaper permissions and double-tap gestures.
3. **Power Management & Doze Mode**: Behavior of periodic wallpaper updates and background WorkManager jobs under aggressive OEM battery optimizations (e.g., Samsung Sleeping Apps, background execution limits).
