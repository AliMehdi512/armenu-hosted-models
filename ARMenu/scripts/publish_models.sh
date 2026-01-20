#!/usr/bin/env bash
# Copy GLB files from app assets into docs/models for GitHub Pages hosting
set -euo pipefail
SRC_DIR="src/main/assets/models"
DEST_DIR="docs/models"
mkdir -p "$DEST_DIR"
cp -v "$SRC_DIR"/*.glb "$DEST_DIR/" || true
echo "Copied GLB files to $DEST_DIR. Commit and push to GitHub to host via Pages."
