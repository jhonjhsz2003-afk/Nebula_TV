# Nebula TV Infinity

Nebula TV é um catálogo desktop premium em Java 17 + JavaFX + SQLite, inspirado em experiências modernas de streaming, com identidade cósmica própria.

## Destaques

- Home com hero cinematográfico, fundo ambiente e setas de navegação.
- Catálogos separados: Filmes, Séries e Animes.
- Busca e filtros: tipo, gênero, ano, nota e ordenação.
- Hover preview com mini sinopse e ações rápidas.
- TMDB assíncrono com cache e atualização em segundo plano.
- Fontes de API adicionais configuráveis pelo admin.
- Add-ons por manifesto.
- Contas, login, criação de conta e múltiplos perfis.
- 20 avatares originais.
- Favoritos, continuar assistindo e marcação de assistido.
- Séries com temporadas e episódios organizados.
- Player JavaFX, fullscreen, seek, volume, player externo e SRT/VTT.
- Configurações em abas: Geral, Aparência, Reprodução, Legendas, Teclado, APIs, Add-ons, Conta, Backup.
- Estatísticas com gráficos JavaFX.
- Admin com CRUD de títulos.
- Backup/importação JSON.
- Site público em `site/` e Worker opcional em `cloudflare/`.

## Execução

Requer JDK 17 e Maven.

```bat
mvn clean javafx:run
```

Ou:

```bat
run.bat
```

## Conta demo

Usuário: `demo`
Senha: `password`

## TMDB

Cole o **Read Access Token** em `Configurações → APIs → TMDB`. Salvar não inicia sincronização. O teste e a atualização são separados e rodam fora da thread de interface.

Você também pode usar `data/apis.json` com base em `data/apis.json.example`.

## GitHub / Cloudflare

Veja `docs/GITHUB_CLOUDFLARE.md` para publicar o repositório e a página pública.
