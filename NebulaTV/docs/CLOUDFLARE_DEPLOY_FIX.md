# Cloudflare — correção do deploy

O erro `Could not detect a directory containing static files` acontece quando o comando `npx wrangler deploy` é executado na raiz e o Wrangler não encontra uma configuração de assets apontando para `site/`.

Agora a configuração principal fica na raiz do repositório:

```text
wrangler.toml
site/
cloudflare/src/index.js
```

A configuração usa Workers Static Assets:

```toml
main = "./cloudflare/src/index.js"

[assets]
directory = "./site"
binding = "ASSETS"
```

O comando continua sendo:

```bash
npx wrangler deploy
```

Na Cloudflare Workers Builds, use a raiz do repositório e esse comando de deploy.

Alternativa: para hospedar somente o site estático pelo Cloudflare Pages, conecte o GitHub em Workers & Pages e use:

- Framework: None
- Build command: vazio
- Build output directory: `site`
- Root directory: raiz do repositório

Nunca commite `data/apis.json` ou bancos locais. O `.gitignore` já protege esses arquivos.
