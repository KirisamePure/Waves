#!/usr/bin/env python3
"""Run the real client render test on an isolated software OpenGL display."""
import argparse
import os
import re
from pathlib import Path
import select
import shutil
import subprocess
import sys
import tempfile

parser = argparse.ArgumentParser()
parser.add_argument("--skip-assets", action="store_true",
                    help="Use client-JAR resources if optional external asset downloads are blocked (missing audio/fonts may warn).")
parser.add_argument("--production", action="store_true", help="Test the remapped release JAR with production Fabric Loader.")
parser.add_argument("--no-iris", action="store_true", help="Run without Iris installed, not merely with shaders disabled.")
parser.add_argument("--shore-alpha", type=Path, help="Test zero/partial/full shore opacity and run-up with an external shader ZIP (not bundled). Requires Iris.")
args = parser.parse_args()
if args.shore_alpha and (args.no_iris or not args.shore_alpha.is_file()):
    parser.error("--shore-alpha needs an existing ZIP and Iris enabled")
root = Path(__file__).resolve().parents[1]
# Production run directories persist. Reset only this test-owned Iris config;
# llvmpipe on the dummy display may reject a saved debug-context request.
if args.production:
    iris_config = root / "build/run/productionClientGameTest/config/iris.properties"
    if iris_config.is_file():
        contents = iris_config.read_text()
        for key in ("enableDebugOptions", "enableShaders"):
            contents = re.sub(rf"(?m)^{key}=.*$", f"{key}=false", contents)
        iris_config.write_text(contents)
xorg = shutil.which("Xorg") or "/usr/lib/xorg/Xorg"
reports = root / ("build/reports/render-production" if args.production else "build/reports/render")
if args.no_iris:
    reports = reports.with_name(reports.name + "-no-iris")
if args.shore_alpha:
    reports = reports.with_name(reports.name + "-shore-alpha")
reports.mkdir(parents=True, exist_ok=True)
config = '''Section "ServerFlags"
 Option "AutoAddDevices" "false"
 Option "AutoEnableDevices" "false"
 Option "DontVTSwitch" "true"
EndSection
Section "Device"
 Identifier "DummyDevice"
 Driver "dummy"
 VideoRam 256000
EndSection
Section "Monitor"
 Identifier "DummyMonitor"
 HorizSync 5.0-1000.0
 VertRefresh 5.0-200.0
 Modeline "1280x720" 74.5 1280 1344 1472 1664 720 723 728 748
EndSection
Section "Screen"
 Identifier "DummyScreen"
 Device "DummyDevice"
 Monitor "DummyMonitor"
 DefaultDepth 24
 SubSection "Display"
  Depth 24
  Modes "1280x720"
 EndSubSection
EndSection
'''
with tempfile.TemporaryDirectory(prefix="waves-display-") as directory:
    configuration = Path(directory) / "xorg.conf"
    configuration.write_text(config)
    read_fd, write_fd = os.pipe()
    with (reports / "xorg-output.log").open("w") as xlog:
        server = subprocess.Popen([xorg, "-displayfd", str(write_fd), "-config", str(configuration),
                                   "-noreset", "-nolisten", "tcp", "-logfile", str(reports / "xorg.log"),
                                   "-novtswitch", "-sharevts"], pass_fds=(write_fd,), stdout=xlog, stderr=xlog)
        os.close(write_fd)
        try:
            if not select.select([read_fd], [], [], 20)[0]:
                raise SystemExit(f"Virtual display did not start; inspect {reports / 'xorg.log'}")
            display_number = os.read(read_fd, 100).decode().strip()
            if not display_number.isdigit():
                raise SystemExit(f"Virtual display failed; inspect {reports / 'xorg.log'}")
            environment = os.environ.copy()
            if args.shore_alpha:
                environment["WAVES_ALPHA_PACK"] = str(args.shore_alpha.resolve())
            else:
                environment.pop("WAVES_ALPHA_PACK", None)
            environment["DISPLAY"] = ":" + display_number
            environment["LIBGL_ALWAYS_SOFTWARE"] = "1"
            environment["MESA_SHADER_CACHE_DIR"] = "/workspace/.cache/mesa"
            environment["ALSOFT_DRIVERS"] = "null"
            task = "runProductionClientGameTest" if args.production else "runClientGameTest"
            command = [sys.executable, str(root / "tools/gradle_cloud.py"), task, "--no-daemon"]
            if args.no_iris:
                command += ["-PwavesNoIris"]
            if args.skip_assets:
                command += ["-x", "downloadAssets"]
            log_path = reports / "client-test.log"
            print(f"Running Minecraft Waves render tests; log: {log_path}", flush=True)
            with log_path.open("w") as log:
                result = subprocess.call(command, cwd=root, env=environment, stdout=log, stderr=subprocess.STDOUT)
            lines = log_path.read_text().splitlines()
            for line in lines:
                if any(marker in line for marker in ("WAVES_RENDER_TEST_PASS", "SHORE_TRANSPARENCY_TEST_PASS", "SHORE_ALPHA_PIXELS", "SHORE_ALPHA_LEGACY_CONTROL")):
                    print(line)
            print("\n".join(lines[-18:]))
            raise SystemExit(result)
        finally:
            os.close(read_fd)
            server.terminate()
            try:
                server.wait(timeout=10)
            except subprocess.TimeoutExpired:
                server.kill()
                server.wait()
