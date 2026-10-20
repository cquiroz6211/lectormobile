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
| T1 | S1 | inline — `EPUBParser.extractCover()` + 6 tests JUnit (RED→GREEN) | `7fa4e1e` |
| T2 | S1, S2 | inline — `CoverStore` (EPUB sin descomprimir todo, PdfRenderer ~256px, cache content-keyed) | `3b54699` |
| T3 | S3 | inline — `item_book.xml` portada 48×64 + `BooksAdapter` async con tag-guard | `3b54699` |
| T4 | S1-S3 | docs + bump 1.3.0 | `52470e1` |
| T5 | S1-S3 | verificado en emulador + release firmado | `95ed428` (merge) |

Delegación: Writer rule no aplica — unidades dependientes (T3 depende de T2 depende de T1), feature única.

## Log

- **L1** — petición: "hagamos esa portada de biblioteca" (feature 1.3 propuesta al cerrar la 1.2.0).
- **L2** — decisiones de diseño: (a) sin librería de imágenes nueva (BitmapFactory + PdfRenderer de plataforma alcanzan; la app no tiene Glide y no la agregamos por una miniatura); (b) sin persistir bitmaps en DB — archivos en `filesDir/covers/` nombrados por hash del path+mtime+size; (c) ratio 3:4 (48×64dp) uniforme.

- **L3** — Verificación T5: 20/20 tests; portada real del Quijote (JPEG 1200×1800 del OPF) y página 1 del PDF (PNG 198×256) generadas y mostradas. Bug encontrado y corregido en el camino: `app:tint` en el ImageView aplicaba SRC_IN y convertía cualquier portada en un rectángulo sólido primario (`2fb4c91`); el tinte pasó a aplicarse por código solo al fallback.
