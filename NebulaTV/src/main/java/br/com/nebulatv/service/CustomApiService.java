package br.com.nebulatv.service;

import br.com.nebulatv.model.ApiSource;
import br.com.nebulatv.model.Title;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/** Adaptador para APIs JSON adicionadas pelo administrador. */
public final class CustomApiService {
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    public CompletableFuture<List<Title>> fetch(String url) {
        return fetch(new ApiSource(0, "API", url, "", "json", true));
    }

    public CompletableFuture<List<Title>> fetch(ApiSource source) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(source.url.trim()))
                    .timeout(Duration.ofSeconds(10)).header("accept", "application/json").GET();
            if (source.token != null && !source.token.isBlank()) builder.header("Authorization", "Bearer " + source.token.trim());
            return client.sendAsync(builder.build(), HttpResponse.BodyHandlers.ofString())
                    .thenApply(r -> {
                        if (r.statusCode() < 200 || r.statusCode() >= 300) return List.<Title>of();
                        try { return parse(mapper.readTree(r.body())); } catch (Exception e) { return List.<Title>of(); }
                    }).orTimeout(12, TimeUnit.SECONDS).exceptionally(e -> List.of());
        } catch (Exception e) {
            return CompletableFuture.completedFuture(List.of());
        }
    }

    private List<Title> parse(JsonNode root) {
        JsonNode arr = root.isArray() ? root : root.path("results");
        if (!arr.isArray()) arr = root.path("items");
        if (!arr.isArray()) return List.of();
        List<Title> out = new ArrayList<>();
        for (JsonNode n : arr) {
            String name = first(n, "title", "name");
            if (name.isBlank()) continue;
            String poster = first(n, "poster", "poster_url", "image", "posterPath");
            String backdrop = first(n, "backdrop", "backdrop_url", "background", "backdropPath");
            String year = first(n, "year", "release_year", "releaseInfo");
            String rating = first(n, "rating", "vote_average", "score", "imdbRating");
            String type = first(n, "type", "media_type", "kind");
            if (type.isBlank()) type = "movie";
            if (type.equals("series")) type = "tv";
            String genres = first(n, "genres", "genre");
            if (genres.startsWith("[")) genres = genres.replace("[", "").replace("]", "").replace("\"", "");
            Title t = new Title(0, name, type, poster, backdrop, first(n, "overview", "description", "synopsis"), genres, year, rating, "custom");
            t.externalId = first(n, "id", "external_id", "imdb_id");
            t.streamUrl = first(n, "stream", "stream_url", "video", "url");
            out.add(t);
        }
        return out;
    }

    private static String first(JsonNode n, String... keys) {
        for (String k : keys) {
            JsonNode x = n.get(k);
            if (x != null && !x.isNull()) return x.isTextual() ? x.asText("") : x.toString();
        }
        return "";
    }
}
