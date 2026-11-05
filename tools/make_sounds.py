"""Generates the game's original sound effects (pure Python, no dependencies).

    python3 tools/make_sounds.py   -> app/src/main/res/raw/pop.wav
"""
import math
import os
import random
import struct
import wave

RATE = 44100
OUT = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "res", "raw")


def write(name, samples):
    peak = max(abs(s) for s in samples) or 1.0
    with wave.open(os.path.join(OUT, name), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes(b"".join(struct.pack("<h", int(s / peak * 0.85 * 32767)) for s in samples))


def envelope(t, attack, decay):
    """Soft fade-in then exponential fade-out (no clicks)."""
    a = min(1.0, t / attack)
    return a * a * (3 - 2 * a) * math.exp(-t / decay)


def tap():
    # Rounded "bloop": a sine gliding gently downward, with a hint of the octave.
    n = int(RATE * 0.16)
    out, phase = [], 0.0
    for i in range(n):
        t = i / RATE
        f = 440 + 300 * math.exp(-t / 0.025)
        phase += 2 * math.pi * f / RATE
        s = math.sin(phase) + 0.15 * math.sin(2 * phase)
        out.append(s * envelope(t, 0.004, 0.045))
    return out


def burst():
    # Plucked, slightly detuned chime (C5) + a soft low "puff" + a whisper of air.
    n = int(RATE * 0.55)
    rnd = random.Random(7)
    out, lp = [], 0.0
    for i in range(n):
        t = i / RATE
        chime = (math.sin(2 * math.pi * 523.25 * t)
                 + 0.6 * math.sin(2 * math.pi * 525.0 * t)
                 + 0.25 * math.sin(2 * math.pi * 1046.5 * t) * math.exp(-t / 0.08))
        chime *= envelope(t, 0.006, 0.16)
        f = 160 - 60 * min(1.0, t / 0.12)
        puff = math.sin(2 * math.pi * f * t) * envelope(t, 0.008, 0.05)
        lp += 0.08 * (rnd.uniform(-1, 1) - lp)  # low-passed noise = soft air, not hiss
        air = lp * envelope(t, 0.01, 0.06) * 3
        out.append(0.55 * chime + 0.5 * puff + 0.35 * air)
    return out


def pop():
    """Soft bubbly pop modelled on the reference the user liked: three quick pulses ~27 ms
    apart (each softer), a low 70-100 Hz thump and a short 1.8 kHz 'tink', ~80 ms total."""
    n = int(RATE * 0.08)
    pulses = [(0.000, 1.00)]  # a single pop (the reference had 3 quick pulses; one is cleaner)
    out, ph_lo = [], 0.0
    for i in range(n):
        t = i / RATE
        amp = 0.0
        for start, level in pulses:
            u = t - start
            if u >= 0:
                amp += level * envelope(u, 0.003, 0.022)
        f_lo = 75 + 70 * math.exp(-t / 0.03)  # thump glides down
        ph_lo += 2 * math.pi * f_lo / RATE
        low = math.sin(ph_lo)
        tink = math.sin(2 * math.pi * 1800 * t) + 0.35 * math.sin(2 * math.pi * 2440 * t)
        tail = min(1.0, (0.08 - t) / 0.02)  # fade to silence, no click
        out.append((0.9 * low + 0.45 * tink * math.exp(-t / 0.04)) * amp * tail)
    return out


if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    write("pop.wav", pop())
    print("wrote pop.wav to", os.path.abspath(OUT))
