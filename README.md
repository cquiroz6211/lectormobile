# Lector — PDF & EPUB Reader

A fast, minimal PDF and EPUB reader for Android with dark mode and bookmarks.

## Features

- 📖 **PDF Reading** — Full PDF support with pinch-to-zoom and swipe navigation
- 📚 **EPUB Reading** — EPUB 2/3 support with chapter navigation and TOC
- 🌙 **Dark Mode** — Default dark theme optimized for night reading
- 🔖 **Bookmarks** — Save and manage bookmarks with SQLite persistence
- 📄 **Progress Tracking** — Automatic reading position saving with exact EPUB scroll restore
- 📊 **Reading Progress** — Percent indicator in the reader and progress bar per book in the library, sorted by last read
- 🔍 **Search** — Filter books by name
- ⚡ **Lightweight** — Fast local rendering, no accounts or network
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
| Debug | ~16 MB |
| Release universal (arm64-v8a + armeabi-v7a) | ~14 MB |
| AAB (Play Store, split per ABI) | see `bundleRelease` output |

## Technical Details

- **Min SDK**: 24 (Android 7.0 Nougat)
- **Target SDK**: 34 (Android 14)
- **Architecture**: arm64-v8a, armeabi-v7a
- **PDF Rendering**: AndroidPdfViewer 3.2.0-beta06 (PDFium 1.9.5-beta01)
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
apksigner verify --verbose LectorMobile-1.0.1.apk

# Check native libraries
unzip -l LectorMobile-1.0.1.apk | grep "\.so$"
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
| android-pdf-viewer (oothp) | 3.2.0-beta06 | Maven Central |
| pdfium-android (oothp) | 1.9.5-beta01 | Maven Central |
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