# Changelog — LectorMobile

## [1.4.3] — 2026-10-20

### Optimizado

- **Apertura de EPUB sin re-descomprimir**: la extracción del zip se cachea en
  `filesDir/epub_cache`, identificada por tamaño y fecha de modificación del
  libro. Abrir un libro sin cambios reutiliza la extracción anterior en lugar
  de descomprimirlo otra vez (antes se re-extraía en cada apertura). El caché
  conserva hasta 6 libros (LRU por último uso), descarta extracciones
  interrumpidas (marcador `.ok`) y sobrevive a la limpieza de caché del
  sistema; solo guarda datos derivados, nunca toca libros ni datos del usuario.
- **Portadas sin re-decodificado**: los bitmaps de portada ya decodificados se
  mantienen en una caché LRU en memoria de 8 MB (`SizeLruCache`); al desplazar
  la biblioteca las portadas se pintan al instante sin volver a decodificar el
  archivo en cada bind. Los bitmaps cacheados ya no se reciclan.
- **Posiciones de lectura fuera del hilo principal**: el guardado debounceado
  de la posición (PDF y EPUB) se ejecuta ahora en un hilo de escritura dedicado
  con orden FIFO (`SerialWriter`); el cálculo sigue en el hilo principal. Al
  pausar y al cerrar se espera a que las escrituras pendientes terminen antes
  de cerrar la base de datos, y una escritura fallida se registra sin tumbar la
  app ni bloquear las siguientes.

### Corregido

- **«Resaltar»/«Nota» fallaban en todos los capítulos salvo el primero**
  (reportado por el usuario en 1.4.2, con el toast «No se pudo capturar la
  selección»): `window.HL` se inyectaba una sola vez por sesión, pero cada
  render crea un documento nuevo en el WebView, así que desde el segundo
  render toda captura devolvía null. El latch se reinicia en `onPageStarted`
  y el JS se re-inyecta en cada carga (verificado en emulador: renders
  seguidos con `typeof window.HL === "object"` tras el fix; antes,
  `"undefined"` desde el render #2). Además, la selección se lee ahora
  **antes** de cerrar el ActionMode y **antes** de abrir el diálogo de nota,
  para que esas acciones no descarten la selección pendiente de capturar.
- **Crash «connection pool has been closed» al abrir un libro** (detectado en
  la verificación de esta versión): `BookmarksDBHelper` cerraba la base de
  datos al final de **cada** método; con los guardados de posición pasados a
  segundo plano, ese cierre destruía la conexión compartida mientras el hilo
  principal seguía consultando (menú del lector → `hasBookmark`). Ahora la
  conexión la posee el helper y se cierra una sola vez al destruir la
  actividad; de paso se elimina la reapertura de la base en cada consulta.

### Técnico

- Nuevas clases puras Java con tests unitarios: `ExtractionCache`,
  `SizeLruCache` y `SerialWriter`. 92 ejecuciones de tests (46 × 2 variantes)
  en verde y `lintRelease` con 0 errores (128 avisos preexistentes, ninguno
  en los archivos nuevos).
- Diagnóstico logcat de anotaciones: estado de `window.HL` por render,
  inyección del menú y resultado de cada captura.
- `versionCode` 9 / `versionName` 1.4.3.

## [1.4.2] — 2026-10-20

### Añadido

- **Temas de lectura (Claro, Sepia, Oscuro)**:
  - Selector de tema con opciones Claro, Sepia / Cálido y Oscuro, disponible tanto desde el menú del lector («Tema de lectura») como desde la pantalla de Ajustes («Apariencia»).
  - Preferencia persistente `reading_theme` con sincronización bidireccional y retrocompatibilidad con el booleano heredado `dark_mode`.
  - EPUB con paleta CSS específica por tema (Claro `#FFFFFF`/`#1A1A1A`, Sepia `#F8F1E3`/`#433422`, Oscuro `#1A1A1A`/`#E8E6E3`).
  - PDF con modo noche real para Oscuro, fondo blanco para Claro y superposición cálida `#22A06B33` con fondo cálido `#F8F1E3` para Sepia.
  - Barras superior e inferior adaptativas con contraste optimizado de íconos y textos (`#212121` oscuro, `#F5F5F5` claro, `#EFE6D5` sepia).
- **Modo lectura inmersivo (pantalla completa)**:
  - Toque en la página (PDF o EPUB) para alternar los controles con animación suave (deslizamiento de barras) y ocultamiento/aparición de barras del sistema (`WindowInsetsControllerCompat`).
  - Detección inteligente en EPUB que no interfiere con enlaces (`<a>`), notas de texto (`.hl-note`) ni selecciones activas.
- **Salto rápido a página o capítulo («Ir a…»)**:
  - Diálogo de salto numérico accesible pulsando el indicador de progreso o desde el menú de opciones («Ir a…»).
  - Salto directo a la página en PDF y al capítulo en EPUB con validación de rangos.
- **Limpieza de recursos**:
  - Eliminación de menús redundantes en desuso (`menu_main.xml` y `menu_reader.xml`).

## [1.4.1] — 2026-10-20

### Corregido

- **«Resaltar»/«Nota» no aparecían al seleccionar texto**: la inyección solo
  cubría el menú flotante; ahora cubre todos los tipos de menú de selección
  (flotante y primario, según la versión de WebView del dispositivo), con
  inyección previa a la construcción del menú y refuerzo posterior.

## [1.4.0] — 2026-10-20

### Añadido

- **Subrayado y notas en EPUB**: al seleccionar texto en el lector aparecen
  «Resaltar» y «Nota» junto a las acciones del sistema. Las marcas persisten
  por libro y capítulo (nueva tabla `annotations`, migración aditiva v5) y se
  re-aplican al reabrir el capítulo; las notas se ven con un toque sobre el
  texto marcado. Nueva pantalla «Notas y subrayados» desde el menú del lector
  (libro actual) y el menú principal (todos los libros), con salto directo al
  punto. Borrar un libro borra sus anotaciones. Los PDF quedan como lector.

## [1.3.0] — 2026-10-20

### Añadido

- **Portadas en la biblioteca**: cada libro muestra su portada real — la
  imagen declarada como cover en el OPF para EPUB, o un render de la
  página 1 para PDF. Las portadas se cachean en disco (una sola
  generación por contenido) y se cargan en segundo plano; si un libro no
  tiene portada, se mantiene el ícono genérico.

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
