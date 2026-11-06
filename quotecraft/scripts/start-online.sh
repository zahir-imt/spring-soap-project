#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
if [ "$(printenv CODESPACES)" != true ]; then
  echo "Use this helper in your GitHub Codespaces terminal."
  exit 1
fi
bash mvnw --batch-mode -DskipTests package
exec java -jar target/quotecraft-1.0.0.jar
