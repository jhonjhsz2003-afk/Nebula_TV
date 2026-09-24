package br.com.nebulatv.service;

import br.com.nebulatv.dao.Database;
import br.com.nebulatv.model.Addon;
import br.com.nebulatv.model.Title;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.*; import java.net.http.*; import java.time.Duration; import java.sql.*; import java.util.*; import java.util.concurrent.*;

public final class AddonService {
    private final ObjectMapper mapper=new ObjectMapper();
    private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    public List<Addon> all(){List<Addon>a=new ArrayList<>();try(PreparedStatement p=Database.conn().prepareStatement("SELECT id,manifest_url,name,version,description,enabled FROM addons ORDER BY id DESC");ResultSet r=p.executeQuery()){while(r.next()){Addon x=new Addon();x.id=r.getLong(1);x.url=r.getString(2);x.name=r.getString(3);x.version=r.getString(4);x.description=r.getString(5);x.enabled=r.getInt(6)==1;a.add(x);}}catch(Exception e){throw new RuntimeException(e);}return a;}
    public CompletableFuture<Addon> inspect(String url){try{URI u=URI.create(url.trim());HttpRequest q=HttpRequest.newBuilder(u).timeout(Duration.ofSeconds(8)).GET().header("accept","application/json").build();return client.sendAsync(q,HttpResponse.BodyHandlers.ofString()).thenApply(r->{if(r.statusCode()!=200)throw new CompletionException(new IllegalArgumentException("Manifesto retornou HTTP "+r.statusCode()));try{JsonNode n=mapper.readTree(r.body());Addon a=new Addon();a.url=url;a.name=n.path("name").asText("Add-on sem nome");a.version=n.path("version").asText("");a.description=n.path("description").asText("");if(n.has("resources"))for(JsonNode x:n.path("resources"))a.resources.add(x.asText());save(a);return a;}catch(Exception e){throw new CompletionException(e);}});}catch(Exception e){return CompletableFuture.failedFuture(e);}}
    private void save(Addon a){try(PreparedStatement p=Database.conn().prepareStatement("INSERT INTO addons(manifest_url,name,version,description,enabled) VALUES(?,?,?,?,1) ON CONFLICT(manifest_url) DO UPDATE SET name=excluded.name,version=excluded.version,description=excluded.description")){p.setString(1,a.url);p.setString(2,a.name);p.setString(3,a.version);p.setString(4,a.description);p.executeUpdate();}catch(Exception e){throw new RuntimeException(e);}}
    public CompletableFuture<List<Title>> catalog(Addon a){
        String base=a.url; int slash=base.lastIndexOf('/'); if(slash>=0) base=base.substring(0,slash);
        String url=base+"/catalog/movie/top.json";
        try{ HttpRequest q=HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(8)).GET().header("accept","application/json").build();
            return client.sendAsync(q,HttpResponse.BodyHandlers.ofString()).thenApply(r->{ if(r.statusCode()!=200)return List.of(); try{return parseMetas(mapper.readTree(r.body()).path("metas"));}catch(Exception e){return List.of();} }).orTimeout(12,TimeUnit.SECONDS).exceptionally(e->List.of());
        }catch(Exception e){return CompletableFuture.completedFuture(List.of());}
    }
    private List<Title> parseMetas(JsonNode arr){List<Title> out=new ArrayList<>(); if(!arr.isArray())return out; for(JsonNode n:arr){Title t=new Title(0,n.path("name").asText("Sem título"),n.path("type").asText("movie"),n.path("poster").asText(""),n.path("background").asText(n.path("poster").asText("")),n.path("description").asText(""),n.path("genres").toString(),n.path("releaseInfo").asText(""),n.path("imdbRating").asText("0"),"addon");t.externalId=n.path("id").asText("");out.add(t);}return out;}

    public void remove(long id){try(PreparedStatement p=Database.conn().prepareStatement("DELETE FROM addons WHERE id=?")){p.setLong(1,id);p.executeUpdate();}catch(Exception e){throw new RuntimeException(e);}}
}
