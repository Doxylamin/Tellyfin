# Settings overhaul: paged layout, keybinds, advanced settings

Status: approved in chat 2026-09-25 (mockup: `.superpowers/brainstorm/2745416-1790324706/content/settings-overhaul-3.html`).

## Goal

Replace the single-page card grid (`SettingsScreen.kt`) with a paged settings screen
modelled on Android TV's own settings, add user keybinds for remotes with odd
buttons, and expose a few pre-buffering/diagnostics knobs under "Advanced".

## Layout

Full screen, app theme. Left rail (~30% width): "Settings" title plus one entry
per page, each with a Material Symbols (rounded) icon, title and one-line summary.
Right pane: the page's rows. Focus is either on the rail or in the pane.

| Page | Icon | Rows |
|---|---|---|
| Streaming | `wifi` | Max streaming bandwidth (picker) · Pre-buffer channels (toggle) |
| Remote & Keybinds | `settings_remote` | Quick menu · Channel info · Live guide · Search · Channel up · Channel down |
| App | `info` | Version (info) · Update (action; hidden when `SELF_UPDATE_ENABLED` is false) |
| Account | `account_circle` | Server (info) · Signed in as (info) · Sign out (action, red focus) |
| Advanced | `tune` | Pre-buffer delay (picker) · Countdown (picker) · Send diagnostic logs (toggle, sentry flavor only) · Restore defaults (action) |

Info rows are not focusable. Icons ship as five vector drawables in `res/drawable`
(`ic_settings_*`), not the material-icons-extended dependency. No emoji anywhere.

Each focusable row may have a hint line shown under it (as today). Keybind rows
show their buttons as chips: default buttons grey, the user-added one purple.
Quick menu also lists "hold OK"; Channel up/down also list ▲/▼ (D-pad while
watching) — those are informational, not rebindable.

## Navigation

- Opening Settings: focus on the rail, first page selected.
- Rail: Up/Down change page (the pane follows live). Right or OK enter the pane
  (first focusable row). Back closes Settings.
- Pane: Up/Down move between focusable rows (clamped, no wrap). Left or Back
  return to the rail. OK activates the row:
  - toggle → flips it
  - picker → opens the option picker dialog (generalised from today's bitrate
    picker: Up/Down move, OK selects, Back cancels)
  - keybind row → opens the capture dialog
  - action → runs it (update / sign out / restore defaults)

## Keybinds

Model: each action has fixed default keys plus at most one user-added key.
Defaults always keep working ("add alongside", decided in chat).

| Action | Default keycode |
|---|---|
| Quick menu | `KEYCODE_MENU` |
| Channel info | `KEYCODE_INFO` |
| Live guide | `KEYCODE_GUIDE` |
| Search | `KEYCODE_SEARCH` |
| Channel up | `KEYCODE_CHANNEL_UP` |
| Channel down | `KEYCODE_CHANNEL_DOWN` |

Resolution: `PlayerViewModel.handleKeyEvent` first translates the raw keycode —
if it is some action's extra key, it becomes that action's default keycode. All
existing per-overlay handlers stay untouched. Unbound keys pass through as-is.
Resolution is skipped while the capture dialog is open.

Capture dialog: "Press the button to use for <action>". The next key down:
- Back → cancel.
- OK/Enter → remove the action's extra key (closes).
- Reserved (D-pad, OK/Enter, Back, 0–9, volume up/down/mute, power, home) →
  ignored with message "That button can't be used."
- Already a default or extra key of another action → refused with
  "<Button> already opens <action>." Dialog stays open.
- Otherwise → saved as the extra key (replacing any previous one), dialog closes.
The dialog always shows "Nothing happening? That button isn't passed to apps."

Button labels: known keycodes get short names (RED, GREEN, YELLOW, BLUE, PLAY,
…); anything else uses `KeyEvent.keyCodeToString` minus the `KEYCODE_` prefix.

Persistence: one string preference `keybinds`, e.g. `GUIDE=183;SEARCH=185`.
Unknown action names or bad numbers are ignored on load.

## Advanced settings

- Pre-buffer delay: 0.5 / 1 / 2 s, default 1 s (replaces `Prebuffer.START_DELAY_MS`).
- Countdown: Auto / 3 / 5 / 7 s, default Auto. Auto = 5 s with pre-buffering on,
  3 s off (today's behaviour). The banner arc uses the same value.
- Send diagnostic logs (sentry flavor only; row absent in noSentry): default off.
  Sentry log streaming is always enabled at init in the sentry flavor, but
  `CrashReporting.log`/breadcrumb logging only sends when debug, pre-release, or
  this switch is on. Set at runtime from the saved preference after startup.
- Restore defaults: resets the Advanced settings above plus all keybinds. Does
  not touch bandwidth, pre-buffer on/off, or the account.

Preferences: `prebuffer_delay_ms` (long), `countdown_ms` (long, 0 = Auto),
`diagnostics_enabled` (bool), `keybinds` (string).

Signing out removes only the account (server, token, user) and server-specific data
(favourites, last channel). All device settings above, plus bandwidth and pre-buffering,
survive a sign-out. Stored delay/countdown values that aren't among the offered options load
as the defaults; KEYCODE_UNKNOWN is reserved; a just-bound button is ignored until released.

## Code structure

- `Keybinds.kt` — pure: `KeyAction` enum with defaults and labels, `Keybinds`
  value (extras map) with `resolve(keyCode)`, `validateCapture(action, keyCode)`
  → Accepted / Reserved / Conflict(action), `with/without`, serialize/parse.
- `SettingsNavigator.kt` — pure: `SettingsState` (page, inPane, row, picker,
  capture) and a reducer from key presses to new state plus an optional command
  (Toggle, Pick, StartUpdate, SignOut, RestoreDefaults, SaveKeybind, Close…).
  Row lists per page are derived from a small `SettingsContext` (flavor flags).
- `SettingsScreen.kt` — rail + pane frame and dialogs; `SettingsPages.kt` — rows.
- `PlayerViewModel` — owns `SettingsState` inside `PlayerUiState`, delegates
  `handleSettingsKeys` to the navigator and executes commands. Removes the
  `SETTINGS_FOCUS_*` constants, `bitratePickerOpen/Index`.

## Testing

Unit tests (plain JUnit, like `PrebufferTest`): keybind resolve/validate/
serialize round-trip; navigator rail↔pane movement, row clamping, picker and
capture flows, per-flavor row lists; countdown/delay derivation. UI verified by
building and on-device.

## Out of scope

Remapping D-pad/OK/Back, multiple extra keys per action, per-device profiles,
start-up buffer and pre-buffer amount settings.
