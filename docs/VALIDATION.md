# Store gallery — 2026-10-17

Eight portrait cards (1080 × 1920 RGB PNG) and a landscape feature graphic (1024 × 500 RGB PNG) were rendered and visually reviewed. They use existing native app renders and new native widget captures with fictitious sample data. The isolated widget capture fixture completed successfully on Robolectric API 35. The application source, version, and signing configuration were not changed.

The review checked captions against implemented features, text readability, sheet cropping, task-family detail, theme labels, and the widget picker. Image sizes and checksums are recorded in [the gallery manifest](store-assets/manifest.json). [Editable compositions and provenance](store-assets/README.md) are included. The gallery identifies native renders and does not claim physical-device testing or measured battery/frame-rate results.

---

# Public source export — 2026-10-17

The clean source ZIP was extracted into a separate directory and built successfully with `:app:assembleRelease --offline --no-daemon --no-configuration-cache`, using the existing dependency cache. No private signing configuration or keystore was present. The resulting unsigned APK retains package `dev.quiettasks`, version `0.2.0` / code `2`, minimum SDK 28, and target SDK 37.

All 82 application and build source files match the previously validated polished export. The [source-export record](validation/fdroid-source-export-0.2.0.json) includes the environment, source inventory digest, and unsigned APK checksum. This Windows build does not establish F-Droid Linux build compatibility, scanner results, GitHub-hosted CI success, or reproducibility against the signed APK. A public repository and release commit are still required before completing the F-Droid recipe.

---

# Scrolling, press feedback, and widget hierarchy — 2026-10-17

- All **83 automated tests passed**, with no failures, errors, or skips. See [the polish suite record](validation/polished-0.2.0-suite.json).
- The complete `testDebugUnitTest lintDebug assembleRelease` check succeeded. Lint reports no errors or fatal issues and 45 warnings.
- Package `dev.quiettasks` remains **0.2.0 / code 2**, database schema 3, with the existing signing certificate.
- The signed APK is **3,351,303 bytes**. [Signature verification](validation/apk-0.2.0-polished-signature.txt) and [package metadata](validation/apk-0.2.0-polished-package.txt) are retained.
- APK SHA-256: `e6375c12ed7e1207a79b7246e68d5adf10455eff58ab8771295a2c3e16321a1c`.

Native checks repeatedly swipe past both list ends and confirm stationary boundary content with elastic overscroll disabled. Widget ListViews also disable overscroll while retaining resize and large-collection behavior. Row press feedback covers the complete row, with existing checkbox and star actions still passing the interaction flow. Native frames at different animation times verify that the press wave reaches the final subtask after the lift is settled. The cached wave uses full-family coordinates and a restrained lift without spring overshoot or directional stretching.

Home renders retain only one All label in the tab strip, alongside the summary controls and brighter shaded cards. Widget hierarchy renders show the branch marker, indented checkbox, smaller child title, and parent-aware accessibility text. A new row layout identity refreshes installed widget rows. Existing tab synchronization, family ordering, subtasks, repeat limits, archive, migrations, reminders, and CalDAV checks remain green.

The environment is Robolectric API 35 with native graphics. Physical Android 17/CalyxOS interaction feel and frame timing, real-server interoperability, and measured battery use remain unverified.

---

# Shaded surfaces and family lift — 2026-10-17

- All **80 automated tests passed**, with no failures, errors, or skips. See [the family suite record](validation/family-0.2.0-suite.json).
- The complete `testDebugUnitTest lintDebug assembleRelease` check succeeded. Lint reports no errors or fatal issues and 43 warnings.
- Package `dev.quiettasks` remains **0.2.0 / code 2**, database schema 3, with the existing signing certificate.
- The signed APK is **3,350,587 bytes**. [Signature verification](validation/apk-0.2.0-family-signature.txt) and [package metadata](validation/apk-0.2.0-family-package.txt) are retained.
- APK SHA-256: `bcdad0344fe5431b6e3232ec4091d6895d46c0909f8ac942a7b1c3f5131b7aa1`.

Native palette checks cover fixed and wallpaper colors: shaded surface roles are non-black, and cards and tabs are lighter than the canvas. Updated home renders show that hierarchy; AMOLED remains black. Native family checks lift a parent with two children and assert that every row follows the same drag while an unrelated task stays in place. Held and released renders are retained. Gesture state checks cover a partially visible family, a single family without reorder targets, release offsets, child-only lifts, and unchanged order without writes. Existing actual pointer dragging, sibling order, tab synchronization, completion, recurrence, widgets, archive, persistence, and CalDAV checks remain green.

The environment is Robolectric API 35 with native graphics. Physical Android 17/CalyxOS interaction feel, frame timing, real-server interoperability, and measured battery use remain unverified.

---

# Shaded dark and AMOLED — 2026-10-17

- All **76 automated tests passed**, with no failures, errors, or skips. See [the theme suite record](validation/themes-0.2.0-suite.json).
- The complete `testDebugUnitTest lintDebug assembleRelease` check succeeded. Lint reports no errors or fatal issues and 43 warnings.
- Package `dev.quiettasks` remains **0.2.0 / code 2**, database schema 3, with the existing signing certificate.
- The signed APK is **3,350,587 bytes**. [Signature verification](validation/apk-0.2.0-themes-signature.txt) and [package metadata](validation/apk-0.2.0-themes-package.txt) are retained.
- APK SHA-256: `0fd1c441efe9e51b3864c2edc2ec28380e1555bfc94d7121edf8c61646d2bfb6`.

Native checks select Dark (shaded) and AMOLED (pure black) from Settings and verify persistence of AMOLED. Pixel checks confirm the app's AMOLED background and widget body are black, while the shaded widget remains gray. The wallpaper-color path retains black AMOLED backgrounds, surfaces, and surface tint. Light, shaded dark, and AMOLED app/widget renders are available under `previews/`. Existing inline subtask behavior, recurring families, tab synchronization, drag, archive, CalDAV, and migration checks remain green. The two-line heart-and-name footer is included.

The environment is Robolectric API 35 with native graphics. Physical Android 17/CalyxOS testing, device frame timing, real-server interoperability, and measured battery use remain unverified.

---

# Inline subtasks and author credit — 2026-10-17

- All **74 automated tests passed**, with no failures, errors, or skips. See [the subtask suite record](validation/subtasks-0.2.0-suite.json).
- The complete `testDebugUnitTest lintDebug assembleRelease` check succeeded. Lint reports no errors or fatal issues and 43 warnings.
- Package `dev.quiettasks` remains **0.2.0 / code 2**, database schema 3, with the existing signing certificate.
- The signed APK is **3,350,207 bytes**. [Signature verification](validation/apk-0.2.0-subtasks-signature.txt) and [package metadata](validation/apk-0.2.0-subtasks-package.txt) are retained.
- APK SHA-256: `19c22a93fe5ecd13647823ede45260d2ca90c78b7f31b6bb47ba49c296ed3479`.

Native interaction checks add subtasks inside the current sheet or editor, restore drafts, verify a checked row's strikethrough, and keep that row beside its unfinished parent. Checking the last child completes the family; checking the parent also completes its children. Completed families count once in Completed. Recurring families receive unchecked child copies with new UIDs, retained metadata and order, shifted dates, and the original count/end-date limits. Store checks cover atomic rollback, deletion tombstones, unchanged child revisions, and My order with checked siblings. Widget checks confirm checked children remain visible and crossed out until their parent completes. Existing tab synchronization and drag checks remain green.

Native previews show inline lists in both themes and the Settings footer: a heart after “Made with,” followed by Ahnaf Hasan Shafin on the next line. The footer follows the active theme. Physical Android 17/CalyxOS testing, frame timing, real-server interoperability, and measured battery use remain unverified.

---

# Motion and subtask refinements — 2026-10-17

- All **65 automated tests passed**, with no failures, errors, or skips. See [the refinement suite record](validation/refined-0.2.0-suite.json).
- The complete `testDebugUnitTest lintDebug assembleRelease` check succeeded. Lint reports no errors or fatal issues and 51 warnings.
- Package `dev.quiettasks` remains **0.2.0 / code 2**, with database schema 3 and the existing signing certificate.
- The signed APK is **3,349,731 bytes**. [Signature verification](validation/apk-0.2.0-refined-signature.txt) and [package metadata](validation/apk-0.2.0-refined-package.txt) are retained.
- APK SHA-256: `e6a75a0b8801552b76e644aebd444bec6028b3d2711ef3207c30cc6fd3c70482`.

New native checks verify that leading tabs stay at the left edge, a later tab centers after five page swipes, and header scroll targets interpolate with the same page progress. A held task and its released state produce different native renders; releasing unchanged order does not write task order. The complete interaction flow creates the first subtask directly from quick-add, exposes the editor's Subtasks section, moves a parent with its child, adds another child through the visible inline action, and reorders siblings while retaining their parent. The existing menu, archive, recurrence, widget, migration, and sync checks remain green.

Native previews include the prominent Subtasks section, a centered later tab, and held/released lift states. Lift motion uses a bounded shape spring, mild directional stretch, a cached radial highlight, tonal elevation, and a 150 ms release transition. Physical Android 17/CalyxOS animation feel, frame pacing, sustained 120 fps, and measured battery use remain unverified.

---

# Interaction refinements — 2026-10-17

- All **62 automated tests passed**, with no failures, errors, or skips. See [the interaction suite record](validation/gestures-0.2.0-suite.json).
- The complete `testDebugUnitTest lintDebug assembleRelease` check succeeded. Lint reports no errors or fatal issues and 51 warnings.
- The APK remains **0.2.0 / code 2**, package `dev.quiettasks`, with the same signing certificate as earlier builds. The internal database schema remains 3; no new migration is needed for this refinement.
- The signed APK is **3,333,347 bytes**. [Signature verification](validation/apk-0.2.0-gestures-signature.txt) and [package metadata](validation/apk-0.2.0-gestures-package.txt) are retained.
- APK SHA-256: `92673b6ffd821b008bd8cbf707918f394a9310fa6224ae6949f9c4747332cb34`.

The native interaction test holds and drags a task to a new position, creates two subtasks through the task options, holds and reorders siblings while retaining their parent, changes the long-press preference to Menu, and exercises its edit/archive/delete menu. Additional checks verify persistence of that preference, canceled drags without writes, parent/child reorder boundaries, a popup anchored at a specified press point with screen-edge clamping, and left-aligned tabs that stay stationary while swiping between visible tabs.

Native preview images now show My order without drag handles and the corrected gear artwork. Page settling uses a short 180 ms transition; no separate animated tab centering remains. Physical Android 17/CalyxOS frame pacing, sustained 120 fps, and measured battery consumption remain unverified.

---

# Same-version fixes — 2026-10-17

- All **59 automated tests passed**, with no failures, errors, or skips. Counts are recorded in [the fixes suite record](validation/fixes-0.2.0-suite.json).
- `testDebugUnitTest lintDebug assembleRelease --no-daemon --no-configuration-cache` completed successfully. Android lint reports no errors or fatal issues and 51 warnings.
- Package `dev.quiettasks` remains version **0.2.0 / code 2**. Compile and target SDK remain **37**; minimum SDK remains **28**. The internal database schema migrates from 2 to 3 without changing the application version.
- The signed APK is **3,300,579 bytes**. `apksigner verify` passes with the same signing certificate as the previous builds. See [signature verification](validation/apk-0.2.0-fixed-signature.txt) and [package metadata](validation/apk-0.2.0-fixed-package.txt).
- APK SHA-256: `57c399ffd6e5bf8036d46dfd211a28c438948b8c1cd3f10a4c01ff06aca91483`.

Regression checks cover hiding either built-in tab, centering the selected tab during swipes, removal of summary subtitles, long-press edit/archive/delete actions, archive restoration and database migrations from schemas 1 and 2, archive CalDAV round-trips, retained daily/weekly limits, repeat start time and weekday editing, light/dark logo resources, and the widget's complete scrolling collection at small and large heights. The widget test reaches the last of 41 active rows, switches to All without a picker activity, and verifies all 152 tasks through the large-collection service. A new collection layout identity prevents reuse of the previous static widget hierarchy on upgrade.

Native renders in `previews/` were refreshed, including the repeat editor, resized widget, and logo variants. The launcher resources follow the system/launcher theme; the in-app forced theme controls Compose and the widget.

The test environment is Robolectric API 35 with native graphics. Physical Android 17/CalyxOS launcher behavior, notification timing, real-server interoperability, and measured battery consumption remain unverified.

---

# Build verification — 2026-10-17

- Version 0.2.0 full automated suite: **51 tests passed, zero failures or errors**. Suite counts are recorded in `validation/update-0.2.0-suite.json`. The original 0.1.0 record is retained separately.
- After adjusting the preview fixture to use the app’s background surface and composite the native sheet over its host window, all **four affected rendering tests** passed again. Production code and the signed APK were unchanged by this fixture adjustment. Counts are in `validation/update-0.2.0-previews.json`.
- `:app:assembleRelease` and `:app:lintDebug` succeeded. Android lint reports zero errors; remaining warnings are mostly dependency-version, localization, and Kotlin style suggestions.
- The optimized APK is **3,248,847 bytes**, signed locally with the same 3,072-bit RSA key as 0.1.0. Android `apksigner verify` passed; its output is saved in `validation/apk-0.2.0-signature.txt`.
- Package: `dev.quiettasks`; version: `0.2.0` / code `2`; compile and target SDK: **37 (Android 17)**; minimum SDK: 28. Included ABIs: arm64-v8a, armeabi-v7a, x86, x86_64.
- APK SHA-256: `12d52399e2cf21dac8dc726b73b034c075e05d9c3218270334a724233f8baabc`.
- Signing certificate SHA-256: `77584964a8e71eff92f8160e6898ae9c68f6ea72f0326faccd04467ad558d3d7`.

## What the tests cover

SQLite persistence, parent/child completion, tombstones, conflict copies, changes made during upload, imported hierarchy visibility, RRULE count/weekday handling, Unicode iCalendar folding, independent deadlines/schedules, actual HTTP-based mock CalDAV synchronization, conditional writes and deletes, same-origin credential handling, XML rejection, request cancellation, charging-only jobs, and one-alarm scheduling for many reminders.

New coverage includes upgrading the shipped version 1 schema without losing task IDs or remote references, preserving imported categories/priority, independent per-tab sort settings, local-only appearance changes without a sync trigger, undated-first grouping, earlier deadline handling, repeat defaults and metadata inheritance, tag/priority HTTP round-trips, and protecting hidden subtasks during filtered bulk deletion.

The native UI interaction test creates tasks, selects priority, edits tags, switches to Custom order, performs an actual swipe on a drag handle and verifies persisted order, filters tags, customizes the summary color/emoji, selects Daily repeat, stars/completes a task, and changes theme. Additional rendering tests cover both themes, Custom order, the customization sheet, editing, settings, and the widget. The widget metadata check confirms periodic widget polling is disabled.

## Device work still needed

The UI environment is Robolectric with native graphics on API 35. Screenshots under `previews` are genuine renders of this implementation using sample tasks, not screenshots from physical hardware. A physical phone or emulator was not used to validate this update.

Physical Android 17 / CalyxOS testing, a real DAV-server interoperability check, notification timing under device power management, launcher widget behavior, and measured battery consumption remain unverified. A claim of pixel identity or best-in-class battery life would require those comparisons.
