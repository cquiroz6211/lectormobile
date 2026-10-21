# paginated-reading — Modo Paginado y Conteo de Páginas (v1.5.0)

Evolución del lector solicitada por el usuario:
«en este momento el scroll es vertical, no por pagina sino por capitulo. no se el libro cuantas paginas tiene»

## Specs

- **S1** — **Estimador y conteo de páginas del libro (EPUB y PDF)**:
  - Clase pura Java `BookPageEstimator` (test-first con JUnit):
    - Para EPUB: estándar de la industria (1.024 caracteres de texto plano por página sintética). Calcula caracteres limpios (sin tags HTML) por capítulo y acumulados (`cumulativeChars`).
    - `totalBookPages`: total de caracteres / 1024 (mínimo 1).
    - `currentBookPage(chapterIndex, scrollFraction)`: página global 1..totalBookPages dentro del libro.
    - Para PDF: páginas reales nativas del documento (`currentPage + 1 / totalPages`).
  - Cálculo en segundo plano sin bloquear el hilo principal ni la apertura del libro.
- **S2** — **Modo paginado horizontal para EPUB**:
  - Layout CSS multi-columna en WebView (`column-width: 100vw; height: 100vh; column-gap: 0; box-sizing: border-box; overflow: hidden;`).
  - Navegación horizontal:
    - Tap en borde derecho (o swipe a la izquierda): avanzar una página de pantalla.
    - Tap en borde izquierdo (o swipe a la derecha): retroceder una página de pantalla.
    - Tap en zona central (35%–65%): alternar controles (modo inmersivo), respetando enlaces, notas y selecciones activas.
  - Conexión entre capítulos: al pasar de la última página del capítulo avanza al siguiente capítulo (página 0); al retroceder de la primera página, va al capítulo anterior (última página).
  - Cálculo y reporte de pantallas en el capítulo: `totalScreens = Math.ceil(scrollWidth / innerWidth)`.
- **S3** — **Visualización clara de páginas y progreso**:
  - En el visor (`tvProgress`):
    - En EPUB paginado: muestra `Pág. A de B · Cap. C (X / Y)` o `Página A de B · Z%`.
    - En PDF: `Página X de Y · Z%`.
  - En diálogo de salto («Ir a…»): en EPUB, permitir saltar por número de página global (1..B) o por capítulo.
- **S4** — **Preferencia de modo de navegación (Ajustes)**:
  - Nueva opción en Ajustes («Modo de navegación» / «Paginación»):
    - `paginated` (Paginado horizontal, por defecto).
    - `continuous` (Scroll vertical continuo).
  - Sincronización en `SharedPreferences` y aplicación inmediata en el lector.
- **S5** — **Paginación horizontal en PDF**:
  - En PDF, si el modo de navegación es paginado, activar `swipeHorizontal(true)` y `pageSnap(true)` en `PDFView` para que la lectura pase página por página como un libro real. En modo continuo, mantener `swipeHorizontal(false)`.

## Tasks

| ID | Specs | Route | Commit |
|----|-------|-------|--------|
| T1 | S1 | `BookPageEstimator` (test-first JUnit) + cálculo de páginas | `5792b72` |
| T2 | S2 | CSS multi-columna + JS bridge paginación (tap zones, swipe, límites de capítulo) | `5792b72` |
| T3 | S3 | Indicador de páginas en visor y diálogo de salto «Ir a…» adaptado a páginas globales | `5792b72` |
| T4 | S4 | Preferencia de navegación en `SettingsActivity` y menú del lector (paginado vs continuo) | `5792b72` |
| T5 | S5 | Paginación horizontal con page snap en PDF (`ReaderActivity`) | `5792b72` |
| T6 | S1-S5 | Docs (CHANGELOG, BACKLOG, README) y version bump a v1.5.0 (versionCode 10) | `5792b72` |
| T7 | S1-S5 | Verificación completa: `test`, `lintRelease`, APK firmado e instalación | `5792b72` |

## Log

- **L1** — «¿cual es el siguiente paso para mejorar el producto?»
- **L2** — «prefiero irnos por otro camino, en este momento el scroll es vertical, no por pagina sino por capitulo. no se el libro cuantas paginas tiene»
- **L3** — «correcto. ese es el cambio que busco»
