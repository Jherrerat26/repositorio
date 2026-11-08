"""Simulate serial delivery and motor outputs without a connected Pico."""
import sys
import types
import unittest
from pathlib import Path
from unittest.mock import patch


class SimulationFinished(Exception):
    pass


def simulate(source, packets, duration=1600):
    clock = [0]
    pwm = {}
    samples = {}
    packets = list(packets)

    class Pin:
        OUT = 1

        def __init__(self, number, mode=None):
            self.number = number

        def value(self, value):
            pass

        def low(self):
            pass

        def on(self):
            pass

    class PWM:
        def __init__(self, pin):
            self.pin = pin.number
            pwm[self.pin] = 0

        def freq(self, frequency):
            pass

        def duty_u16(self, duty):
            pwm[self.pin] = duty

    class UART:
        def __init__(self, *args, **kwargs):
            pass

        def any(self):
            return bool(packets and packets[0][0] <= clock[0])

        def read(self, count=None):
            return packets.pop(0)[1] if self.any() else None

        def readline(self):
            return self.read()

    def sleep_ms(delay):
        samples[clock[0]] = (pwm[6], pwm[7])
        clock[0] += delay
        if clock[0] > duration:
            raise SimulationFinished

    machine = types.SimpleNamespace(Pin=Pin, PWM=PWM, UART=UART)
    time = types.SimpleNamespace(
        ticks_ms=lambda: clock[0], ticks_diff=lambda a, b: a - b,
        sleep_ms=sleep_ms,
    )
    with patch.dict(sys.modules, {"machine": machine, "time": time}):
        try:
            exec(compile(source.read_text(encoding="utf-8"), str(source), "exec"), {})
        except SimulationFinished:
            pass
    return samples


class SerialMotionTests(unittest.TestCase):
    sources = [source for source in (
        Path(__file__).resolve().parents[1] / "PicoCarrito.py",
        Path(__file__).resolve().parents[1] / "repositorio/android-carrito/PicoCarrito.py",
        Path(__file__).resolve().parents[1] / "android-carrito/PicoCarrito.py",
    ) if source.exists()]

    def test_sources_exist(self):
        self.assertTrue(self.sources)

    def test_fragmented_orders_do_not_interrupt_motion_and_release_stops(self):
        packets = [(0, b"F,140\n"), (100, b"F"), (110, b",140\n"),
                   (200, b"F,140\n"), (300, b"S,"), (310, b"0\n")]
        for source in self.sources:
            with self.subTest(source=str(source)):
                samples = simulate(source, packets)
                self.assertTrue(all(left > 0 and right > 0
                                    for tick, (left, right) in samples.items() if tick < 310))
                self.assertEqual(samples[310], (0, 0))

    def test_combined_orders_and_noise_do_not_pause_or_refresh_watchdog(self):
        packets = [(0, b"F,140\n"), (100, b"F,140\nF,140\n"),
                   (200, b"noise\n"), (300, b"X,140\n"),
                   (400, b"\xff\n"), (500, b"x" * 40 + b"F,140\n")]
        for source in self.sources:
            with self.subTest(source=str(source)):
                samples = simulate(source, packets)
                self.assertTrue(all(left > 0 and right > 0
                                    for tick, (left, right) in samples.items() if tick <= 1300))
                self.assertEqual(samples[1310], (0, 0))

    def test_sustained_hold_and_partial_data_timeout(self):
        packets = [(tick, b"R,140\n") for tick in range(0, 2001, 100)]
        packets.append((2100, b"R,14"))
        for source in self.sources:
            with self.subTest(source=str(source)):
                samples = simulate(source, packets, duration=3300)
                self.assertTrue(all(left > 0 and right > 0
                                    for tick, (left, right) in samples.items() if tick <= 3200))
                self.assertEqual(samples[3210], (0, 0))


if __name__ == "__main__":
    unittest.main()
