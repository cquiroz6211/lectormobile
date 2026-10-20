# resume-progress — Retomar lectura exacta + progreso visible (v1.1)

Feature elegida por el usuario para la 1.1 de LectorMobile: la biblioteca "funciona pero no es un producto".
Rama: `feat/resume-progress`. Specs citan las palabras exactas del usuario/la opción elegida; nada agregado.

## Specs

- **S1** — "Guardar posición exacta: `chapter:N:fraction` (scroll %) y restaurarla al abrir": el EPUB reabre en el mismo scroll del capítulo, no arriba. Formato nuevo `chapter:N:fraction`; los formatos viejos (`chapter:N`, `page:N`) siguen parseando igual. Los bookmarks siguen guardando `chapter:N`/`page:N` sin fraction (toggle de bookmark debe seguir funcionando idéntico).
- **S2** — "% de progreso en el lector (junto a «Página 3/100»)": el texto del lector pasa a "Página 3 / 100 · 3%" (PDF) y "Capítulo 3 / 40 · 6%" (EPUB). Sin total conocido no se muestra % ni se guarda percent.
- **S3** — "% + barra de progreso por libro en la biblioteca, orden «Último leído»": cada fila de `item_book` muestra barra de progreso + "42%" (oculto si nunca se abrió); la lista se ordena por `last_read_at` descendente, empates y no-leídos alfabéticamente.
- **S4** — "Migración aditiva de DB (v4) sin borrar datos": `DATABASE_VERSION` 3→4 agrega `reading_positions.percent`; `onCreate` la crea, `onUpgrade` hace `ALTER TABLE ... ADD COLUMN` con try/catch; nunca DROP/DELETE de datos del usuario. Fila vieja sin percent → 0 hasta la próxima lectura.

## Tasks

| ID | Specs | Route | Commit |
|----|-------|-------|--------|
| T1 | S1, S2 | inline — helper puro `ReadingPosition` (parse/format/percent) + tests JUnit, test-first (RED→GREEN) | commit T1 |
| T2 | S1, S2 | inline — `ReaderActivity`: restaurar scroll EPUB, guardar fraction (debounce + onPause), % en label, guardar total antes del render | commit T2 |
| T3 | S4 | inline — `BookmarksDBHelper` v4: columna percent, ALTER aditivo, `getAllProgress()`, save con percent | `91a0d0b` |
| T4 | S3 | inline — biblioteca: `BookItem.percent`, sort last-read, barra en `item_book.xml`, bind en `BooksAdapter` | `36c75e6` |
| T5 | S1-S4 | inline — docs (CHANGELOG/BACKLOG/README) + bump 1.1.0 | `e00bc48` |
| T6 | S1-S4 | inline — verificación: unit tests, `assembleDebug`, upgrade in-place en emulador (1.0.1→1.1.0 conserva datos), checks funcionales | verificado |

Delegación: Writer rule no aplica — las unidades son dependientes entre sí (T2/T3/T4 dependen del formato y helper de T1), una sola feature coherente.

## Log

- **L1** — petición original: "revisa este proyecto, es mi app personal para leer libros, papers y epubs. tengo instalado en el mobile la version 1.0.1 funciona pero no es un producto ¿cual deberia ser la siguiente feature?"
- **L2** — corrección de orden: "start with this: - Dead code RecentBooksAdapter/item_recent_book.xml" → completado y commiteado como `85e5fbf chore: remove dead Recent Books cluster` (push a origin/main).
- **L3** — "first mcommit and push and then go with the feature to 1.1." → commit+push hechos, feature arrancada.
- **L4** — decisión de producto (ask_user_question): opción elegida "Retomar + progreso (recomendada)" con preview T1-T4 = las specs S1-S4 de arriba, incluyendo "Dolor que cierra: abro un EPUB, me sueltan arriba del capítulo, pierdo 10 minutos buscando dónde estaba."
- **L5** — decisiones de diseño (sin decisión abierta del usuario): (a) PDF mantiene granularidad de página — la fraction es de scroll de WebView, aplica a EPUB; (b) % de EPUB = (capítulo + fraction) / total capítulos; (c) la fraction se guarda con debounce de 700 ms y flush en `onPause`; (d) se corrige el orden existente donde `updateProgress()` corre con `totalPages == 0` en el primer render (mostraba "Capítulo 1 / 0" y, con T3, habría pisado el percent guardado).
- **L6** — T1 completado: RED observado (`cannot find symbol class ReadingPosition`), GREEN 14/14 tests (`app:testDebugUnitTest`). El percent de ejemplo en S2 quedó "Capítulo 3 / 40 · 6%" en el test (`percent(2, 0.6f, 40) = 6`) — fórmula floor((índice+fraction)*100/total); para PDF la página alcanzada cuenta completa (`percent(2, 1f, 100) = 3` → "Página 3 / 100 · 3%", corregido respecto al 2% ilustrativo de la preview).
- **L7** — T2 completado (commit `016b851`, build green + 14/14): wrappers privados muertos `saveReadingPosition`/`getReadingPosition` removidos; `totalPages` duplicado en setup EPUB eliminado. Corte de slice: T1+T2 = 469 líneas → el resto de la feature (T3-T5) forma el segundo slice.
