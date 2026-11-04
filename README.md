# Service Compliance — Android (Kotlin)

Aplicación Android nativa de **Service Compliance** dirigida al trabajo de campo. Su objetivo es que el operario pueda autenticarse, consultar las obligaciones que tiene asignadas, registrar su ejecución y adjuntar evidencias desde el dispositivo móvil.

La solución Service Compliance centraliza la trazabilidad de los servicios tercerizados: relaciona obligaciones operativas, ejecuciones, evidencias y las decisiones de cumplimiento posteriores. El dominio y la arquitectura de la solución se documentan en el [Project Report](https://github.com/OperviaStartup/service-compliance-project-report).

## Stack

| Componente          | Tecnología                                                  |
|:--------------------|:------------------------------------------------------------|
| Lenguaje            | Kotlin 2.2.10                                               |
| Plataforma          | Android, `minSdk` 30 y `compileSdk` / `targetSdk` 37        |
| Interfaz            | Jetpack Compose con Material 3                              |
| Componentes Android | AndroidX Core KTX, Activity Compose y Lifecycle Runtime KTX |
| Construcción        | Gradle Wrapper y Android Gradle Plugin 9.4.1                |
| Pruebas             | JUnit 4, AndroidX Test y Espresso                           |

## Bounded Contexts

El diseño del producto separa cinco contextos. La app Android se enfoca inicialmente en los tres primeros, especialmente en el trabajo del operario.

| Bounded Context           | Responsabilidad                                                                            | Participación de la app Android                                                  |
|:--------------------------|:-------------------------------------------------------------------------------------------|:---------------------------------------------------------------------------------|
| **Identity & Access**     | Identidad, autenticación y autorización por rol.                                           | Inicio de sesión, conservación de la sesión y consulta del perfil.               |
| **Service Planning**      | Convierte las condiciones operativas aprobadas en planes y definiciones de obligación.     | Consume las obligaciones asignadas; su administración corresponde al supervisor. |
| **Field Operations**      | Registra el trabajo de campo: obligación, ejecución, evidencia e impedimentos.             | Consulta obligaciones, registra ejecuciones y adjunta evidencias.                |
| **Compliance Management** | Evalúa el cumplimiento y conserva el historial de decisiones, observaciones y correctivas. | Fuera del alcance inicial de la app de operario.                                 |
| **Reporting & Follow-up** | Proyecta el estado operativo, métricas, reportes y alertas.                                | Fuera del alcance inicial de la app de operario.                                 |

## Development

### 1. Requirements

- Android Studio con un SDK de Android que incluya la plataforma API 37.
- Un emulador o dispositivo con Android 11 (API 30) o superior.
- Git.
- JDK administrado por Android Studio, compatible con la versión del Android Gradle Plugin configurada en el proyecto.

No es necesario instalar Gradle globalmente: el repositorio incluye el Gradle Wrapper.

### 2. Start Frontend

Clona el repositorio y ábrelo en Android Studio:

```bash
git clone https://github.com/OperviaStartup/service-compliance-kotlin.git
cd service-compliance-kotlin
```

Selecciona un emulador o dispositivo y ejecuta la configuración `app` desde Android Studio. Desde Windows PowerShell también puedes compilar e instalar la variante de depuración:

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat installDebug
```

El APK generado se encuentra en `app/build/outputs/apk/debug/`.

## Authentication

La app consumirá autenticación basada en JWT:

1. Envía las credenciales a `POST /api/v1/auth/login`.
2. Recibe un token JWT, el identificador, nombre, correo y rol de la cuenta.
3. Incluye el token en las solicitudes protegidas mediante `Authorization: Bearer <token>`.
4. Consulta `GET /api/v1/auth/me` para restaurar o verificar el perfil autenticado.

El endpoint de registro actual crea únicamente usuarios con el rol `OPERATOR`; las operaciones de creación de obligaciones están restringidas al rol `SUPERVISOR`. La implementación móvil no debe registrar contraseñas ni tokens en logs; la decisión de almacenamiento seguro del token se implementará junto con el manejo de sesión y cierre de sesión.

## Contratos identificados en el backend

Esta es la fuente de verdad para la primera integración Android: [ServiceComplianceAPI]([https://github.com/OperviaStartup/serviceComplianceAPI/tree/a70a67b2feb955bc586632be0d1d3966e36891cf](https://github.com/OperviaStartup/serviceComplianceAPI/tree/main)). Las rutas, cuerpos y códigos exitosos de la tabla se verificaron contra los controladores del último commit. Los endpoints protegidos requieren `Authorization: Bearer <JWT>`.

| Método y ruta                           | Acceso                                                 | Solicitud que debe implementar la app                                                           | Respuesta exitosa actual                                                           |
|:----------------------------------------|:-------------------------------------------------------|:------------------------------------------------------------------------------------------------|:-----------------------------------------------------------------------------------|
| `POST /api/v1/auth/register`            | Público                                                | `{ fullName, email, password }`; contraseña de 8 a 72 caracteres.                               | `201 Created`; `token`, `userId`, `fullName`, `email`, `role`. Crea un `OPERATOR`. |
| `POST /api/v1/auth/login`               | Público                                                | `{ email, password }`                                                                           | `200 OK`; `token`, `userId`, `fullName`, `email`, `role`.                          |
| `GET /api/v1/auth/me`                   | Autenticado                                            | Sin cuerpo.                                                                                     | `200 OK`; `id`, `fullName`, `email`, `role`.                                       |
| `POST /api/v1/obligations`              | `SUPERVISOR`                                           | `{ title, description, siteName, assignedOperatorId, dueAt }`; `dueAt` es un instante ISO-8601. | `201 Created`; obligación creada.                                                  |
| `GET /api/v1/obligations`               | Autenticado                                            | Sin cuerpo.                                                                                     | `200 OK`; lista de obligaciones asignadas al usuario autenticado.                  |
| `POST /api/v1/executions`               | Autenticado; la obligación debe pertenecer al operario | `{ obligationId, result, notes? }`; `result`: `COMPLETED`, `PARTIAL` o `BLOCKED`.               | `200 OK`; ejecución registrada.                                                    |
| `GET /api/v1/executions/{id}`           | Autenticado                                            | Path parameter `id`.                                                                            | `200 OK`; detalle de la ejecución.                                                 |
| `POST /api/v1/executions/{id}/evidence` | Autenticado; la ejecución debe pertenecer al operario  | `{ type, url, description? }`; `type`: `PHOTO`, `QR_CODE`, `LOCATION` o `SIGNATURE`.            | `200 OK`; metadatos de evidencia registrados.                                      |
| `GET /api/v1/executions/{id}/evidence`  | Autenticado                                            | Path parameter `id`.                                                                            | `200 OK`; lista de evidencias de la ejecución.                                     |
| `GET /health`                           | Público                                                | Sin cuerpo.                                                                                     | `200 OK`; `{ "status": "UP" }`.                                                    |

### Errores y validaciones

La integración debe manejar `400 Bad Request` para datos inválidos o violaciones de reglas de negocio, `401 Unauthorized` para un JWT ausente, inválido o expirado, `403 Forbidden` para operaciones sin el rol requerido y `404 Not Found` cuando no exista una ejecución. Los errores controlados siguen el formato `ApiError` con `code`, `message` y `timestamp`.

## Useful Commands

| Objetivo                          | Windows PowerShell                   |
|:----------------------------------|:-------------------------------------|
| Compilar depuración               | `.\gradlew.bat assembleDebug`        |
| Instalar en dispositivo/emulador  | `.\gradlew.bat installDebug`         |
| Ejecutar pruebas unitarias        | `.\gradlew.bat test`                 |
| Ejecutar pruebas instrumentadas   | `.\gradlew.bat connectedAndroidTest` |
| Ejecutar análisis estático        | `.\gradlew.bat lint`                 |
| Limpiar artefactos de compilación | `.\gradlew.bat clean`                |

En Linux o macOS reemplaza `./gradlew.bat` por `./gradlew`.