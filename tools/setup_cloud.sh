#!/usr/bin/env bash
set -euo pipefail
cd /workspace/Waves
task_tools=/workspace/.tools
task_jdk="$task_tools/jdk-21.0.12.1+1"
if [[ ! -x "$task_jdk/bin/javac" ]]; then
    task_tmp=$(mktemp -d /tmp/waves-jdk.XXXXXX)
    trap 'rm -rf "$task_tmp"' EXIT
    curl --fail --location --silent --show-error \
        'https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.12.1%2B1/OpenJDK21U-jdk_x64_linux_hotspot_21.0.12.1_1.tar.gz' \
        --output "$task_tmp/jdk.tar.gz"
    printf '%s  %s\n' 'ce79869e1307ed8ee1e2baa86a412b1eb5b75d10a01006d788a6f968bcfaee94' "$task_tmp/jdk.tar.gz" | sha256sum --check --status
    mkdir -p "$task_tools"
    tar -xzf "$task_tmp/jdk.tar.gz" -C "$task_tools"
fi
export JAVA_HOME="$task_jdk"
export GRADLE_USER_HOME=/workspace/.gradle
python3 tools/gradle_cloud.py build downloadAssets --no-daemon
