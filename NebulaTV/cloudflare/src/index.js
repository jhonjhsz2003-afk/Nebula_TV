/**
 * Nebula TV - Cloudflare Worker + Static Assets
 *
 * O Worker atende apenas as rotas /api/*.
 * O restante do site é servido pelo binding ASSETS.
 */

const corsHeaders = {
  "access-control-allow-origin": "*",
  "access-control-allow-methods": "GET,OPTIONS",
  "access-control-allow-headers": "content-type,authorization",
  "cache-control": "no-store"
};

function json(data, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: {
      ...corsHeaders,
      "content-type": "application/json; charset=utf-8"
    }
  });
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);

    if (request.method === "OPTIONS") {
      return new Response(null, { status: 204, headers: corsHeaders });
    }

    if (url.pathname === "/api/health") {
      return json({
        ok: true,
        app: "Nebula TV",
        version: env.NEBULA_VERSION || "unknown",
        platform: "Cloudflare Workers + Static Assets"
      });
    }

    if (url.pathname.startsWith("/api/")) {
      return json({
        ok: false,
        error: "API route not found",
        path: url.pathname
      }, 404);
    }

    // Todo o conteúdo público (HTML/CSS/imagens) vem de ./site.
    return env.ASSETS.fetch(request);
  }
};
