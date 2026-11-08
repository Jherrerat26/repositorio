# Carrito Bluetooth

Aplicación Android nativa (Java) y programa MicroPython para Raspberry Pi Pico, módulo serial Bluetooth clásico y controlador L298N. La app usa el perfil serial SPP. Primero hay que emparejar el módulo desde Ajustes Bluetooth de Android; luego se selecciona en la app.

## Cableado

| Pico | Conectar a |
|---|---|
| GP0 (pin físico 1, UART0 TX) | Bluetooth RXD |
| GP1 (pin físico 2, UART0 RX) | Bluetooth TXD |
| VBUS (pin físico 40) | VCC del módulo Bluetooth (su placa indica 3,6–6 V) |
| GND (por ejemplo pin físico 3) | GND Bluetooth y GND L298N |
| GP2, GP3 | L298N IN1, IN2 (motor izquierdo) |
| GP4, GP5 | L298N IN3, IN4 (motor derecho) |
| GP6, GP7 | L298N ENA, ENB (PWM) |

Conecta los motores a OUT1/OUT2 y OUT3/OUT4. Quita los puentes de ENA y ENB para poder regular velocidad. Alimenta los motores por la bornera de alimentación del L298N usando una fuente adecuada a los motores; no alimentes los motores desde la Pico. Une las tierras (GND). No conectes el terminal +5V del L298N a VBUS/VUSB de la Pico.

## Protocolo

La versión 1.1 de la app repite la orden cada 100 ms mientras se mantiene pulsado. El programa de la Pico acumula fragmentos UART hasta recibir el salto de línea y mantiene la salida de los motores entre órdenes. Para aplicar esta corrección instala el APK nuevo y vuelve a guardar `PicoCarrito.py` en la Pico como `main.py`, reemplazando el anterior; luego reinicia la placa. Al soltar el botón se envía `S,0`. Si se pierde la comunicación, la Pico conserva la parada de seguridad de 1,2 segundos desde la última orden válida.

La app envía líneas ASCII a 9600 baudios: `F,velocidad`, `B,velocidad`, `L,velocidad`, `R,velocidad` o `S,0`, con velocidad de 0 a 255. Los botones funcionan mientras se mantienen presionados y paran al soltarlos. La Pico aplica parada de seguridad si no recibe una orden válida durante 1,2 segundos.

## Uso

### Descargar el APK desde GitHub

En la pestaña Actions del repositorio, abre el flujo **APK Android**. Se ejecuta al subir cambios de este proyecto y también permite ejecución manual con **Run workflow**. Cuando termine correctamente, descarga **carrito-bluetooth-apk** en la sección **Artifacts** y extrae `app-debug.apk` del ZIP para instalarlo en Android. Es una compilación de depuración firmada automáticamente, adecuada para pruebas.

Para compilar localmente se necesitan JDK 17, Android SDK y Gradle 8.12. Desde la raíz del repositorio ejecuta `gradle -p android-carrito --no-daemon assembleDebug`. El resultado se guarda en `android-carrito/app/build/outputs/apk/debug/app-debug.apk`.

El LED integrado se enciende al iniciar el programa y permanece encendido, tanto con alimentación USB como por VSYS (pin físico 39). Para que ocurra automáticamente al alimentar la placa, guarda el programa en la Pico como `main.py`.

1. Carga `PicoCarrito.py` en la Pico como `main.py` con Thonny y reiníciala.
2. Empareja el módulo desde los ajustes de Android (el PIN depende del módulo; en muchos compatibles es `1234` o `0000`).
3. Abre el proyecto en Android Studio, compílalo e instálalo en el teléfono.
4. En la app elige el dispositivo emparejado, toca Conectar y prueba primero a baja velocidad con las ruedas levantadas.

Si avanzar hace que un motor gire al revés, invierte los dos cables de ese motor en la salida del L298N o intercambia el sentido de ese lado en el código Pico. Para iPhone se requeriría otro hardware/protocolo; esta app apunta a Android y Bluetooth clásico SPP.
