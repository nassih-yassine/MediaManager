# The mark

A bold `M` — MediaManager's initial — whose counter is cut to a single point.
It takes Spotify's confidence (one flat shape, one colour, no outline) without
copying their arcs: the letter is ours, and the pointed counter is the signature.

## Construction

A 320 × 320 box on the 512 grid, every part from the box and φ.

| Part | Value | Derivation |
|---|---|---|
| Box | 320 × 320 | content box |
| Corner radius | 24 | visual rounding, kept small so it stays bold |
| Stem width | 76 | `320 / φ³` |
| Counter apex | y = 294 | `96 + 320 / φ` |

The counter is one triangle from the two inner stem edges to that apex, cut with
`fill-rule="evenodd"`, so it is a real hole — the mark works on any background,
not just the near-black tile.

## Rules the mark follows

1. **One shape.** A box minus one triangle; no strokes, no gradients, no shadow.
2. **One colour.** Accent on the tile; white for the status bar; black for
   monochrome print.
3. **Golden ratios.** Stem and counter are `320/φ³` and `320/φ`, so the letter's
   weight and its open mouth stay in proportion at any scale.
4. **Scalable.** Below 32 px the pointed counter starts to close and the mark
   reads as a solid M — a simplification of the same letter, not a second logo.
5. **One source of truth.** `apps/web/public/logo.svg`; the Android foreground and
   the notification glyph are the same geometry scaled to 108 and 24.

## Files

- `apps/web/public/logo.svg` — source of truth, and the favicon.
- `apps/web/public/logo.png` — 512 px render for Apple touch icons and docs.
- `apps/mobile/app/src/main/res/drawable/ic_logo_fg.xml` — adaptive-icon
  foreground, same geometry scaled to 108.
- `apps/mobile/app/src/main/res/drawable/ic_stat_mm.xml` — status-bar glyph at 24.
