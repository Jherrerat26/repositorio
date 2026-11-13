# Carrito Bluetooth 1.3: historial y Firebase

La app tiene una opción **Historial de movimientos**. Guarda dirección, velocidad de 0 a 255, fecha y hora del teléfono, nombre y dirección Bluetooth del módulo y sesión de conexión. Permite filtrar por dirección y distinguir los registros pendientes de los sincronizados.

Solo se registran cambios después de una escritura Bluetooth exitosa; no se duplica el heartbeat de 100 ms. Son órdenes enviadas, no confirmaciones físicas de los motores: el protocolo actual de la Pico no devuelve telemetría. Se conserva el mapeo calibrado de la app: `R` corresponde al botón Izquierda y `L` a Derecha.

Los movimientos se guardan primero en SQLite, en un hilo distinto del control Bluetooth. Una cola persistente los sube a Realtime Database cuando hay conexión y autenticación, usando el mismo UUID al reintentar. Los fallos de Firebase no bloquean el control. El historial no exige conexión para consultarlo. Si el APK aún no tiene configuración Firebase, lo indica y funciona en modo local; eso **no** significa que se haya creado o conectado una base de datos.

## Configuración del proyecto Firebase

1. En la [consola de Firebase](https://console.firebase.google.com/), crear o seleccionar el proyecto del carrito. No se necesita Analytics.
2. Registrar una app Android con paquete `com.example.carritobluetooth`.
3. Crear **Realtime Database**, elegir una región y comenzar con reglas restringidas. No usar modo de prueba público.
4. En **Authentication → Sign-in method**, habilitar **Anónimo**.
5. En **Realtime Database → Reglas**, publicar el contenido de [firebase/database.rules.json](firebase/database.rules.json). También se puede ejecutar `firebase deploy --only database --project ID_DEL_PROYECTO` desde esta carpeta si la CLI ya está autenticada.
6. Volver a descargar `google-services.json` DESPUÉS de crear Realtime Database, comprobando que incluya `project_info.firebase_url`, y guardarlo como `app/google-services.json`. No contiene credenciales de administrador, pero este proyecto lo mantiene fuera de Git.
7. En GitHub, añadir el secreto de Actions `FIREBASE_GOOGLE_SERVICES_JSON` con el contenido completo de ese archivo. No subir claves privadas de cuentas de servicio al APK.
8. Ejecutar el workflow **APK Android** con `firebase_required=true`. Esta opción impide presentar como conectado un APK sin configuración. Descargar el artefacto `carrito-bluetooth-1.3-apk`.

Si no existe el secreto y `firebase_required=false`, el workflow compila únicamente el historial local y deja una advertencia explícita en el resumen. Registrar un secreto no crea la base de datos ni habilita Authentication: hay que completar también los pasos anteriores.

## Datos y acceso

Ruta: `/users/<uid-anonimo>/movements/<uuid-del-evento>`.

Cada registro tiene `command`, `direction`, `speed`, `timestamp` (milisegundos Unix del teléfono), `deviceName`, `deviceAddress` y `sessionId`. Las reglas permiten leer y escribir solo al usuario autenticado dueño de esa ruta, validan los campos e indexan la fecha.

La identidad anónima pertenece a esa instalación. No es una cuenta compartida entre teléfonos: desinstalar la app o borrar sus datos elimina la copia local y el acceso a esa identidad. Los datos remotos pueden seguir existiendo, pero no se recuperan automáticamente sin vincular antes esa identidad a una cuenta permanente. Se desactiva la copia de seguridad Android para evitar restaurar el estado de sincronización sin la identidad correspondiente.

## Verificación

El workflow conserva la prueba del protocolo de la Pico, ejecuta los tests Java del registro de cambios y compila el APK debug de versión 1.3 (código 4). Tras configurar Firebase, probar en un teléfono:

1. Mantener Adelante pulsado: debe aparecer un movimiento, no diez por segundo; al soltar debe aparecer Detener.
2. Abrir Historial: el carrito debe detenerse antes de navegar. Verificar fecha, velocidad, dispositivo y filtros.
3. Desactivar internet, mover y detener, cerrar y abrir la app: los registros deben seguir pendientes y visibles.
4. Recuperar internet y tocar Sincronizar: verificar los mismos UUID en Realtime Database y que el estado cambie solo tras la confirmación del servidor.
5. Comprobar que otro UID no pueda leer ni escribir la ruta del primer usuario.

El APK debug generado por otro entorno puede tener una firma diferente al APK instalado. Si Android rechaza la actualización, no desinstalar sin guardar antes los datos que se necesiten conservar; usar la firma original para una actualización que preserve datos.
