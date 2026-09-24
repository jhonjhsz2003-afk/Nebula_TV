# Cloudflare deployment

This repository is intentionally deployable from the repository root.

## Workers Builds (Worker + static assets)
Deploy command:

```bash
npx wrangler deploy --config ./wrangler.toml
```

Repository root directory: `/`
Build command: leave empty

The root must contain `wrangler.toml`, `site/`, and `cloudflare/`.

## Cloudflare Pages (static site only)
Do not use `npx wrangler deploy` as the Pages build/deploy command.
Use:
- Build command: empty
- Build output directory: `site`

For a CLI Pages deployment:

```bash
npx wrangler pages deploy site --project-name nebula-tv
```
