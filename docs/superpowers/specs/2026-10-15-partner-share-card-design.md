# Partner share card

Status: design approved on the canvas (https://claude.ai/artifact/BcrEGMr357vuq6Q4yWhWuS, boards "Mix · week 5" and
"Mix · week 2"); spec waiting for review. Target release: v0.5.0.

## Goal

My partner wants to follow my progress and I want the accountability. After each weekly weigh-in I send them one
picture card with a one-line caption, over WhatsApp (or any app in Android's share menu). Nothing leaves the phone
unless I tap Send.

Out of scope for now:
- A view-only app for my partner that stays in sync (planned later; they use Android).
- Automatic sending.

## The card

One portrait image, 360 × 450 dp (about 1080 × 1350 px), in the "B orange" dark style. Top to bottom:

1. **Ring and week**
   - Goal ring (`GoalProgress.fraction`, the same trend-based progress as Trend and "Weigh-in saved").
   - Inside the ring: the scale weight ("87.5") and "kg on the scale". (The change since the previous weigh-in is in the
     caption only; the "−0.4 kg since …" pill was removed from the picture on 2026-10-17.)
   - Beside the ring: "Week 5 of 14" (`Dashboard.programWeek()`) and "Weigh-in · Mon, 2 Nov".
2. **Downhill trail**
   - Label row: "Downhill to 82 kg" on the left, "9 weeks to go" on the right (total weeks − current week).
   - The plan as a trail: a dashed blue-grey line through the active plan's checkpoints, start kg at the top left
     ("88.3"), falling to a white flag at the bottom right ("82 kg · 7 Jan"), on a darker hillside fill.
   - A small post on the trail for each program week; this week's post is orange.
   - My weigh-ins since the plan start as a solid orange line, ending in an orange dot labelled "You · 87.5".
   - A dashed blue-grey "plan 86.6" marker on the trail at today's date. It appears only once the weigh-ins span two
     weeks (the same moment the Trend tab starts showing over/under plan, see `trendWait`), so early cards don't have it.
   - Planned and mine stay apart by dash and lightness, not hue alone.
3. **Run strip**
   - "Last week" with "5 runs · 15.0 km" on the right.
   - Seven circles M–S for the Mon–Sun week before the weigh-in's week (from `Dashboard.distance`): orange with a check
     for a run day, blue-grey with a moon for a rest day, a dash for a missed day, an empty dark circle for days before
     the first entry.
   - If there is no week before (first week), it shows "This week" so far instead.
4. **Footer**: "Estimates, not medical advice" on the left, "Wt-tracker" on the right.

**Caption** (sent as the message text with the image):
- "Week 5 weigh-in: 87.5 kg (−0.4). Last week 5 runs, 15.0 km."
- First weigh-in: no "(…)" part. No runs: "Last week no runs." First week: "This week …".

All text in sentence case. Numbers use one decimal ("15.0 km", "87.5 kg").

## Flow

1. The "Weigh-in saved" card on Today gets a **Share with partner** button (≥ 44 dp, under the plan line, above Edit).
   Trend's top bar also has a share icon (next to Settings) for the latest weigh-in, any day.
2. It opens a preview dialog with the card at full width, and **Send** / **Cancel**.
3. **Send** captures the card as drawn (Compose graphics layer → bitmap → PNG in `cacheDir/share/`, overwritten each
   time), then opens Android's share menu: `ACTION_SEND`, type `image/png`, the image through a `FileProvider`,
   the caption as `EXTRA_TEXT`, read permission granted, chooser title "Share progress".
4. WhatsApp shows near the top; after the first send, Android offers the partner's chat directly.

No new permissions, no internet access, no Room or backup change.

## Code layout

**core** (`wt.core.summary`, tested):
- `WeighInProgress` gains `previousDate: LocalDate?` (the weigh-in the change is measured from).
- New `WeekShare.kt`: `fun weekShare(dashboard: Dashboard, date: LocalDate): WeekShare?` (null when the day has no
  weight). `WeekShare` holds:
  - `week`, `totalWeeks`, `weeksToGo`, `date`
  - `kg`, `changeKg`, `previousDate`, `fraction`
  - `planStart: Checkpoint`, `goal: Checkpoint`, `planPoints: List<Checkpoint>` (the trail),
    `weighIns: List<WeightEntry>` (from the plan start to `date`), `planMarkerKg: Double?` (null before the trend wait ends)
  - `strip: List<DayDistance>` (7 days), `stripLabel` ("Last week" or "This week"), `runs`, `km`
  - `caption: String`
- Reuses `weighInProgress`, `goalProgress`, `programWeek()`, `trendWait`, `Dashboard.distance` and `plan.at()`.

**app** (`wt.app.ui`):
- `ShareCard.kt`: `ShareCard(s: WeekShare)` draws the card with `ProgressRing`, a Canvas for the trail, `RestMark` and
  the theme fonts; `ShareDialog(s, onSend, onDismiss)` is the preview.
- `Share.kt`: capture to PNG and the share intent.
- `WeighInSavedCard` gets the button; `LogScreen` builds the `WeekShare` for the saved day.
- `AndroidManifest.xml`: `androidx.core.content.FileProvider` with authority `${applicationId}.share` and
  `res/xml/share_paths.xml` (cache path `share/`).

## Testing

- `WeekShareTest` (core): week 5 normal case (numbers above), first weigh-in (no change, no pill, "This week"),
  before the trend wait (no plan marker), a week without runs (caption "no runs"), a rest day and a before-start day in
  the strip, a re-baselined plan (trail uses the active checkpoints).
- Robolectric screenshots: `2i-share-card` (week 5), `2j-share-card-early` (week 2), `2k-share-preview` (dialog), added
  to the review page.
- On the phone: send to WhatsApp, check the image and caption arrive, and that the partner's chat appears in the share
  menu the second time.

## Release

- Version 0.5.0 (`versionCode` 10).
- HANDOVER: feature line under Today; "View-only partner app" under open items as the planned follow-up.
