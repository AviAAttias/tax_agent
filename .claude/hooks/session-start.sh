#!/bin/bash
set -euo pipefail

if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then
  exit 0
fi

cd "${CLAUDE_PROJECT_DIR}"

echo "Downloading Maven dependencies..."
mvn dependency:go-offline -q

echo "Compiling project..."
mvn compile -q

echo "Session start complete."
