package br.com.nebulatv.service;

import javafx.scene.image.Image;
import java.io.*; import java.net.*; import java.nio.file.*; import java.security.*; import java.util.concurrent.*;

public final class ImageCacheService {
    private final Path dir=Path.of("data","cache","images");
    private final ExecutorService pool=Executors.newFixedThreadPool(3,r->{Thread t=new Thread(r,"nebula-image");t.setDaemon(true);return t;});
    private final ConcurrentHashMap<String,Image> memory=new ConcurrentHashMap<>();
    public ImageCacheService(){try{Files.createDirectories(dir);}catch(IOException ignored){}}
    public CompletableFuture<Image> load(String url,double w,double h){
        if(url==null||url.isBlank())return CompletableFuture.completedFuture(null);
        String key=url+"|"+w+"|"+h;Image m=memory.get(key);if(m!=null)return CompletableFuture.completedFuture(m);
        return CompletableFuture.supplyAsync(()->{
            try{Path p=dir.resolve(hash(url));if(!Files.exists(p))download(url,p);Image i=new Image(p.toUri().toString(),w,h,true,true);if(!i.isError())memory.put(key,i);return i;}catch(Exception e){return null;}
        },pool);
    }
    private void download(String url,Path p)throws Exception{HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();c.setConnectTimeout(5000);c.setReadTimeout(8000);c.setRequestProperty("User-Agent","NebulaTV/3.0");c.connect();if(c.getResponseCode()!=200)return;try(InputStream in=c.getInputStream()){Files.copy(in,p,StandardCopyOption.REPLACE_EXISTING);}}
    private static String hash(String s)throws Exception{return sha256(s)+ext(s);}
    private static String sha256(String s)throws Exception{MessageDigest d=MessageDigest.getInstance("SHA-256");StringBuilder b=new StringBuilder();for(byte x:d.digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8)))b.append(String.format("%02x",x));return b.toString();}
    private static String ext(String s){String x=s.toLowerCase();if(x.contains(".webp"))return ".webp";if(x.contains(".png"))return ".png";return ".jpg";}
    public void shutdown(){pool.shutdownNow();}
}
