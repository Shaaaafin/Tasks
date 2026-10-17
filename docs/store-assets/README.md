# Store gallery

Eight portrait feature cards are installed in `fastlane/metadata/android/en-US/images/phoneScreenshots/`. Each is a 1080 × 1920 RGB PNG. The matching `featureGraphic.png` is 1024 × 500. Upload the numbered portrait files in order; use the feature graphic as the landscape banner. `overview.png` is a review sheet, not an additional store screenshot.

| File | Feature |
| --- | --- |
| `1.png` | All lists in a chronological overview; offline use without an account |
| `2.png` | Inline subtasks, completed steps, tags, and priorities |
| `3.png` | Repeat intervals, weekdays, end dates, and occurrence limits |
| `4.png` | Tab colors, icons, and emoji |
| `5.png` | My order and moving a parent with its subtasks |
| `6.png` | Light, shaded dark, AMOLED, and optional wallpaper colors |
| `7.png` | Widget task completion, subtask hierarchy, and list switching |
| `8.png` | Optional CalDAV and foreground, charging, or manual sync |

The current display name is Tasks. Update the branding text and regenerate the gallery if a different name is chosen.

## Image provenance

The application interface comes from this project's native Android UI rendered through Robolectric API 35, with fictitious sample tasks. The UI is not generated or redrawn by an image model. These are promotional compositions of native app renders, not physical-device captures. Each card carries a small native-render attribution.

The supplied dot-and-bars brand mark is recreated as vector geometry. Sage, cream, and charcoal backgrounds, headings, frames, labels, and shadows form the surrounding presentation. Phone-like frames are decorative; they do not identify or imply testing on particular hardware. The repeat and customization sheets, held task family, and sync settings use clipped excerpts of their source captures. The widget card combines two actual widget sizes/states. The banner intentionally shows a partial app window.

Source images remain in `docs/previews/`. `captions.json` records each card's wording, palette, and input filenames. `svg/` contains editable vector compositions with the native UI images embedded. There are no third-party app screenshots in the gallery. Measured battery savings, CalyxOS certification, and frame-rate claims are not advertised.

## Regeneration

The renderer needs Node.js and the `sharp` package. It does not run during an Android build. Install its dependency outside the public source tree, for example under the ignored `.tools/` directory:

```sh
npm install --prefix .tools/store-assets --no-save --no-package-lock sharp
NODE_PATH="$PWD/.tools/store-assets/node_modules" node scripts/render-store-assets.cjs
```

In PowerShell, set `$env:NODE_PATH` to the absolute `.tools/store-assets/node_modules` path, then run `node scripts/render-store-assets.cjs`. The layout uses Segoe UI with DejaVu Sans and generic sans-serif fallbacks; output can vary slightly when those fonts are unavailable. Fonts are not bundled with the project.

The existing app UI tests produce the main source captures. `fixtures/StoreWidgetCaptureTest.kt` produces the fuller widget examples. To refresh these, copy the fixture into `app/src/test/java/dev/quiettasks/widget/` in a separate temporary checkout and run:

```sh
./gradlew :app:testDebugUnitTest --tests dev.quiettasks.widget.StoreWidgetCaptureTest --no-daemon --no-configuration-cache
```

Copy `app/build/previews/store-widget-*.png` from that checkout into `docs/previews/`, then rerun the renderer. The fixture does not change application behavior or the standard test suite.

The PNG files use the [F-Droid Fastlane image layout](https://f-droid.org/en/docs/All_About_Descriptions_Graphics_and_Screenshots/). The application version remains 0.2.0 / code 2.
