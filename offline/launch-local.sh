#!/usr/bin/env sh
# Zero-Node local launcher for macOS, Linux, and Android Termux.
# Prefers localhost HTTP if Python or BusyBox is present (unlocks File System Access handles & OAuth),
# and falls back to opening offline/ExamSet-Standalone.html directly in browser.

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
REPO_ROOT="$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)"
STANDALONE="$SCRIPT_DIR/ExamSet-Standalone.html"
PORT=4173

if [ -d "$REPO_ROOT/dist" ]; then
  SERVE_DIR="$REPO_ROOT/dist"
  ENTRY_URL="http://127.0.0.1:$PORT/"
else
  SERVE_DIR="$SCRIPT_DIR"
  ENTRY_URL="http://127.0.0.1:$PORT/ExamSet-Standalone.html"
fi

if command -v python3 >/dev/null 2>&1; then
  echo "Serving ExamSet on $ENTRY_URL (Press Ctrl+C to stop)"
  if command -v termux-open-url >/dev/null 2>&1; then
    (sleep 1 && termux-open-url "$ENTRY_URL") &
  elif command -v xdg-open >/dev/null 2>&1; then
    (sleep 1 && xdg-open "$ENTRY_URL") &
  elif command -v open >/dev/null 2>&1; then
    (sleep 1 && open "$ENTRY_URL") &
  fi
  exec python3 -m http.server "$PORT" --bind 127.0.0.1 --directory "$SERVE_DIR"
elif command -v busybox >/dev/null 2>&1; then
  echo "Serving ExamSet via BusyBox on $ENTRY_URL"
  exec busybox httpd -f -p "127.0.0.1:$PORT" -h "$SERVE_DIR"
else
  echo "Open this standalone offline file in any browser:"
  echo "$STANDALONE"
fi
