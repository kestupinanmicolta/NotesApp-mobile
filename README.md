# NotesApp Mobile

App Android de notas con estrategia **offline-first**: Room como fuente inmediata y sincronización con la API REST al recuperar conexión.

## Stack técnico

- **Lenguaje**: Kotlin 1.9.24 · **Gradle** 8.7 · **AGP** 8.5.2 · **KSP** (Room compiler)
- **SDK**: min 24 · target/compile 34 · Java 17 · ViewBinding
- **Local**: Room 2.6.1 (SQLite) · **Red**: Retrofit 2.9 + OkHttp (timeouts 5 s) + Gson
- **Async**: Coroutines · **Sync fondo**: WorkManager (cada 15 min) + auto-sync al volver a foreground
- **Backend**: URL configurable en `data/remote/ApiClient.kt` (`BASE_URL`)

## Estructura

```
app/src/main/java/com/notes/mobile/
├── NotesApp.kt               # Application + DI manual + agenda SyncWorker
├── data/
│   ├── local/                # NotesDatabase (Room v3), NoteDao, NoteEntity
│   ├── remote/               # NotesApi, ApiClient (token/userId/email), ApiModels
│   ├── repository/           # NotesRepository (offline-first + tombstones)
│   ├── location/             # LocationHelper (single-fix), GeocoderHelper (nombre legible)
│   └── sync/                 # SyncManager (Mutex), SyncWorker
└── ui/
    ├── auth/                 # SplashActivity (launcher), Login, Register
    ├── notes/                # NotesListActivity, NoteDetailActivity, NotesViewModel
    ├── session/              # SessionViewModel (estado global de sesión)
    ├── adapter/              # NotesAdapter
    └── common/               # Dialogs (avisos; no se usan Toasts)
```

Pantallas: Splash (decide destino por token) → Login/Registro (solo email + contraseña) → Lista (banner de pendientes + pull-to-refresh, tarjetas con ubicación) → Detalle (crear/editar/eliminar + adjuntar ubicación). Tema azul con degradados.

## Modelo local

`NoteEntity(id, title, content, userId, latitude?, longitude?, locationName?, createdAt, updatedAt, isPendingSync, isDeleted)` (migraciones 1→2→3):

- `isPendingSync=true`: cambio pendiente de enviar al server.
- `isDeleted=true` (**tombstone**): borrado offline que aún debe propagarse al server. Nunca se muestra (`WHERE isDeleted = 0`).
- `latitude`/`longitude`/`locationName` (opcionales): ubicación de la nota; `locationName` es el texto legible ("Barrio, Municipio") por geocodificación inversa.

## Sincronización

- **Cache-first**: la lista se pinta al instante desde Room (`getNotesDirect()`); luego sync en background.
- **Crear/editar offline**: se guarda en Room con `isPendingSync=true` (IDs locales = timestamp > 1000000).
- **Borrar offline**: NO se elimina de Room; se marca tombstone. Al volver el backend, `getNotes()` excluye esos IDs (`getPendingDeletedIds()`) para no restaurarlos, y `syncPendingNotes()` envía el `DELETE`.
- **Reglas de sync**: local-only + borrado → borrar directo en Room (nunca existió en server). DELETE con 404 → limpiar tombstone (ya no existe en server). Otro error → **conservar tombstone** y reintentar (borrarla antes es lo que restauraba la nota).
- `SyncManager` usa `Mutex` contra syncs concurrentes; banner `syncBanner` muestra pendientes.
- Notas filtradas por `userId` (proviene del `AuthResponse` del backend).

## Ejecución (cualquier PC)

Requisitos: Android Studio (JDK 17) y el backend corriendo (ver README del backend).

1. Apunta la app a tu backend en `ApiClient.kt` (`BASE_URL`):
   - **Emulador**: `http://10.0.2.2:8081/` (así viene por defecto; `10.0.2.2` es el localhost del PC).
   - **Dispositivo físico**: `http://TU_IP_LAN:8081/` (PC y celular en la misma red Wi-Fi; la IP LAN se consulta con `ipconfig` en Windows o `ip addr`/`ifconfig` en Linux/macOS). Puede requerirse permitir el puerto 8081 en el firewall.
2. Compila e instala:

```bash
./gradlew assembleDebug   # Windows: .\gradlew.bat assembleDebug
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
  ubicación) → login. El header muestra "Hola, {email}".
- **Avisos**: `ui/common/Dialogs.kt` centraliza `AlertDialog` (error/éxito/aviso con Aceptar).
  No se usan Toasts: todo mensaje —incluidos errores del backend ya sanitizados—
  se muestra en diálogo completo. Los éxitos con navegación avanzan al pulsar Aceptar.
- **Ubicación bajo demanda**: en el detalle, "Agregar ubicación actual" pide
  `ACCESS_FINE_LOCATION` solo al pulsarse (con rationale si aplica; si se deniega
  permanente o el GPS está apagado se guía a Ajustes); `LocationHelper`
  pide un único fix (sin tracking) con timeout de 30 s y `cancelAll()` en `onStop`
  y en el logout para ahorrar batería. `GeocoderHelper` resuelve el nombre legible.

## Documentos de entrega

- `DOCUMENTO_ENTREGA.md`: justificación del gestor de estado, estrategias de seguridad y optimización de recursos (máx. 2 páginas).
- `GUION_VIDEO.md`: guion de sustentación con demo paso a paso.
