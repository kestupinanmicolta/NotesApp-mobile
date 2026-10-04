# Guion de sustentación — NotesApp Mobile (Android nativo, Kotlin)

Duración sugerida: 5–7 min. Requisitos previos: backend corriendo en el puerto
8081 (ver README del backend) y APK `app-debug.apk` instalado.

## 1. Demo en pantalla (qué mostrar y qué decir)

1. **Splash → Login.** "La Splash decide el destino según el token guardado; sin
   sesión va al login" (`ui/auth/SplashActivity.kt`, `ApiClient.isLoggedIn`).
2. **Registro con email + contraseña.** Forzar un error (correo duplicado):
   "Los errores del backend llegan como diálogo legible, nunca SQL crudo"
   (`ui/common/Dialogs.kt`, `NotesRepository.sanitizeServerMessage`).
3. **Pantalla principal "Mis Notas".** "Saludo personalizado con el email
   autenticado y lista offline-first desde Room" (`NotesListActivity.kt:103`).
4. **Crear nota + ubicación.** Pulsar **+**, escribir, **Agregar ubicación actual**:
   mostrar el diálogo de permiso (solo aquí se pide), las coordenadas y luego el
   nombre "Barrio, Municipio" (`LocationHelper.kt`, `GeocoderHelper.kt`).
   Guardar → diálogo de éxito → la tarjeta muestra 📍 + nombre (`item_note.xml`).
5. **Editar y Quitar ubicación.** Entrar a la nota, quitar, guardar.
6. **Cerrar sesión.** "Limpia token, caché local, cancela ubicación y sync, y
   vuelve al login" (`SessionViewModel.logout()`).

## 2. Documento (3 secciones exigidas)

### a) Gestor de estado global: ViewModel + LiveData

Se eligió **ViewModel con LiveData** (Jetpack, sin librerías externas) porque el
estado de sesión y de notas debe sobrevivir a rotaciones y ser observado por
varias Activities con ciclo de vida seguro:

- `SessionViewModel`: única fuente de verdad (`Authenticated(email)` /
  `Unauthenticated`); `NotesListActivity` y `NoteDetailActivity` solo observan.
- `NotesViewModel`: lista, pendientes de sync, carga, error y sesión expirada.
- `AppViewModelFactory`: inyecta el `NotesRepository` compartido (offline-first
  Room + Retrofit) a ambos ViewModels.

Alternativas como Redux/Provider añaden dependencias y boilerplate
innecesarios para este alcance; ViewModel ya integra el ciclo de vida Android.

### b) Seguridad

- **Token cifrado:** `EncryptedSharedPreferences` (AES256 + Keystore) en
  `data/remote/ApiClient.kt`, con migración desde prefs planas y fallback
  controlado; `allowBackup="false"` en el manifest para que la sesión no salga
  en backups.
- **Guards:** `NotesList/NoteDetail` redirigen al login si no hay token; un 401
  publica `SessionExpiredException` → logout total → login.
- **Backend:** BCrypt, JWT 24 h con subject = email, `/api/auth/**` público y
  resto con `Bearer`; `GlobalExceptionHandler` responde mensajes en español sin
  filtrar detalles técnicos (ni SQL ni stacktraces).
- **Transporte local:** la IP del backend vive en una constante (`BASE_URL`).

### c) Recursos: ubicación bajo demanda y batería

- **Permiso explícito y tardío:** `ACCESS_FINE_LOCATION` solo se solicita al
  pulsar "Agregar ubicación actual" (`ActivityResultContracts.RequestPermission`);
  si se deniega permanente se guía a Ajustes; si el GPS está apagado se ofrece
  abrir ajustes de ubicación.
- **Sin tracking:** un solo fix por petición (`requestSingleFix`), primero caché
  reciente y luego ambos proveedores; timeout de 30 s con fallback a caché.
- **Ahorro de batería/memoria:** `LocationHelper.cancelAll()` en `onStop` y en
  el logout; sync con WorkManager cada 15 min + `Mutex` contra syncs
  concurrentes; el logout además cancela el sync periódico.

## 3. Cierre (30 s)

"Auth email + JWT cifrado, estado global con ViewModels, ubicación opcional con
permiso justo a tiempo y persistida hasta MySQL, y errores siempre en diálogo
legible. Código en GitHub y colección Bruno en `./bruno` del backend."
