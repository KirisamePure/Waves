#!/usr/bin/env python3
"""Prepare bundled surf excerpts from a pinned CC0 recording. Requires ffmpeg.

No downloads occur during normal Mod builds. Supply --source for an offline copy.
"""
import argparse
from array import array
import hashlib
import json
import math
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
    for clip in metadata['clips']:
        pcm = subprocess.check_output(['ffmpeg', '-v', 'error', '-ss', str(clip['start_seconds']),
            '-i', str(source), '-t', str(clip['duration_seconds']), '-ac', '1', '-ar', '44100', '-f', 'f32le', '-'])
        samples = array('f'); samples.frombytes(pcm)
        peak = max(abs(value) for value in samples)
        rms = math.sqrt(sum(value * value for value in samples) / len(samples))
        # Constant gain preserves the natural crash/retreat dynamics; no noise synthesis.
        gain = min(10 ** (-3 / 20) / peak, 10 ** (-20 / 20) / rms)
        fade_out = clip['duration_seconds'] - 1.0
        filters = f'highpass=f=45,volume={gain},afade=t=in:d=0.12,afade=t=out:st={fade_out}:d=1'
        subprocess.run(['ffmpeg', '-v', 'error', '-y', '-ss', str(clip['start_seconds']), '-i', str(source),
            '-t', str(clip['duration_seconds']), '-ac', '1', '-ar', '44100', '-af', filters,
            '-c:a', 'libvorbis', '-q:a', '5', str(destination / clip['file'])], check=True)
        print(f"Prepared {clip['file']} ({clip['duration_seconds']} s, constant gain {gain:.3f})")
