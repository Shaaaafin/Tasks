# Screenshots

The prepared public gallery is in:

```text
fastlane/metadata/android/en-US/images/phoneScreenshots/
```

Eight numbered 1080 × 1920 PNG feature cards are included, covering the summary, subtasks, repeats, custom tabs, My order, themes, widget, and optional CalDAV synchronization. A 1024 × 500 `featureGraphic.png` sits beside the screenshot directory. See [the gallery overview and editable sources](store-assets/README.md).

Use fictitious task content and remove private account information. Capture the release version intended for publication. Do not use third-party reference screenshots as application screenshots.

To embed selected cards in a repository page:

```html
<p>
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/1.png" width="240" alt="Task summary in light mode">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/2.png" width="240" alt="Task summary in dark mode">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/3.png" width="240" alt="Task editor">
</p>
```

A 512-pixel native render of the supplied logo is included at `fastlane/metadata/android/en-US/images/icon.png`. Android launcher assets remain in `app/src/main/res/`.

The gallery uses actual app UI rendered through Robolectric API 35 with sample data, composed with feature captions and decorative frames. It is identified as native UI renders, not physical-device captures. Full source captures are retained under `docs/previews/`. Device screenshots can be added or substituted later without changing the app version. Keep the provenance accurate when replacing images.
