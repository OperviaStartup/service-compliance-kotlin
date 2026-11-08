# Service Compliance Android

Aplicación nativa Android para el trabajo de campo de Service Compliance. El operario consulta las obligaciones asignadas, registra su resultado y vincula metadatos de evidencia. El dominio del producto se describe en el [Project Report](https://github.com/OperviaStartup/service-compliance-project-report); los contratos de API pertenecen al [Service Compliance API](https://github.com/OperviaStartup/serviceComplianceAPI).

## Stack

| Componente | Tecnología |
|:--|:--|
| Lenguaje | Kotlin 2.2.10 |
| Plataforma | Android; `minSdk` 30, `compileSdk` y `targetSdk` 37 |
| UI | Jetpack Compose y Material 3 |
| Navegación y estado | Navigation Compose, ViewModel, StateFlow y Lifecycle Compose |
| Red | Retrofit 2, OkHttp y Gson |
| Persistencia local | Preferences DataStore |
| Construcción | Gradle Wrapper y Android Gradle Plugin 9.4.1 |

## Arquitectura

El código móvil organiza los bounded contexts dentro de `features`:

- `features/iam`: inicio de sesión, restauración de sesión y cierre de sesión.
- `features/operations`: obligaciones, ejecución, evidencia por URL, historial local y centro de sincronización.
- `core/network` y `core/session`: cliente HTTP, encabezado de autorización y almacenamiento cifrado de sesión.

Las pantallas de operaciones solo están disponibles para cuentas cuyo rol es `OPERATOR`. El rol y el acceso a los recursos también deben ser validados por el backend.

## Funcionalidad disponible y límites actuales

- El operario puede consultar sus obligaciones asignadas y enviar una ejecución con resultado `COMPLETED`, `PARTIAL` o `BLOCKED`.
- El historial confirmado y la caché de obligaciones se guardan localmente por cuenta. Se conservan al cerrar y volver a abrir la app.
- Las tareas con una ejecución confirmada `COMPLETED` aparecen en la pestaña **Completadas**, incluso si la API mantiene el estado original de la obligación.
- Las operaciones de escritura requieren conexión con la API. Esta versión no tiene cola de operaciones offline.
- La evidencia se registra como URL y metadatos; aún no se carga una imagen desde la cámara.
- Los impedimentos se envían como resultado `BLOCKED` con notas. La API actual no ofrece un recurso independiente para incidencias.
- El historial disponible en la app contiene ejecuciones guardadas localmente. El backend actual no expone una consulta de historial de ejecuciones por usuario ni un flujo de corrección del supervisor.
- El centro de sincronización vuelve a consultar la API para verificar registros; no envía una cola local.
- La app móvil no contiene registro ni aceptación de invitaciones. La creación de cuentas se administra mediante el flujo de invitaciones del backend; el registro público está deshabilitado en el contrato backend actual.

## Configuración y ejecución

### Requisitos

- Android Studio con Android SDK API 37.
- Emulador o dispositivo con Android 11 (API 30) o superior.
- JDK compatible con el Android Gradle Plugin del proyecto; Android Studio puede proporcionar el JDK.

### Backend local en el emulador

La variante `debug` utiliza `http://10.0.2.2:8080/`, que permite al emulador Android conectarse al puerto 8080 del equipo anfitrión. El manifiesto `debug` permite HTTP sin cifrar solo para desarrollo. En Android API 37, la app solicita permiso de acceso a la red local.

Para usar un dispositivo físico, configura la URL `debug` con la dirección IP LAN del equipo donde corre el backend y permite el acceso al puerto 8080 en la red local. `10.0.2.2` es específico del emulador.

### Construir e instalar

Abre el repositorio en Android Studio, sincroniza Gradle, selecciona un emulador o dispositivo y ejecuta la configuración `app`. Desde PowerShell:

```powershell
.gradlew.bat assembleDebug
.gradlew.bat installDebug
```

El APK debug se genera en `app/build/outputs/apk/debug/`. La variante release está configurada con `https://service-compliance-api.onrender.com/` en `app/build.gradle.kts`.

## Autenticación y creación de operarios

El login envía `identifier` y `password` a `POST /api/v1/auth/login`; el identificador admite correo o DNI de ocho dígitos. La app conserva los tokens de sesión cifrados, consulta `/api/v1/auth/me` y puede renovar la sesión al restaurarla si el access token expiró. El cierre de sesión limpia la sesión móvil.

Para crear una cuenta `OPERATOR`, el backend requiere una invitación administrativa. `POST /api/v1/auth/register` devuelve `403` en el backend actual; no debe usarse para registro público. La app no proporciona una pantalla para crear o aceptar invitaciones.

## Contratos consumidos

Las solicitudes protegidas usan `Authorization: Bearer <token>`.

| Método y ruta | Uso en la app |
|:--|:--|
| `POST /api/v1/auth/login` | Inicio de sesión; cuerpo `{ "identifier": "...", "password": "..." }`. |
| `POST /api/v1/auth/refresh` | Renovación con `{ "refreshToken": "..." }`. |
| `POST /api/v1/auth/logout` | Revocación de refresh token. |
| `GET /api/v1/auth/me` | Validación y lectura del perfil actual. |
| `GET /api/v1/obligations` | Consulta de obligaciones del usuario autenticado. |
| `POST /api/v1/executions` | Registro `{ "obligationId": 1, "result": "COMPLETED", "notes": null }`. |
| `GET /api/v1/executions/{id}` | Consulta de una ejecución. |
| `GET /api/v1/executions/{id}/evidence` | Consulta de evidencias asociadas. |
| `POST /api/v1/executions/{id}/evidence` | Registro de metadatos `{ "type": "PHOTO", "url": "https://...", "description": null }`. |

Los resultados de ejecución disponibles son `COMPLETED`, `PARTIAL` y `BLOCKED`; los tipos de evidencia son `PHOTO`, `QR_CODE`, `LOCATION` y `SIGNATURE`. La evidencia actualmente requiere una URL accesible; no se envía un archivo binario.

El backend responde errores estructurados con `code`, `message` y `timestamp`. La app presenta mensajes diferenciados para credenciales inválidas, falta de permisos, conflictos y errores de servidor/conectividad.

## Pruebas y análisis

Los archivos `ExampleUnitTest` y `ExampleInstrumentedTest` son los ejemplos iniciales de Android Studio; no cubren los flujos de autenticación ni operaciones. Aún no hay pruebas de integración móvil contra el backend. Cuando se prepare esa etapa, los comandos del wrapper son:

| Objetivo | PowerShell |
|:--|:--|
| Pruebas unitarias | `.gradlew.bat test` |
| Pruebas instrumentadas | `.gradlew.bat connectedAndroidTest` |
| Lint Android | `.gradlew.bat lint` |

En Linux o macOS, usa `./gradlew` en lugar de `gradlew.bat`.
