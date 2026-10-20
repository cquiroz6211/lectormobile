# App Name: Nightlight Reader

> Alternative: **Lumina** (shorter, same vibe)

## Tagline

> **Read in the dark, without the strain.**

Short variant for Play Store subtitle: *Dark mode by default · PDF & EPUB · Zero ads*

## Brand Colors

| Role | Hex | Purpose |
|---|---|---|
| **Primary Dark** | `#0D0D0D` | Background (true dark mode base) |
| **Surface** | `#1A1A2E` | Cards, panels, reader controls |
| **Accent** | `#E2A03F` | Warm amber — highlights, active states, icons |
| **Accent Light** | `#F5C77E` | Secondary emphasis, badges, active bookmarks |
| **Text Primary** | `#E8E6E3` | Body text (warm white, not pure white — reduces glare) |
| **Text Secondary** | `#9E9B97` | Metadata, timestamps, secondary labels |
| **Book Cover BG** | `#16162A` | Bookshelf background for contrast |
| **Success** | `#4ECDC4` | "Read 100%", completed chapters |
| **Warning** | `#FF6B6B` | Errors, reading time exceeded alerts |

**Rationale:** The palette centers on a warm-amber accent on a near-black background — mimicking the experience of reading with a physical night light. The text color is deliberately warm white (`#E8E6E3`), not pure white, reducing blue-light glare for night reading. The entire UI should work comfortably at dark mode at 100% — that is the core promise.

## App Icon Concept

**Style:** Minimalist flat icon with a subtle gradient, Material You compatible.

**Description:** A stylized open book whose pages form a soft glowing crescent shape — the negative space between the pages radiates a warm amber glow upward, suggesting light from reading. The book rests on a dark rounded-square background.

**Composition:**
- Dark square (`#0D0D0D` to `#1A1A2E` gradient) base
- Two simple book pages in `#E8E6E3` (thin stroke, not filled)
- A warm amber glow (`#E2A03F` at 60% opacity) emanating from between the pages
- No text in the icon

**Shape language:** Rounded square (Android 14+ adaptive icon standard), 30% corner radius on the book pages for a friendly, modern feel.

**Variants:**
- **Light mode fallback:** Same shape but pages in dark charcoal with white background (rarely used given dark-first strategy)
- **Adaptive icon:** Inner shape is the open-book crescent; outer background is the gradient

## 3-Sentence Brand Story

> Nightlight Reader was born from a simple frustration: every book reader on Android forces you to fight the app just to read. Dark mode that brightens your screen. Cluttered interfaces with a hundred settings you'll never touch. Ads between your chapters. We built the reader we wanted — one that opens instantly, defaults to a warm dark mode that doesn't strain your eyes, and gets out of the way so you can actually read. No account required. No premium wall. Just books and you.

## Play Store Description Draft

### Short Description (80 chars max)

Dark-mode PDF & EPUB reader. Fast, clean, zero ads.

### Long Description

**Read comfortably, day or night.**

Nightlight Reader is a fast, clean book reader for Android that puts your eyes first. Dark mode is the default — always. No bright white screens. No forced settings changes. Just your books, beautifully rendered.

**Supported formats**
- 📄 PDF — fluid reflow for text-based PDFs, fixed layout for scanned documents
- 📖 EPUB & EPUB3 — with full CSS rendering, custom fonts, and line spacing controls
- 🔖 Bookmarks — save your place, organize with tags, search across all bookmarks
- 🌙 True dark mode — warm white text on deep black. Your eyes will thank you at 2 AM.
- ⚡ Instant open — no login, no cloud sync nagging. Open a file and read.
- 📚 Bookshelf — clean library view with cover art, last-read position, and reading progress
- 🔍 Search in book — find any text with highlighted matches
- 📱 Adaptive layout — optimized for phones, tablets, and foldables

**No fluff. No ads. No accounts.**

We stripped away everything that gets in the way of reading. No forced account creation. No premium paywall for basic features. No banner ads between your favorite chapters. Nightlight Reader is free and always will be.

**Built for how you actually read.**

- Custom font size and family
- Line spacing and margin controls
- Page turn animations (slide, fade, or instant)
- Reading time estimates
- Daily reading goals
- Sleep timer (auto-close after X minutes)
- Export bookmarks and notes as text

**Privacy first.**
Your reading data stays on your device. We don't track what you read. We don't collect usage statistics. You are not the product — your books are.

---

### What's New (placeholder for first release)
- Initial release: PDF and EPUB reading
- Dark mode default with warm text
- Bookmarks with tags
- Custom font, spacing, and margin settings
- Sleep timer and reading goals
- Clean, distraction-free bookshelf

## Competitor Analysis

| # | App | Rating | Downloads | Key Weaknesses |
|---|---|---|---|---|
| 1 | **Moon+ Reader** | 3.5★ | 10M+ | Lowest-rated major reader; themes don't save on foldables; steep learning curve; UI feels dated and overwhelming; poor adaptation to new screen shapes |
| 2 | **ReadEra** | 4.9★ | 10M+ | Recurring bugs (TTS crashes, auto-scroll-to-end glitch); EPUB-only RTL support broken; requires Premium for Google Drive sync; occasional decoding errors |
| 3 | **Librera Reader** | 4.3★ | 10M+ | Dated/cluttered interface; ads in free version; too many settings overwhelm casual users; heavy app size (107 MB); inconsistent PDF rendering |
| 4 | **Lithium** | 4.0★ | 500K+ | EPUB-only (no PDF); dark mode bug (brightness spikes at night); no dictionary; poor CSS rendering; abandoned/infrequent updates |
| 5 | **KOReader** | 4.1★ | 5M+ (GitHub stars 8K+) | Extremely complex for casual users; buggy on Android phones (designed for e-ink); poor tap-zone accuracy on small screens; steep config curve |
| 6 | **PocketBook** | 4.2★ | 10M+ | Heavy app; sync unreliable between devices; format quirks with certain EPUBs; cluttered library view |
| 7 | **FBReader** | 3.9★ | 30M+ | Ads in free version; interface feels very old; PDF support requires separate plugin; separate premium purchase; weak on custom EPUB CSS |
| 8 | **Kindle** | 4.8★ | 1B+ | Ecosystem lock-in; weak local file support; DRM-protected only for purchased books; limited customization; forced into Amazon store |
| 9 | **Google Play Books** | 4.7★ | 1B+ | Google ecosystem only; limited custom reading settings; upload size limits; no true dark mode (gray instead of black); tied to Google account |
| 10 | **Readest** | 4.3★ | New/Unknown | Newer app; limited Android adoption; still maturing feature set; less stable than established readers |

## Market Gaps & Opportunities

### What's Missing in the Market

1. **True dark mode, done right.** Most readers bolt on dark mode as an afterthought. None make it the default experience with warm text (not harsh white-on-black). Our app makes this the core identity.

2. **Lightweight + fast.** Most readers are bloated (100MB+), slow to launch, and overwhelmed with settings nobody uses. A reader that opens a PDF in under 0.5 seconds would stand out.

3. **Clean UI without clutter.** Competitors bury reading behind 50+ settings pages. Users consistently praise Lithium for its simplicity — but it lacks PDF support and is barely maintained.

4. **No forced account/cloud.** Every major reader pushes you to create an account, sign in, or sync. A local-first reader with zero accounts would earn instant trust.

5. **PDF + EPUB in one clean app.** Most readers that do both do mediocrely at one. The market lacks a reader that does both well with a unified, clean experience.

6. **Ad-free, no premium wall.** Competitors all have "Pro" tiers. An honest free app with optional donation builds strong goodwill.

## Domain Recommendation

### Primary: `nightlight.app`

- Short, memorable, directly tied to the brand name
- `.app` TLD is appropriate for a mobile application
- Easy to spell, no hyphens, no numbers

### Alternatives (if primary unavailable)

1. `nightlightreader.com` — explicit, SEO-friendly but longer
2. `nightlight.ink` — clever play on "reading" + .ink TLD
3. `nightlight.co` — shorter alternative to .com
4. `getnightlight.com` — action-oriented, available as backup

### Play Store Name Strategy

- **App title:** `Nightlight Reader`
- **Subtitle:** `Dark mode PDF & EPUB reader`
- **Keywords for metadata:** `pdf reader, epub reader, book reader, dark mode, night reading, ebook, no ads, bookmark`
- **Category:** Books & Reference > EBooks

## Naming Rationale

**Nightlight Reader** was chosen because:

1. **Evokes the core promise:** Dark mode night reading. When you read at night, you want a "nightlight" — soft light that doesn't blind you.
2. **Memorable and brandable:** Two syllables + one syllable. Easy to say, easy to remember. Not a generic "PDF Reader."
3. **Emotionally warm:** "Nightlight" suggests comfort, safety, and warmth — aligning with the warm-amber brand palette.
4. **Not used in app space:** No major competing reader with this name. Conceptually available on Play Store.
5. **Works globally:** "Nightlight" is understood in English-speaking markets and easily translatable.
6. **Distinct from competitors:** Moon, Lithium, Read, Pocket, Book — all are single words. Two-word compound names are rare in this space.
7. **Domain available:** `.app` and `.ink` domains are likely available, plus `.com` variants.

## Icon Design Specs (for implementation)

```
Outer background: 512x512px, gradient #0D0D0D → #1A1A2E
Book pages: 256x200px, stroke #E8E6E3, 6px width
Page curve radius: 24px
Glow: radial gradient #E2A03F at 40% opacity, centered between pages
Shadow: soft drop shadow on book (8px, 20% opacity)
Export: PNG @1x, @2x, @3x + SVG source
Adaptive foreground: book pages + glow only, centered
Adaptive background: gradient with 30% rounded corners
```

## Additional Naming Notes

**Why NOT these names (and why they were rejected):**

- **Lectormobile** — This is the codename/internal project name but "lector" is too formal/Latin, "mobile" is generic. Better for an internal project than a consumer-facing brand.
- **Lithium** — Already taken by an existing EPUB reader (4.0★, 500K+ downloads)
- **Nightlight** alone — Good but could be confused with baby products. "Nightlight Reader" disambiguates.
- **DarkReader** — Already a well-known browser extension (conflict)
- **PageNight** — Unnatural phrasing, hard to remember
- **Lumina** — Short and nice, but "Lumina" is used by multiple apps across categories; "Lumina Reader" has trademark conflicts

---

*Document generated for the Lectormobile Android project.*
*Package: com.example.lectormobile*
*Last updated: 2025-10-19*