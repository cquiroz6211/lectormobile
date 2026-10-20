# Changelog — LectorMobile

## [1.0.0] — 2025-10-19

### Added
- Initial release
- PDF document reading with pinch-to-zoom and swipe navigation
- EPUB 2.0 and EPUB 3.0 reading with chapter navigation
- Full table of contents extraction from EPUB files
- Dark mode as default theme (optimized for night reading)
- Bookmarks system with SQLite persistence
- Automatic reading position saving
- Progress tracking per book
- Adjustable font sizes for EPUB reading
- File picker for opening PDF/EPUB files
- Intent handling for sharing PDF/EPUB to the app
- Material Design 3 UI with smooth animations
- Chapter navigation for EPUB books

### Technical
- 2.6MB release APK (minified and optimized)
- Android 7.0+ support (API 24+)
- arm64-v8a and armeabi-v7a native libraries
- AndroidPdfViewer (PDFium-based) for PDF rendering
- Custom EPUB parser with NCX and nav.xhtml support
- SQLite database for bookmarks
- No network permissions required

---

**Version 1.0.0** — First release. Built for quality reading.