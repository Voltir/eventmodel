#!/usr/bin/env bash
set -euo pipefail

# Docker creates named-volume mountpoints as root:root, so the volumes backing
# .metals/.bloop and the build caches (see devcontainer.json "mounts") arrive
# unwritable by the vscode user. Metals fails quietly if it can't write its own
# state, so this is not optional.
sudo chown -R vscode:vscode \
  /workspace/.metals /workspace/.bloop \
  "${HOME}/.cache/coursier" "${HOME}/.sbt" "${HOME}/.ivy2"

echo "java:  $(java -version 2>&1 | head -n1)"
echo "scala: $(scala -version 2>&1)"
echo "sbt:   $(sbt --version 2>&1 | tail -n1)"
echo "claude: $(claude --version 2>&1)"

# Credentials come from the bind-mounted ~/.claude dir, but ~/.claude.json sits
# beside it and stays container-local — without this flag Claude Code runs the
# onboarding/login flow on every rebuild despite valid credentials.
config="${HOME}/.claude.json"
[ -f "$config" ] || echo '{}' > "$config"
tmp="$(mktemp)"
jq '.hasCompletedOnboarding = true' "$config" > "$tmp" && mv "$tmp" "$config"
chmod 600 "$config"
