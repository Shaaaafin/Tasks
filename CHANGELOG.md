# Changelog

## 0.2.0 scrolling and press feedback — 2026-10-17

The application version remains 0.2.0 (code 2).

- Remove elastic end-of-list stretching in the app and widget, and stop drag edge scrolling at collection bounds.
- Apply tap feedback to the full task row and expand the long-press wave across the complete lifted family.
- Replace directional stretching and spring overshoot with a short, restrained lift and release.
- Remove the duplicate All heading while retaining summary controls.
- Lighten shaded task cards while preserving the canvas and AMOLED appearance.
- Distinguish widget subtasks with branch arrows, indented checkboxes, smaller titles, and parent-aware accessibility descriptions.
- Use a new widget row layout identity so installed widgets refresh their hierarchy presentation.


## 0.2.0 shaded surfaces and family lift — 2026-10-17

The application version remains 0.2.0 (code 2).

- Put lighter task cards and tabs above a darker gray canvas in shaded dark mode.
- Keep shaded surface roles non-black with fixed or wallpaper colors; retain pure black for AMOLED.
- Lift, stretch, move, and release a parent and its subtasks as one visual branch.
- Preserve independent sibling dragging and permit a single task family to lift without writing an unchanged order.


## 0.2.0 dark theme choices — 2026-10-17

The application version remains 0.2.0 (code 2).

- Offer Dark (shaded) and AMOLED (pure black) as separate appearance options.
- Keep AMOLED backgrounds black when wallpaper accent colors are enabled.
- Match widgets to shaded gray or AMOLED black backgrounds.
- Retain the existing shaded palette for previously saved Dark preferences.


## 0.2.0 inline subtasks — 2026-10-17

The application version remains 0.2.0 (code 2).

- Replace separate subtask entry pages with an initially empty, editable inline list.
- Place a return-arrow Add subtask action below the rows and support the keyboard Next action.
- Preserve unsaved subtask drafts across activity recreation and save families atomically.
- Keep checked subtasks crossed out beside their unfinished parent in the app and widget.
- Complete the family when its parent is checked or every subtask is complete; reopening a child also reopens its parent.
- Allow recurring parents to contain subtasks and copy unchecked children into each next occurrence while retaining repeat limits.
- Protect checked children of unfinished parents from bulk deletion of completed tasks.
- Add a Settings footer crediting Ahnaf Hasan Shafin.

## 0.2.0 motion and subtask refinements — 2026-10-17

The application version remains 0.2.0 (code 2).

- Keep leading tabs left-aligned and center later selections, with header and underline motion driven by the page's swipe progress.
- Replace the static task lift with a bounded shape, stretch, tonal highlight, elevation, and release transition.
- Expose Add subtask in quick-add and a prominent Subtasks section near the top of the editor.
- Preserve parent data before opening a child draft and focus the new title field.

## 0.2.0 interaction refinements — 2026-10-17

The application version remains 0.2.0 (code 2).

- Align tabs from the left, remove forced centering, and use a shorter page transition.
- Rename Custom order to My order.
- Lift tasks with long-press and drag them directly, with row-based drop positions and edge scrolling.
- Preserve parent/child relationships and allow sibling subtask reordering in My order.
- Add a long-press preference for reordering or a menu anchored at the press position.
- Add Add subtask to the task editor options and correct the settings gear's vector alignment.

## 0.2.0 fixes — 2026-10-17

The application version remains 0.2.0 (code 2).

- Compact the tab spacing, center the selected tab during swipes, and smooth page transitions.
- Rename the summary tab to All and remove its explanatory subtitles.
- Add independent settings to hide All and Starred.
- Add long-press Edit, Archive/Restore, and Delete actions, with an Archived tasks screen.
- Configure recurrence start dates, times, intervals, weekdays, and end-date/count limits.
- Replace fixed widget rows with a full scrolling collection that fills the resized widget.
- Switch between All, Starred, and lists within the widget; keep initial setup in a compact dialog.
- Recreate the supplied logo as native adaptive vectors with light, dark, and monochrome resources.
- Migrate the database to schema 3 while preserving existing tasks and references.

## Unreleased

- Prepare repository documentation, contribution guidance, privacy information, and GitHub CI.
- Add the Apache License, Version 2.0.
- Add store metadata, screenshot directories, and an F-Droid build recipe template.
- Add a source export utility that excludes local tools, credentials, generated files, and downloaded research images.

## 0.2.0 — 2026-10-17

- Add an All tasks summary with undated-first chronological groups.
- Replace the home screen cloud button with a settings gear.
- Add independent tab sorting and Custom order with drag handles.
- Add task priorities, tags, and tag filtering, with CalDAV serialization.
- Add tab color, icon, and emoji customization.
- Expose recurrence controls in quick add and the task editor.
- Preserve existing data through the version 2 database migration.

## 0.1.0 — 2026-10-17

- Introduce native task lists, subtasks, notes, stars, completion, reminders, deadlines, and recurrence.
- Add local SQLite storage and optional two-way HTTPS CalDAV synchronization.
- Add foreground, charging-only, and manual synchronization modes.
- Add light, dark, and system themes and a configurable home-screen widget.
