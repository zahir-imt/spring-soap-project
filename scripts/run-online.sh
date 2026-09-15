#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
echo "Building StockBridge in this cloud workspace..."
bash mvnw --batch-mode -DskipTests package
echo "Open the Ports tab, then Open in Browser for port 8080. Keep its visibility Private."
echo "Press Control+C in this terminal to stop the app. Stop the codespace when finished."
exec java -jar target/stockbridge-1.0.0.jar
