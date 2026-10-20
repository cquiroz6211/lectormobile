# library-covers — Portadas reales en la biblioteca (v1.3)

Feature elegida por el usuario. Convierte la lista de nombres de archivo en una estantería visual.
Rama: `feat/library-covers`.

## Specs

- **S1** — "portadas de biblioteca": cada fila de libro muestra la portada real: para EPUB, la imagen marcada como cover en el OPF (`manifest item properties="cover-image"` o `meta[name=cover]` → id); para PDF, un render de la página 1. Si no hay cover o falla la extracción, se muestra el ícono genérico actual (`ic_pdf`) — nunca un hueco ni crash.
- **S2** — Las portadas se cachean en disco (`filesDir/covers/`) y se generan una sola vez por contenido (clave: path + lastModified + size del archivo); regenerar solo si el libro cambió. Nada de red: solo archivos locales.
- **S3** — La carga es async (hilo de fondo) y la lista nunca se bloquea ni crashea: bind inmediato con fallback, portada cuando esté lista; recicaje de ViewHolder correcto (sin portadas cruzadas al scrollear).

## Tasks

| ID | Specs | Route | Commit |
|----|-------|-------|--------|
| T1 | S1 | inline — `EPUBParser.extractCover()`: localiza la cover en el OPF y copia el archivo de imagen a destino; tests JUnit con EPUB fixture armado en el test (test-first) | pendiente |
| T2 | S1, S2 | inline — `CoverStore`: genera/obtiene portadas (EPUB vía T1, PDF vía `PdfRenderer` página 1, tamaño ~256px), cachea en `filesDir/covers` con clave content-based | pendiente |
| T3 | S3 | inline — `item_book.xml`: ImageView de portada 48×64dp con esquinas redondeadas + fallback; `BooksAdapter` carga async con cancelación por recycle | pendiente |
| T4 | S1-S3 | inline — docs (CHANGELOG/BACKLOG/README) + bump 1.3.0 (versionCode 5) | pendiente |
| T5 | S1-S3 | inline — verificación: tests, assembleDebug, emulador (EPUB con cover + PDF con miniatura + fallback), release firmado | pendiente |

Delegación: Writer rule no aplica — unidades dependientes (T3 depende de T2 depende de T1), feature única.

## Log

- **L1** — petición: "hagamos esa portada de biblioteca" (feature 1.3 propuesta al cerrar la 1.2.0).
- **L2** — decisiones de diseño: (a) sin librería de imágenes nueva (BitmapFactory + PdfRenderer de plataforma alcanzan; la app no tiene Glide y no la agregamos por una miniatura); (b) sin persistir bitmaps en DB — archivos en `filesDir/covers/` nombrados por hash del path+mtime+size; (c) ratio 3:4 (48×64dp) uniforme.
