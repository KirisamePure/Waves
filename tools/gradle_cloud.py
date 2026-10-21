#!/usr/bin/env python3
"""Run the normal wrapper with the cloud's existing HTTP proxy and a writable cache.

Does not log proxy URLs, alter trust stores, or disable certificate/checksum verification.
Local desktop builds can simply use ./gradlew instead.
"""
import os
from pathlib import Path
import shlex
import subprocess
import sys
from urllib.parse import urlsplit

root = Path(__file__).resolve().parents[1]
environment = os.environ.copy()
environment.setdefault("GRADLE_USER_HOME", "/workspace/.gradle")
if not environment.get("JAVA_HOME"):
    for candidate in sorted(Path("/workspace/.tools").glob("jdk-21*"), reverse=True):
        if (candidate / "bin/javac").is_file():
            environment["JAVA_HOME"] = str(candidate)
            break
options = shlex.split(environment.get("GRADLE_OPTS", ""))
system_trust = Path("/etc/ssl/certs/java/cacerts")
if system_trust.is_file():
    options.append(f"-Djavax.net.ssl.trustStore={system_trust}")
for protocol in ("http", "https"):
    proxy = environment.get(protocol.upper() + "_PROXY") or environment.get(protocol + "_proxy")
    if not proxy:
        continue
    parsed = urlsplit(proxy)
    if parsed.username or parsed.password:
        raise SystemExit("Authenticated proxy URL cannot safely be passed in JVM arguments.")
    if parsed.hostname:
        options += [f"-D{protocol}.proxyHost={parsed.hostname}", f"-D{protocol}.proxyPort={parsed.port or 80}"]
options += ["-Dhttp.nonProxyHosts=localhost|127.*|[::1]"]
environment["GRADLE_OPTS"] = shlex.join(options)
raise SystemExit(subprocess.call([str(root / "gradlew"), *sys.argv[1:]], cwd=root, env=environment))
