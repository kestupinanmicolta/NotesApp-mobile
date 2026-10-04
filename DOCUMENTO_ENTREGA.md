# NotesApp Mobile — Documento de entrega

App Android nativa (Kotlin) de notas offline-first con backend Spring Boot + JWT.
Auth con email/contraseña, lista personalizada por usuario, sesión persistente
cifrada y ubicación opcional por nota (coordenadas + nombre legible).

## 1. Justificación del gestor de estado global seleccionado

Se eligió **ViewModel + LiveData** (Android Jetpack, sin dependencias externas)
frente a alternativas como Redux, Bloc o Context API, por tres razones:

1. **Ciclo de vida:** el estado sobrevive a rotaciones y las Activities observan
   `LiveData` de forma segura (sin fugas ni callbacks manuales).
2. **Fuente única de verdad:** `SessionViewModel` publica `Authenticated(email)` /
   `Unauthenticated` y `NotesViewModel` publica lista, pendientes de sync,
   carga, error y sesión expirada; las pantallas solo observan y reaccionan
   (ir al login, pintar banner, mostrar diálogo).
3. **Costo proporcional:** Redux/Bloc añaden boilerplate y librerías para un
   estado que aquí son dos flujos bien delimitados; `AppViewModelFactory`
   inyecta el `NotesRepository` compartido (Room + Retrofit) a ambos ViewModels
   con DI manual, suficiente y testeable.

## 2. Estrategias aplicadas para la seguridad

- **Token en almacenamiento seguro:** `EncryptedSharedPreferences` (AES-256 +
  Keystore) con migración desde prefs planas; `allowBackup="false"` para que la
  sesión no salga en backups del sistema.
- **Acceso condicionado a sesión:** guards en `NotesList`/`NoteDetail` (sin token
  → login) y ante un 401 el repositorio publica `SessionExpiredException` →
  logout total (limpia token, caché Room, cancela sync y ubicación) → login.
- **Backend:** BCrypt, JWT de 24 h con el email como subject, `/api/auth/**`
  público y `Authorization: Bearer` en el resto; `GlobalExceptionHandler`
  responde en español con códigos correctos (400/401/404/409/500) sin exponer
  SQL ni stacktraces (detalle solo en log del servidor).
- **Defensa en el cliente:** `sanitizeServerMessage` sustituye cualquier texto
  con pinta técnica por un mensaje genérico, y todo error se muestra en
  `AlertDialog` completo (`Dialogs`), nunca truncado.

## 3. Medidas tomadas para optimizar el manejo de recursos

- **Permiso justo a tiempo:** la ubicación solo se solicita al pulsar "Agregar
  ubicación actual"; si se deniega permanente o el GPS está apagado se guía a
  Ajustes en vez de reintentar a ciegas.
- **Sin tracking:** un único fix por petición (caché reciente primero, luego
  GPS + red con timeout de 30 s); `LocationHelper.cancelAll()` en `onStop` y en
  el logout detiene listeners y timeouts para ahorrar batería.
- **Sync eficiente:** WorkManager cada 15 min + auto-sync al volver a foreground,
  `Mutex` contra syncs concurrentes, y estrategia offline-first (Room primero,
  tombstones para borrados) que minimiza red y escrituras.
