# Flujo del operario · primera entrega

El paquete `fieldoperations` implementa detalle, inicio/recuperación de una única ejecución,
pasos, fotografías, impedimentos y envío explícito. `MainActivity` es la entrada de Run y,
solo en debug, abre el formulario de pruebas existente. No se implementan login ni listado.
Usa navegación Compose con rutas y callbacks, siguiendo el ejemplo `scripts/Navigation.zip`.
La separación de paquetes mantiene la navegación fuera de las pantallas y un archivo por pantalla.
El ViewModel conserva el estado durante cambios de configuración y consulta el avance al volver
al primer plano. El servidor conserva el avance entre aperturas. No hay cola offline (US-08).

## Integración con el compañero

La entrada recomendada para el NavHost del compañero es `FieldOperationsRoute`. Recibe URL
base **sin `/api/v1`**, token de la sesión existente **sin `Bearer`**, ID positivo de la obligación
seleccionada y el callback de retorno al host:

```kotlin
FieldOperationsRoute(
    baseUrl = apiBaseUrl,
    bearerToken = sessionToken,
    obligationId = selectedObligationId,
    onExit = { navController.popBackStack() }
)
```

Import: `com.upc.example.serviceComplianceKotlin.fieldoperations.FieldOperationsRoute`.
Las variables del ejemplo proceden de la configuración, sesión y selección del compañero;
no son constantes que deban introducirse en el módulo. El token no debe formar parte del
nombre de una ruta, de argumentos de navegación, de `SavedStateHandle` ni de logs.

Registrar esta entrada dentro de un `composable` propio del NavHost. Cada obligación conserva
su ViewModel en el `NavBackStackEntry` de esa pantalla. El host retira esa entrada al cerrar
sesión o cambiar de cuenta; si renueva la sesión después de un 401, debe abrir una entrada
nueva con el token actualizado. El módulo no implementa login, renovación ni almacenamiento
de credenciales. Ante un 401 muestra el aviso y el botón Regresar devuelve el control al host.
Al volver al listado, el host debe consultarlo nuevamente para reflejar avance, envío o vencimiento.

El debug utiliza exactamente esta misma entrada. La alternativa mediante actividad se conserva
para un host que la necesite, sin exponerla a otras aplicaciones (`exported=false`):

```kotlin
startActivity(FieldOperationsActivity.intent(context, apiBaseUrl, sessionToken, selectedObligationId))
```

`FieldOperationsFlow` sigue disponible como contenedor de presentación. La construcción del
ViewModel está centralizada en `FieldOperationsRoute`, compartida por debug y la actividad.

## Organización del módulo

```text
fieldoperations/
├── FieldOperationsActivity.kt       # Entrada desde el listado existente
├── FieldOperationsRoute.kt          # Entrada Compose con sesión e ID seleccionados
├── FieldOperationsFlow.kt           # Contenedor, ciclo de vida y estado compartido
├── navigation/
│   └── FieldNavigation.kt           # NavHost, rutas y conexión de callbacks
├── screens/
│   ├── DetailScreen.kt
│   ├── ExecutionScreen.kt
│   ├── PhotoScreen.kt
│   ├── ExceptionScreen.kt
│   └── FieldLabels.kt               # Etiquetas y formato de presentación compartidos
├── components/
│   ├── FieldComponents.kt           # Tarjetas, etiquetas, avisos y acciones reutilizables
│   ├── FieldBackdrop.kt             # Fondo celeste geométrico
│   └── FieldIcon.kt                 # Image/painterResource de los iconos en res/drawable
├── theme/
│   └── FieldTheme.kt                # Colores y tema propios del módulo
├── model/
│   └── FieldModels.kt               # Modelos de obligación y ejecución
├── data/
│   ├── FieldRepository.kt           # Comunicación HTTP y mapeo JSON
│   └── FieldApiException.kt
└── viewmodel/
    └── FieldViewModel.kt            # Estado y coordinación de operaciones
```

Las pantallas reciben estado y callbacks; no navegan directamente ni realizan llamadas HTTP.
`FieldOperationsRoute` centraliza la conexión del host con el módulo; el compañero no necesita
instanciar el repositorio ni el ViewModel. `FieldEntryScreen` permanece en el source set debug, y las pruebas
de modelos están en el paquete de pruebas `fieldoperations.model`.

## Relación con los ejemplos del profesor

Se revisaron los tres archivos ZIP de `scripts`. Se reutiliza su forma de componer las pantallas
y conectar eventos; las páginas y textos de demostración no se copian a las pantallas del producto.

| Ejemplo | Aplicación en nuestro módulo |
| --- | --- |
| `Navigation.zip`: MainActivity con setContent, MaterialTheme y AppNavigation | Misma entrada Compose desde MainActivity |
| `Navigation.zip`: NavHost, composable, navigate y popBackStack | Grafos AppNavigation y FieldNavigation, separados de screens |
| `Navigation.zip`: pantallas que reciben callbacks | Las pantallas informan acciones al grafo sin manejar sus propias rutas |
| `miprimeraapp.zip`: Column, Row, Spacer, Text, Button y Modifier | Composición de las pantallas y componentes compartidos |
| `miprimeraapp.zip`: Image y painterResource | FieldIcon carga recursos vectoriales locales; ya no dibuja iconos con Canvas |
| `MiAplicacion2.zip`: remember, mutableStateOf y callbacks de datos | Estado de formularios y callbacks de nuestras pantallas |

Los scripts no incluyen cliente HTTP, ViewModel, StateFlow ni carga de fotografías. Esas partes
se incorporaron para conectar realmente el flujo con los contratos del backend:

- `FieldRepository`: envía solicitudes, archivos y sesión a la API y convierte el JSON en modelos.
- `FieldViewModel`: coordina las operaciones, conserva el estado durante rotaciones y expone
  carga/errores/avance a la UI mediante StateFlow. Las coroutines permiten hacer las peticiones
  fuera del hilo de UI. No se sustituyen por SharedPreferences: el avance lo guarda el servidor.
- `FieldOperationsRoute`: entrega los datos del host al ViewModel. Su Factory permite construirlo
  con repositorio e ID, manteniendo el ciclo de vida que administra Android.
- `TakePicture` y FileProvider: delegan la captura en la cámara y comparten solo el archivo temporal.
- Variantes debug/release: mantienen el formulario técnico fuera de la aplicación final del grupo.

Los títulos de obligaciones, pasos, requisitos, IDs, versiones y fotos proceden del servidor.
La URL y la sesión se reciben del host; la dirección de emulador es solo una sugerencia en debug.
Los colores, etiquetas y recursos visuales son valores de presentación. Los cuatro motivos,
el máximo de 5 MiB y la zona America/Lima corresponden al contrato acordado, no a datos de prueba
incrustados en el flujo. Los textos visibles siguen escritos en español dentro de los composables;
extraerlos a strings.xml es una posible mejora de recursos/localización, sin alterar estas reglas.

### Criterio de lectura y simplificación

La simplificación prioriza pasos explícitos, incluso cuando aumenta el número de líneas:

- El repositorio prepara un JSONObject, agrega sus campos, envía la solicitud y convierte
  la respuesta en instrucciones separadas. Los modelos usan argumentos con nombre.
- Los arrays JSON se recorren con `for` y listas del tipo correspondiente, sin una función
  genérica de transformación ni cadenas de `let` para realizar varias operaciones juntas.
- Cada acción pública del ViewModel tiene su bloque. Se mantienen dos ayudas comunes con
  propósito concreto: comprobar si se puede editar y coordinar carga/errores de la petición.
- La validación de envío recorre pasos y fotos explícitamente; conserva las mismas condiciones.
- El tamaño de vista previa de fotografías se calcula con un `while`, manteniendo el cálculo
  anterior de potencias de dos y el límite de 1200 píxeles.
- Los callbacks que realizan varias acciones las escriben en líneas distintas y conservan
  su orden. Los textos, composición, dimensiones, colores y reglas no se modifican.

Se conservan ViewModel, StateFlow, coroutines, el ciclo de vida y el permiso de red local porque
tienen una función en el comportamiento ya probado. No se trasladan peticiones HTTP a las
pantallas ni se guarda el avance en preferencias para acercarse artificialmente a los ejemplos.

Para verificar esta refactorización, `FieldRepositoryTest` usa un servidor HTTP temporal en
la JVM y la biblioteca `org.json` exclusivamente en `testImplementation`; no se agrega un
cliente HTTP ni otra dependencia a la aplicación distribuida. Sus cinco pruebas se ejecutaron
contra el repositorio original antes de editarlo y se repiten contra la versión explícita.
Cubren el orden y los nulos del JSON, solicitudes con versión, multipart de foto, autorización,
errores y ausencia de reintentos automáticos. No acceden al backend del grupo ni a sus datos.

Resultado de esta simplificación (2026-11-06): APK debug generado, código release compilado,
10 pruebas unitarias aprobadas y lint con 0 errores y 13 advertencias. Los cinco casos HTTP
adicionales pasaron tanto antes como después de reescribir el repositorio. La nueva advertencia
corresponde a una versión posterior disponible de la biblioteca JSON de pruebas.
Se verificaron por hash 151 archivos fuente y de documentación/configuración del backend:
ninguno cambió. Componentes visuales, tema y grafos de navegación también se conservaron.
Los textos y valores literales de las pantallas se compararon con la versión probada, y el cálculo
de escala de las fotos se contrastó en 81 combinaciones de dimensiones. No se repitió el recorrido
visual en el emulador durante esta refactorización; la validación nueva es de compilación,
contratos HTTP locales, pruebas y comparación de código.

### Entrada temporal desde MainActivity

`MainActivity` ejecuta `setContent { MaterialTheme { AppNavigation() } }`, siguiendo el ejemplo
del profesor. No contiene el texto inicial de demostración ni redirige a una actividad de pruebas.
El paquete `navigation` define `AppNavigation`, separado de las pantallas, por variante:

- `src/debug/.../navigation/AppNavigation.kt`: NavHost con la entrada `FieldEntryScreen` y
  el módulo mediante `FieldOperationsRoute`. La pantalla recibe un callback; el grafo navega al módulo.
  La sesión se mantiene en `FieldSessionViewModel` solo en memoria, sobrevive a una rotación
  y se vuelve a solicitar si se recrea el proceso. No se incluye el token en rutas o argumentos.
- `src/release/.../navigation/AppNavigation.kt`: grafo con un aviso de integración pendiente,
  sin formulario de pruebas, credenciales ni funcionalidades de login/listado inventadas.

MainActivity continúa como única actividad con filtro MAIN/LAUNCHER. Se eliminan
`FieldDebugActivity` y los puentes `startup/MainEntry`; `FieldOperationsActivity` se conserva
como entrada opcional para el compañero. Las pantallas de detalle, ejecución, foto e impedimento,
sus callbacks, la UI y las reglas del módulo no cambian.
Cuando llegue login/listado, integrar el grafo del compañero en `AppNavigation` y abrir nuestro
módulo con la sesión y obligación seleccionada; mantener el contrato descrito arriba.
Actualmente `AppNavigation` está definido por variante para separar el acceso de pruebas.
Si el compañero mueve el grafo final a `src/main/.../navigation/AppNavigation.kt`, debe retirar
las dos definiciones homónimas de debug/release para evitar clases duplicadas. El módulo
`fieldoperations` de `src/main` se conserva. El formulario y `FieldSessionViewModel` son solo
herramientas de debug y no participan en la integración con el login real.

## Presentación visual

El estilo del módulo toma como referencia los mockups de detalle, ejecución, revisión de foto,
impedimento y resultado enviado (`180625`, `180638`, `180648`, `180706`, `180721`). Usa fondo
celeste geométrico, tarjetas blancas de bordes suaves, títulos azul marino, iconos de línea,
etiquetas de estado y botones azules. Los pasos del detalle están numerados y la ejecución
muestra contadores y una barra de avance calculados a partir de la respuesta existente.
Las acciones principales de detalle, ejecución e impedimento permanecen al pie mientras el
contenido se desplaza. Los textos largos pueden ocupar varias líneas sin ocultar los botones.

Las imágenes mostradas son las capturadas o consultadas en la API; no se agregan fotos de
demostración, verificaciones de nitidez, auditorías o aprobaciones sin respaldo del servidor.
El formulario técnico de acceso existe solo en debug y conserva su función de prueba.
Los cambios visuales no alteran las rutas, el ViewModel, repositorios, reglas ni contratos.
El usuario confirmó que el flujo y la UI funcionan correctamente mediante Run en el emulador
(2026-11-05). La captura automatizada de Android Studio falló por tiempo de espera, por lo que
esta confirmación se atribuye al usuario; no se adjuntan capturas de todo el recorrido.

## Contratos y comportamiento

Fuente: `backend/serviceComplianceAPI/docs/field-operations/operator-flow.md` y controladores
`FieldOperationController`, `ObligationService` del backend del workspace.

- Las instrucciones, pasos, requisitos, identificadores y versiones proceden de la API.
- Cada paso se confirma en el servidor antes de cambiar el avance mostrado.
- Los pasos y las fotos exigidas son colecciones independientes. Se admite una obligación
  configurada sin fotografías; no se inventan requisitos.
- La captura delega en la cámara del dispositivo mediante `TakePicture` y un FileProvider
  limitado a la carpeta de caché del módulo. Se permite repetir antes de subir.
- La foto se envía como archivo JPEG, hasta 5 MiB. Después de confirmarse no se reemplaza.
  Las fotos registradas se consultan mediante la ruta privada autenticada de la API.
- El envío requiere todos los pasos y todas las fotografías. Solo una respuesta correcta
  del servidor muestra «Completada y enviada». No equivale a cumplimiento aprobado.
- US-07 registra los cuatro motivos acordados con descripción obligatoria y UUID estable
  para reintentar el mismo contenido. Se registra sobre la obligación completa (paso opcional
  del contrato no enviado). El impedimento no completa ni exime requisitos.
- Se usa `expectedVersion` de la última respuesta. Tras conflicto o resultado de escritura
  incierto se bloquean nuevas escrituras hasta pulsar Actualizar. No hay reintento automático.
- `canEdit` y el estado del servidor controlan la edición. Se muestran fechas en Lima y el
  estado Vencida; se consulta cada 30 segundos mientras la pantalla está visible. Cada comando
  vuelve a ser validado por el backend contra su reloj.

## Ejecutar desde Android Studio

1. Abrir este proyecto y sincronizar Gradle. Usar el JDK indicado por el proyecto (25).
2. Seleccionar variante **debug**, dispositivo/emulador y configuración **app**.
   La configuración compartida `.run/app.run.xml` usa Launch: **Default Activity** (`MainActivity`).
   Si el IDE conserva la configuración anterior «Flujo operario», seleccionar `app` o configurar
   una Android App del módulo app con Launch: Default Activity. Ya no se usa Specified Activity.
3. Pulsar **Run**. MainActivity renderiza AppNavigation y su pantalla inicial de pruebas,
   que solicita URL, ID y token de una sesión real.
   No realiza login, listado ni usa datos ficticios. Solo existe en `src/debug`.
4. Con el backend local levantado, el emulador accede al host mediante
   `http://10.0.2.2:8080` (ajustar el puerto real). HTTP solo se permite para destinos locales
   en debug; el resto requiere HTTPS. No usar credenciales del almacenamiento en Android.
   En Android 17/API 37, al pulsar Abrir obligación, aceptar la solicitud de acceso a la red
   local. Se declara y solicita `ACCESS_LOCAL_NETWORK` únicamente en la entrada debug cuando
   la URL apunta al equipo local. Si se deniega, no se abre el flujo ni se envían solicitudes.
5. Usar una obligación real asignada al operario del token y dentro de su horario.
   Para fotos, configurar el almacenamiento privado del backend; sin él la API responde 503.

Recorrido a corroborar con ambos procesos activos: detalle → iniciar → marcar un paso → salir
y reabrir → verificar conservación → capturar/repetir/subir foto → consultar foto registrada →
reportar impedimento → comprobar que sigue incompleta → completar requisitos → enviar →
reabrir enviada y sin edición. Comprobar también hora límite y una versión desactualizada.

## Verificación y límites

Comando de verificación local: `gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:compileReleaseKotlin :app:processReleaseMainManifest --no-daemon`.
Las pruebas unitarias cubren las condiciones de envío y restricciones de la URL de conexión.
Compilar y ejecutar esas pruebas no demuestra funcionamiento HTTP, de cámara ni visual.
La evidencia del recorrido requiere Run, backend activo, sesión válida y almacenamiento real.
No se han cambiado requisitos, endpoints, esquema de base de datos ni backend para este incremento.

Resultado local (2026-11-05): compilación debug y pruebas unitarias aprobadas; lint sin errores,
con advertencias de recursos originales sin uso, etiqueta redundante y versiones más nuevas
de dependencias. No se actualizaron versiones existentes para silenciar advertencias.
El flujo local fue probado con éxito por el usuario desde Kotlin contra el backend local.
El backend también comprobó el flujo mediante HTTP y persistencia real de fotos en disco.
Esto no verifica Supabase, la base compartida ni la integración final con login/listado.
La limpieza posterior conserva las pantallas y sus reglas; las verificaciones de compilación,
pruebas y lint se repiten antes de subir la rama. El host de release continúa pendiente de
login/listado, y US-08 sigue fuera de esta entrega.

Verificación de la limpieza para entregar la rama: APK debug generado, código release compilado,
5 pruebas unitarias aprobadas y lint con 0 errores y 12 advertencias. Se confirmó MainActivity
como única entrada LAUNCHER en ambas variantes; el permiso de red local permanece solo en debug.
La comparación de contenidos confirmó intactos las pantallas, estilos, modelos, repositorio,
ViewModel y rutas del módulo. La nueva entrada centraliza únicamente la construcción del ViewModel.
Se revisaron también los archivos nuevos antes del staging: sin espacios sobrantes ni líneas
vacías adicionales al final; build, caches, configuración local y datos de sesión quedan excluidos.

Archivos del incremento: los archivos Kotlin de la estructura anterior, `field_photo_paths.xml`, manifiesto principal,
dependencias en `app/build.gradle.kts` y `gradle/libs.versions.toml`, entrada/manifiesto/red de
debug, `ExecutionTest.kt`, configuración `.run/app.run.xml`, grafos `navigation/AppNavigation.kt`
de debug/release, `FieldEntryScreen`, `FieldSessionViewModel`, aviso de integración de release,
ajuste de `MainActivity` y esta guía.
