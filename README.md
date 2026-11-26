# Service Compliance Android

Aplicación nativa Android para el trabajo de campo de Service Compliance. El operario consulta las obligaciones asignadas, registra su ejecución y adjunta las evidencias exigidas. El dominio del producto se describe en el [Project Report](https://github.com/OperviaStartup/service-compliance-project-report); los contratos de API pertenecen al [Service Compliance API](https://github.com/OperviaStartup/serviceComplianceAPI).

## Stack

| Componente | Tecnología |
|:--|:--|
| Lenguaje | Kotlin 2.2.10 |
| Plataforma | Android; `minSdk` 30, `compileSdk` y `targetSdk` 37 |
| UI | Jetpack Compose y Material 3 |
| Navegación y estado | Navigation Compose, ViewModel, StateFlow y Lifecycle Compose |
| Red | Retrofit 2, OkHttp y Gson |
| Persistencia local | Preferences DataStore |
| Lectura QR | ZXing Android Embedded 4.3.0 mediante ScanContract |
| Construcción | Gradle Wrapper y Android Gradle Plugin 9.4.1 |

## Arquitectura

El código móvil organiza los bounded contexts dentro de `features`:

- `features/iam`: inicio de sesión, restauración de sesión y cierre de sesión.
- `features/operations`: obligaciones, ejecución, fotografía con cámara, impedimentos, historial del servidor y centro de sincronización.
- `core/network` y `core/session`: cliente HTTP, encabezado de autorización y almacenamiento cifrado de sesión.

Las pantallas de operaciones solo están disponibles para cuentas cuyo rol es `OPERATOR`. El rol y el acceso a los recursos también deben ser validados por el backend.

## Funcionalidad disponible y límites actuales

- El operario consulta sus obligaciones asignadas. Las obligaciones configuradas usan el inicio real, recuperación del avance, marcado de pasos con `expectedVersion` y envío por el contrato de ejecución. El servidor determina el resultado final; el operario no selecciona un estado para estas obligaciones. Las obligaciones antiguas sin `startsAt` conservan el envío anterior.
- El botón de inicio abre la ejecución después de confirmarla con la API. Al regresar se recupera la misma ejecución. Las claves de inicio y las solicitudes de envío pendientes se conservan en DataStore por cuenta y obligación; un reintento de envío reutiliza clave, versión y notas. Esto no constituye una cola offline.
- Los pasos cambian cuando el servidor confirma el avance. Los conflictos recuperan el estado actual; una ejecución vencida conserva el avance como solo lectura. El envío exige todos los pasos y las evidencias obligatorias, y solo una ejecución enviada por el servidor se conserva como ejecución confirmada en la caché local.
- El detalle se consulta por el ID seleccionado mediante `GET /api/v1/obligations/{id}` y muestra el inicio programado, los pasos y los requisitos fotográficos devueltos por la API. Conserva obligaciones antiguas con `startsAt: null` y colecciones vacías, sin inventar instrucciones ni cantidades. Los demás tipos de requisitos se consultan mediante la vista de ejecución; este GET de detalle todavía no los incluye.
- La caché de obligaciones y ejecuciones confirmadas se guarda localmente por cuenta para Inicio, Trabajo y el centro de sincronización. Historial consulta el servidor y puede recuperarse al iniciar sesión desde otro dispositivo.
- Historial usa `GET /api/v1/executions`, con búsqueda por título, Hoy/7 días y paginación por cursor. Separa el resultado operativo de la evaluación explícita: sin `complianceResult` muestra **Pendiente de revisión**, incluso con `COMPLETED`. **Conforme**, **Excepción aceptada** e **Incumplimiento** corresponden únicamente a los resultados canónicos del supervisor. Los filtros se aplican en el servidor con las mismas reglas.
- Las tareas con una ejecución confirmada `COMPLETED` aparecen en la pestaña **Completadas**, incluso si la API mantiene el estado original de la obligación.
- Las operaciones de escritura requieren conexión con la API. Esta versión no tiene cola de operaciones offline.
- Las ejecuciones configuradas permiten tomar una fotografía, revisarla y subir los bytes JPEG/PNG al requisito correspondiente. Después se consultan por la API privada. Una fotografía registrada no se reemplaza; en requisitos configurables se respeta el máximo devuelto por el servidor. El registro de URL y metadatos se conserva únicamente para el recorrido antiguo.
- El borrador fotográfico permanece en el espacio privado de la cuenta hasta confirmar la subida o descartarlo antes del envío. Las subidas inciertas conservan archivo, checksum, fecha, clave y versión para reintentar; una respuesta perdida se reconcilia con la evidencia del servidor. No se simula una foto adjuntada ni se reinventa la fecha de captura.
- Si el requisito PHOTO incluye `requireGeolocation`, se solicita ubicación en primer plano con permiso del usuario. Los requisitos QR_CODE y LOCATION tienen captura y revisión antes del registro JSON; SIGNATURE permite dibujar, revisar y subir un PNG privado de hasta 1 MiB. Cada opción solo aparece cuando el backend incluye ese requisito, con sus cantidades mínima/máxima y restricciones.
- El QR se obtiene con la cámara mediante `ScanContract` o leyendo una imagen elegida mediante el selector de archivos del sistema. En ambos casos se revisa y registra el código decodificado; la imagen seleccionada no se sube como evidencia PHOTO. Se valida longitud y `expectedPrefix`. LOCATION usa la ubicación del dispositivo y su fecha/precisión, con permiso en primer plano y `maxAccuracyMeters`. La firma conserva los trazos introducidos por el usuario y no puede subirse desde un panel vacío. Si el requisito de firma exige geolocalización, también se solicita antes de subir.
- Las capturas QR/LOCATION se guardan en DataStore por cuenta, ejecución y requisito. El envío pendiente conserva la clave, versión, fecha y contenido exactos; una respuesta perdida se reconcilia por tipo, contenido y fecha. La firma reutiliza los archivos privados, revisión y reintentos de la subida binaria. Nada de esto constituye una cola offline.
- Las obligaciones configuradas registran impedimentos con el recurso independiente de excepciones. Se elige uno de los cuatro motivos del backend, se exige una descripción de hasta 2000 caracteres y se puede indicar un paso y vincular evidencias ya registradas en la misma ejecución. El reporte conserva el avance y no completa, envía ni evalúa la actividad; los requisitos siguen siendo obligatorios. El formulario no tiene un contrato para subir archivos nuevos directamente al impedimento. El recorrido antiguo sin `startsAt` conserva el resultado `BLOCKED` con notas.
- La solicitud de impedimento pendiente se guarda por cuenta y obligación con clave, `operationId`, versión y contenido inmutables. Un reintento reutiliza la misma solicitud; una respuesta perdida se reconcilia con las excepciones devueltas por el servidor. Mientras el registro siga incierto se impiden nuevos cambios, fotos y el envío final. Un rechazo definitivo permite corregir el formulario. Esto no implementa una cola offline ni garantiza un resultado favorable de supervisión.
- La consulta de Historial requiere la versión del backend que incluye BE-US-22. No usa registros locales como sustituto de una respuesta del servidor. Si falla una actualización, puede conservar las páginas previamente confirmadas de esa misma consulta durante la sesión, indicando que no están actualizadas; cambiar filtros elimina esos resultados. Un error de permisos retira los registros visibles. La evaluación se muestra en lectura; esta app de operario no permite registrar decisiones del supervisor.
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
| `GET /api/v1/obligations/{id}` | Detalle de la obligación seleccionada, pasos y requisitos fotográficos. |
| `GET /api/v1/obligations/{id}/execution` | Recuperar la ejecución actual; 404 significa que aún no existe. |
| `POST /api/v1/obligations/{id}/execution` | Iniciar una única ejecución con `Idempotency-Key`. |
| `PUT /api/v1/executions/{id}/steps/{stepId}` | Marcar o desmarcar un paso con `{ completed, expectedVersion }`. |
| `POST /api/v1/executions/{id}/exceptions` | Reporte separado con `Idempotency-Key` y `{ reason, description, stepId?, operationId, evidenceIds, expectedVersion }`; devuelve el avance con `exceptions`. |
| `POST /api/v1/executions/{id}/submit` | Enviar con `Idempotency-Key` y `{ expectedVersion, notes }`; no incluye un resultado elegido por el operario. |
| `POST /api/v1/executions/{id}/photo-requirements/{requirementId}/photo` | Fotografía inmutable: multipart `file`, `expectedVersion`, `capturedAt`. |
| `POST /api/v1/executions/{id}/evidence-requirements/{requirementId}/evidence` | PHOTO/SIGNATURE: multipart; QR_CODE/LOCATION: JSON. Incluye fecha, versión, `Idempotency-Key` y ubicación cuando corresponde. |
| `GET /api/v1/evidence/{id}/content` | Descargar bytes de la imagen con la misma sesión autenticada. No se siguen URLs de almacenamiento proporcionadas por el usuario. |
| `POST /api/v1/executions` | Registro `{ "obligationId": 1, "result": "COMPLETED", "notes": null }`. |
| `GET /api/v1/executions` | Historial personal: `fromDate`, `toDate` inclusivas Lima, `query`, `reviewStatus`, `cursor` y `limit`; respuesta `{ items, nextCursor, hasMore, asOf }`. |
| `GET /api/v1/executions/{id}` | Consulta de una ejecución. |
| `GET /api/v1/executions/{id}/evidence` | Consulta de evidencias asociadas. |
| `POST /api/v1/executions/{id}/evidence` | Registro de metadatos `{ "type": "PHOTO", "url": "https://...", "description": null }`. |

Los resultados del contrato anterior son `COMPLETED`, `PARTIAL` y `BLOCKED`. En el flujo configurado, el servidor determina `COMPLETED` al aceptar el envío. Los tipos de evidencia del backend son `PHOTO`, `QR_CODE`, `LOCATION` y `SIGNATURE`; la app integra la captura de los cuatro tipos a partir de los requisitos devueltos por la ejecución.

La cámara se integra mediante [TakePicture](https://developer.android.com/reference/androidx/activity/result/contract/ActivityResultContracts.TakePicture) y un [FileProvider privado](https://developer.android.com/reference/androidx/core/content/FileProvider) con permisos temporales para el archivo seleccionado. Los archivos permanecen bajo `files/field-photos/user-<id>/execution-<id>/`; se valida tamaño (5 MB), formato, dimensiones y pertenencia. La vista previa se reduce en memoria y lee la orientación con [AndroidX ExifInterface 1.4.2](https://developer.android.com/jetpack/androidx/releases/exifinterface); el archivo original enviado no se modifica. El [selector de contenido de Android](https://developer.android.com/reference/androidx/activity/result/contract/ActivityResultContracts.GetContent) permite leer únicamente la imagen elegida, sin acceso general a la galería ni permiso de almacenamiento externo. La lectura QR admite imágenes de hasta 5 MB y 25 millones de píxeles; se reduce la imagen antes de decodificarla fuera del hilo principal. Las imágenes vacías, dañadas o sin QR legible se rechazan y conservan cualquier captura anterior. El lector [ZXing Android Embedded](https://github.com/journeyapps/zxing-android-embedded#usage-with-scancontract) usa `ScanContract` y requiere cámara; por ello se solicita ese permiso tanto al abrir el lector como al tomar una fotografía. No se solicita durante login. La firma utiliza los mismos archivos privados y la API protegida, con validación adicional de PNG y 1 MiB.

El backend responde errores estructurados con `code`, `message` y `timestamp`. La app presenta mensajes diferenciados para credenciales inválidas, falta de permisos, conflictos y errores de servidor/conectividad.

La captura de firma mantiene el tamaño y los trazos dentro de un lienzo acotado; la imagen se prepara fuera del hilo principal. El sistema no interpreta ni certifica la identidad del firmante. La app no introduce URLs ni coordenadas de prueba en el flujo del usuario.

## Pruebas y análisis

`HistoryEntryTest` comprueba las etiquetas del historial y la separación entre resultado operativo y evaluación explícita. `ExecutionHistoryRepositoryTest` verifica el contrato HTTP, los filtros, el cursor y el rechazo de respuestas incoherentes. `HistoryViewModelTest` cubre búsqueda, cambio de filtros, paginación, respuestas antiguas, errores y permisos. `HistoryScreenInstrumentedTest` comprueba etiquetas, detalle, selección de filtro y carga de páginas en Compose. `ObligationDetailRepositoryTest` utiliza un servidor HTTP local de pruebas para verificar la ruta de detalle, su mapeo, la compatibilidad con obligaciones antiguas y los errores 401/403/404. Se retiraron los tests iniciales sin comportamiento de producto y una pantalla antigua de cuenta activa sin referencias. Las pruebas móviles usan el contrato HTTP simulado y pruebas instrumentadas; la integración completa con el backend local se comprueba además mediante Run. No hay cobertura automatizada completa de autenticación. Los comandos del wrapper son:

`ExecutionFlowRepositoryTest` verifica las rutas, versiones y claves del flujo mediante HTTP local de pruebas. `FieldExecutionViewModelTest` cubre inicio único, recuperación de pasos, conflictos, respuestas perdidas, persistencia del envío pendiente, requisitos incompletos y acceso revocado. La dependencia `kotlinx-coroutines-test` se usa únicamente en pruebas para controlar las operaciones suspendidas del ViewModel.

Las pruebas de ejecución también cubren el cuerpo HTTP del impedimento, la conservación de pasos y fotos, el envío incompleto, los reintentos inmutables, la recuperación de respuestas perdidas, los conflictos y la ausencia de confirmaciones o historial prematuros. `ExceptionStoreInstrumentedTest` verifica en Android que la solicitud exacta persista al recrear el almacenamiento y que las claves estén aisladas por cuenta y obligación.

`PhotoRepositoryTest`, `LocalPhotoFilesTest` y `PhotoViewModelTest` comprueban multipart, contenido privado, aislamiento de archivos, revisión local, cancelación, reintentos inmutables, conflictos y revocación de acceso. `PhotoFilesInstrumentedTest` valida en Android el proveedor configurado, la lectura real de JPEG y la orientación EXIF. Esta prueba genera un archivo propio de test; no captura ni sube fotos en obligaciones del usuario. La comprobación de la cámara y de las pantallas se realiza mediante Run de Android Studio.

`StructuredEvidenceViewModelTest` cubre revisión local, prefijo QR, ubicación real mediante el proveedor, precisión, límites, respuestas perdidas, reintentos persistentes, conflictos y aislamiento. Las pruebas HTTP verifican JSON QR/LOCATION y multipart SIGNATURE, incluidos formato y tamaño. `RequiredEvidenceInstrumentedTest` dibuja mediante gestos sobre el panel desplazable, comprueba el PNG resultante, usa el decodificador QR real y verifica aislamiento del borrador en DataStore. El código QR generado es una imagen exclusiva de demostración; esta prueba no sustituye el escaneo físico con la cámara, que se valida con Run. `QrImageDecoderTest` verifica el lector real, rotación, inversión, transparencia, rechazo de otros códigos y límites de bytes. `QrImageReaderInstrumentedTest` lee PNG/JPEG mediante una URI privada real, rechaza archivos inválidos y comprueba que el botón de imagen solo aparezca para QR. Las pruebas del ViewModel verifican que importar una imagen conserve la revisión previa, el prefijo y la solicitud inmutable ante reintentos.

| Objetivo | PowerShell |
|:--|:--|
| Pruebas unitarias | `.gradlew.bat test` |
| Pruebas instrumentadas | `.gradlew.bat connectedAndroidTest` |
| Lint Android | `.gradlew.bat lint` |

En Linux o macOS, usa `./gradlew` en lugar de `gradlew.bat`.
