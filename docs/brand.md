# The mark

Three arcs rising like sound waves — a library that plays. It takes Spotify's
signal as a reference point and keeps the app's own character: near-black tile,
the theme accent, and golden-ratio proportions instead of a fixed grid.

## Construction

Built on a 512 grid, anchored lower-left so the wave bleeds off the tile the way
a real sound mark should.

| Part | Value | Derivation |
|---|---|---|
| Outer radius | 330 | fills the tile |
| Middle radius | 204 | `330 / φ` |
| Inner radius | 126 | `330 / φ²` |
| Stroke | 44 | equal across all three arcs |
| Arc span | −150° → −30° | 120°, open to the upper-right |
| Anchor | (200, 430) | lower-left of the content box |

Equal stroke is deliberate: tapering the inner arcs to `44/φ`, `44/φ²` reads more
"designed" but the thin arcs vanish below 32 px. Equal weight keeps all three
waves legible down to a 16 px favicon.

## Rules the mark follows

1. **One colour.** Accent on the near-black tile; the same shape in white for the
   status-bar glyph and black on white for monochrome use.
2. **Golden radii.** The three circles are `R`, `R/φ`, `R/φ²`, so the eye reads a
   harmonic decay rather than arbitrary spacing.
3. **Bleed is the signature.** The wave is anchored off the tile, which is what
   makes it a wave and not three rings in a box.
4. **Scalable.** At 16 px it reads as two or three rising lines; the identity is in
   the arc gesture, not in fine detail.
5. **One source of truth.** `apps/web/public/logo.svg`; the Android foreground and
   the notification glyph are the same geometry scaled to 108 and 24.

## Files

- `apps/web/public/logo.svg` — source of truth, and the favicon.
- `apps/web/public/logo.png` — 512 px render for Apple touch icons and docs.
- `apps/mobile/app/src/main/res/drawable/ic_logo_fg.xml` — adaptive-icon
  foreground, same geometry scaled to 108.
- `apps/mobile/app/src/main/res/drawable/ic_stat_mm.xml` — status-bar glyph at 24.
