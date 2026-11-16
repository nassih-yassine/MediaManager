# The mark

A card with one corner turned, and a play mark cut through it. The card is the
thing you keep; the play is the thing it does. Everything is one colour, so the
mark survives a circular launcher mask, a 16 px favicon and a monochrome
notification icon without a second version.

## Construction

Built on a 512 grid with a 320 content box, every part derived from it and φ.

| Part | Value | Derivation |
|---|---|---|
| Card | 320 × 320 | content box |
| Corner radius | 76 | `320 / φ³` |
| Turned corner | 47 | `76 / φ` |
| Fold inner radius | 11 | `47 / φ³` |
| Play height | 159 | `320 / φ^1.45` |
| Play stroke | 16 | `47 / φ²`, rounds the triangle's points |

The play is **not** centred in its bounding box: it is nudged right so its mass
sits at the card's optical centre. A triangle centred by geometry always looks
like it leans left.

## Rules the mark follows

1. **One silhouette.** Three shapes, one fill, no strokes, no gradient, no
   shadows. It reads as a shape, not a picture.
2. **One colour.** It works on any accent from the six theme families, in black
   on white, and as a white-on-transparent status-bar glyph.
3. **Fixed proportions.** Change the size, never the ratios; all values scale
   from the 320 content box.
4. **Mask-safe.** The Android foreground keeps its content inside the 66 dp safe
   circle (`52 / 108` of the canvas), so launchers may crop to a circle, squircle
   or rounded square without shaving the card.
5. **Degrades deliberately.** Below 32 px the fold disappears and the mark reads
   as a card with a play — the small-size version is a simplification of the same
   shape, not a different logo.

## Files

- `apps/web/public/logo.svg` — source of truth, and the favicon.
- `apps/web/public/logo.png` — 512 px render for Apple touch icons and docs.
- `apps/mobile/app/src/main/res/drawable/ic_logo_fg.xml` — adaptive-icon
  foreground, same geometry scaled to 108.
- `apps/mobile/app/src/main/res/drawable/ic_stat_mm.xml` — status-bar glyph,
  simplified to one path for 24 dp.
