# Guide redesign: one shared guide for Home and the Live overlay

Status: approved in chat 2026-09-25 (mockup: `.superpowers/brainstorm/2745416-1790324706/content/epg-redesign.html`; option "A+").

## Goal

The home screen's Live tab (`HomeScreen.kt` → `LiveEpgContent`/`EpgHeroSection`/`HomeEpgGrid`)
and the in-playback guide (`EpgOverlay.kt`) are two near-identical, separately maintained
grids with the same problems. Replace both with one shared guide: calmer colours, a time ruler
that stays visible, less past, correct programme states, more channels on screen — and in the
overlay, the channel you're watching keeps playing in a small window instead of being hidden.

## Problems being fixed

1. Every airing programme is filled red (plus red dot, line, bar): red stops meaning anything.
2. The time ruler is item 0 of the channel `LazyColumn`, so it scrolls away as soon as focus moves down.
3. The window starts up to 60 min before now; the left edge is unreadable slivers.
4. The hero takes ~40% of the screen; only ~5 channels fit.
5. Channel column is a tiny number + logo; no name.
6. Two copies of the grid code with diverging styling (overlay uses purple for live, home red).

(Overlapping titles on screenshot 2 predate the clipping fix from 2026-09-24 13:24; the clip
logic moves into the shared, unit-tested layout code so it stays fixed.)

## Design

**Colour rules.** Red only means "now": the now-line with a time pill on the ruler, and the LIVE
badge. Airing programmes: slightly lighter surface (`#26263C`) with a thin white progress stripe
along the bottom showing elapsed time. Upcoming: `#1B1B2B`. Past (ended): `#141420`, title dimmed,
plus a dark shade over the whole past part of the grid. Focused block: purple gradient + light
purple outline, as today.

**Time window.** Starts at the current half hour boundary rounded down (0–30 min before now),
5 h long, 4 dp/min (unchanged scale). Half-hour tick labels.

**Ruler.** A separate row above the channel list sharing the horizontal scroll state — never
scrolls vertically. Shows tick labels and a red pill with the current time at the now position.

**Channel column.** 150 dp: number, logo tile, channel name (ellipsised). Highlighted channel:
purple tint + 3 dp purple left bar.

**Hero (compact, ~112 dp).** Logo tile, title (1 line), meta line (LIVE badge if airing,
channel badge, genre badge if any, "12:10 – 12:40 · noch 16 min" / "· vorbei" / "· in 20 min"),
thin red progress bar only while airing. Right side: either the programme description (max 3
lines) or — in the overlay — the video slot.

**Hints.** One bar at the bottom of the guide, never over the grid.

**Home (Live tab).** Top nav bar (unchanged) → hero → ruler + grid → hint bar
("OK Ansehen · MENU Optionen · ←→ Zeit · ↑↓ Kanäle"). The duplicate "Programmführer / OK:
Kanal wechseln / date · time" line goes away; the nav bar already shows the clock. Block-level
focus and key handling are unchanged.

**Overlay (GUIDE while watching).** Title row ("Programmführer" + time) → hero with the video
slot on the right → ruler + grid → hint bar. The overlay background is drawn around the video
slot (not over it), and the player's view is resized into that slot while the overlay is open, so
the current channel keeps playing visibly. The overlay fades in/out (no slide — the slot must not
move while the video is sized into it). Row-only focus and key handling are unchanged.

## Code structure

- `GuideLayout.kt` — pure, unit-tested: `guideWindowStart(now)`, `guideTicks(windowStart)`,
  `guideBlocks(programs, windowStartSec, windowEndSec)` (window + next-start clipping, drops
  empty/zero-width, tolerates unsorted input), `programTiming(program, now)` →
  `Live(remainingMin, progress)` / `Upcoming(inMin)` / `Ended`.
- `GuideViews.kt` — Compose: `GuideHero`, `GuideGrid`, `GuideHintBar`, shared styling.
- `HomeScreen.kt` — `LiveEpgContent` uses the shared views; `EpgHeroSection`, `HomeEpgGrid`,
  `EpgChannelCell`, `HomeEpgRow`, `MetadataBadge` and the `EPG_*` constants are removed.
- `EpgOverlay.kt` — rebuilt on the shared views; reports the video slot bounds.
- `PlayerScreen.kt` — sizes/positions `VideoPlayer` into the reported slot while the Epg overlay
  is open, full screen otherwise (same composable instance, so the player never re-attaches).

## Testing

Unit tests for all of `GuideLayout.kt`. Visual/overlay behaviour verified on device via a beta.

## Out of scope

New navigation (day switching, jump-to-time), programme artwork, changing the key handling of
either guide, the "Für dich" tab.
