#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
if [ -z "${CODESPACE_NAME:-}" ]; then
  echo 'This launcher is for GitHub Codespaces. Open the cloud terminal first.'
  exit 1
fi
python3 -m venv .venv
.venv/bin/pip install -r requirements.txt
.venv/bin/python manage.py migrate --noinput
.venv/bin/python manage.py collectstatic --noinput
.venv/bin/python manage.py offer_worker &
worker_pid=$!
trap 'kill "$worker_pid" 2>/dev/null || true' EXIT
.venv/bin/gunicorn config.wsgi:application --bind 0.0.0.0:8000 --workers 1 --threads 4 --access-logfile -
