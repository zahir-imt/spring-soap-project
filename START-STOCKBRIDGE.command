#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")"
trap 'echo "StockBridge stopped because a step failed. Keep this window open and share the error above."; read -r -p "Press Return to close."' ERR
bash scripts/setup-mac.sh
export JAVA_HOME="$PWD/.tools/jdk/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"
APP_PORT=${PORT:-8080}
if curl -fsS "http://127.0.0.1:$APP_PORT/api/summary" >/dev/null 2>&1; then
  echo "An application is already running on port $APP_PORT. Opening it."
  open "http://localhost:$APP_PORT" 2>/dev/null || echo "Open your browser and visit http://localhost:$APP_PORT"
  exit 0
fi
if [ ! -f target/stockbridge-1.0.0.jar ] || [ pom.xml -nt target/stockbridge-1.0.0.jar ] || [ -n "$(find src/main -type f -newer target/stockbridge-1.0.0.jar -print -quit 2>/dev/null)" ]; then
  echo "Building StockBridge. First use downloads dependencies."
  .tools/maven/bin/mvn -Dmaven.repo.local="$PWD/.tools/m2" -B -DskipTests package
fi
echo "Starting StockBridge at http://localhost:$APP_PORT"
echo "Keep this window open. Press Control+C to stop the application."
java -jar target/stockbridge-1.0.0.jar &
APP_PID=$!
trap 'kill "$APP_PID" 2>/dev/null || true' EXIT
trap 'exit 0' INT TERM
for i in $(seq 1 60); do
  if curl -fsS "http://127.0.0.1:$APP_PORT/api/summary" >/dev/null 2>&1; then
    open "http://localhost:$APP_PORT" 2>/dev/null || echo "Open your browser and visit http://localhost:$APP_PORT"
    wait "$APP_PID"
    exit 0
  fi
  kill -0 "$APP_PID" 2>/dev/null || { echo "Startup failed. Read the message above."; exit 1; }
  sleep 1
done
echo "Startup is taking longer than expected. Read the messages above."
wait "$APP_PID"
