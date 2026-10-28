# Facts Wallpaper v2 — Finish Plan (hand-off brief for an autonomous agent)

You are finishing the Android app in this repo. Work **autonomously**: never stop to ask for design approval; make a reasonable choice, record it in `docs/DECISIONS.md`, and keep going. Do **not** spawn subagents. Do **not** run builds in the background — always run them synchronously and wait for the result.

## 0. Context

- Repo: `C:\Users\Ahmed\Desktop\facts wallpaper app 2` (GitHub `Godzilla675/facts-wallpaper-v2`, public, branch `master`).
- Authoritative spec: `Facts Wallpaper Complete Feature Specification.md` (repo root). Design notes: `docs/DECISIONS.md`. Older phase plans: `docs/P8-PLAN.md` (if present).
- Stack: Kotlin, Jetpack Compose (Material 3), Hilt, Room (schema v5+, with migrations), DataStore, WorkManager, PdfRenderer + PDFBox, multi-module (`app`, `core/*`, `feature/*`).
- Latest release: v2.3.0 (debug APK). Everything through P9.3 is merged; P9.4 is partial.

## 1. Environment rules (critical)

- **Always build via `.\gw.ps1 <tasks>` from the repo root.** Machine env vars point to a missing `D:` drive; `gw.ps1` fixes `JAVA_HOME` (Android Studio JBR 21), `GRADLE_USER_HOME`, `ANDROID_HOME`. Never call `gradlew` directly.
- Run `.\gw.ps1 --stop` when finished.
- Full verification command (must be green before every commit):
  ```powershell
  .\gw.ps1 assembleDebug testDebugUnitTest lintDebug --continue --console=plain 2>&1 | Select-String '^BUILD|FAILED|^e: '
  ```
  Lint details: `*/build/reports/**/lint-results-debug.txt`.
- Windows/PowerShell: use `;` not `&&` before PowerShell statements. LF/CRLF warnings from git are harmless.

## 2. Known pitfalls (previous agents broke these — avoid)

1. **Adding a method to an interface (e.g. `SettingsRepository`) → update every fake/test double.** Known fakes:
   - `core/data/src/test/.../RoomRotationPoolRepositoryTest.kt`
   - `feature/outline/src/test/.../OutlineViewModelTest.kt`
   - `feature/settings/src/test/.../ProviderSettingsViewModelTest.kt`
   Search with `grep -r ": SettingsRepository" --include=*.kt` (and same for any other interface you touch).
2. **Never call `context.getString(...)` / `LocalContext.current.resources` inside composables** — lint error `LocalContextGetResourceValueCall`. Hoist `stringResource(...)` to the composable scope and pass strings into lambdas.
3. All user-visible strings go in `strings.xml` of the relevant module (no hardcoded text).
4. Room changes require a new version + `Migration` + exported schema JSON + migration test.
5. Do not leave half-finished edits. Commit only compiling, green states. Check for stray scratch files before committing: `git ls-files --others --exclude-standard`.
6. Missing imports (e.g. `com.factswallpaper.core.model.ThemeMode`) were a recurring failure — compile after every milestone.

## 3. Work items (do in order; one commit per milestone)

### M1 — Finish P9.4 (settings & polish)
1. **Settings search**: search field at top of Settings that filters all settings entries (title + summary + keywords) across sub-screens; tapping a result navigates to the screen/item. Unit-test the filter function.
2. **Model picker for OpenAI-compatible / NVIDIA NIM / OpenRouter / Ollama providers**: "Fetch models" button that calls `GET {baseUrl}/models` (with auth header), shows a searchable list, fills the model field. Fall back to free text on error; show the error message. Unit-test parsing with MockWebServer or a fake HTTP client (follow existing test style in `core/ai`).
3. **Haptics**: light haptic on card favorite/hide, Study Mode "Got it"/"Again", wallpaper "next fact" actions; respect a new `hapticsEnabled` setting (default on).
4. **Predictive back**: set `android:enableOnBackInvokedCallback="true"` in the manifest; make sure bottom sheets/dialogs/selection mode use `BackHandler` correctly.
5. **Library coach hint** (was reverted): one-time dismissible hint on the Library screen explaining import → outline → generate. Persist dismissal via existing `setLibraryCoachHintDismissed` in `SettingsRepository`. Do NOT rework Library undo-delete (it already exists from P9.3 — keep it working).
6. **Empty states** for Library, Facts, Outline, Activity/Jobs, Favorites: icon + message + primary action button.
7. **README**: update the features list to match the current app (P7–P9 features: agentic pipeline, advanced params/raw JSON, Drive via SAF, background processing + notifications, vision mode, study mode, exports, tile/widget/shortcuts, quiet hours, etc.) and a short "How PDFs reach the LLM" section (text extracted on-device, chunked by page; vision mode sends rendered page images).

Commit: `P9.4: settings search, model picker, haptics, predictive back, coach hint, empty states, README`.

### M2 — Verify & close earlier gaps
1. **Quick Settings TileService**: confirm a `TileService` exists, is declared in the manifest with `BIND_QUICK_SETTINGS_TILE`, and advances the wallpaper fact. If missing, implement it.
2. **Home-screen widget, app shortcuts, fact-of-the-day notification, quiet hours**: confirm each is wired end-to-end (manifest declarations, receivers, POST_NOTIFICATIONS runtime permission on API 33+). Fix anything missing.
3. **P7 tool-calling gap**: the spec's agentic generator should let the model request pages via a `get_pages` tool. Currently lesson pages are sent directly. Implement provider-native tool calling for at least OpenAI-compatible Chat Completions and Gemini, falling back to the current direct-pages behavior for providers/models without tool support (make it a setting, default = auto). Add unit tests with fake responses containing tool calls.
4. **Reasoning models** (user-reported bug, P7.1 fix): re-check that "Test connection" for NVIDIA NIM reasoning models doesn't fail with `finishReason=length` (adequate `max_tokens`, reasoning content tolerated). Add a regression unit test if none exists.
5. Grep for `TODO`, `FIXME`, `NotImplementedError`, `TODO()` in `app/`, `core/`, `feature/`; resolve each or document why it stays in `docs/DECISIONS.md`.

Commit: `P10.1: verify/complete tile, widget, notifications; get_pages tool calling; TODO sweep`.

### M3 — Spec compliance audit
Read the spec section by section. Build a checklist table in `docs/SPEC-AUDIT.md` with columns: Spec section | Requirement | Status (Done/Partial/Missing) | File(s). Implement every Missing/Partial item that is feasible without external accounts. For anything infeasible, explain why in the table.

Commit: `P10.2: spec audit + remaining spec items`.

### M4 — Testing (mandatory)

**4a. Unit tests** (JVM, `testDebugUnitTest`) — add/extend so that each of these has coverage:
- ViewModels: Library (search/sort/selection/undo delete), Facts (favorite/hide/edit/export), Outline, Settings (search filter, model picker states), Study Mode (counters, spaced repetition scheduling).
- Repositories / DAOs with fakes or Robolectric in-memory Room (follow existing patterns).
- AI layer: request building for each provider (Gemini, Anthropic, OpenAI Responses, OpenAI Chat Completions) incl. images and tool calls; response parsing incl. `finishReason=length`, malformed JSON, reasoning fields; `CostEstimator`.
- Pipeline: `DocumentProcessingScheduler` honors `AutoProcessMode` (OUTLINE_ONLY / OUTLINE_AND_GENERATE_ALL / ASK_ME); retry-failed-only logic; pause/resume all.
- Room migrations: test every migration from v1 → latest with `MigrationTestHelper` (instrumented) or a Robolectric equivalent.
- Wallpaper rotation controller: advance, quiet hours, battery-saver interval, rotation pool selection.

**4b. Instrumented tests** (`connectedDebugAndroidTest`) — only if an emulator/device is available (`adb devices` shows one; else create an AVD via `sdkmanager`/`avdmanager` from `ANDROID_HOME` if feasible, otherwise skip and note it):
- Room migration tests.
- Compose UI smoke tests: app launches, navigate to Library/Facts/Settings, open Settings search, empty states render.
- WorkManager test with `WorkManagerTestInitHelper` for the pipeline chain.

**4c. Static checks**: `lintDebug` must have 0 errors. Fix warnings that are trivial.

**4d. Manual smoke checklist** (run on emulator if available, else put it in `docs/TESTING.md` for the user):
1. Fresh install → onboarding → add provider (OpenAI-compatible with custom base URL) → Fetch models → Test connection.
2. Import PDF via file picker, via Share-to-app, via "Open with". Duplicate import shows warning.
3. Auto pipeline: outline → generate → cards appear; notification shows progress; cancel/pause/retry work; app killed mid-run resumes.
4. Vision mode on a scanned PDF.
5. Set live wallpaper; double-tap advances; QS tile advances; widget shows fact; quiet hours respected.
6. Study mode flow; Markdown/ZIP/PDF export via SAF; backup + restore.
7. Theme SYSTEM/LIGHT/DARK, dynamic color, rotation, large font scale, TalkBack labels on main buttons.

Write results into `docs/TESTING.md` (what was automated, what passed, what still needs a real device).

Commit: `P10.3: test coverage + TESTING.md`.

### M5 — Release
1. Bump `versionCode` (+1) and `versionName` to `2.4.0` in `app/build.gradle(.kts)`.
2. Run the full verification command — must be green.
3. Push: `git push origin master`.
4. Publish (the APK is ~185 MB; the upload takes 10+ minutes — wait for it synchronously):
   ```powershell
   $apk = Get-ChildItem app\build\outputs\apk\debug\*.apk | Select-Object -First 1
   Copy-Item $apk.FullName "$env:TEMP\FactsWallpaper-v2.4.0-debug.apk"
   gh release create v2.4.0 "$env:TEMP\FactsWallpaper-v2.4.0-debug.apk" --target master --prerelease --title "v2.4.0" --notes "<summary of M1–M4>"
   gh release view v2.4.0 --json isDraft,assets
   Remove-Item "$env:TEMP\FactsWallpaper-v2.4.0-debug.apk"
   ```
   If the upload is interrupted and leaves a draft: `gh release delete v2.4.0 --yes --cleanup-tag`, then retry.
5. `.\gw.ps1 --stop`.

## 4. Definition of done
- All M1–M5 items complete or explicitly documented as infeasible in `docs/DECISIONS.md` / `docs/SPEC-AUDIT.md`.
- `assembleDebug testDebugUnitTest lintDebug` green at HEAD; working tree clean; pushed.
- `docs/TESTING.md` and `docs/SPEC-AUDIT.md` exist and are accurate.
- v2.4.0 pre-release published with the APK attached (not a draft).
- Final message: list of commits, test counts, release URL, and anything left for real-device testing.
