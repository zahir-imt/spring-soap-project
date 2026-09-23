#!/usr/bin/env bash
# Source this from a launcher; never write the bootstrap password to disk.
if [ -z "${STOCKBRIDGE_ADMIN_PASSWORD:-}" ] && [ -t 0 ]; then
  echo "First startup: choose an admin password (12–72 bytes, at least 12 characters)."
  echo "If this database already has an admin account, press Return to reuse it."
  read -r -s -p "Initial admin password: " STOCKBRIDGE_ADMIN_PASSWORD
  echo
  export STOCKBRIDGE_ADMIN_PASSWORD
fi
