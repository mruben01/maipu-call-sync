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
4. Seleccionar la variante `debug`.
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

La sección **DIAGNÓSTICO** muestra:

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
3. Presionar **BUSCAR GRABACIONES** después de terminar la llamada.
4. Revisar los tres conteos del diagnóstico.
5. Revisar `adb logcat` y el mensaje de error mostrado.
6. Comprobar si la ruta que Samsung muestra es `Recordings/Call/`.

La POC no usa `DATA` ni una ruta absoluta como requisito. La única referencia
de ubicación usada para filtrar es `MediaStore.MediaColumns.RELATIVE_PATH`
cuando Android la proporciona.

## V3: alta de los dos Samsung del piloto

El monitoreo es **independiente** de la sincronización de audios ya validada.
La nueva APK sigue usando el mismo proyecto de Supabase y el `device_id` que
genera la instalación existente. Antes de instalarla, aplicar la migración
`supabase/migrations/20260925000000_device_monitoring_v3.sql` y desplegar
`monitor-report` con autenticación propia (verificación JWT de la puerta de
Edge Functions desactivada **solo** para esta función; la función verifica el
secreto de cada teléfono). Su clave de servicio queda exclusivamente en
Supabase; nunca se copia a los teléfonos.

1. Instalar la nueva APK **sobre la versión existente**, sin borrar datos de
   la aplicación. Esto conserva el `device_id` y los estados locales. Abrir la
   app en el **teléfono A**. La sincronización de audios no depende de completar
   el alta de monitoreo.
2. En la configuración inicial de A ingresar nombre y apellido del vendedor A
   y la línea corporativa A. Revisar cuidadosamente: la identidad queda
   bloqueada frente a cambios accidentales. Anotar o copiar **device_id** y
   **huella SHA-256 de monitoreo** desde la tarjeta «Pendiente de vinculación».
   Esa huella **no es** el secreto del teléfono. No solicitar, copiar ni
   transmitir el secreto bruto: la aplicación nunca lo muestra.
3. Repetir el paso anterior en el **teléfono B**, con vendedor y línea B.
   Comprobar que el `device_id` y la huella son distintos de los de A.
4. Un administrador autorizado entra en el **SQL Editor del mismo proyecto de
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
5. Abrir nuevamente cada app con Internet. El reporte al abrir se limita para
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