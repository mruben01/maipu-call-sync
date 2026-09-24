# Maipú Call Sync — POC Android nativa

Esta carpeta contiene el proyecto Android nativo de la prueba de concepto.
La aplicación **no graba llamadas** ni usa VoIP/SIP. Consulta `MediaStore.Audio`
para detectar grabaciones `.m4a` de Samsung Phone, las reproduce mediante su
`Content URI` y permite subirlas manualmente a Supabase sin moverlas ni
eliminarlas.

## Alcance implementado

- Kotlin + Gradle + Android SDK.
- Material 3 con una pantalla única para la POC.
- Solicitud de `READ_MEDIA_AUDIO` en Android 13 o superior.
- Compatibilidad con `READ_EXTERNAL_STORAGE` en Android 12 o anterior.
- Consulta oficial de `MediaStore.Audio.Media`.
- Filtro por archivos `.m4a`/audio MP4 dentro de `Recordings/`.
- Preferencia por `Recordings/Call/`, indicada en el diagnóstico.
- Metadata: nombre, fecha, tamaño, duración, MIME type, ruta relativa y
  `Content URI`.
- Parser independiente del nombre observado en Samsung:
  `Grabación de llamada +543513993670_260918_090437.m4a`.
- Reproducción por `Content URI`, sin copiar el audio.
- Sección de diagnóstico con conteos, dispositivo, versión Android y errores.
- Sincronización manual con Supabase Storage (`call-recordings`) y Data API
  (`public.call_recordings`).
- Lectura del audio por streaming desde `ContentResolver`; no se guarda una
  copia temporal completa.
- Identificador aleatorio por instalación y estado local de sincronización,
  reintentos y errores.
- No contiene WorkManager ni sincronización automática en segundo plano.
- No incluye autenticación corporativa, TIMM, WISE, Power BI, transcripción,
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

Los fallos quedan guardados localmente con un mensaje seguro para mostrar y un
contador de intentos. Si la subida de audio ya fue confirmada pero falla la
inserción de metadatos, el botón de reintento vuelve a insertar los metadatos
sin volver a transmitir el audio. El campo remoto `sync_status` se inserta como
`synced` solo después de completar ambas operaciones; los errores se conservan
localmente porque la tabla actual no tiene una columna de error.

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

## Siguiente fase

La automatización en segundo plano con WorkManager sigue fuera de esta etapa.
Por ahora, el usuario inicia cada sincronización desde la pantalla.