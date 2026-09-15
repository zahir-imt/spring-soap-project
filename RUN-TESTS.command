#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")"
trap 'echo "A test or setup step failed. Keep the messages above for troubleshooting."; read -r -p "Press Return to close."' ERR
bash scripts/setup-mac.sh
export JAVA_HOME="$PWD/.tools/jdk/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"
.tools/maven/bin/mvn -Dmaven.repo.local="$PWD/.tools/m2" -B verify
open target/cucumber-report.html 2>/dev/null || echo "Open target/cucumber-report.html in your browser."
echo "All checks passed. The Cucumber report is open in your browser."
read -r -p "Press Return to close."
