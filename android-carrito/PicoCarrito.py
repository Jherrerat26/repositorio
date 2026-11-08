"""Control para Raspberry Pi Pico + L298N + Bluetooth serial en UART0.

Protocolo recibido por UART: F,velocidad\n | B,velocidad\n | L,velocidad\n | R,velocidad\n | S,0\n La velocidad se expresa de 0 a 255.
"""
from machine import Pin, PWM, UART
import time

# Indicador de encendido: funciona al arrancar con USB o VSYS (pin 39).
# El alias LED admite Pico/Pico W; GP25 permite firmware antiguo de Pico.
try:
    led = Pin("LED", Pin.OUT)
except (TypeError, ValueError):
    led = Pin(25, Pin.OUT)
led.on()

# Bluetooth: TXD -> GP1 (RX Pico), RXD <- GP0 (TX Pico)
uart = UART(0, baudrate=9600, tx=Pin(0), rx=Pin(1))

# L298N: quitar puentes ENA/ENB para que la Pico controle PWM.
in1, in2 = Pin(2, Pin.OUT), Pin(3, Pin.OUT)  # motor izquierdo
in3, in4 = Pin(4, Pin.OUT), Pin(5, Pin.OUT)  # motor derecho
ena, enb = PWM(Pin(6)), PWM(Pin(7))
ena.freq(1000)
enb.freq(1000)

last_command = time.ticks_ms()
STOP_TIMEOUT_MS = 1200

def stop():
    ena.duty_u16(0); enb.duty_u16(0)
    in1.low(); in2.low(); in3.low(); in4.low()

def motor(left, right, speed):
    speed = max(0, min(255, speed))
    duty = speed * 65535 // 255
    in1.value(1 if left > 0 else 0); in2.value(1 if left < 0 else 0)
    in3.value(1 if right > 0 else 0); in4.value(1 if right < 0 else 0)
    ena.duty_u16(duty if left else 0); enb.duty_u16(duty if right else 0)

def handle(line):
    global last_command
    try:
        action, raw_speed = line.strip().split(",", 1)
        speed = max(0, min(255, int(raw_speed)))
    except (ValueError, TypeError):
        stop(); return
    last_command = time.ticks_ms()
    if action == "F": motor(1, 1, speed)
    elif action == "B": motor(-1, -1, speed)
    elif action == "L": motor(-1, 1, speed)
    elif action == "R": motor(1, -1, speed)
    else: stop()

stop()
while True:
    if uart.any():
        data = uart.readline()
        if data:
            try: handle(data.decode())
            except UnicodeError: stop()
    if time.ticks_diff(time.ticks_ms(), last_command) > STOP_TIMEOUT_MS:
        stop()
    time.sleep_ms(10)
