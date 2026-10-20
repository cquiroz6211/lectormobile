# Lector — PDF & EPUB Reader

A fast, minimal PDF and EPUB reader for Android with dark mode and bookmarks.

## Features

- 📖 **PDF Reading** — Full PDF support with pinch-to-zoom and swipe navigation
- 📚 **EPUB Reading** — EPUB 2/3 support with chapter navigation and TOC
- 🌙 **Dark Mode** — Default dark theme optimized for night reading
- 🔖 **Bookmarks** — Save and manage bookmarks with SQLite persistence
- 📄 **Progress Tracking** — Automatic reading position saving
- 🔍 **Search** — Filter books by name
- ⚡ **Lightweight** — 2.7MB APK, instant loading
- 🔒 **Privacy** — No ads, no tracking, no internet needed

## Screenshots

1. Main library screen with book list and search
2. PDF reader with dark theme
3. EPUB reader with chapter navigation
4. Bookmarks management
5. Settings screen

## System Requirements

- Android 7.0 (API 24) or higher
- Available on phones and tablets

## APK Sizes

| Build | Size |
|-------|------|
| Debug | 6.4 MB |
| Release (minified) | 2.7 MB |
| AAB (Play Store) | 2.1 MB |

## Technical Details

- **Min SDK**: 24 (Android 7.0 Nougat)
- **Target SDK**: 34 (Android 14)
- **Architecture**: arm64-v8a, armeabi-v7a
- **PDF Rendering**: AndroidPdfViewer (PDFium-based) v2.0.2
- **EPUB Parsing**: Custom parser with TOC support
- **UI**: Material Design 3, Dark theme default
- **Database**: SQLite for bookmarks and reading history

## Building

```bash
# Clone repository
git clone https://github.com/yourusername/lectormobile.git
cd lectormobile

# Build debug APK (for testing)
./gradlew assembleDebug

# Build release APK (for distribution)
./gradlew assembleRelease

# Build AAB (for Play Store upload)
./gradlew bundleRelease
```

## APK Verification

```bash
# Verify APK signature
apksigner verify --verbose app/build/outputs/apk/release/LectorMobile-1.0.0.apk

# Check APK contents
unzip -l app/build/outputs/apk/release/LectorMobile-1.0.0.apk | grep -E "\.class$|\.so$"
```

## Publishing to Google Play

1. Upload `app/build/outputs/bundle/release/app-release.aab` to Google Play Console
2. Create app listing with:
   - Title: Lector — PDF & EPUB Reader
   - Short description: Fast, minimal PDF and EPUB reader with dark mode
   - Full description: See PLAYSTORE.md in project root
   - Screenshots: Generate from emulator (5 recommended)
   - Icon: Provided in app/src/main/res/mipmap-* directories
   - Category: Books & Reference
3. Submit for review

## Library Versions (Verified)

| Library | Version | Source |
|---------|---------|--------|
| AndroidPdfViewer | 2.0.2 | JitPack (verified with Context7) |
| appcompat | 1.6.1 | Maven Central |
| material | 1.12.0 | Maven Central |
| recyclerview | 1.3.2 | Maven Central |
| constraintlayout | 2.1.4 | Maven Central |
| swiperefreshlayout | 1.1.0 | Maven Central |
| preference | 1.2.1 | Maven Central |

## License

MIT License — Free for personal and commercial use.

## Support

For issues, feature requests, or questions, please open a GitHub issue.

---

Made with ❤️ for book lovers everywhere.