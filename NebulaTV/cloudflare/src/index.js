export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    const headers = { "content-type": "application/json; charset=utf-8", "access-control-allow-origin": "*" };
    if (request.method === "OPTIONS") return new Response(null, { headers: { ...headers, "access-control-allow-methods": "GET,OPTIONS", "access-control-allow-headers": "content-type,authorization" } });
    if (url.pathname === "/api/health") return new Response(JSON.stringify({ ok: true, app: "Nebula TV", version: env.NEBULA_VERSION, platform: "Cloudflare Workers" }), { headers });
    return new Response(JSON.stringify({ ok: true, service: "Nebula TV", routes: ["/api/health"], note: "Use este Worker como ponto de extensao para catalogo compartilhado; tokens TMDB nao devem ser enviados ao GitHub." }), { headers });
  }
};
