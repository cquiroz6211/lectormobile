# epub-annotations — Subrayado y notas en EPUB (v1.4)

Feature elegida por el usuario (opción "EPUB: subrayado + notas (recomendada)").
Rama: `feat/epub-annotations`.

## Specs

- **S1** — "Selección de texto en el lector EPUB → menú flotante «Resaltar» / «Nota»": al seleccionar texto en el WebView aparecen las acciones del sistema **más** «Resaltar» y «Nota»; Resaltar marca el texto (fondo amarillo); Nota marca y abre un diálogo para escribir una nota asociada (guardar o cancelar).
- **S2** — "tabla `annotations` (libro, capítulo, offset inicio/fin, texto, color, nota, fecha)": DB v5 aditiva; CRUD completo; borrar libro borra sus anotaciones; sin borrar datos existentes al migrar.
- **S3** — "al abrir el capítulo, los subrayados se re-aplican sobre el texto": tras renderizar un capítulo EPUB, cada anotación del libro+capítulo se re-aplica como marca visual (amarillo; subrayado punteado si tiene nota). Es tolerante: si el texto del libro cambió y el offset ya no coincide, la anotación no se pinta pero sigue en la lista (nunca crashea).
- **S4** — "Lista «Notas y subrayados» por libro, tap → salta al punto": pantalla con todas las anotaciones (texto subrayado + nota + libro + capítulo), tap → abre el lector en ese capítulo y hace scroll hasta la marca. Accesible desde el menú del lector (libro actual) y desde el menú principal (todos los libros). Permitir borrar desde la lista.
- **PDF queda como lector** (sin subrayado) — decisión explícita del usuario.

## Anchoring (decisión técnica, L2)

Offsets = posición global de caracteres sobre el **texto renderizado** del capítulo (textContent del body), calculados en JS dentro del WebView. Aplicación: walk de nodos de texto + wrap en `<span class="hl">` (o `<span class="hl hl-note">`). Es determinístico entre renders del mismo contenido (el HTML se regenera idéntico desde el archivo) y degrada sin romper si cambia.

## Tasks

| ID | Specs | Route | Commit |
|----|-------|-------|--------|
| T1 | S2 | inline — DB v5: tabla `annotations` + CRUD en `BookmarksDBHelper` (+ deleteAnnotationsForPath en deleteBook) | pendiente |
| T2 | S1, S3 | inline — `ReaderActivity`: JS de selección/offsets/apply (inline en constantes), ActionMode con Resaltar/Nota, diálogo de nota, re-aplicar tras onPageFinished | pendiente |
| T3 | S4 | inline — `AnnotationsActivity` + adapter + layout + entradas de menú (reader + main) + navegación con salto (extra chapter + JS scrollIntoView) | pendiente |
| T4 | S2-S4 | inline — docs (CHANGELOG/BACKLOG/README) + bump 1.4.0 (versionCode 6) | pendiente |
| T5 | S2-S4 | inline — verificación en emulador: flujo completo (seleccionar → resaltar → nota → reabrir → persistido → lista → salto), migración v4→v5 con datos, tests existentes, release firmado | pendiente |

Test-first: la lógica crítica (offsets, wrap, re-apply) vive en JavaScript dentro del WebView — no hay runner JUnit aplicable; se verifica funcional y determinísticamente en emulador (T5). La parte Java es SQL + UI.

## Log

- **L1** — "hagamos subrayado y notas".
- **L2** — decisión de producto vía ask_user_question: "EPUB: subrayado + notas (recomendado)"; PDF queda fuera (el visor no expone selección de texto). Anchoring por offsets de texto renderizado (ver arriba).
