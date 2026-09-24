package br.com.nebulatv.service;

import br.com.nebulatv.model.Episode;
import br.com.nebulatv.model.Title;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/** Cliente TMDB assíncrono: nenhuma chamada de rede roda na thread do JavaFX. */
public final class TmdbService {
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6)).version(HttpClient.Version.HTTP_2).build();
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<List<Title>>> inFlight = new ConcurrentHashMap<>();
    private static final long TTL = 15 * 60_000L;
    private final Map<Integer,String> genres = Map.ofEntries(
            Map.entry(12,"Aventura"),Map.entry(14,"Fantasia"),Map.entry(16,"Animação"),Map.entry(18,"Drama"),Map.entry(27,"Terror"),Map.entry(28,"Ação"),Map.entry(35,"Comédia"),Map.entry(36,"História"),Map.entry(37,"Faroeste"),Map.entry(53,"Thriller"),Map.entry(80,"Crime"),Map.entry(878,"Ficção científica"),Map.entry(9648,"Mistério"),Map.entry(10749,"Romance"),Map.entry(10751,"Família"),Map.entry(10752,"Guerra"),Map.entry(10759,"Ação e aventura"),Map.entry(10762,"Infantil"),Map.entry(10763,"Notícias"),Map.entry(10764,"Reality"),Map.entry(10765,"Fantasia e ficção científica"),Map.entry(10766,"Novela"),Map.entry(10767,"Talk show"),Map.entry(10768,"Guerra e política")
    );

    public boolean configured(){ return !ConfigService.get("tmdb.token", "").isBlank(); }
    public CompletableFuture<Boolean> validate(String token){ return request("https://api.themoviedb.org/3/configuration?language=pt-BR", token).thenApply(r -> r.statusCode() >= 200 && r.statusCode() < 300).exceptionally(e -> false); }
    public CompletableFuture<List<Title>> trending(){ return get("https://api.themoviedb.org/3/trending/all/week?language=pt-BR&include_adult=false"); }
    public CompletableFuture<List<Title>> popularMovies(){ return get("https://api.themoviedb.org/3/movie/popular?language=pt-BR&page=1"); }
    public CompletableFuture<List<Title>> popularTv(){ return get("https://api.themoviedb.org/3/tv/popular?language=pt-BR&page=1"); }
    public CompletableFuture<List<Title>> popularAnime(){ return discover("tv", "with_genres=16&sort_by=popularity.desc"); }
    public CompletableFuture<List<Title>> search(String q){ if(q==null||q.isBlank())return CompletableFuture.completedFuture(List.of());return get("https://api.themoviedb.org/3/search/multi?query="+enc(q)+"&include_adult=false&language=pt-BR&page=1"); }
    public CompletableFuture<List<Title>> hero(){ return trending().thenApply(a -> a.stream().filter(t -> !t.backdrop.isBlank()).limit(7).toList()); }
    public CompletableFuture<List<Episode>> episodes(long tmdbId,int season){
        if(tmdbId<=0)return CompletableFuture.completedFuture(List.of());
        String url="https://api.themoviedb.org/3/tv/"+tmdbId+"/season/"+season+"?language=pt-BR";
        return request(url,ConfigService.get("tmdb.token","")).thenApply(resp->{ if(resp.statusCode()!=200)return List.of(); try{return parseEpisodes(mapper.readTree(resp.body()),tmdbId,season);}catch(Exception e){return List.of();}}).orTimeout(12,TimeUnit.SECONDS).exceptionally(e->List.of());
    }
    public void clearCache(){ cache.clear(); inFlight.clear(); }

    private CompletableFuture<List<Title>> discover(String type,String query){ return get("https://api.themoviedb.org/3/discover/"+type+"?language=pt-BR&page=1&include_adult=false&"+query); }
    private CompletableFuture<List<Title>> get(String url){
        CacheEntry entry=cache.get(url);
        if(entry!=null && System.currentTimeMillis()-entry.at<TTL) return CompletableFuture.completedFuture(entry.items);
        CompletableFuture<List<Title>> running=inFlight.get(url);
        if(running!=null) return running;
        String token=ConfigService.get("tmdb.token","");
        if(token.isBlank()) return CompletableFuture.completedFuture(List.of());
        CompletableFuture<List<Title>> created=request(url,token)
                .thenApply(resp->{
                    if(resp.statusCode()!=200) return List.<Title>of();
                    try{
                        List<Title> a=parse(mapper.readTree(resp.body()).path("results"));
                        cache.put(url,new CacheEntry(a,System.currentTimeMillis()));
                        return a;
                    }catch(Exception ex){return List.<Title>of();}
                })
                .orTimeout(12,TimeUnit.SECONDS)
                .exceptionally(ex->List.of());
        CompletableFuture<List<Title>> prior=inFlight.putIfAbsent(url,created);
        if(prior!=null) return prior;
        created.whenComplete((x,e)->inFlight.remove(url));
        return created;
    }
    private CompletableFuture<HttpResponse<String>> request(String url,String token){
        try{ HttpRequest req=HttpRequest.newBuilder(URI.create(url)).header("Authorization","Bearer "+token.trim()).header("accept","application/json").timeout(Duration.ofSeconds(12)).GET().build();return client.sendAsync(req,HttpResponse.BodyHandlers.ofString()); }catch(Exception e){return CompletableFuture.failedFuture(e);}
    }
    private List<Title> parse(JsonNode arr){List<Title>a=new ArrayList<>();for(JsonNode n:arr){String media=n.path("media_type").asText("");if(media.isBlank())media=n.has("first_air_date")?"tv":"movie";if(!media.equals("movie")&&!media.equals("tv"))continue;String name=n.path("title").asText("");if(name.isBlank())name=n.path("name").asText("Sem título");String date=n.path("release_date").asText("");if(date.isBlank())date=n.path("first_air_date").asText("");String year=date.length()>=4?date.substring(0,4):"";Title t=new Title(0,name,media,img(n.path("poster_path").asText(""),"w500"),img(n.path("backdrop_path").asText(""),"w1280"),n.path("overview").asText(""),genreText(n.path("genre_ids")),year,n.path("vote_average").asText("0"),"tmdb");t.externalId=String.valueOf(n.path("id").asLong());t.runtime=n.path("runtime").asInt(0);a.add(t);}return a;}
    private List<Episode> parseEpisodes(JsonNode n,long tmdbId,int season){List<Episode> out=new ArrayList<>();JsonNode arr=n.path("episodes");if(!arr.isArray())return out;for(JsonNode e:arr){Episode x=new Episode();x.titleId=tmdbId;x.seasonNumber=season;x.episodeNumber=e.path("episode_number").asInt();x.name=e.path("name").asText("Episódio "+x.episodeNumber);x.overview=e.path("overview").asText("");x.still=img(e.path("still_path").asText(""),"w500");x.runtime=e.path("runtime").asInt(0);out.add(x);}return out;}
    private String genreText(JsonNode arr){List<String> out=new ArrayList<>();if(arr.isArray())for(JsonNode x:arr){String g=genres.get(x.asInt());if(g!=null)out.add(g);}return String.join(" • ",out);}
    private static String img(String path,String size){return path==null||path.isBlank()?"":"https://image.tmdb.org/t/p/"+size+path;}
    private static String enc(String s){try{return URLEncoder.encode(s,java.nio.charset.StandardCharsets.UTF_8);}catch(Exception e){return "";}}
    private record CacheEntry(List<Title> items,long at){}
}
