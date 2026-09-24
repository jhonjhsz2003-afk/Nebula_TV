# Publicar o Nebula TV no GitHub + Cloudflare

## GitHub

1. Crie um repositório vazio.
2. Na pasta do projeto:

```bash
git init
git add .
git commit -m "feat: Nebula TV Infinity"
git branch -M main
git remote add origin https://github.com/SEU-USUARIO/nebula-tv.git
git push -u origin main
```

Nunca coloque `data/apis.json` no repositório. O `.gitignore` deste projeto já exclui o arquivo e os bancos locais.

## Cloudflare Pages

A pasta `site/` é a página pública do projeto. Ela pode ser conectada ao mesmo repositório pelo Git integration. Configure:

- Framework preset: None
- Build command: deixe vazio
- Build output directory: `site`

Depois de cada push, a Cloudflare pode criar uma nova implantação automaticamente.

## Importante sobre o desktop

JavaFX é um aplicativo desktop. A Cloudflare não executa o `.jar`/JavaFX como uma aplicação desktop no navegador. No Cloudflare, publique o site de apresentação/download e, opcionalmente, um Worker para APIs compartilhadas. O aplicativo Windows continua sendo baixado e executado no PC.

## Worker opcional

O esqueleto está em `cloudflare/`. Ele possui `/api/health` e pode ser ampliado com D1/R2/KV quando o projeto precisar de sincronização compartilhada.
