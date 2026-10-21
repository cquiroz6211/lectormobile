# background-optimization — Optimización de Ciclo de Vida y Segundo Plano (v1.5.1)

Optimización de comportamiento en segundo plano solicitada por el usuario:
«revisa en doc o proyectos como estos. quiero que revisemos optimizacion. ¿que sucede salirse de la app? en background como se compporta»
«si, con eso sacamos 1.5.1»

## Specs

- **S1** — **Pausa y reanudación completa de WebView (`webView.onPause()` / `webView.onResume()`)**:
  - En `ReaderActivity.onPause()`: además de `pauseTimers()`, llamar a `webView.onPause()` para suspender el pipeline de renderizado de Chromium, GPU y animaciones mientras la app esté en segundo plano.
  - En `ReaderActivity.onResume()`: llamar a `webView.onResume()` junto a `resumeTimers()`.
- **S2** — **Liberación de memoria en segundo plano (`onTrimMemory`)**:
  - Implementar `onTrimMemory(int level)` en `MainActivity`: ante `TRIM_MEMORY_UI_HIDDEN`, `TRIM_MEMORY_RUNNING_CRITICAL` o `TRIM_MEMORY_COMPLETE`, vaciar el caché LRU en RAM de portadas de `BooksAdapter` (`booksAdapter.clearCoverCache()`), devolviendo hasta 8 MB de heap de Bitmaps al sistema operativo.
  - Al volver a la biblioteca, las portadas se vuelven a cargar desde `CoverStore` (caché en disco) en segundo plano sin latencia perceptible.
- **S3** — **Cierre prolijo de recursos en `MainActivity.onDestroy()`**:
  - En `BooksAdapter`: método `destroy()` que apaga ordenadamente el pool `coverExecutor`.
  - En `MainActivity.onDestroy()`: invocar `booksAdapter.destroy()` y `dbHelper.close()`, eliminando hilos de fondo y conexiones SQLite huérfanas ante recreaciones de la actividad.
- **S4** — **Persistencia de estado ante muerte de proceso (`onSaveInstanceState`)**:
  - En `ReaderActivity.onSaveInstanceState(Bundle outState)`: persistir `saved_position`, `current_page`, `current_chapter` y `nav_mode`.
  - En `ReaderActivity.onCreate(Bundle savedInstanceState)`: si `savedInstanceState != null`, priorizar `savedInstanceState.getString("saved_position")` sobre los extras heredados de `getIntent()`, evitando que una actividad recreada resucite un marcador o salto viejo en lugar del punto real alcanzado por el usuario.
- **S5** — **Docs, versión y verificación (v1.5.1, versionCode 11)**:
  - Actualización de CHANGELOG, BACKLOG, README, `version.properties` y `app/build.gradle`.
  - Verificación completa: `test`, `lintRelease`, APK firmado e instalación en emulador.

## Tasks

| ID | Specs | Route | Commit |
|----|-------|-------|--------|
| T1 | S1 | `webView.onPause()` y `webView.onResume()` en `ReaderActivity` | pending |
| T2 | S2 | `clearCoverCache()` en `BooksAdapter` + `onTrimMemory` en `MainActivity` | pending |
| T3 | S3 | `destroy()` en `BooksAdapter` + `onDestroy()` en `MainActivity` | pending |
| T4 | S4 | `onSaveInstanceState` y restauración prioritaria en `ReaderActivity` | pending |
| T5 | S5 | Docs (CHANGELOG, BACKLOG, README), version bump v1.5.1 (versionCode 11) y verificación | pending |

## Log

- **L1** — «revisa en doc o proyectos como estos. quiero que revisemos optimizacion. ¿que sucede salirse de la app? en background como se compporta»
- **L2** — «si, con eso sacamos 1.5.1»
