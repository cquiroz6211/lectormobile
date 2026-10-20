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

- **S4** — **«Resaltar»/«Nota» en cualquier capítulo (bug del usuario, v1.4.2)**:
  en un EPUB, al seleccionar texto en **cualquier capítulo** (no solo el primer
  render) y tocar «Resaltar» o «Nota», la anotación se guarda y se pinta la
  marca; si la captura falla se muestra «No se pudo capturar la selección».
  Condiciones: (a) `window.HL` se re-inyecta en cada render — el latch
  `annotationsJsReady` se reinicia en `onPageStarted` porque cada
  `loadDataWithBaseURL` crea un documento nuevo sin `window.HL`;
  (b) la selección se lee **antes** de cerrar el ActionMode y **antes** de
  abrir el diálogo de nota; (c) los subrayados existentes se re-aplican en
  cada render (depende de (a)).

## Tasks

| ID | Specs | Route | Commit |
|----|-------|-------|--------|
| T1 | S1 | `ExtractionCache` (test-first) + wiring en `ReaderActivity` | `39e9b44` |
| T2 | S2 | `SizeLruCache` (test-first) + wiring en `BooksAdapter` | `c29cca9` |
| T3 | S3 | `SerialWriter` (test-first) + wiring en `ReaderActivity` | `94865b5` |
| T4 | S1-S3 | CHANGELOG, BACKLOG, README y bump 1.4.3 (versionCode 9) | `5918c11` (parcial) |
| T5 | S1-S3 | verificación: `test`, `lintRelease`, APK en emulador | evidencia en BACKLOG, commit `8a31fb0` |
| T6 | S4 | fix latch HL + captura de selección pre-finish/pre-diálogo + desanidado JSON + diagnóstico logcat | `8a31fb0` |

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
  slice 2 = T3 (`94865b5`); slice 3 = docs + bump + evidencia de verificación
  cierra la feature.
- **L6** — S3 ampliado (decisión técnica): una escritura que falla se registra
  y se salta en `SerialWriter.write` — una excepción no capturada en cualquier
  hilo de Android mata el proceso, y hoy ese mismo fallo ocurría en el hilo
  principal durante el debounce. Spec S3 actualizado con este comportamiento.
- **L7** — **Crash en emulador encontrado por la verificación E2E y corregido**:
  al importar un PDF (flujo FAB → picker → lector) la app crasheó con
  `IllegalStateException: connection pool has been closed` en
  `ReaderActivity.onPrepareOptionsMenu → BookmarksDBHelper.hasBookmark`.
  Causa raíz: `BookmarksDBHelper` llamaba `db.close()` al final de **cada**
  método (15 llamadas); con S3, el hilo `serial-writer` cerraba el pool
  compartido mientras el hilo principal tenía un `rawQuery` en vuelo (timeline:
  display 14:18:49.475 + debounce 700 ms ≈ crash 14:18:49.894). Fix: eliminar
  los 15 `db.close()` por método — la conexión la posee el helper y se cierra
  una sola vez en `onDestroy` (además elimina el reopen en cada consulta).
  Sin runner JUnit local para SQLite (android.database.sqlite es stub en unit
  tests); se verifica reproduciendo el flujo exacto en emulador (T5).
- **L8** — Reporte del usuario: «delega e investiga. en un epub que estoy leyendo
  no puedo hacer notas ni reslatar»; vía ask_user_question: síntoma "no se pudo
  capturar la seccion" y versión instalada **1.4.2**.
- **L9** — Diagnóstico delegado (gentle-ai-explore, con file:line): la cadena es
  selección → wrapper `onWindowStartingActionMode` → `captureSelection` →
  `window.HL && window.HL.sel()` → null → toast «No se pudo capturar la
  selección» (1354). Causa #1 (confirmada en emulador, RED): el latch
  `annotationsJsReady` (línea 87) se pone en true y **nunca se reinicia**, pero
  cada render llama `loadDataWithBaseURL` → documento nuevo sin `window.HL` →
  desde el render #2 toda captura devuelve null. Evidencia RED en emulador:
  render #1 `HL js: injected` + `HL typeof: "object"`; renders #2+ `HL js: skip
  (latch still ready)` + `HL typeof: "undefined"`. Causas #2/#4 (secundarias):
  `mode.finish()` síncrono después del eval asíncrono y «Nota» capturando la
  selección recién al cerrar el diálogo — ambas rutas podían perder la
  selección. #3 (endpoints que no son nodos de texto) queda registrada como
  sospechosa residual si el bug persiste tras el fix.
- **L10** — Verificación S4 en emulador (E2E real): (1) RED pre-fix: render #1
  `HL typeof: "object"`, renders #2+ `undefined` con `HL js: skip` (ver L9);
  (2) GREEN post-fix: 7 renders seguidos con `HL js: injected` + `"object"`;
  (3) tap real en el menú (uiautomator no expone la toolbar flotante: se
  localizó visualmente con screencap — los ítems viven en el overflow ⋮:
  «Resaltar | Nota | Read aloud»): «Resaltar» → `HL capture: saved s=40 e=48`
  + fila `1|35|40|48|'extrañas'`; «Nota» → diálogo, tap en Aceptar con bounds
  refrescados post-teclado → `saved s=40 e=48 (note)` + fila
  `2|...|'extrañas'|prueba nota 1.4.3`. (4) **Tercer bug destapado por el tap
  real**: `HL.sel()` devuelve un string JSON que `evaluateJavascript`
  re-serializa → `new JSONObject(value)` lanzaba `JSONException` ("Value
  {...} of type java.lang.String cannot be converted") y **toda selección
  válida se perdía en silencio** incluso con HL presente; parser corregido
  con desanidado doble (JSONTokener). Tests+lint finales: BUILD SUCCESSFUL,
  0 FAILED; lint 0 errores / 128 avisos (ninguno en archivos nuevos).
  Corrección de claim: L3 decía «0 avisos»; era «0 errores».
