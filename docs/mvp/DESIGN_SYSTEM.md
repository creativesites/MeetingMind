# MeetingMind design system (F-1)

Every screen is built only from what is defined here. If a screen needs something that isn't here, add it here
first, then use it. The goal: four home designs, three spaces and dozens of screens that look like one product.

Code lives in `ui/theme` (tokens) and `core/ui` (components). `ColorLiteralGuardTest` already enforces "no colour
literals". F-4 extends the guards to font sizes and corner radii.

---

## 1. Character

**Calm, warm, precise.** Paper and ink:

- neutral surfaces
- one accent at a time
- hierarchy carried by type, space and weight, not by colour or shadows
- one memorable element per screen (Von Restorff)

Faith gets warmth through gold and a serif face for scripture. Work gets clarity. Study gets focus.

## 2. Colour

**Role tokens:** keep `MMColors` (`ui/theme/Tokens.kt`). The roles are right, and Paper and Midnight Graphite are good
palettes.

**Accent personalization (F-6).** `accent`, `accentWash` and `onAccent` come from the user's chosen accent, not the
palette. Each accent swatch defines a light value and a dark value. Washes are derived with fixed alpha: 10% in light,
15% in dark. `onAccent` comes from contrast.

| Accent | Light | Dark | Notes |
|---|---|---|---|
| Indigo (default) | `#5B5BD6` | `#9B9CF6` | current |
| Ocean | `#0E7490` | `#5CC8DD` | |
| Forest | `#15803D` | `#5FD08A` | |
| Gold | `#996515` | `#E0B25A` | Faith default (light value darkened from #B7791F to meet 4.5:1 on Paper) |
| Rose | `#BE185D` | `#F27AAE` | |
| Graphite | `#3F3F46` | `#C9CDD6` | monochrome |

**Rules:**

- **Space colour.** A space's default accent applies only when the user hasn't chosen one: Faith uses Gold, Work
  Indigo, Study Ocean, Everyday Indigo. Per-space accents are an Advanced option.
- **Semantic colours** (`success`, `warning`, `danger`, `recording`, `gold`, `speaker*`) never follow the accent.
- **Contrast.** Every text-on-surface pair meets WCAG AA at 4.5:1; large text needs 3:1. Each new accent swatch is
  verified with a unit test (contrast of `accent` on `surface` and on `background` in both themes).
- **Retire:** `CleanMac*`, the legacy `Light*`/`Dark*` surfaces, gradient brushes, the private `Slate` colours, and
  `forTheme()` (once F-4 finishes).

## 3. Type

**Fonts.** Inter is already bundled in `res/font`; the theme currently uses `FontFamily.Default`. Use Inter for all UI.
Use **Outfit 600** only for Display, and **Serif** (the system serif, or Literata later) only for scripture and
devotional *body content*, never for screen titles.

| Token (`MMType.*`) | Size/line | Weight | Use |
|---|---|---|---|
| `display` | 30/36 | Outfit 600 | Home greeting, onboarding headline. One per screen at most. |
| `title` | 22/28 | Inter 600 | Screen title |
| `heading` | 17/24 | Inter 600 | Section header, card title |
| `body` | 15/22 | Inter 400 | Default text, note text |
| `bodyStrong` | 15/22 | Inter 500 | List-row title |
| `secondary` | 13/18 | Inter 400 | Previews, subtitles |
| `caption` | 12/16 | Inter 500 | Metadata, timestamps, chips |
| `overline` | 11/14 +0.6 tracking | Inter 600, caps | Rare. Small labels above a hero only. |
| `scripture` | 17/28 | Serif 400 | Verse text, devotional reading |

That makes 9 tokens, replacing about 20 ad-hoc sizes. Text-size personalization multiplies all of them by
0.9, 1.0, 1.15 or 1.3. All sizes are in `sp`, so the system font scale still applies.

`MaterialTheme.typography` is mapped onto these, so Material components inherit them.

## 4. Space, shape, elevation

**Spacing (`MMSpace`).**

| Token | Value |
|---|---|
| `xs` | 4 |
| `s` | 8 |
| `m` | 12 |
| `l` | 16 |
| `xl` | 24 |
| `xxl` | 32 |

- Screen gutter: `l` (16) everywhere.
- Gap between sections: `xl`.
- Card padding: `l`.
- Gap between rows inside a card: `s`/`m`.
- Density setting (Advanced): Comfortable is the default; Compact reduces `m`/`l`/`xl` by one step.

**Radius (`MMRadius`).**

| Token | Value | Use |
|---|---|---|
| `small` | 8 | chips, inputs, thumbnails |
| `card` | 16 | cards, sheets, dialogs |
| `pill` | 50% | buttons, pills, the record button |

Nothing else.

**Elevation.**

- Flat by default.
- Cards are separated by a 1 dp `line` border in light. In dark they are separated by the `surface` vs `background`
  step, with no border.
- Only floating chrome gets a shadow: the bottom bar, sheets, the mini-player and snackbars. They share one shadow
  (8 dp, 8% opacity).

## 5. Iconography

- Material Symbols **Rounded, outlined** at 24 dp (20 dp inside chips and rows).
- Filled only for the selected bottom-bar item and toggled states.
- Icon colour is `inkSecondary` by default, `accent` only on the screen's one primary affordance.

## 6. Motion (`MMMotion`)

| Token | Value | Use |
|---|---|---|
| `quick` | 150 ms | Press and toggle |
| `standard` | 250 ms, emphasized easing | Content changes, expand and collapse |
| `enter` | 300 ms | Sheets and screens |
| `spring` | dampingRatio 0.8, stiffness medium-low | Bar indicator, Mimi |

**Rules:**

- No ambient or infinite animation on any screen at idle. Mimi's breathing is the single exception, and it pauses
  when not visible.
- Every animation respects the system animator duration scale. "Remove animations" switches to static end states.

## 7. Surfaces: exactly four kinds

| Surface | Look | Use |
|---|---|---|
| **Card** | `surface`, `card` radius, line border (light) | Grouped content |
| **Hero** | `accentWash` fill (or the Faith scripture treatment), `card` radius, no border | **The one** primary element of a screen: Next up, Today's Word, Pulse, Revise today |
| **Inset** | `surfaceSunk`, `small` radius | Fields and nested panels inside a card |
| **Sheet** | `surfaceRaised`, top `card` radius, floating shadow | Bottom sheets and menus |

Full-bleed images are allowed only inside a Hero (for example a scripture background) and in Spark cards.

## 8. Components (`core/ui`)

`ScreenHeader(title, actions)` · `HomeHeader(greeting, subtitle, mimi, actions)` · `SectionHeader(title, count?, action?)` ·
`MMCard` · `HeroCard` · `InsetPanel` · `ListRow(leading, title, subtitle, meta, trailing)` · `NoteRow(note)` ·
`PrimaryButton` · `SecondaryButton` · `TextAction` · `MMChip` / `FilterChipRow` · `SegmentedControl` ·
`EmptyState(mimiState, title, body, action)` · `ProcessingPill` · `StatusLine(kind, text, action)` (the honest
fallback line) · `SelectableBody` (F-7) · `ReadAloudButton` (F-7).

**Rules:**

- **Section headers.** A section header carries the count ("Tasks · 5") and a single "All →" action. No stat tiles.
- **Empty sections** return nothing. Empty *screens* use `EmptyState`.
- **The 48 dp rule.** Every tappable element has at least a 48 dp target.

## 9. Home designs on one scaffold

`HomeScaffold(header, hero, sections)` controls gutter, spacing, scrolling and pull-to-refresh. Each home design is a
list of section components. They differ in **content and hero treatment**, never in spacing, type or card style.

| Home | Hero | Character |
|---|---|---|
| Everyday | Next up (event or last note) | User accent |
| Faith | Scripture or quote of the day (user-selectable). A full-bleed image is allowed here, with serif scripture | Gold, warm |
| Work | Pulse: next meeting with Prepare/Record | Indigo, dense and clear |
| Study | Revise today: due cards and quiz | Ocean, focused |

**Recent notes** appears on every home, as `NoteRow` ×5.

## 10. Definition of done for any screen

1. It uses only tokens and components from this document. The F-4 guard tests stay green.
2. It answers the four questions. There is one hero at most.
3. It looks right in light and dark, with all 6 accents, at text size 1.3, and with a 200% font scale.
4. Every text block of user content is selectable (F-7).
5. It has no idle animation. Reduced motion is respected.

## 11. Implementation notes (F-2, merged)

**Access.** Read everything through the `MM` object: `MM.type`, `MM.space`, `MM.radius`, `MM.elevation`,
`MM.motion`, `MM.colors` and `MM.size`. The KDoc is in `ui/theme/DesignSystem.kt`.

**Theme parameters.** `MeetMindTheme` takes `accent`, `spaceDensity` and `textScale`.

**Additions beyond the spec:**

- `MMSize`: the 48 dp touch target, icon sizes and the hairline.
- `MM.radius.sheet`.

**Components** live in `core/ui/mm/`: `MMSurfaces`, `MMButtons`, `MMHeaders`, `MMRows`, `MMChoice`, `MMFeedback` and
`HomeScaffold`. Screenshots are in `app/src/test/screenshots/mm/`, recorded with `-Proborazzi.test.record=true`.

**Known follow-ups:**

- The legacy `AccentChoice`/`Appearance` accent still exists. F-6 merges it into `MMAccent`.
- `ProcessingPill` and `SelectableBody` (F-7) are not yet wired.
