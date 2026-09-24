NEBULA TV — APIs

TMDB
1) Gere seu TMDB Read Access Token.
2) Abra Configurações > APIs.
3) Cole o token e clique em Salvar token.
4) O salvamento não inicia sincronização nem bloqueia a interface.
5) Use Testar conexão para validar.
6) Use Atualizar catálogo para iniciar a sincronização em segundo plano.

ARQUIVO
Você também pode criar data/apis.json a partir de data/apis.json.example.
Nunca comite data/apis.json no GitHub: ele está no .gitignore.

APIs ADICIONAIS
Na tela Configurações > APIs, o administrador pode cadastrar nome, URL, token e tipo.
Depois use Testar ou Sincronizar. O adaptador entende resultados em `results[]` ou `items[]`
e campos comuns como title/name, poster, backdrop/background, overview/description,
year/releaseInfo, rating/vote_average e stream/stream_url/url.
