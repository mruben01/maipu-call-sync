# Maipú Call Sync — aplicación Android

Esta carpeta contiene el proyecto Android nativo de Maipú Call Sync.
La aplicación **no graba llamadas** ni usa VoIP/SIP. Consulta `MediaStore.Audio`
para detectar grabaciones `.m4a` de Samsung Phone, las reproduce mediante su
`Content URI` y las sincroniza automáticamente con Supabase sin moverlas ni
eliminarlas. También conserva la sincronización manual.

## Alcance implementado

- Kotlin + Gradle + Android SDK.
- Material 3 con Inicio y Reporte para el teléfono actual.
- Solicitud de `READ_MEDIA_AUDIO` en Android 13 o superior.
- Compatibilidad con `READ_EXTERNAL_STORAGE` en Android 12 o anterior.
- Consulta oficial de `MediaStore.Audio.Media`.
- Filtro por archivos `.m4a`/audio MP4 dentro de `Recordings/`.
- Sincronización automática acotada a `Recordings/Call/`, indicada en el
  diagnóstico.
- Metadata: nombre, fecha, tamaño, duración, MIME type, ruta relativa y
  `Content URI`.
- Parser independiente del nombre observado en Samsung:
  `Grabación de llamada +543513993670_260918_090437.m4a`.
- Reproducción por `Content URI`, sin copiar el audio.
- Diagnóstico interno con conteos, dispositivo, versión Android y errores;
  ya no se muestra la sección técnica al vendedor.
- Sincronización manual con Supabase Storage (`call-recordings`) y Data API
  (`public.call_recordings`).
- Lectura del audio por streaming desde `ContentResolver`; no se guarda una
  copia temporal completa.
- Identificador aleatorio por instalación y estado local de sincronización,
  reintentos y errores.
- WorkManager ejecuta la sincronización en segundo plano con conectividad,
  escaneo periódico cada 15 minutos y reintentos con backoff.
- Un observador de MediaStore adelanta el escaneo mientras el proceso está
  activo. El audio debe mantener tamaño y fecha sin cambios durante dos minutos.
- WorkManager restaura el trabajo programado después de reiniciar el teléfono.
- Cada ejecución procesa como máximo dos grabaciones listas y encadena el resto
  para no agotar el tiempo disponible de ejecución en segundo plano.
- El monitoreo V3 usa una credencial independiente por instalación y un
  servicio central; no modifica la autenticación del flujo de audios.
- No incluye TIMM, WISE, Power BI, transcripción,
  IA, publicidad ni trackers.

## Compatibilidad de herramientas

La configuración queda fijada así:

```text
Gradle Wrapper: 8.9
Android Gradle Plugin: 8.7.3
Java/JDK en GitHub Actions: Temurin 17
compileSdk: 35
targetSdk: 35
minSdk: 26
```

El proyecto incluye todos los archivos del Gradle Wrapper:

```text
gradlew
gradlew.bat
gradle/wrapper/gradle-wrapper.jar
gradle/wrapper/gradle-wrapper.properties
```

## Limitación de este entorno

El Wrapper se pudo ejecutar y descargar Gradle 8.9 correctamente. La
compilación local se detiene porque este entorno Replit no tiene Android SDK
configurado:

```text
SDK location not found. Define a valid SDK location with an ANDROID_HOME
environment variable or by setting the sdk.dir path in local.properties.
```

Por esa razón no se produjo ni se validó una APK aquí. GitHub Actions instala
Android SDK 35 y Build Tools 35.0.0 antes de ejecutar el mismo Wrapper, por lo
que la compilación automática es el camino preparado para esta POC. No se
afirma que la funcionalidad completa funcione en el Galaxy A04e hasta ejecutar
la prueba física descrita abajo.

## Requisitos para compilar

- Android Studio actualizado.
- JDK 17, normalmente provisto por Android Studio.
- Android SDK Platform 35.
- Android SDK Build-Tools compatible con Android Gradle Plugin 8.7.3.
- Un dispositivo Samsung Galaxy A04e SM-A042M con Android 13 para la prueba.

## Abrir y compilar

1. Copiar o abrir la carpeta `android-app/` en Android Studio.
2. Esperar la sincronización de Gradle.
3. Si Android Studio solicita instalar componentes del SDK, aceptarlos.
4. Seleccionar la variante `debug` **solo para desarrollo**, nunca como APK
   oficial distribuible a los vendedores.
5. Ejecutar `app` para instalarla en un dispositivo conectado, o usar
   **Build > Build Bundle(s) / APK(s) > Build APK(s)**.

El proyecto incluye el Gradle Wrapper, por lo que desde Linux se puede
ejecutar:

```bash
cd android-app
./gradlew assembleDebug
```

El Wrapper está fijado a Gradle 8.9 en
`gradle/wrapper/gradle-wrapper.properties`. En el primer uso descarga esa
versión automáticamente.

## Firma persistente de la APK oficial

**Estado:** la configuración está preparada, pero todavía NO se generó la
keystore de Maipú ni una APK release oficial. Solo Maipú debe generar y
custodiar la clave fuera del repositorio. Un push a `main` ejecuta las pruebas
Android, **no** intenta compilar ni publicar un release. El release se ejecuta
únicamente con **Run workflow** desde `main`, tras la aprobación del
Environment protegido `android-signing`.

La APK oficial conserva `applicationId = com.maipu.callsync`, comienza con
`versionCode = 2`, `versionName = 3.0.0` y usa el alias fijo
`maipu-call-sync-release`. En cada versión posterior se incrementa
`versionCode` (3, 4, ...); `versionName` puede avanzar de forma legible, por
ejemplo `3.0.1`. No distribuir APKs debug a estos teléfonos después de la
primera migración: cada firma debug puede ser distinta. La keystore permanente
debe conservarse para **todas** las versiones oficiales siguientes.

### Preparación en una PC Windows de confianza

1. Usar una PC controlada por Maipú. Instalar JDK 17 (o usar el JDK 17 de
   Android Studio) y abrir **PowerShell**. Confirmar que `keytool` responde con
   `keytool -help`. **No escribir nunca una contraseña dentro de un comando**:
   `keytool` la pedirá de forma interactiva.
2. Crear una carpeta **fuera del clon del repositorio**, por ejemplo
   `Documentos\MaipuFirma`, y elegir allí la ruta
   `maipu-call-sync-release.p12`. Si el archivo ya existe, **no volver a
   ejecutar el comando de generación**: regenerarlo crearía otra identidad
   imposible de usar para actualizar las APK ya instaladas.
3. En PowerShell, ajustar la ruta si se eligió otra carpeta y ejecutar:

   ```powershell
   $KeyDir = Join-Path $env:USERPROFILE 'Documents\MaipuFirma'
   New-Item -ItemType Directory -Force -Path $KeyDir | Out-Null
   $KeyPath = Join-Path $KeyDir 'maipu-call-sync-release.p12'
   if (Test-Path $KeyPath) { throw 'La keystore ya existe: no la reemplaces.' }
   keytool -genkeypair -keystore $KeyPath -storetype PKCS12 `
     -alias maipu-call-sync-release -keyalg RSA -keysize 3072 `
     -sigalg SHA256withRSA -validity 10950
   ```

   Elegir una contraseña larga y única, guardarla en el gestor de contraseñas
   de la organización y responder las preguntas de identidad del certificado.
   Para este archivo PKCS#12 el build utiliza la misma contraseña para el
   almacén y la clave. **No compartirla por chat, correo ni tickets.**
   `Test-Path $KeyPath` debe devolver `True`: esa es la ubicación del original.
4. Obtener la **huella pública** del certificado:

   ```powershell
   keytool -list -v -keystore $KeyPath -storetype PKCS12 `
     -alias maipu-call-sync-release
   ```

   Identificar la línea `SHA256:` (64 caracteres hexadecimales, posiblemente
   separados por `:`). Anotarla como referencia aprobada. Es la huella del
   **certificado**, distinta del hash del archivo `.p12` y distinta de la
   huella del token de monitoreo mostrada por la app. No copiar ninguna clave
   ni contraseña a la documentación.
5. Hacer **antes del primer release** dos copias cifradas de la keystore fuera
   de GitHub, en custodias independientes. Guardar la contraseña por separado
   en el gestor de contraseñas. Desde una copia, ejecutar el mismo
   `keytool -list -v -keystore <ruta-de-la-copia> -storetype PKCS12 -alias
   maipu-call-sync-release` y comparar la línea `SHA256:` con el original.
   Repetir esta restauración de prueba periódicamente. Perder la clave privada
   impediría actualizar las instalaciones oficiales.

### Environment y secretos de GitHub

6. En el repositorio **mruben01/maipu-call-sync**, ir a **Settings →
   Environments → New environment** y llamarlo exactamente
   `android-signing`. Restringirlo a la rama `main` y configurar revisores
   obligatorios de confianza. Si el plan o los permisos de GitHub no permiten
   esas protecciones, **detenerse antes de la primera publicación**.
7. Dentro de ese Environment, crear **Environment secrets** con estos nombres:

   - `MAIPU_SIGNING_KEYSTORE_B64`: representación Base64 completa del `.p12`.
   - `MAIPU_SIGNING_PASSWORD`: contraseña única guardada en el gestor.

   Para copiar el Base64 al portapapeles en la PC, sin imprimirlo ni guardarlo
   en un `.txt`, ejecutar en PowerShell con `$KeyPath` definido como arriba:

   ```powershell
   [Convert]::ToBase64String([IO.File]::ReadAllBytes($KeyPath)) | Set-Clipboard
   ```

   Desactivar temporalmente el historial y la sincronización del portapapeles
   de Windows; pegar **solo** en el campo Value del secreto correspondiente,
   guardar y vaciar el portapapeles (`Set-Clipboard -Value ' '`). No pegarlo
   en la terminal, un chat ni un issue. Introducir la contraseña directamente
   desde el gestor en el segundo secreto. Nunca añadir `.p12`, Base64 ni
   contraseñas al repositorio.
8. Crear la **Environment variable** (no secreto)
   `MAIPU_SIGNING_CERT_SHA256` con la huella pública de `SHA256:` obtenida en
   el paso 4. Se aceptan letras hexadecimales y `:`. El workflow **falla**
   si esta variable falta o si la APK usa otro certificado. Los secretos
   `SUPABASE_URL` y `SUPABASE_PUBLISHABLE_KEY` existentes deben estar
   disponibles para el build; **nunca** agregar `service_role` a GitHub.

### Primer build y comprobación de continuidad

9. Ir a **Actions → Build Android → Run workflow**, elegir `main` y ejecutar.
   Primero deben pasar las pruebas. Un revisor habilita el Environment
   `android-signing`; recién entonces comienza el job release. Si faltan
   secretos o la huella no coincide, el job falla y **no publica APK**.
10. En el run exitoso, descargar el artifact `maipu-call-sync-release`,
    descomprimirlo y buscar dentro **`maipu-call-sync.apk`**. El workflow
    comprueba `com.maipu.callsync`, `versionCode 2`, `versionName 3.0.0` y
    ejecuta `apksigner verify --verbose --print-certs`. Solo si la firma es
    válida y su SHA-256 coincide con la huella aprobada sube el artifact.
    **No distribuir** un `app-debug.apk` como sustituto.
11. Como segunda compilación de prueba, usar nuevamente **Run workflow** en
    el **mismo commit de `main`**, sin cambiar `versionCode`. Debe superar la
    misma comparación de SHA-256 pública. También se pueden descargar ambas
    APK y ejecutar `apksigner verify --print-certs` sobre cada una en una PC
    con Android SDK: el `Signer #1 certificate SHA-256 digest` debe ser
    idéntico. Los hash SHA-256 de los **archivos APK** pueden diferir sin que
    cambie el certificado; no son la prueba de identidad de firma. Para la
    siguiente versión distribuida, incrementar `versionCode`.

El runner reconstruye el `.p12` en su directorio temporal con permisos
restringidos, no lo incluye en el artifact y lo elimina al terminar. Gradle
rechaza `assembleRelease` si falta el archivo o la contraseña. El contenido
del archivo y las contraseñas no se imprimen en logs.

## Configuración de Supabase

Gradle lee `SUPABASE_URL` y `SUPABASE_PUBLISHABLE_KEY` del entorno de
compilación y los genera en `BuildConfig`. No se guardan valores en el código
fuente ni se escriben en logs. Si no están presentes al compilar, la aplicación
mostrará un error de configuración al intentar sincronizar.

Ejemplo de compilación local (reemplazar los marcadores en el entorno local;
no guardar valores en el repositorio):

```bash
SUPABASE_URL="<project-url>" \
SUPABASE_PUBLISHABLE_KEY="<publishable-key>" \
./gradlew assembleDebug
```

Las Secrets de Replit no se transfieren automáticamente a Android Studio ni a
GitHub Actions. Para generar una APK utilizable, esas variables deben estar
disponibles en el entorno que ejecute Gradle. El workflow de GitHub Actions
requiere configurar los mismos nombres como GitHub Actions Secrets si se usa
para producir APKs sincronizables.

La publishable key está diseñada para clientes públicos; aunque no aparece en
el código fuente, queda dentro de la APK y puede extraerse. No usar aquí una
`service_role` key. El acceso sigue dependiendo de las policies de Supabase.

La sincronización calcula SHA-256 leyendo el URI en streaming. El objeto se
guarda con la ruta `<sha256>.m4a`, sin incluir número de teléfono ni nombre
original en la ruta. La app usa esa ruta estable para que repetir una solicitud
no cree otro objeto. Para hacer idempotente el registro de metadatos, el SQL de
`supabase/migrations/20260924000000_unique_call_recordings_storage_path.sql`
establece `storage_path` como `NOT NULL` y `UNIQUE`. La migración falla si
encuentra valores duplicados o nulos; no elimina ni modifica filas existentes.

## Ubicación de la APK

Después de ejecutar `assembleDebug`, la APK queda en:

```text
android-app/app/build/outputs/apk/debug/app-debug.apk
```

También se puede usar el botón **Locate** que aparece en Android Studio luego
de **Build APK(s)**.

## Compilación automática con GitHub Actions

El repositorio incluye el workflow:

```text
.github/workflows/build-android.yml
```

Se ejecuta manualmente mediante `workflow_dispatch` y automáticamente en cada
`push` a `main` o `master` que modifique `android-app/`.

El workflow:

1. instala Java 17;
2. instala el Android SDK, Android 35 y Build Tools 35.0.0;
3. da permisos de ejecución a `android-app/gradlew`;
4. ejecuta las pruebas unitarias del parser desde `android-app/`;
5. ejecuta `./gradlew assembleDebug` desde `android-app/`;
6. publica la APK como artifact descargable llamado
   `maipu-call-sync-debug`.

### Pasos para usarlo

1. Descargar el proyecto completo desde Replit, incluyendo la carpeta oculta
   `.github/`, o subirlo a un repositorio GitHub.
2. Verificar que el repositorio contenga `android-app/` y
   `.github/workflows/build-android.yml`.
3. Hacer push a la rama `main` o `master`.
4. En GitHub abrir **Actions** y seleccionar **Build Android**.
5. Para ejecutarlo manualmente, pulsar **Run workflow**, elegir la rama y
   confirmar.
6. Abrir la ejecución correspondiente y esperar el job **Test and build debug
   APK**.
7. La compilación es exitosa cuando el job aparece con estado verde y finaliza
   la etapa **Build debug APK**.
8. En la página de la ejecución, descargar el artifact
   `maipu-call-sync-debug`.
9. Extraer el ZIP descargado para obtener `app-debug.apk`.
10. Transferir `app-debug.apk` al Samsung por cable USB, Google Drive,
    almacenamiento compartido u otro medio local.
11. Abrir el APK desde **Mis archivos** en el Samsung. Si Android lo solicita,
    entrar en **Ajustes > Datos biométricos y seguridad > Instalar apps
    desconocidas**, seleccionar **Mis archivos** y activar temporalmente el
    permiso.
12. Volver a abrir el APK, pulsar **Instalar** y abrir **Maipú Call Sync**.

## Instalación manual en el Galaxy A04e

### Opción A: Android Studio

1. Activar **Opciones de desarrollador** y **Depuración USB** en el teléfono.
2. Conectar el teléfono por USB y aceptar la autorización.
3. Seleccionar el Galaxy A04e en Android Studio.
4. Ejecutar la configuración `app`.

### Opción B: APK descargada desde GitHub

1. Copiar `app-debug.apk` al teléfono.
2. Abrir el archivo desde Mis archivos.
3. Permitir temporalmente la instalación desde **Mis archivos** si Android lo
   solicita.
4. Instalar **Maipú Call Sync** y abrirla.

## Permiso esperado en Android 13

La aplicación solicita:

```text
android.permission.READ_MEDIA_AUDIO
```

La pantalla debería mostrar **AUTORIZADO** después de concederlo. La
aplicación no solicita `MANAGE_EXTERNAL_STORAGE`, no usa root, ADB permanente
ni rutas físicas absolutas.

## Prueba de éxito

1. En el Galaxy A04e, verificar que Samsung Phone tenga habilitado:
   **Teléfono > Ajustes > Grabar llamadas > Grabar llamadas automáticamente**.
2. Instalar y abrir Maipú Call Sync.
3. Conceder el permiso de audio.
4. Realizar una llamada celular normal desde Samsung Phone o recibir una.
5. Esperar a que Samsung cree la grabación `.m4a`.
6. Abrir Maipú Call Sync.
7. Presionar **BUSCAR GRABACIONES**.
8. Confirmar que aparece la grabación, su metadata y el `Content URI`.
9. Presionar **REPRODUCIR** y confirmar que se escucha el archivo.

10. Presionar **SINCRONIZAR** en una grabación (o **SINCRONIZAR PENDIENTES**).
11. Confirmar en la app el estado **Sincronizada** y verificar que el objeto y
    la fila correspondiente aparezcan en Supabase.

La primera apertura sigue siendo necesaria para conceder el permiso de audio.
Luego, el proceso automático no requiere abrir la aplicación ni pulsar el
botón de sincronización. WorkManager espera conectividad, reintenta errores
transitorios y conserva el trabajo pendiente después de reiniciar. Los
rechazos permanentes se reevalúan como máximo una vez por hora; el botón
manual siempre permite reintentarlos de inmediato. El escaneo
periódico recupera eventos perdidos si Android no entrega una notificación de
MediaStore. Android y las optimizaciones de batería de Samsung pueden demorar
la ejecución; los 15 minutos son un intervalo mínimo, no una garantía de
tiempo real.

Los fallos quedan guardados localmente con un mensaje y contador de intentos.
Si la subida de audio ya fue confirmada pero falla la inserción de metadatos,
el reintento automático y el botón manual vuelven a insertar solo los
metadatos. El estado local `SYNCED` evita volver a subir grabaciones
completadas. El campo remoto `sync_status` se inserta como `synced` solo
después de completar ambas operaciones; los errores se conservan localmente
porque la tabla actual no tiene una columna de error.

**Limitación a validar en el piloto:** el objeto remoto se identifica por el
SHA-256 del audio. Si dos llamadas distintas producen bytes exactamente
iguales, la restricción única de `storage_path` solo permite una fila remota;
el conflicto actual se interpreta como existente. Resolver esa ambigüedad
requiere cambiar la identidad de Storage o la restricción remota y queda
fuera de esta actualización.

El resultado exitoso responde que, en ese modelo/configuración, la aplicación
puede detectar y leer las grabaciones expuestas por Samsung Phone. Si el
archivo existe en el almacenamiento pero no aparece en la aplicación, el
resultado indica que Android/Samsung no lo está exponiendo en el
`MediaStore.Audio` consultado; la POC no intenta evadir esa restricción.

## Interpretación del nombre

El parser reconoce exactamente el prefijo observado y el sufijo:

```text
Grabación de llamada <número>_<YYMMDD>_<HHMMSS>.m4a
```

Para el ejemplo:

```text
Grabación de llamada +543513993670_260918_090437.m4a
```

la interpretación aplicada es:

- número: `+543513993670`
- fecha: `18/09/2026` (`26` = año 2026, `09` = mes, `18` = día)
- hora: `09:04:37`

El parser usa 2000–2069 para años `00`–`69` y 1970–1999 para `70`–`99`.
Si el nombre no coincide o la fecha es inválida, la app sigue funcionando y
muestra el nombre original sin inventar una fecha.

La implementación está en:

```text
app/src/main/java/com/maipu/callsync/domain/CallFileNameParser.kt
```

Y está cubierta por pruebas unitarias en:

```text
app/src/test/java/com/maipu/callsync/domain/CallFileNameParserTest.kt
```

La prueba usa exactamente el nombre real de la POC y verifica:

```text
phoneNumber = +543513993670
date        = 2026-09-18
time        = 09:04:37
```

También verifica que una fecha imposible, como `260232`, no sea corregida
silenciosamente y que un nombre no compatible devuelva metadata parseada nula.

## Diagnóstico y logs

El diagnóstico interno conserva información para soporte técnico; la sección
**Soporte y diagnóstico** ya no aparece en la interfaz del vendedor. La
información disponible para investigar problemas incluye:

- cantidad total de audios que devolvió `MediaStore.Audio`;
- cantidad con ruta relativa dentro de `Recordings`;
- cantidad dentro de `Recordings/Call`;
- estado del permiso;
- versión de Android, fabricante y modelo;
- errores de consulta o reproducción.

Para obtener logs desde una máquina con Android SDK:

```bash
adb logcat -c
adb logcat | grep -i -E "MediaStore|MaipuCallSync|SecurityException|MediaPlayer"
```

Si no aparece ninguna grabación:

1. Confirmar que el permiso está en **AUTORIZADO**.
2. Confirmar que Samsung Phone realmente creó el `.m4a`.
3. Abrir Inicio después de terminar la llamada y esperar la nueva lectura.
4. Revisar los conteos de diagnóstico interno con soporte técnico.
5. Revisar `adb logcat` y el mensaje de error mostrado.
6. Comprobar si la ruta que Samsung muestra es `Recordings/Call/`.

La POC no usa `DATA` ni una ruta absoluta como requisito. La única referencia
de ubicación usada para filtrar es `MediaStore.MediaColumns.RELATIVE_PATH`
cuando Android la proporciona.

## V3: alta de los dos Samsung del piloto

El monitoreo es **independiente** de la sincronización de audios ya validada.
La nueva APK usa el mismo proyecto de Supabase. La migración
`supabase/migrations/20260925000000_device_monitoring_v3.sql` y la función
`monitor-report` ya forman parte de V3; la función verifica el secreto propio
de cada teléfono. Su clave de servicio queda exclusivamente en Supabase.

**Migración controlada, una sola vez, para los dos Samsung actuales:**

1. **No empezar hasta tener una APK release oficial firmada y verificada.**
   En la versión instalada, comprobar que hay **0 llamadas pendientes y
   0 errores**, revisar la última sincronización exitosa y confirmar
   centralmente las grabaciones esperadas. Si hay algo pendiente o dudoso,
   detenerse antes de desinstalar. Anotar el `device_id` anterior cuando esté
   disponible para distinguirlo del nuevo en soporte.
2. Desinstalar la versión anterior **solo después** de esa comprobación.
   `allowBackup=false`: la desinstalación borra datos y estado local, incluida
   la identidad y la credencial de monitoreo. Las grabaciones de Samsung
   accesibles en MediaStore deben verificarse nuevamente; no asumir que los
   estados de sincronización locales se conservarán.
3. Instalar `maipu-call-sync.apk`, no `app-debug.apk`. Abrirla y otorgar
   los permisos de audio solicitados. Confirmar que la grabación nativa de
   llamadas Samsung sigue habilitada y que la app detecta las grabaciones.
   **No instalar un debug sobre esta versión oficial:** cambiaría la firma.
4. En el **teléfono A**, ingresar el vendedor y la línea corporativa. La
   instalación nueva tendrá su propio `device_id` y huella de monitoreo.
   Verificar ambos en «Pendiente de vinculación». Repetir en el **teléfono
   B** con sus propios datos; comprobar que ambos identificadores difieren.
5. El administrador da de alta las **nuevas** parejas en Supabase usando el
   SQL y la comprobación de filas de abajo. Si existía un alta de monitoreo
   de la instalación anterior, desactivarla/revocar su credencial
   administrativamente, sin modificar los registros históricos. Confirmar
   que cada app indica «Vinculado».
6. Realizar **una llamada de prueba por teléfono**, verificar que Samsung
   la graba, que la app la detecta y sincroniza, y comprobar audio y metadata
   en Supabase. Verificar después un `last_heartbeat` nuevo para A y B.
   WorkManager puede demorar: dos horas es un objetivo aproximado, no un
   horario exacto.

La desinstalación puede hacer que archivos de audio aún presentes vuelvan a
evaluarse porque se pierde el estado local: antes y después del cambio revisar
en Supabase que no se produzcan registros inesperados. **Solo esta primera
migración de firma exige desinstalar; las futuras APK oficiales firmadas con
la misma keystore y `versionCode` creciente se instalarán encima sin borrar
datos.** La sincronización de audios no depende de completar el alta de
monitoreo.

**Datos de alta de la nueva instalación:**

1. Abrir la app en el **teléfono A** y confirmar nombre y línea; la identidad
   queda bloqueada frente a cambios accidentales. Anotar o copiar
   **device_id** y **huella SHA-256 de monitoreo** desde la tarjeta
   «Pendiente de vinculación». Esa huella **no es** el secreto del teléfono
   ni la huella pública del certificado de firma Android. No solicitar,
   copiar ni transmitir el secreto bruto: la aplicación nunca lo muestra.
2. Repetir el paso anterior en el **teléfono B**, con vendedor y línea B.
   Comprobar que el `device_id` y la huella son distintos de los de A.
3. Un administrador autorizado entra en el **SQL Editor del mismo proyecto de
   Supabase** y registra cada asociación. Sustituir los campos entre `<...>`
   por lo copiado de la pantalla. La línea debe coincidir exactamente con lo
   ingresado en el teléfono, incluidos espacios o prefijos. **No ingresar
   secretos de monitoreo en SQL**.

   ```sql
   BEGIN;
   INSERT INTO public.devices (device_id, seller_name, corporate_phone)
   VALUES
     ('<UUID_A>'::uuid, '<VENDEDOR_A>', '<LINEA_A>'),
     ('<UUID_B>'::uuid, '<VENDEDOR_B>', '<LINEA_B>');

   INSERT INTO public.device_monitor_credentials (device_id, token_hash)
   VALUES
     ('<UUID_A>'::uuid, '<HUELLA_SHA256_A>'),
     ('<UUID_B>'::uuid, '<HUELLA_SHA256_B>');
   COMMIT;
   ```

   Revisar los nombres si incluyen comillas simples: SQL exige duplicarlas.
   Nunca reutilizar una misma huella entre instalaciones. Las tablas nuevas
   no permiten escritura directa de la APK: solo la función central acepta
   reportes que acrediten el secreto correspondiente a la huella registrada.
4. Abrir nuevamente cada app con Internet. El reporte al abrir se limita para
   no duplicar trabajo; WorkManager también intentará enviar el heartbeat
   aproximadamente cada dos horas, sin garantía de minuto exacto. Esperar a
   que cada teléfono muestre «Vinculado» y **verificar ambos en Supabase**:

   ```sql
   SELECT d.device_id, d.seller_name, d.corporate_phone, d.active,
          d.last_heartbeat, d.last_scan_received_at, d.permissions_ok,
          d.health_status, d.calls_detected, d.calls_synced,
          d.calls_pending, d.calls_error, c.revoked_at
   FROM public.devices AS d
   JOIN public.device_monitor_credentials AS c USING (device_id)
   WHERE d.device_id IN ('<UUID_A>'::uuid, '<UUID_B>'::uuid);
   ```

   Deben aparecer **dos filas distintas**, ambas con `active = true`,
   `revoked_at IS NULL`, el vendedor y línea correctos y `last_heartbeat`
   informado. Los conteos pueden quedar `NULL` hasta una lectura local válida:
   no reemplazarlos por ceros inventados. También debe cumplirse
   `calls_detected = calls_synced + calls_pending + calls_error` cuando haya
   conteos.

Si el teléfono sigue «Pendiente de vinculación», comprobar que `device_id` y
huella se copiaron exactamente, la línea/vendedor coinciden con la asignación
administrativa y que `monitor-report` está desplegada. Un 401 indica que el
secreto no corresponde a una credencial vigente; **no** cambiar las policies
existentes para intentar solucionarlo. Si se pierde el teléfono o su
credencial, revocar la fila de `device_monitor_credentials` y marcar
`devices.active = false` administrativamente antes de volver a vincular.

**Reinstalación:** desinstalar o borrar los datos puede generar un nuevo
`device_id` y destruye la clave protegida por Android Keystore. Requiere
revocar la credencial anterior y dar de alta el nuevo identificador; no se debe
asumir que el teléfono físico sigue siendo la misma instalación. Para
250–300 líneas está previsto sustituir estos INSERT manuales por enrolamiento
administrado.

## Reportes, alertas y correo

Inicio y Reporte muestran únicamente llamadas del teléfono actual. Para
Hoy/Ayer/7 días solo se asignan a una fecha las llamadas cuya fecha de
grabación se conoce de forma fiable; las de fecha desconocida no se inventan.
Los reportes diarios centrales usan `America/Argentina/Cordoba`. Las
estadísticas por dispositivo conservan fechas anteriores en Supabase; cada
heartbeat actualiza solamente la ventana reciente.

`device_alerts` conserva una sola incidencia abierta por dispositivo y tipo.
No se deduce la causa de un heartbeat ausente: la evaluación automática
`HEARTBEAT_MISSING` queda **desactivada** hasta medir los dos Samsung y
acordar un umbral. `device_email_recipients`,
`device_daily_report_settings` y `device_email_outbox` preparan destinatarios,
hora local y auditoría del correo. La generación central del resumen está
disponible por `daily-report`, pero **no hay proveedor de email, envío ni
programación automática de correo activados**: requieren una aprobación
posterior. Ningún teléfono envía email.