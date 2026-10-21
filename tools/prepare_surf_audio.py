#!/usr/bin/env python3
"""Prepare bundled surf excerpts from a pinned CC0 recording. Requires ffmpeg.

No downloads occur during normal Mod builds. Supply --source for an offline copy.
"""
import argparse
from array import array
import math
import hashlib
import json
from pathlib import Path
import subprocess
import tempfile
import urllib.request

root = Path(__file__).resolve().parents[1]
metadata = json.loads((root / 'licenses/surf-source.json').read_text())
parser = argparse.ArgumentParser()
parser.add_argument('--source', type=Path, help='Local copy of the pinned waves.ogg recording')
args = parser.parse_args()
with tempfile.TemporaryDirectory(prefix='waves-surf-') as temporary:
    source = args.source or Path(temporary) / 'waves.ogg'
    if args.source is None:
        with urllib.request.urlopen(metadata['mirror_url'], timeout=90) as response:
            source.write_bytes(response.read())
    if hashlib.sha256(source.read_bytes()).hexdigest() != metadata['mirror_sha256']:
        raise SystemExit('Source checksum mismatch; refusing to replace audio assets')
    destination = root / 'src/main/resources/assets/waves/sounds'
    destination.mkdir(parents=True, exist_ok=True)
    # Decode one 22-second excerpt, then join the tail to the head in PCM.
    # Explicit PCM slicing avoids trim/EOF interactions in a shared FFmpeg filter graph.
    pcm = subprocess.check_output(['ffmpeg', '-v', 'error', '-ss', '42', '-t', '22', '-i', str(source),
        '-ac', '1', '-ar', '44100', '-af', 'highpass=f=45,volume=0.9', '-f', 'f32le', '-'])
    samples = array('f'); samples.frombytes(pcm)
    rate, overlap = 44100, 2 * 44100
    if len(samples) != 22 * rate:
        raise SystemExit('Unexpected decoded source duration')
    loop = samples[overlap:20 * rate]
    for i in range(overlap):
        angle = i / (overlap - 1) * math.pi / 2
        loop.append(samples[20 * rate + i] * math.cos(angle) + samples[i] * math.sin(angle))
    peak = max(abs(value) for value in loop)
    if peak > .9:
        loop = array('f', (value * .9 / peak for value in loop))
    subprocess.run(['ffmpeg', '-v', 'error', '-y', '-f', 'f32le', '-ar', str(rate), '-ac', '1', '-i', '-',
        '-c:a', 'libvorbis', '-q:a', '5', str(destination / 'shore_loop.ogg')], input=loop.tobytes(), check=True)
    print('Prepared shore_loop.ogg (20-second crossfaded mono loop)')
