"""Render the original instrumental menu loop with the Python standard library."""
from array import array
import math
import random
import sys
import wave


def render_chime(path):
    rate = 22050
    pcm = array("h")
    for i in range(rate // 3):
        t = i / rate
        sound = 0.0
        for start, frequency in ((0, 659.25), (.09, 987.77)):
            age = t - start
            if age >= 0:
                sound += min(1, age * 240) * math.exp(-19 * age) * math.sin(math.tau * frequency * age)
        pcm.append(round(7000 * sound))
    if sys.byteorder != "little":
        pcm.byteswap()
    with wave.open(str(path), "wb") as out:
        out.setparams((1, 2, rate, 0, "NONE", "not compressed"))
        out.writeframes(pcm.tobytes())


def render_cabinet_cues(folder):
    # Original short contact sounds: a leather thump, a dry metal tick and a
    # softer falling release. Each tail ends before the next navigation tap.
    rate = 22050
    for name, length, start, end, strike in (
            ("open", .21, 350, 580, .52), ("turn", .072, 940, 690, .20),
            ("back", .16, 460, 205, .38)):
        noise = random.Random(219)
        pcm = array("h")
        phase = previous = 0.0
        for i in range(round(length * rate)):
            t = i / rate
            phase += math.tau * (start + (end-start)*t/length) / rate
            grain = noise.uniform(-1, 1)
            attack = min(1, t/.002)
            release = min(1, (length-t)/.012)
            metal = (.22*math.sin(phase) + .10*math.sin(2.71*phase)) * math.exp(-5*t/length)
            tap = (grain-previous)*.17*math.exp(-135*t)
            body = strike*math.sin(math.tau*(86*t+.14*(1-math.exp(-55*t))))*math.exp(-36*t)
            pcm.append(round(26000*attack*release*(metal+tap+body)))
            previous = grain
        if sys.byteorder != "little":
            pcm.byteswap()
        with wave.open(str(folder/("cabinet_"+name+".wav")), "wb") as out:
            out.setparams((1,2,rate,0,"NONE","not compressed"))
            out.writeframes(pcm.tobytes())


def render(path):
    rate = 22050
    beat = 60 / 86
    samples = [0.0] * round(16 * beat * rate)
    noise = random.Random(31)
    tau = 2 * math.pi

    def add(start, length, instrument):
        offset = round(start * beat * rate)
        for i in range(round(length * rate)):
            t = i / rate
            edge = min(1.0, i / 45, (length - t) * 140)
            samples[(offset + i) % len(samples)] += edge * instrument(t)

    for bar, root in enumerate([38, 38, 34, 36]):
        for tick in [0, 1.75, 2.5]:
            add(bar*4+tick, .28, lambda t: .43 * math.exp(-15*t) * math.sin(tau*(49*t+.9*(1-math.exp(-30*t)))))
        for tick in [1, 3]:
            add(bar*4+tick, .19, lambda t: math.exp(-24*t)*(.22*noise.uniform(-1,1)+.09*math.sin(tau*167*t)))
        for tick in range(8):
            add(bar*4+tick*.5+(tick%2)*.035, .07, lambda t: .085*math.exp(-67*t)*noise.uniform(-1,1))
        for tick, step in [(0,0),(1.5,7),(2,0),(3.5,3)]:
            freq = 440 * 2**((root+step-69)/12)
            add(bar*4+tick,.36,lambda t: .21*math.exp(-7*t)*(math.sin(tau*freq*t)+.2*math.sin(2*tau*freq*t)))
        for interval in (12,15,19,22):
            freq = 440 * 2**((root+interval-69)/12)
            add(bar*4+.5,1.1,lambda t: .054*math.exp(-3.5*t)*math.sin(tau*freq*t+.7*math.exp(-6*t)*math.sin(2*tau*freq*t)))
    pcm = array("h", (round(32767*math.tanh(s*.95)) for s in samples))
    if sys.byteorder != "little": pcm.byteswap()
    with wave.open(str(path),"wb") as out:
        out.setparams((1,2,rate,0,"NONE","not compressed"))
        out.writeframes(pcm.tobytes())
