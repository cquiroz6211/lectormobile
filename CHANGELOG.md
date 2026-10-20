# Changelog — LectorMobile

## [1.2.0] — 2026-10-20

### Añadido

- **Modo oscuro real para PDF**: los documentos PDF ahora invierten sus
  colores («papel oscuro») cuando el modo oscuro está activo, mediante
  `nightMode()` del visor. Alternar el tema recarga el documento en la
  página actual. Cierra la limitación #1 del backlog.

## [1.1.0] — 2026-10-20

### Añadido

- **Reanudar lectura exacta en EPUB**: la posición se guarda como
  `chapter:N:fraction` (capítulo + % de scroll) y al reabrir el libro vuelve
  al mismo punto, no al inicio del capítulo. Los formatos anteriores
  (`chapter:N`, `page:N`) siguen siendo válidos.
- **% de progreso en el lector**: junto al contador aparece el progreso del
  libro («Página 3 / 100 · 3%», «Capítulo 3 / 40 · 6%»); se omite si el total
  aún no se conoce.
- **Progreso en la biblioteca**: cada libro muestra una barra de progreso con
  su porcentaje (oculta si nunca se abrió) y la lista se ordena por «último
  leído», con orden alfabético como desempate.

### Corregido

- **«Capítulo 1 / 0» al abrir un EPUB**: el total de capítulos ahora se fija
  antes del primer render.

## [1.0.1] — 2025-10-20

### Corregido

- **PDF no renderizaba / crash al abrir**: el build excluía `libmodpdfium.so`,
  que `PDFView` necesita (`UnsatisfiedLinkError`). Se eliminó la exclusión.
- **El selector solo mostraba PDFs**: ahora usa `OpenDocument` con
  `application/pdf` y `application/epub+zip`, por lo que los EPUB también se
  pueden importar.
- **Los libros importados desaparecían**: las copias se guardaban en `cacheDir`.
  Ahora se guardan en `filesDir/books` con nombres únicos y se incluyen en la
  biblioteca.
- **EPUB fuera de orden / capítulos duplicados**: nuevo parser v3 basado en el
  spine del OPF, rutas absolutas y extracción de índice desde `nav.xhtml` y
  `toc.ncx`.
- **TOC inaccesible**: se añadió «Índice» al menú del lector y el diálogo
  navega por `href` en lugar de por título.
- **Búsqueda rompía la selección**: los clics y el borrado resuelven el libro
  por ruta, no por posición de la lista filtrada.
- **Guardar posición llenaba los marcadores**: nueva tabla
  `reading_positions`; las posiciones ya no se insertan como marcadores.
- **Ajuste de tamaño de fuente**: `Settings` guardaba `int` y el lector leía
  `String` (crash potencial); ahora ambos usan `int` y el cambio se aplica al
  instante.
- **Pantalla de marcadores crasheaba**: IDs de layout incorrectos
  (`bookmarks_recycler`/`bookmarks_toolbar`) y `dbHelper` creado con `context`
  nulo en el adaptador; el `id` de cada marcador no se leía.
- **Marcadores no navegaban**: ahora abren el libro en la página/capítulo
  guardado; se añadió borrado con estado vacío.
- **Atrás en EPUB desincronizaba título y contenido**: el lector ya no navega
  el historial interno del WebView; «atrás» cierra el lector.
- **Menú del lector**: IDs de ajustes/marcadores corregidos; se añadió índice;
  títulos de fuente claros.
- **Diálogo de opciones del libro**: «Marcador» abría el libro (texto
  engañoso) → ahora «Abrir»; borrar pide confirmación y elimina la copia
  interna, marcadores, posición e historial.
- **Abrir/compartir archivos grandes**: la copia se hace en segundo plano.
- **Escaneo de biblioteca**: se ejecuta en segundo plano, carga el historial
  una sola vez y ya no bloquea la UI.
- **Tema**: `values-night` usaba un tema claro (texto oscuro sobre fondo
  oscuro). Ahora la app es oscura en modo claro y oscuro del sistema.
- **Textos**: interfaz completamente en español; la versión de Ajustes se lee
  del paquete (ya no está fija en 1.0.0).

### Cambiado

- Visor PDF migrado a `io.github.oothp:android-pdf-viewer:3.2.0-beta06`
  (pdfium 1.9.5-beta01, con segmentos LOAD alineados a 16 KB).
- `versionCode` 2 / `versionName` 1.0.1.
- Barra de estado y navegación oscuras; títulos visibles en Ajustes y
  Marcadores; estado vacío en biblioteca y marcadores.

### Limitaciones

- El modo oscuro no invierte el contenido del PDF (fondo blanco del documento).
- Los EPUB se extraen a caché en cada apertura.
- Sin soporte de DRM.

---

## [1.0.0] — 2025-10-19

### Añadido

- Lectura de PDF con zoom y navegación por swipe.
- Lectura de EPUB 2.0/3.0 con capítulos e índice.
- Tema oscuro por defecto.
- Marcadores con SQLite y guardado de posición.
- Progreso por libro y tamaño de fuente ajustable.
- Selector de archivos PDF/EPUB.
- Apertura de archivos compartidos desde otras apps.
- Interfaz Material 3.

### Técnico

- APK release de ~2.7 MB, minSdk 24.
- Librerías nativas arm64-v8a y armeabi-v7a.
- Parser EPUB propio con soporte NCX y nav.xhtml.
- Sin permisos de red.
