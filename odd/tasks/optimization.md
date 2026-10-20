# optimization — Optimización de rendimiento (v1.4.3)

Tres mejoras de rendimiento elegidas tras revisar la documentación de Android
(developer.android.com: startup/WebView, SQLite fuera del hilo principal,
LruCache de bitmaps).
Rama: `feat/optimization`.

## Specs

- **S1** — **Caché de extracción EPUB (abrir sin re-descomprimir)**: al abrir un
  EPUB, si `filesDir/epub_cache/<clave>` existe y está marcado como completo, se
  reutiliza sin volver a descomprimir el zip. La clave identifica el libro por
  tamaño + fecha de modificación; si el archivo cambia, la clave cambia y se
  extrae de nuevo. Un directorio sin marcador `.ok` (extracción interrumpida) se
  descarta y se vuelve a extraer. El caché vive en `filesDir` (sobrevive a la
  limpieza de `cacheDir` por el sistema), nunca supera 6 entradas (LRU por
  último uso; las más viejas se borran) y solo guarda datos derivados:
  jamás se borran libros, posiciones, marcadores ni anotaciones. Los directorios
  `epub_*` heredados en `cacheDir` se limpian al abrir como hoy.
- **S2** — **Caché en memoria de portadas**: `BooksAdapter` no vuelve a
  decodificar el bitmap de la portada en cada `onBindViewHolder`: usa una caché
  LRU en memoria con tamaño máximo en bytes (8 MB) y sizer por `byteCount`. El
  decode sigue en hilo de fondo y el bind sigue anclándose al holder
  (tag del libro); los bitmaps cacheados nunca se reciclan.
- **S3** — **Escrituras de posición fuera del hilo principal**: el guardado
  debounceado (700 ms) de la posición de lectura (PDF y EPUB) se encola en un
  escritor de una sola hebra con orden FIFO; el cálculo de la posición sigue en
  el hilo principal (lee el `WebView`/`PDFView`). Al pausar (`onPause`) se
  encola el valor fresco y se espera (`flush` con timeout de 2 s) a que todas
  las escrituras pendientes terminen antes de cerrar; `onDestroy` drena y apaga
  el escritor **antes** de cerrar la base de datos. Ninguna escritura se pierde
  ni llega fuera de orden. Una escritura que falla se registra y se salta sin
  tumbar la app ni bloquear las siguientes.

## Tasks

| ID | Specs | Route | Commit |
|----|-------|-------|--------|
| T1 | S1 | `ExtractionCache` (test-first) + wiring en `ReaderActivity` | `39e9b44` |
| T2 | S2 | `SizeLruCache` (test-first) + wiring en `BooksAdapter` | `c29cca9` |
| T3 | S3 | `SerialWriter` (test-first) + wiring en `ReaderActivity` | — |
| T4 | S1-S3 | CHANGELOG, BACKLOG, README y bump 1.4.3 (versionCode 9) | — |
| T5 | S1-S3 | verificación: `test`, `lintRelease`, APK en emulador | — |

Test-first aplicable a los tres núcleos: `ExtractionCache`, `SizeLruCache` y
`SerialWriter` son clases puras Java (sin Android), unit-testeadas con JUnit.
El wiring en Activities se verifica funcionalmente (T5).

## Log

- **L1** — "que deberiamos hacer para la version 1.4.3 ? ¿cual es el siguiente paso de la app que mas valor da?"
- **L2** — Decisión del usuario vía ask_user_question: "centremonos en optimizacion. busca en documentacion y hagamos mejoras de la app."
- **L3** — Documentación consultada: (a) Android Developers 2025 — startup
  profiles/baseline y `startUpWebView` de androidx.webkit para no inicializar
  WebView en el hilo principal (postergado: exigiría subir compileSdk/AGP);
  (b) mantener SQLite/disco fuera del hilo principal + StrictMode para
  detectar violaciones; (c) `LruCache` de bitmaps y evitar decodificar en
  `onBindViewHolder` por el reciclaje de holders. Lint (`./gradlew lintRelease`)
  pasa con 0 avisos.
- **L4** — Hallazgos en el código: (1) `ReaderActivity` borra la extracción
  entera y re-descomprime el EPUB completo en **cada** apertura (limitación #1
  del backlog); (2) `BooksAdapter.loadCover` re-decodifica el archivo de portada
  en cada bind sin caché en memoria; (3) `saveCurrentReadingPosition` escribe
  SQLite en el hilo principal vía el Handler del debounce.
- **L5** — Frontera de slices: slice 1 = T1 + T2 (`39e9b44`, `c29cca9`);
  desde T3 en adelante va en el slice 2 (docs + bump + verificación cerran la
  feature).
