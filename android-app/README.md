# Maipú Call Sync — POC Android nativa

Esta carpeta contiene el proyecto Android nativo de la prueba de concepto.
La aplicación **no graba llamadas**, no usa VoIP/SIP, no copia ni borra archivos
y no sube audio a Internet. Consulta `MediaStore.Audio` para comprobar si
Samsung Phone expone sus grabaciones `.m4a` a otras aplicaciones y, cuando el
URI es accesible, las reproduce directamente mediante `Content URI`.

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
- No contiene backend, autenticación corporativa, sincronización, WorkManager,
  TIMM, WISE, Power BI, transcripción, IA, publicidad ni trackers.

## Limitación de este entorno

El entorno donde se generó el código no dispone de Java, Gradle, Android SDK ni
ADB. Por esa razón no se pudo producir ni probar una APK aquí. El proyecto está
preparado para abrirse y compilarse en Android Studio en una máquina con el
Android SDK instalado. No se afirma que la POC funcione en el Galaxy A04e hasta
ejecutar la prueba real descrita abajo.

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

Desde una terminal con Gradle disponible también se puede ejecutar:

```bash
cd android-app
gradle --no-daemon assembleDebug
```

Este repositorio no incluye `gradlew` ni el `gradle-wrapper.jar` porque el
entorno de generación no tenía Gradle para crearlos. Android Studio puede
sincronizar este proyecto con su Gradle integrado. Si se desea usar terminal,
desde Android Studio se puede ejecutar **File > Sync Project with Gradle Files**
y luego generar un wrapper con Gradle instalado:

```bash
gradle wrapper --gradle-version 8.9
```

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
.github/workflows/android-apk.yml
```

Se ejecuta automáticamente en cada `push` a `main` o `master`, en cada pull
request y manualmente desde la pestaña **Actions** de GitHub.

El workflow:

1. instala Java 17;
2. instala el Android SDK, Android 35 y Build Tools 35.0.0;
3. instala Gradle 8.9;
4. ejecuta las pruebas unitarias del parser;
5. genera `app-debug.apk`;
6. publica la APK como artifact descargable llamado
   `maipu-call-sync-debug-apk`.

No depende de `gradlew` ni de un `gradle-wrapper.jar`: GitHub Actions instala
Gradle de forma reproducible mediante `gradle/actions/setup-gradle`. Esto
permite compilar el proyecto aunque el entorno que generó la POC no tuviera
Gradle instalado.

### Pasos para usarlo

1. Descargar el proyecto completo desde Replit o subirlo a un repositorio
   GitHub.
2. Conservar tanto `android-app/` como `.github/workflows/android-apk.yml`.
3. Hacer push a `main`/`master`, abrir **Actions** y seleccionar el workflow.
4. Esperar a que termine el job **Test and build debug APK**.
5. En la página de la ejecución, descargar el artifact
   `maipu-call-sync-debug-apk`.
6. Extraer el ZIP descargado para obtener `app-debug.apk`.
7. Instalar esa APK en el Galaxy A04e siguiendo la sección de instalación
   manual.

También se puede iniciar sin hacer un push: en GitHub abrir **Actions >
Build Android APK > Run workflow**.

## Instalación manual en el Galaxy A04e

### Opción A: Android Studio

1. Activar **Opciones de desarrollador** y **Depuración USB** en el teléfono.
2. Conectar el teléfono por USB y aceptar la autorización.
3. Seleccionar el Galaxy A04e en Android Studio.
4. Ejecutar la configuración `app`.

### Opción B: APK

1. Copiar `app-debug.apk` al teléfono.
2. Abrir el archivo desde Mis archivos.
3. Si Android lo solicita, permitir temporalmente la instalación desde esa
   fuente.
4. Instalar **Maipú Call Sync**.

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

## Próxima fase, fuera de esta POC

No está implementada. Solo queda como dirección arquitectónica:

`Samsung Phone → MediaStore → Maipú Call Sync → API HTTPS → Cloud Storage /
base de datos → TIMM / dashboards`

En una fase posterior podrían evaluarse WorkManager, deduplicación y
sincronización de pendientes, pero no deben agregarse hasta completar los
nueve puntos de la prueba de éxito.