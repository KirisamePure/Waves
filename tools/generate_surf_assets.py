#!/usr/bin/env python3
"""Reproduce the original, procedural mono surf recordings and spray sprite (no external assets)."""
from pathlib import Path
import math
import random
import struct
import subprocess
import tempfile
import wave
import zlib

root = Path(__file__).resolve().parents[1] / 'src/main/resources/assets/waves'
(root / 'sounds').mkdir(parents=True, exist_ok=True)
(root / 'textures/particle').mkdir(parents=True, exist_ok=True)
(root / 'particles').mkdir(parents=True, exist_ok=True)
for variant in range(2):
    rng = random.Random(9021 + variant)
    rate = 22050
    duration = 2.7 + variant * .3
    low, mid = 0., 0.
    samples = []
    for i in range(int(rate * duration)):
        t = i / rate
        noise = rng.uniform(-1, 1)
        low += .018 * (noise - low)
        mid += .2 * (noise - mid)
        # Fast arrival, broad breaking hiss, then a long receding wash.
        attack = 1 - math.exp(-t * 13)
        envelope = attack * math.exp(-t * 1.55) * (1 - t / duration)
        wash = .55 + .22 * math.sin(t * 9) + .12 * math.sin(t * 17 + .8)
        signal = (low * 2.4 + mid * .65 + noise * .12) * envelope * wash
        samples.append(int(math.tanh(signal * 2.1) * 27000))
    with tempfile.TemporaryDirectory() as temporary:
        wav_path = Path(temporary) / 'surf.wav'
        with wave.open(str(wav_path), 'wb') as output:
            output.setparams((1, 2, rate, 0, 'NONE', 'not compressed'))
            output.writeframes(struct.pack('<' + 'h' * len(samples), *samples))
        subprocess.run(['ffmpeg', '-v', 'error', '-y', '-i', str(wav_path), '-c:a', 'libvorbis', '-q:a', '4',
                        str(root / f'sounds/shore_break_{variant + 1}.ogg')], check=True)
# A soft, elongated water droplet, generated without an image-library dependency.
rows = bytearray()
for y in range(16):
    rows.append(0)
    for x in range(16):
        r2 = ((x - 7.5) / 4.6) ** 2 + ((y - 7.5) / 7.2) ** 2
        alpha = int(255 * max(0, 1 - r2) ** .65)
        rows.extend((220, 245, 255, alpha))
def chunk(kind, data):
    return struct.pack('>I', len(data)) + kind + data + struct.pack('>I', zlib.crc32(kind + data))
png = b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', 16, 16, 8, 6, 0, 0, 0))
png += chunk(b'IDAT', zlib.compress(rows)) + chunk(b'IEND', b'')
(root / 'textures/particle/shore_spray.png').write_bytes(png)
(root / 'particles/shore_spray.json').write_text('{"textures":["waves:shore_spray"]}\n')
(root / 'sounds.json').write_text('''{
  "shore_break": {
    "subtitle": "subtitles.waves.shore_break",
    "sounds": [
      {"name":"waves:shore_break_1","attenuation_distance":28},
      {"name":"waves:shore_break_2","attenuation_distance":28}
    ]
  }
}
''')
