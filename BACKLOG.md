# LectorMobile — Backlog y estado del proyecto

## Estado actual (2026-10-20)

**Versión 1.5.0 (versionCode 10)** — Modo lectura paginado horizontal y conteo
estándar de páginas del libro:
- Paginación horizontal EPUB por CSS multi-columna con paso de página por tap
  en bordes y swipe táctil, más transición automática entre capítulos.
- Conteo y estimación estándar de páginas (`BookPageEstimator`, 1.024 caracteres
  por página, estándar Adobe/EPUB) y salto rápido adaptado a páginas globales.
- Paginación horizontal con page snap en PDF.
- Selector de modo de navegación (Paginado horizontal vs Scroll continuo) en
  Ajustes y menú del lector.
La 1.4.3 (versionCode 9) trajo optimización de rendimiento: extracción de
EPUB cacheada, portadas en caché LRU 8 MB y escritura SQLite en hilo FIFO.
La 1.4.2 (versionCode 8) trajo modo lectura inmersivo, tema sepia/cálido y
salto rápido a página/capítulo.
La 1.4.1 (versionCode 7) corrigió la inyección de acciones de selección en ActionModes primarios.
La 1.4.0 (versionCode 6) trajo subrayado y notas en EPUB.
La 1.3.0 (versionCode 5) trajo portadas reales en la biblioteca.
La 1.2.0 (versionCode 4) trajo el modo oscuro real para PDF.
La 1.1.0 (versionCode 3) trajo retomar lectura exacta + progreso visible.
La 1.0.1 (versionCode 2) fue APK release firmado, zipaligned e instalado y
probado en un emulador ARM64 con Android 16 (API 37).

### Verificado en ejecución

| Área | Estado | Evidencia |
|------|--------|-----------|
| Instalación del APK | ✅ | `adb install -r` → Success, firma v2+v3 |
| Arranque de la app | ✅ | MainActivity abre sin crash |
| PDF: abrir y renderizar | ✅ | `lector_test.pdf` (3 páginas) renderizado con pdfium 1.9.5 |
| PDF: navegación | ✅ | Seekbar salta a página 3, contador «Página 3 / 3» |
| PDF: posición persistente | ✅ | Al reabrir vuelve a la página 3 |
| EPUB: abrir y renderizar | ✅ | `lector_test.epub` (3 capítulos) en modo oscuro |
| EPUB: orden de lectura | ✅ | Spine del OPF, rutas absolutas |
| EPUB: índice (TOC) | ✅ | Diálogo con títulos de `nav.xhtml`, navegación por `href` |
| EPUB: navegación | ✅ | Seekbar capítulo 2/3 y título desde el TOC |
| EPUB: posición persistente | ✅ | Al reabrir vuelve al capítulo 2 |
| EPUB: tamaño de fuente | ✅ | Ajuste 18sp → 24sp aplicado y persistido |
| Importar libros (SAF) | ✅ | Selector acepta PDF y EPUB (`OpenDocument`, varios MIME) |
| Biblioteca persistente | ✅ | Copias en `filesDir/books`, sobreviven reinicios |
| Abrir con / compartir | ✅ | `ACTION_VIEW` con `content://` importa y abre el libro |
| Marcadores | ✅ | Alta/baja, lista con página/capítulo, salto al punto, estado vacío |
| Ajustes | ✅ | Tema oscuro, fondo de lectura, tamaño de fuente, versión real |
| Menú principal | ✅ | Buscar, ajustes y marcadores abren sus pantallas |
| Borrar libro | ✅ | Confirmación, borra copia interna, marcadores, posición e historial |
| Extracción EPUB cacheada (1.4.3) | ✅ | 1.ª apertura «EPUB extracted to cache»; reaperturas «EPUB extraction cache hit» con la misma clave; miss reproducido tras limpiar el caché en el dispositivo |
| Portadas en caché de memoria (1.4.3) | ✅ | portada real (Don Quijote) pintada en ~10 reingresos a la biblioteca, sin re-decodificado ni crash |
| Posición en hilo de escritura (1.4.3) | ✅ | `page:2` (PDF) y `chapter:89:0.239` (EPUB) persistidos tras onPause; restauración exacta a «Capítulo 90 / 147 · 60%» |
| Crash «connection pool» (fix 1.4.3) | ✅ | el flujo que crasheaba (importar PDF → abrir) ×3 + 9 aperturas de lector: 0 `FATAL EXCEPTION` |
| «Resaltar»/«Nota» (fix 1.4.3) | ✅ | tap real en el menú (overflow ⋮): `HL capture: saved s=40 e=48` → fila `'extrañas'`; nota → fila `'extrañas'` + `prueba nota 1.4.3`; 7 renders con `typeof window.HL == "object"` (antes: `"undefined"` desde el render #2) |
| Tests unitarios y lint (1.4.3) | ✅ | `./gradlew test lintRelease` BUILD SUCCESSFUL, 0 fallos (46 tests × 2 variantes); lint 0 errores, 128 avisos preexistentes |

## Arquitectura

```
app/src/main/java/com/example/lectormobile/
├── MainActivity.java          Biblioteca, búsqueda, importación SAF, intents VIEW/SEND
├── ReaderActivity.java        Lector PDF/EPUB, progreso, posición, marcadores
├── CoverStore.java           Portadas: extracción EPUB/PDF y caché en disco
├── EPUBParser.java            Parser EPUB v3: container.xml, OPF, spine, nav.xhtml, toc.ncx
├── ExtractionCache.java       Caché en disco de extracciones EPUB (LRU, v1.4.3)
├── SizeLruCache.java          Caché LRU con presupuesto en bytes (portadas, v1.4.3)
├── SerialWriter.java          Escritor SQLite de una hebra FIFO (posiciones, v1.4.3)
├── TOCDialog.java             Diálogo del índice EPUB
├── BookmarksActivity.java     Lista de marcadores de todos los libros
├── BookmarksAdapter.java      Adaptador de marcadores
├── BookmarksDBHelper.java     SQLite v3: bookmarks, reading_history, reading_positions
├── SettingsActivity.java      Ajustes (tema, tamaño de fuente, versión)
├── BooksAdapter.java          Lista de libros
└── UriFileCopier.java         Copia content:// → filesDir/books (nombres únicos)

app/src/main/res/
├── layout/                    activity_main, activity_reader, activity_settings,
│                              activity_bookmarks, item_book, item_bookmark
├── menu/                      main_menu, reader_menu
├── values/                    strings (español), colors, themes, styles
└── values-night/              tema oscuro (la app es oscura en ambos modos)
```

### Base de datos (versión 3)

- `bookmarks(id, book_path, bookmark_value, timestamp, label)`
- `reading_history(id, book_path, last_read_at, times_read)`
- `reading_positions(id, book_path UNIQUE, position, updated_at)`

Las posiciones se guardan como `page:N` (PDF, base 0) o `chapter:N` (EPUB).
Las migraciones son aditivas: nunca se borran datos del usuario.

## Librerías

| Librería | Versión | Uso |
|----------|---------|-----|
| `io.github.oothp:android-pdf-viewer` | 3.2.0-beta06 | Visor PDF (fork mantenido de barteksc, mismo paquete) |
| `io.github.oothp:pdfium-android` | 1.9.5-beta01 | Motor PDFium; segmentos LOAD alineados a 16 KB |
| `androidx.appcompat:appcompat` | 1.6.1 | UI base |
| `com.google.android.material:material` | 1.12.0 | Material 3 |
| `androidx.constraintlayout:constraintlayout` | 2.1.4 | Layouts |
| `androidx.recyclerview:recyclerview` | 1.3.2 | Listas |
| `androidx.preference:preference` | 1.2.1 | Preferencias |
| `androidx.activity:activity` (transitiva) | — | `ActivityResultContracts` |

> La dependencia transitiva `androidx.core:core:1.16.0` se excluye del visor PDF
> para no forzar compileSdk 35/AGP 8.6; el visor solo usa APIs básicas y funciona
> con la versión de core que ya aporta AppCompat.

## Compilación y firma

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export ANDROID_HOME="/Users/usuario/Library/Android/sdk"

./gradlew assembleRelease

$ANDROID_HOME/build-tools/36.0.0/zipalign -f 4 \
  app/build/outputs/apk/release/app-release-unsigned.apk aligned.apk

$ANDROID_HOME/build-tools/36.0.0/apksigner sign \
  --ks lectormobile.keystore --ks-key-alias lectormobile \
  --ks-pass pass:android --key-pass pass:android \
  --v2-signing-enabled true --v3-signing-enabled true aligned.apk
```

## Pendientes antes de publicar en Play Store

- [ ] Actualizar a `targetSdk 35/36` y AGP 8.6+ (requisito de Play para 2025).
  Requiere `compileSdk 35+`; hoy el proyecto compila con AGP 8.2.2 / compileSdk 34.
- [ ] Revisar el aviso de 16 KB en dispositivos con páginas de 16 KB. En el
  emulador API 37 aparece «RELRO segment not aligned» para `libjniPdfium.so` y
  `libmodpdfium.so` aun con pdfium 1.9.5 (LOAD a 0x4000). No afecta a teléfonos
  con páginas de 4 KB; hay que validarlo con un build NDK actualizado o un
  dispositivo 16 KB antes de apuntar a targetSdk 35+.
- [ ] Subir AAB y activar Play App Signing.
- [ ] Icono adaptativo (hoy hay PNGs de launcher), capturas y ficha de Play.
- [ ] QA final en dispositivo físico (instalación, PDF y EPUB reales).
- [ ] (Opcional) Escaneo de almacenamiento compartido: en Android 13+ el acceso
  directo está limitado; la vía soportada es importar con el selector SAF.

## Limitaciones conocidas

- El caché de extracción EPUB (`filesDir/epub_cache`) conserva hasta 6 libros;
  al borrar un libro su entrada cacheada no se elimina de inmediato, se libera
  por LRU. Solo ocupa datos derivados (se regeneran abriendo el libro).
- No hay soporte de DRM (Adobe/Readium) ni de EPUB con fuentes cifradas.
- El escaneo de almacenamiento compartido es «best effort» y solo funciona en
  versiones con permiso de lectura concedido.
