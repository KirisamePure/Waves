#!/usr/bin/env python3
"""Reproduce the original spray sprite. Recorded surf audio uses prepare_surf_audio.py."""
from pathlib import Path
import struct
import zlib

root = Path(__file__).resolve().parents[1] / 'src/main/resources/assets/waves'
(root / 'sounds').mkdir(parents=True, exist_ok=True)
(root / 'textures/particle').mkdir(parents=True, exist_ok=True)
(root / 'particles').mkdir(parents=True, exist_ok=True)
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
