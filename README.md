# NotesApp Mobile

App Android de notas con estrategia **offline-first**: Room como fuente inmediata y sincronización con la API REST al recuperar conexión.

## Stack técnico

- **Lenguaje**: Kotlin 1.9.24 · **Gradle** 8.7 · **AGP** 8.5.2 · **KSP** (Room compiler)
- **SDK**: min 24 · target/compile 34 · Java 17 · ViewBinding
- **Local**: Room 2.6.1 (SQLite) · **Red**: Retrofit 2.9 + OkHttp (timeouts 5 s) + Gson
- **Async**: Coroutines · **Sync fondo**: WorkManager (cada 15 min) + auto-sync al volver a foreground
- **Backend**: `http://192.168.1.10:8081/` (ver `data/remote/ApiClient.kt`)

## Estructura

```
app/src/main/java/com/notes/mobile/
├── NotesApp.kt               # Application + DI manual + agenda SyncWorker
├── data/
│   ├── local/                # NotesDatabase, NoteDao, NoteEntity
│   ├── remote/               # NotesApi, ApiClient (token/userId/username)
│   ├── repository/           # NotesRepository (offline-first + tombstones)
│   └── sync/                 # SyncManager (Mutex), SyncWorker
└── ui/
    ├── auth/                 # SplashActivity (launcher), Login, Register
    ├── notes/                # NotesListActivity, NoteDetailActivity
    └── adapter/              # NotesAdapter
```

Pantallas: Splash (decide destino por token) → Login/Registro → Lista (banner de pendientes + pull-to-refresh) → Detalle (crear/editar/eliminar). Tema azul con degradados.

## Modelo local

`NoteEntity(id, title, content, userId, createdAt, updatedAt, isPendingSync, isDeleted)`:

- `isPendingSync=true`: cambio pendiente de enviar al server.
- `isDeleted=true` (**tombstone**): borrado offline que aún debe propagarse al server. Nunca se muestra (`WHERE isDeleted = 0`).

## Sincronización

- **Cache-first**: la lista se pinta al instante desde Room (`getNotesDirect()`); luego sync en background.
- **Crear/editar offline**: se guarda en Room con `isPendingSync=true` (IDs locales = timestamp > 1000000).
- **Borrar offline**: NO se elimina de Room; se marca tombstone. Al volver el backend, `getNotes()` excluye esos IDs (`getPendingDeletedIds()`) para no restaurarlos, y `syncPendingNotes()` envía el `DELETE`.
- **Reglas de sync**: local-only + borrado → borrar directo en Room (nunca existió en server). DELETE con 404 → limpiar tombstone (ya no existe en server). Otro error → **conservar tombstone** y reintentar (borrarla antes es lo que restauraba la nota).
- `SyncManager` usa `Mutex` contra syncs concurrentes; banner `syncBanner` muestra pendientes.
- Notas filtradas por `userId` (proviene del `AuthResponse` del backend).

## Ejecución

Requisitos: Android Studio (JDK 17), backend corriendo en `192.168.1.10:8081`, dispositivo/emulador en la misma red.

```bash
.\gradlew.bat assembleDebug   # si hay poco RAM: .\gradlew.bat --no-daemon assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`. Permisos: `INTERNET`, `ACCESS_NETWORK_STATE`,
`ACCESS_FINE_LOCATION` + `ACCESS_COARSE_LOCATION` (solo se solicitan al adjuntar ubicación a una nota).
La sesión persiste (token cifrado); Splash redirige a lista o login.

## Estado global, seguridad y recursos

- **Estado global**: `SessionViewModel` (sesión: autenticado/no autenticado) y `NotesViewModel`
  (notas, pendientes, carga, errores) exponen `LiveData`; las Activities solo observan
  (`ui/AppViewModelFactory.kt`).
- **Token cifrado**: `EncryptedSharedPreferences` (AES256 + Keystore) con migración
  automática desde las prefs planas; `allowBackup="false"` en el manifest.
- **Guards + 401**: `NotesList`/`NoteDetail` redirigen a login sin sesión; un 401 del
  backend publica `SessionExpiredException` → logout (limpia caché, cancela sync y
  ubicación) → login. El header muestra "Hola, {usuario}".
- **Ubicación bajo demanda**: en el detalle, "Agregar ubicación actual" pide
  `ACCESS_FINE_LOCATION` solo al pulsarse (con rationale si aplica); `LocationHelper`
  pide un único fix (sin tracking) con timeout de 15 s y `cancelAll()` en `onStop`
  y en el logout para ahorrar batería.
