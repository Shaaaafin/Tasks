# Native render previews

These images are generated from this app by native-graphics Android UI tests with sample data. They are not mockups or physical-device screenshots.

- `home-light.png`, `home-dark.png`: All date summary, priorities, tags, colored list labels, and collapsed completed section.
- `home-custom-dark.png`: My order with long-press reordering and no drag handles.
- `tab-customization-dark.png`: native bottom sheet for colors, symbols, and emoji, composited over the host window.
- `task-editor-dark.png`: editable title/notes, separate deadline and reminder, subtasks, and completion.
- `settings-dark.png`: appearance, tab visibility, archive access, and sync preferences.
- `widget-light.png`, `widget-dark.png`: scrolling list widget with completion and quick add.
- `widget-resized-420.png`, `widget-resized-1100.png`: the same 41-task collection at different heights; rows beyond the viewport remain scrollable.
- `repeat-weekly-dark.png`: repeat schedule with start date, time, interval, weekdays, and an occurrence limit.
- `logo-light.png`, `logo-dark.png`: native renders of the reference symbol and theme resources.

The main app defaults to the device’s wallpaper colors; previews use a fixed blue palette so visual comparisons are repeatable.

- `tabs-later-centered-light.png`: a later selected tab centered after swiping through a longer tab strip.
- `task-lift-held-dark.png`, `task-lift-rest-dark.png`: native held and released task states.

- `subtasks-inline-light.png`: inline subtask entry in quick-add, with the Add action below the rows.
- `subtasks-inline-dark.png`: an editable subtask list with a checked row crossed out beside an unfinished row.

- `settings-footer-dark.png`: the author credit at the end of Settings.

- `home-amoled.png`, `widget-amoled.png`: pure black app and widget surfaces, alongside the shaded dark previews.

- `family-lift-held-dark.png`, `family-lift-rest-dark.png`: a parent and both subtasks lifting together and returning to rest. Shaded previews now show a darker non-black canvas with lighter cards and tabs.

- `family-wave-expanding-dark.png`, `family-wave-expanded-dark.png`: native animation frames showing the press wave spreading to the final subtask.
- `widget-hierarchy-dark.png`: parent and child rows with a branch arrow, separate indentation, and smaller child text.

- `store-widget-light.png`, `store-widget-dark.png`: full widget renders with All selected, a parent and checked/unchecked subtasks, plus tasks from Personal and Shopping.
- `store-widget-picker-dark.png`: the same widget's actual list-picker state at a smaller height. Captured with the [store fixture](../store-assets/fixtures/StoreWidgetCaptureTest.kt).

The [store gallery](../store-assets/README.md) uses these native sources in promotional compositions with captions. Source images here remain free of promotional text.
