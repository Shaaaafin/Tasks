# Architecture and behavior

## Native client

`MainActivity` hosts Compose. `TaskViewModel` routes mutations to an indexed SQLite database on an I/O dispatcher and exposes immutable snapshots through StateFlow. Screen subscriptions follow the activity lifecycle. Database transactions protect task/hierarchy updates. Changes persist before an editor closes.

`SettingsStore` keeps ordinary preferences in private SharedPreferences and encrypts only the credential secret with a non-exportable AES-GCM key in Android Keystore. Credentials are sent only to the configured HTTPS origin. Redirects to another origin are rejected. There is no network destination other than the user-selected DAV server.

## Sync permissions and scheduling

| Mode | Automatic behavior |
| --- | --- |
| While using | A short debounce batches edits. The lifecycle scope is canceled on stop, including pending HTTP calls and response-body reads. No background network job is registered. |
| Only while charging | Android JobScheduler requires charging and connectivity. A one-shot job handles the first eligible sync after configuration; a persisted six-hour periodic job handles subsequent opportunities. Android decides exact execution times. Removing the charging constraint cancels the job. |
| Manual | No automatic network requests or scheduled network jobs. |

Manual **Sync now** requires the app to be visible and respects Wi-Fi only. It is an explicit override of the automatic mode. The Wi-Fi preference is rechecked in the engine; the charging job’s unmetered constraint is supplemented by a transport check.

No foreground service, permanent wake lock, background polling timer, push service, or process-keeping mechanism is used. The widget has `updatePeriodMillis=0`. Task changes update it through the application’s debounced snapshot stream; launcher size changes trigger a local redraw. The visible summary refreshes its date labels at local midnight and when resuming; its coroutine is suspended when the activity stops.

## CalDAV

1. Discover principal, calendar home, and VTODO-capable collections, or accept a direct task collection.
2. Import collections; create local-only lists as VTODO calendars using MKCALENDAR.
3. Read a complete VTODO-only ETag inventory using a CalDAV calendar-query REPORT. Event bodies in mixed event/task calendars are never fetched. Fetch only changed task resources. Failed or malformed inventories never imply an empty remote calendar.
4. Pull remote changes and reconcile missing resources. Preserve both sides of conflicting edits as a remote version plus a separate local copy.
5. Upload dirty resources using `If-Match`, or `If-None-Match: *` for new tasks. Delete tombstones only after the remote server acknowledges the conditional deletion. Local changes made while a request is in flight remain dirty.

Unknown iCalendar properties, timezone components, and nested alarms are retained when editing an imported resource. The client rejects multi-VTODO or mixed-event resources rather than overwriting them. It does not disable certificate validation, forward credentials across origins, or accept HTTP credentials in production.

The engine compares ETags across a complete collection inventory; it does not yet implement RFC 6578 sync-token deltas or multiget batching. Thus unchanged task bodies are not transferred, but collection metadata is read on each sync. Very large collections will benefit from a future token-based incremental path.

## Data mapping

| UI data | iCalendar mapping |
| --- | --- |
| Title / notes | SUMMARY / DESCRIPTION |
| Completed | STATUS / COMPLETED |
| Archived | X-QUIETTASKS-ARCHIVED; hidden locally without changing completion state |
| Star | X-QUIETTASKS-STARRED; old Quiet Tasks PRIORITY=1 favorites are recognized on import |
| Priority | Standard PRIORITY: High=1, Medium=5, Low=9, None=0; imported 1–4 map to High and 6–9 to Low |
| Tags | Standard CATEGORIES text list, including escaped commas/backslashes and multiple category properties |
| Subtask | RELATED-TO;RELTYPE=PARENT |
| Manual order | X-APPLE-SORT-ORDER |
| Recurrence | RRULE, DTSTART, plus a retained original anchor |
| Deadline | Standard date-only DUE plus X-QUIETTASKS-DEADLINE |
| Scheduled reminder | DUE when no separate deadline exists; X-QUIETTASKS-SCHEDULE always preserves the app’s separate schedule |

The app’s additional schedule/anchor properties allow its two independent date fields to round-trip. Another client may show only the standard DUE date. Imported nested alarms are preserved; local notifications are scheduled by this app rather than relying on server alarms. Imported RDATE/EXDATE exceptions are retained in the calendar payload, but are not currently expanded when the app generates the next task occurrence.

Recurrence expansion uses dmfs lib-recur. Completion stores a completed instance without a repeating rule and creates the next active occurrence with a new UID and retained original anchor. The custom-rule editor supports day/week/month/year intervals, selected weekdays, and count/until limits. The schedule editor also chooses a start date and optional reminder time. A recurring parent may contain subtasks. The next occurrence receives new child UIDs linked to the new parent, with completion reset, metadata and order preserved, and child dates shifted by the same interval as the parent. The original recurrence anchor keeps count and end-date limits intact.

Deleting a list uses conditional deletion of its known tasks. The empty server collection is deliberately retained to avoid erasing changes concurrently added by another client. The delete confirmation explains this. A fully removed remote collection is surfaced as a sync error rather than silently deleting local tasks.

## Visual references

The baseline combines Google Tasks’ Android card redesign (2024), deadline editor (2025), and refreshed widget (2026). Official Play marketing images include some iOS and tablet views; these are marked as supplemental references. The Android-specific screenshots take priority for platform details. Original app fonts/source are unavailable; system fonts are used.

The account photo is replaced by a settings gear because Google account integration was explicitly excluded. Settings expose the custom DAV and sync policy options.

## Summary, sorting, and appearance

The optional All tab combines active tasks from all non-deleted lists. The page retains its summary controls without repeating the All title beneath the tab. All and Starred can be hidden independently in Settings. The compact tab strip starts at the left edge. Early selections remain there; later selections center once their center passes the viewport midpoint, with natural clamping near the final tab. Tab widths are measured once per layout. Header scroll and the single underline interpolate from the pager's current position, so they share its gesture and settling progress rather than running independent animations. Page taps and fling settling use a short 180 ms transition. Undated tasks appear first, followed by dated groups in chronological order, including overdue dates. Each task appears once on the earlier of its schedule and deadline. Children stay directly below their parent in the parent's date group and show their parent in the list label. Checked children remain crossed out in that group while the parent is pending. Only completed family roots, or completed tasks with no live parent, appear in the collapsed Completed section.

My order removes date grouping and permits dragging across dates without editing dates. Date, Priority first, Starred first, and Title are computed views and do not overwrite the saved custom order. Each tab persists its own sort setting. A tag filter disables dragging until cleared. Settings can instead assign long-press to the task menu; its popup uses the recorded window-coordinate press position and clamps to the screen edges. Edit, archive, and delete remain available in the task editor in either mode. Long-press dragging lifts the selected task branch, including its subtasks, and follows the pointer through a shared graphics-layer translation. Rows use a common press-point pivot so scaling does not separate children from their parent. Only the outside corners of the lifted branch round off, and the branch shares its lift tone and release offset. Dragging a child lifts only that child and its descendants. A single branch can lift even when there are no other reorder targets; releasing an unchanged order does not write to storage. A 120 ms lift uses restrained uniform scaling and rounded outside corners. A cached radial press wave expands from the recorded finger position over 260 ms, with its radius calculated from the complete family bounds. Each row uses the same wave origin in family coordinates, so the effect reaches subtasks as well as the parent. Graphics transforms expand the cached shader without constructing a new gradient each frame. Tap and menu feedback belong to the full task row, with checkbox and star actions remaining independent. Release returns shape and translation over 150 ms. These are transient gesture animations; they do not add an idle animation loop. Row-center crossings determine order instead of fixed-distance jumps; nearby rows settle quickly and edge scrolling runs only while holding a task. Edge scrolling checks the remaining scroll direction and only updates drop targets after consuming scroll distance. The resulting order commits in one database transaction on release. Accessibility actions provide Move up/down. Parent moves retain their descendants; child moves stay within their sibling set. All and individual lists render task families together.

Clearing completed tasks respects the selected list, star, and tag filters. A completed parent is retained when it contains any non-deleted descendant outside the selected set, so filtering cannot silently delete hidden tasks. Checked children of unfinished parents are also excluded from bulk completion deletion.

Appearance offers Light, Dark (shaded), AMOLED (pure black), and Follow system. DARK uses a non-black dark gray canvas with lighter card and tab surfaces. Task cards use a brighter container tone while the canvas remains unchanged. Wallpaper colors retain that surface hierarchy; any pure black surface from the dynamic palette receives a shaded fallback. AMOLED uses black backgrounds and primary surfaces, with very dark raised containers and light text; wallpaper colors can still supply accents without replacing its black surfaces. The widget uses gray in shaded dark and black in AMOLED on every supported API level. Follow system selects the light or shaded dark palette from the device configuration. Symbols use native vectors or user-entered emoji. Built-in tab appearance and sort choices live in preferences; list appearance lives in SQLite. These preferences are local to the device because no broadly compatible task-list emoji property exists in the current sync implementation. Appearance-only changes do not increment the local sync-change stream or trigger a network sync.

Database schema version 2 adds list appearance, task priority, and JSON tags through ALTER TABLE migration from the shipped version 1. Existing task IDs, foreign keys, remote URLs, ETags, recurrence, completion, and credentials are retained. Existing retained calendar payloads supply previously imported categories and priority. Stars remain independent from priority, including when completing or expanding repeating tasks.

## Archive, widget, and launcher

Schema version 3 adds an archived flag with a false default. Archiving includes descendants; restoring a child also restores its ancestors. Archived tasks are excluded from active views, widget collections, completion cascades, and reminder alarms. Settings exposes the archive for editing, restoring, and deleting. The custom CalDAV archive property round-trips between installations of this app; clients that ignore it may show an archived task as active.

The widget uses a weighted ListView for its complete collection. A separate collection layout resource forces installed widgets to reinflate when upgrading from the previous static layout. Its header switches that same collection to an inline list selector, so selecting All, Starred, or a list never opens a full-screen picker. API 31 and later use RemoteCollectionItems for small collections. Larger collections and older versions use a protected RemoteViewsService whose factory creates requested rows on demand, avoiding a large Binder payload. Both paths retain every matching task. Subtasks indent the entire row, including the checkbox, display a branch arrow, and use a smaller title. Accessibility descriptions identify their parent. A separate hierarchy row layout identity forces row reinflation for existing widget installations. Widget state is independent for each widget. Updates are driven by local changes and launcher resize events; periodic polling remains disabled.

The supplied dot and staggered bars are native vector resources in an adaptive launcher icon. Cream/espresso backgrounds follow system night resources; a monochrome layer supports launcher-themed icons. The launcher's theme and cache control its displayed icon. Choosing a forced theme inside Compose changes the app and widget, but does not override a launcher's resource configuration.

Compose scrolling disables elastic overscroll through its factory provider, and widget ListViews use OVER_SCROLL_NEVER. Normal in-bounds scrolling retains standard fling behavior.

The app does not force a 120 Hz display mode or keep a frame loop running while idle. Direct drag transforms and ordinary Compose page transitions are used. Physical-device frame timing and a sustained 120 fps result have not been measured.

## Subtask entry

Quick-add and the task editor use the same inline Subtasks list. It starts empty, with a return-arrow Add subtask action below the rows. Adding focuses an editable row in the current screen; keyboard Next adds another row. Each row provides a checkbox and remove action. Completion applies a strikethrough without changing the row's position. Draft parent and child fields survive activity recreation through custom saveable state.

Saving a family persists the parent, retained children, and removed-child tombstones in one database transaction, publishing once after success. Unchanged child records do not receive unnecessary writes or sync revisions. Blank draft rows are omitted. Checking a child saves inline edits immediately; checking the final pending child completes the parent. Checking the parent completes all its active children. Reopening a completed child reopens its parent while preserving the other children's checks. A completed family is represented once in Completed and its children remain accessible inside the parent editor. These behaviors also apply to recurring families and widget checkbox actions. No additional database schema migration is needed.
