# Third-party audio

`assets/waves/sounds/shore_break_1.ogg`, `shore_break_2.ogg`, and `shore_break_3.ogg` are edited excerpts of **Crashing Waves - Pacific Ocean**, recorded by **SecureSubset**.

- Original: https://freesound.org/s/817075/
- License: **CC0 1.0 Universal** — https://creativecommons.org/publicdomain/zero/1.0/
- Accessible source mirror: https://github.com/funcoder/omarchy-ambient/blob/2686c861bff26bc7da55dd8e4e858f6d66d39a41/sounds/waves.ogg
- Mirror's recording attribution and CC0 statement: https://github.com/funcoder/omarchy-ambient/blob/2686c861bff26bc7da55dd8e4e858f6d66d39a41/CREDITS.md
- Waves edits: three 4.8-second excerpts; conversion to mono 44.1 kHz; constant gain adjustment; 45 Hz high-pass; short fade-in and one-second fade-out; Ogg Vorbis encoding. No additional restrictions apply to these audio assets.

The original Freesound page was unavailable in the build environment; provenance and CC0 status were obtained from the pinned mirror's explicit sound credits. The full mirrored recording is not included in Waves.

Pinned source hash, excerpt offsets and processing details are in `licenses/surf-source.json`. Reproduce with `python3 tools/prepare_surf_audio.py --source /path/to/waves.ogg` (requires ffmpeg). The project's MIT code license does not replace the audio's CC0 dedication.
