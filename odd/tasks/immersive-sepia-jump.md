# immersive-sepia-jump — Modo Lectura Inmersivo, Tema Sepia y Salto Rápido (v1.4.2)

Feature elegida por el usuario («Opción 1: Modo Lectura Inmersivo + Tema Sepia + Salto Rápido a Página/Capítulo»).

## Specs

- **S1** — **Modo lectura inmersivo (pantalla completa)**: Al hacer tap en la pantalla del lector (tanto en PDF como en EPUB), se ocultan/muestran la barra superior de herramientas (`Toolbar`) y los controles de lectura inferiores (`SeekBar` + progreso), ocultando o restaurando las barras del sistema (status bar y navigation bar) mediante `WindowInsetsControllerCompat` con comportamiento transitorio por deslizamiento. Los taps en enlaces, notas y selecciones de texto no interfieren con la alternancia de controles.
- **S2** — **Tema Sepia / Cálido (3 modos de lectura: Claro, Sepia, Oscuro)**:
  - Clase de gestión `ReadingTheme` con valores `"light"`, `"sepia"` y `"dark"`, guardada bajo la clave `"reading_theme"` y con retrocompatibilidad bidireccional con el valor histórico `"dark_mode"`.
  - EPUB adapta fondo (`#F8F1E3`), texto (`#433422`), títulos y enlaces para sepia.
  - PDF adapta fondo, invierte colores en modo oscuro y aplica un filtro cálido semi-transparente (`#22A06B33`) en sepia.
  - Las barras superior e inferior adaptan dinámicamente sus colores de fondo, texto e íconos (incluyendo iconos oscuros/claros en la barra de estado).
  - Selector de tema interactivo desde el menú del lector (`menuTheme`) y desde la pantalla de Ajustes (`SettingsActivity`).
- **S3** — **Salto rápido a página / capítulo («Ir a…»)**:
  - Tocar el indicador de progreso (`tvProgress`) o la opción de menú `menuJumpTo` abre un diálogo con campo numérico pre-llenado con la página o capítulo actual.
  - Validación de rango (1 a total) con feedback visual y salto directo al punto (`pdfView.jumpTo` o `loadChapter`).

## Verificación

- Tests unitarios en `ReadingThemeTest` (migración de clave legacy, defaults, normalización y sincronización).
- Todos los tests unitarios pasaron (`./gradlew test`).
- Build de release/debug verificado (`./gradlew assembleDebug`).
