#!/bin/bash
cd "$(dirname "$0")"

echo ""
echo "========================================"
echo "   Invoice Manager - Starting..."
echo "========================================"
echo ""

# Check Node.js
if ! command -v node &> /dev/null; then
  echo "[ERROR] Node.js not found."
  echo "Install: https://nodejs.org"
  exit 1
fi

# Auto install on first run
if [ ! -d "node_modules" ]; then
  echo "[1/2] First run - installing dependencies..."
  npm install
  echo ""
fi

echo "[2/2] Starting server..."
echo ""

# Open browser (works on macOS, most Linux)
if command -v open &> /dev/null; then
  (sleep 1 && open http://localhost:3456) &
elif command -v xdg-open &> /dev/null; then
  (sleep 1 && xdg-open http://localhost:3456) &
fi

node --no-warnings server.js
