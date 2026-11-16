---
name: OpenFSP Demo Shop
description: A fictional Haitian fine grocery built by hand like a Jacmel carnival float, paying through the OpenFSP gateway.
colors:
  mango: "#f08a24"
  mango-ink: "#4a2306"
  sea: "#1d6bb0"
  sea-ink: "#e9f3fc"
  hibiscus: "#bd2a5e"
  hibiscus-ink: "#fff0f5"
  leaf: "#226f3b"
  leaf-ink: "#eefbf1"
  sun: "#f3c21a"
  sun-ink: "#3b2c00"
  ground: "#ece4d2"
  newsprint: "#f7f2e6"
  ink: "#1b1916"
  ink-soft: "#57524a"
  danger: "#b4232f"
typography:
  display:
    fontFamily: "Bricolage Grotesque Variable, Inter Variable, sans-serif"
    fontSize: "clamp(3rem, 1.8rem + 5vw, 6rem)"
    fontWeight: 800
    lineHeight: 0.95
    letterSpacing: "-0.03em"
  headline:
    fontFamily: "Bricolage Grotesque Variable, Inter Variable, sans-serif"
    fontSize: "clamp(2.4rem, 1.6rem + 3vw, 4rem)"
    fontWeight: 800
    lineHeight: 0.95
    letterSpacing: "-0.03em"
  title:
    fontFamily: "Bricolage Grotesque Variable, Inter Variable, sans-serif"
    fontSize: "1.55rem"
    fontWeight: 800
    lineHeight: 0.95
    letterSpacing: "-0.03em"
  price:
    fontFamily: "Bricolage Grotesque Variable, Inter Variable, sans-serif"
    fontSize: "2.4rem"
    fontWeight: 800
    letterSpacing: "-0.02em"
    fontFeature: "tnum"
  body:
    fontFamily: "Inter Variable, sans-serif"
    fontSize: "1.08rem"
    fontWeight: 400
    lineHeight: 1.55
    fontFeature: "tnum"
  label:
    fontFamily: "Inter Variable, sans-serif"
    fontSize: "0.92rem"
    fontWeight: 600
  button:
    fontFamily: "Bricolage Grotesque Variable, Inter Variable, sans-serif"
    fontSize: "1.02rem"
    fontWeight: 700
    letterSpacing: "-0.01em"
rounded:
  button: "3px 6px 4px 7px"
  input: "4px"
  row: "4px 8px 5px 9px"
  patch: "4px 10px 6px 12px"
  plate: "6px 14px 8px 16px"
  chip: "999px 999px 999px 4px"
  pill: "999px"
spacing:
  xs: "0.5rem"
  sm: "0.75rem"
  md: "1rem"
  lg: "1.5rem"
  xl: "2rem"
  2xl: "2.5rem"
  3xl: "3rem"
components:
  button-primary:
    backgroundColor: "{colors.ink}"
    textColor: "{colors.ground}"
    typography: "{typography.button}"
    rounded: "{rounded.button}"
    padding: "0.6rem 1.2rem"
    height: "2.75rem"
  button-small:
    backgroundColor: "{colors.ink}"
    textColor: "{colors.ground}"
    rounded: "{rounded.button}"
    padding: "0.35rem 0.8rem"
    height: "2.25rem"
  chip:
    backgroundColor: "{colors.newsprint}"
    textColor: "{colors.ink}"
    typography: "{typography.label}"
    rounded: "{rounded.chip}"
    padding: "0.3rem 0.85rem"
    height: "2.25rem"
  chip-pressed:
    backgroundColor: "{colors.ink}"
    textColor: "{colors.ground}"
  input:
    backgroundColor: "{colors.newsprint}"
    textColor: "{colors.ink}"
    rounded: "{rounded.input}"
    padding: "0.55rem 0.8rem"
    height: "2.9rem"
  aisle-strip:
    backgroundColor: "{colors.newsprint}"
    textColor: "{colors.ink}"
    typography: "{typography.button}"
    padding: "0.45rem 1.1rem 0.7rem"
    height: "2.75rem"
  product-patch:
    backgroundColor: "{colors.mango}"
    textColor: "{colors.mango-ink}"
    rounded: "{rounded.patch}"
  price-tag:
    backgroundColor: "{colors.sun}"
    textColor: "{colors.sun-ink}"
    rounded: "4px 12px 5px 14px"
    padding: "1rem 1.1rem 1.1rem"
  receipt:
    backgroundColor: "{colors.newsprint}"
    textColor: "{colors.ink}"
    rounded: "4px 9px 0 0"
    padding: "1.5rem 1.5rem 1.25rem"
  status-stamp:
    backgroundColor: "{colors.sun}"
    textColor: "{colors.sun-ink}"
    rounded: "{rounded.plate}"
    padding: "2rem"
---

# Design System: OpenFSP Demo Shop

## Overview

**Creative North Star: "The Kanaval Float"**

The shop is built by hand like a Jacmel carnival float. Every product is a painted object standing on its own sheet of coloured paper, never a photo on a white tile. Sheets are cut, torn and pasted at slight angles over a newsprint ground; the lettering is heavy, uneven and loud, the reading text plain and calm. The world borrows papier-mâché's materials (painted paper, torn edges, paste seams) but keeps the product objects as flat painted poster vectors: that is a confirmed decision, not an unfinished raster pass.

Density is moderate and market-like. Product stalls stagger and tilt; the checkout reads as a short row of ordered stations; prices are set in the display face with tabular numerals so a column of HTG amounts always lines up. Colour comes from five paints that each own an aisle, and ink-black text carries every reading surface.

This world belongs to the shop alone. It shares no visual tokens with the OpenFSP gateway console; only locale, theme switching, money formatting and the status indicator come from the shared design system.

**Key Characteristics:**
- Five paints (mango, sea, hibiscus, leaf, sun) on a newsprint ground, each paint paired with its own ink.
- Torn paper edges (SVG) close the masthead, the hero, the aisle strips and the receipt.
- Small deliberate tilts (-1.5deg to 3deg) on brand, patches, tags, summary and stamps.
- Asymmetric hand-cut corners: no two corner radii equal on paper pieces.
- Heavy Bricolage Grotesque for names and prices, Inter for reading, tabular numerals everywhere.
- Flat painted SVG product objects with a faint printed-line texture and a cast ellipse shadow.

## Colors

A carnival paint box on newsprint: saturated tropical paints, each with a matched ink, over warm cream paper and ink-black type.

### Primary
- **Mango Paint** (`mango`): the featured product's field (the hero) and the coffee aisle; the active nav underline. Carries `mango-ink` text.
- **Ink Black** (`ink`): all reading text, the primary button fill, the pressed chip, the current station.

### Secondary
- **Caribbean Sea** (`sea`): the spirits aisle and the global focus ring. Carries `sea-ink`.
- **Hibiscus Pink** (`hibiscus`): the brand wordmark, the cacao aisle, the input caret and radio accent, failed or expired order stamps. Carries `hibiscus-ink`.

### Tertiary
- **Leaf Green** (`leaf`): the fruit aisle, completed stations, verified webhook checks, the succeeded stamp. Carries `leaf-ink`.
- **Sun Yellow** (`sun`): the price tag, the cart summary, the chosen provider, the pending stamp, the cart count, text selection. Carries `sun-ink`.

### Neutral
- **Newsprint Cream** (`newsprint`): the default paper piece (masthead, receipt, rows, chips, inputs).
- **Kraft Ground** (`ground`): the page beneath all paper; also the text colour on ink buttons.
- **Soft Ink** (`ink-soft`): secondary text, origins, units, inactive nav and stations.
- **Seam** (`rgb(27 25 22 / 0.35)` light, `rgb(242 238 230 / 0.3)` dark): the dashed paste seam above totals and across product art.
- **Danger Red** (`danger`): inline error text only.

Dark theme (`.dark`) keeps the same roles and moves to night paper: ground `#121528`, newsprint `#1d2137`, ink `#f2eee6`, ink-soft `#b9b3a8`, paints lifted (mango `#f39a3c`, sea `#3b8ad0`, hibiscus `#ec5a8d`, leaf `#46a865`, sun `#f6cf3d`) with dark inks, danger `#ff6b76`.

### Named Rules
**The Paint Owns An Aisle Rule.** Each paint is bound to one aisle (coffee mango, cacao hibiscus, fruit leaf, spirits sea, pantry sun) and that binding holds on strips, stall patches, product pages and cart thumbnails. Never recolour a product off its aisle.

**The Paired Ink Rule.** A paint field always uses its own `-ink` token for text, never ink black or white by guess.

**The Paired Contrast Rule.** Every paint is tuned so its paired ink reaches at least 4.5:1, hibiscus 5.2:1 and leaf 5.8:1 in light theme, so body text may sit on any paint. A new paint ships only with an ink that clears the same bar.

## Typography

**Display Font:** Bricolage Grotesque Variable (with Inter Variable, sans-serif), self-hosted via @fontsource-variable
**Body Font:** Inter Variable (with sans-serif)

**Character:** A heavy, slightly wonky grotesque shouting product names like hand-painted float lettering, against a neutral, legible reading face. Numbers in both are tabular.

### Hierarchy
- **Display** (800, clamp(3rem, 1.8rem + 5vw, 6rem), 0.95, -0.03em, balanced wrap): the featured product name in the hero only.
- **Headline** (800, clamp(2.4rem, 1.6rem + 3vw, 4rem)): page titles; product page name runs up to 4.5rem; status stamps clamp(2rem, ..., 3.25rem).
- **Title** (800, 1.55rem to 2.2rem): stall names, section titles (1.6rem to 1.8rem), cart line names (1.3rem).
- **Price** (800, -0.02em, tabular): every money amount, from 1.15rem on cart lines to 2.4rem on the product and summary.
- **Body** (400, 1.08rem to 1.1rem, line-height 1.55 to 1.6, max 34rem to 36rem): descriptions and hints.
- **Label** (600, 0.92rem): field labels, stations, nav, chips, origins.
- **Button** (Bricolage 700, 1.02rem, -0.01em): every button and aisle strip.

### Named Rules
**The Tabular Price Rule.** Every amount is rendered through the Price primitive (display face, 800, tabular numerals) and formatted by the shared money formatter. No hand-formatted HTG strings.

**The Sentence Case Rule.** No uppercase labels or tracked-out small type in the interface. Uppercase lives only inside product art, as printed packaging.

## Layout

A single centred column, `min(72rem, 100% - 2rem)`, under a sticky newsprint masthead whose band extends edge to edge. The hero is a full-bleed mango field with a two-column grid (1.1fr copy, 1fr object); below 48rem the object moves above the copy and the price tag shrinks and moves to the object's lower right.

The catalog is a stall grid: two columns, four from 64rem, with one wide stall spanning two columns at a 2:1 patch. Stalls stagger vertically (alternate stalls drop 2rem to 2.5rem) so the grid never reads as equal cards. Cart, checkout and order pages use a content column beside a fixed side piece (20rem summary, 22rem receipt) and collapse to one column below 56rem. The orders ledger is a six-column row grid that folds to two below 56rem.

Rhythm runs on rem steps: 0.5, 0.75, 1, 1.5, 2, 2.5, 3rem gaps; pages open with 2.5rem top padding.

### Named Rules
**The Stall Not Tile Rule.** Product lists stagger, tilt alternately (-1.2deg / 1deg) and include a wide stall. A uniform equal-card grid is off-world.

## Elevation & Depth

Depth is paper pasted on paper: a hybrid of a short contact line and a soft drop below each piece, plus physical lift on interaction. Torn edges and product art take `drop-shadow` filters so the cut silhouette casts, not the bounding box.

### Shadow Vocabulary
- **Paper** (`0 1px 0 rgb(27 25 22 / 0.18), 0 10px 24px -12px rgb(27 25 22 / 0.45)`; dark `0 1px 0 rgb(0 0 0 / 0.5), 0 14px 30px -14px rgb(0 0 0 / 0.8)`): every paper piece at rest.
- **Button** (`0 6px 14px -6px rgb(0 0 0 / 0.5)`): ink buttons.
- **Lifted** (`0 14px 22px -12px` to `0 16px 24px -12px rgb(0 0 0 / 0.5 to 0.55)`): the chosen provider and the pressed aisle strip.
- **Art cast** (`drop-shadow(0 18px 18px rgb(0 0 0 / 0.25))`): the hero object; inside every product SVG, an ink ellipse at 0.18 opacity.

### Named Rules
**The Lift Not Glow Rule.** Interaction raises paper (translateY -2px to -5px, small rotation change) and deepens its shadow. No glows, no coloured shadows, no hard offset shadows.

## Shapes

Hand-cut paper. Paper pieces use four different corner radii so no corner matches its neighbour (`3px 6px 4px 7px` on buttons up to `6px 14px 8px 16px` on product plates and stamps). Torn edges are two authored jagged SVG paths (400 x 14 viewBox, stretched) filled with `currentColor`, flipped for bottom edges. Chips are pills with one cut corner (`999px 999px 999px 4px`); station dots are near circles (`50% 46% 52% 48%`). Inputs alone keep a regular 4px corner and a 2px ink border, because a field is a printed form, not a paper cutout. Dashed 2px seams mark totals and run across product art.

## Components

### Buttons
Ink stamps: tactile, heavy, slightly crooked on hover.
- **Shape:** hand-cut corners (`rounded.button`), 2.75rem minimum height.
- **Primary:** ink fill, ground text, Bricolage 700, icon gap 0.5rem; the pay button grows to 3.25rem and 1.2rem.
- **Hover / Focus:** rises 2px and rotates -0.6deg over 160ms `cubic-bezier(0.16, 1, 0.3, 1)`; presses down 1px; global focus is a 3px sea outline at 3px offset. Disabled at 0.45 opacity.
- **Ghost:** transparent with a 2px inset `currentColor` ring. **Small:** 2.25rem, 0.92rem.

### Chips
- **Style:** newsprint pill with one cut corner, paper shadow, label type. Used for locale and theme toggles.
- **State:** pressed chips invert to ink fill with ground text.

### Aisle Strips (signature)
Torn strips of aisle paint used as the catalog filter: Bricolage 700 label, square top corners, a torn SVG bottom edge in the strip's own paint, alternate tilts (-1deg / 1.2deg). Hover and pressed straighten and rise 5px; pressed adds the lifted shadow and a 3px ink outline.

### Product Patches (signature)
A square field of the product's aisle paint (2:1 when wide) holding a flat painted SVG object at about 80% width, tilted alternately; hover straightens and lifts 4px over 400ms.

### Cards / Containers
- **Corner Style:** hand-cut radii from `rounded.row` to `rounded.plate`.
- **Background:** newsprint by default; sun for the summary, price tag and chosen provider; status paint on stamps.
- **Shadow Strategy:** Paper at rest (see Elevation & Depth).
- **Border:** none; edges are torn or seamed.
- **Internal Padding:** 0.75rem to 1rem on rows, 1.5rem to 2rem on summary, receipt and stamp.

### Receipt
A newsprint slip with flat bottom corners closed by a torn edge, line items in a flex list, the total above a dashed seam.

### Status Stamp
A tilted (-1deg) paint plate whose colour follows the gateway status: sun pending, leaf succeeded, hibiscus failed, expired or canceled. It always ends with a line naming the status source.

### Inputs / Fields
- **Style:** newsprint fill, 2px ink border, 4px radius, 2.9rem height, hibiscus caret; label 600 at 0.92rem above, soft-ink hint below.
- **Focus:** the global sea outline.
- **Error:** danger text below, 600 weight, announced as an alert.

### Navigation
A sticky newsprint masthead closed by a torn edge with a soft drop. The hibiscus wordmark is tilted -1.5deg; nav links are soft ink, 600, gaining ink colour and a 3px mango underline when active. Tools (language and theme chips, the ink cart button with a sun count badge) sit right and wrap to a full row below 40rem.

### Stations
Checkout progress as numbered near-circle dots: next is soft ink with a ring, current is ink-filled, done is leaf-filled.

## Do's and Don'ts

### Do:
- **Do** put every product on its own aisle-paint field as a flat painted SVG object (240 x 240 viewBox, faint printed-line pattern, ink cast ellipse).
- **Do** close banners, strips and slips with the authored torn edge in the piece's own colour.
- **Do** give paper pieces asymmetric corners and a small tilt between -1.5deg and 3deg.
- **Do** render every amount through the Price primitive, tabular, in the display face.
- **Do** pair every paint with its own ink token, in both themes.
- **Do** respect reduced motion: transitions off, no hover transforms on buttons.

### Don't:
- **Don't** build product grids of equal white cards or use product photography.
- **Don't** render product objects as raster papier-mache; flat painted vectors are the chosen medium.
- **Don't** borrow the gateway console's tokens, components or look; only the shared providers, money formatter and status indicator cross over.
- **Don't** set body-size or small text on hibiscus or leaf fields in light theme.
- **Don't** use uppercase eyebrows, kickers or tracked labels in the interface.
- **Don't** use hard offset shadows or glows; depth is paste and lift.
