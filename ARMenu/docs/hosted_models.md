# Hosted models (GitHub Pages)

Place model files in `docs/models/` to serve them via GitHub Pages.

Example raw URL when pushed to GitHub (replace `OWNER` and `REPO` and `BRANCH`):

```
https://raw.githubusercontent.com/OWNER/REPO/BRANCH/docs/models/fried_chicken.glb
```

Recommended steps:

1. Run the helper to copy models from the Android project into `docs/models/`:

```bash
./scripts/publish_models.sh
git add docs/models
git commit -m "Add models for GitHub Pages"
git push origin BRANCH
```

2. Enable GitHub Pages in the repository settings and set the source to the `docs/` folder (or use the `gh` CLI).

3. Use the raw URL (or the `https://OWNER.github.io/REPO/models/...` URL) in your QR codes. The app will accept `http(s)` URLs and load them directly.

Notes:
- If loading fails due to CORS, use the download-to-local approach instead (I can implement that if needed).
