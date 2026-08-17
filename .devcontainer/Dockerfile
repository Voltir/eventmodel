FROM mcr.microsoft.com/devcontainers/base:noble

# JDK comes from the java devcontainer Feature (see devcontainer.json), which
# installs via SDKMAN and sets JAVA_HOME itself.

# --- sbt + Scala 3.8.4 via Coursier ---
# Coursier runs fine here without a JVM present; the Feature that installs the
# JDK runs after this image is built.
USER vscode
RUN curl -fL "https://github.com/coursier/coursier/releases/latest/download/cs-x86_64-pc-linux.gz" \
    | gzip -d > /tmp/cs \
    && chmod +x /tmp/cs \
    && /tmp/cs install cs --install-dir /home/vscode/.local/bin \
    && rm /tmp/cs \
    && /home/vscode/.local/bin/cs install sbt --install-dir /home/vscode/.local/bin \
    && /home/vscode/.local/bin/cs install scala:3.8.4 scalac:3.8.4 --install-dir /home/vscode/.local/bin

# --- Claude Code CLI (native binary, no Node/npm required) ---
RUN curl -fsSL https://claude.ai/install.sh | bash

ENV PATH="/home/vscode/.local/bin:${PATH}"
