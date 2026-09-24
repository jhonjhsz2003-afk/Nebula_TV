package br.com.nebulatv.dao;

import java.nio.file.*;
import java.sql.*;

public final class Database {
    private static Connection c;
    private Database() {}
    public static synchronized void init() {
        if (c != null) return;
        try {
            Files.createDirectories(Path.of("data"));
            c = DriverManager.getConnection("jdbc:sqlite:data/nebulatv_infinity.db");
            try (Statement s = c.createStatement()) {
                s.execute("PRAGMA foreign_keys=ON");
                s.execute("PRAGMA journal_mode=WAL");
                s.execute("PRAGMA synchronous=NORMAL");
                s.execute("PRAGMA busy_timeout=5000");
                s.execute("CREATE TABLE IF NOT EXISTS accounts(id INTEGER PRIMARY KEY AUTOINCREMENT,username TEXT UNIQUE NOT NULL,email TEXT UNIQUE NOT NULL,display_name TEXT NOT NULL,password_hash TEXT NOT NULL,admin INTEGER NOT NULL DEFAULT 0,created_at TEXT DEFAULT CURRENT_TIMESTAMP)");
                s.execute("CREATE TABLE IF NOT EXISTS profiles(id INTEGER PRIMARY KEY AUTOINCREMENT,user_id INTEGER NOT NULL,name TEXT NOT NULL,avatar TEXT NOT NULL,child INTEGER NOT NULL DEFAULT 0,pin TEXT NOT NULL DEFAULT '',FOREIGN KEY(user_id) REFERENCES accounts(id) ON DELETE CASCADE)");
                s.execute("CREATE TABLE IF NOT EXISTS titles(id INTEGER PRIMARY KEY AUTOINCREMENT,external_id TEXT NOT NULL DEFAULT '',name TEXT NOT NULL,overview TEXT NOT NULL DEFAULT '',poster TEXT NOT NULL DEFAULT '',backdrop TEXT NOT NULL DEFAULT '',type TEXT NOT NULL DEFAULT 'movie',genres TEXT NOT NULL DEFAULT '',year TEXT NOT NULL DEFAULT '',rating TEXT NOT NULL DEFAULT '',source TEXT NOT NULL DEFAULT 'local',stream_url TEXT NOT NULL DEFAULT '',runtime INTEGER NOT NULL DEFAULT 0,UNIQUE(source,external_id,name))");
                s.execute("CREATE TABLE IF NOT EXISTS progress(profile_id INTEGER NOT NULL,title_id INTEGER NOT NULL,seconds INTEGER NOT NULL DEFAULT 0,duration INTEGER NOT NULL DEFAULT 0,completed INTEGER NOT NULL DEFAULT 0,last_played TEXT DEFAULT CURRENT_TIMESTAMP,PRIMARY KEY(profile_id,title_id),FOREIGN KEY(profile_id) REFERENCES profiles(id) ON DELETE CASCADE,FOREIGN KEY(title_id) REFERENCES titles(id) ON DELETE CASCADE)");
                s.execute("CREATE TABLE IF NOT EXISTS favorites(profile_id INTEGER NOT NULL,title_id INTEGER NOT NULL,PRIMARY KEY(profile_id,title_id),FOREIGN KEY(profile_id) REFERENCES profiles(id) ON DELETE CASCADE,FOREIGN KEY(title_id) REFERENCES titles(id) ON DELETE CASCADE)");
                s.execute("CREATE TABLE IF NOT EXISTS settings(k TEXT PRIMARY KEY,v TEXT NOT NULL)");
                s.execute("CREATE TABLE IF NOT EXISTS addons(id INTEGER PRIMARY KEY AUTOINCREMENT,manifest_url TEXT UNIQUE NOT NULL,name TEXT NOT NULL DEFAULT '',version TEXT NOT NULL DEFAULT '',description TEXT NOT NULL DEFAULT '',enabled INTEGER NOT NULL DEFAULT 1)");
                s.execute("CREATE TABLE IF NOT EXISTS api_sources(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL,url TEXT NOT NULL,token TEXT NOT NULL DEFAULT '',kind TEXT NOT NULL DEFAULT 'json',enabled INTEGER NOT NULL DEFAULT 1)");
                s.execute("CREATE TABLE IF NOT EXISTS episodes(id INTEGER PRIMARY KEY AUTOINCREMENT,title_id INTEGER NOT NULL,season_number INTEGER NOT NULL,episode_number INTEGER NOT NULL,name TEXT NOT NULL,overview TEXT NOT NULL DEFAULT '',still TEXT NOT NULL DEFAULT '',runtime INTEGER NOT NULL DEFAULT 0,stream_url TEXT NOT NULL DEFAULT '',UNIQUE(title_id,season_number,episode_number),FOREIGN KEY(title_id) REFERENCES titles(id) ON DELETE CASCADE)");
                s.execute("CREATE TABLE IF NOT EXISTS episode_progress(profile_id INTEGER NOT NULL,episode_id INTEGER NOT NULL,seconds INTEGER NOT NULL DEFAULT 0,completed INTEGER NOT NULL DEFAULT 0,last_played TEXT DEFAULT CURRENT_TIMESTAMP,PRIMARY KEY(profile_id,episode_id),FOREIGN KEY(profile_id) REFERENCES profiles(id) ON DELETE CASCADE,FOREIGN KEY(episode_id) REFERENCES episodes(id) ON DELETE CASCADE)");
            }
            seed();
        } catch (Exception e) { throw new RuntimeException("Banco local indisponível: " + e.getMessage(), e); }
    }

    private static void seed() {
        try {
            try (PreparedStatement q = c.prepareStatement("SELECT COUNT(*) FROM accounts"); ResultSet r = q.executeQuery()) {
                if (r.next() && r.getInt(1) == 0) {
                    try (PreparedStatement p = c.prepareStatement("INSERT INTO accounts(username,email,display_name,password_hash,admin) VALUES(?,?,?,?,1)", Statement.RETURN_GENERATED_KEYS)) {
                        p.setString(1,"demo"); p.setString(2,"demo@nebulatv.local"); p.setString(3,"Demo");
                        p.setString(4,"5e884898da28047151d0e56f8dc6292773603d0d6aabbdd2d6e7fb1bb5f5dbb"); p.executeUpdate();
                        long id=0; try(ResultSet g=p.getGeneratedKeys()){if(g.next())id=g.getLong(1);}
                        try(PreparedStatement x=c.prepareStatement("INSERT INTO profiles(user_id,name,avatar) VALUES(?,?,?)")){x.setLong(1,id);x.setString(2,"Demo");x.setString(3,"profile-01.png");x.executeUpdate();}
                    }
                }
            }
            try (PreparedStatement q=c.prepareStatement("SELECT COUNT(*) FROM titles");ResultSet r=q.executeQuery()){
                if(r.next()&&r.getInt(1)==0){
                    String[][] seed={{"Starlight Protocol","Ação • Ficção científica","2026","8.8","movie"},{"Neon Ronin","Ação • Animação","2025","9.1","tv"},{"Beyond Orion","Aventura • Ficção científica","2025","8.6","movie"},{"Crimson Harbor","Drama • Mistério","2024","8.4","movie"},{"Skyforge","Fantasia • Animação","2025","8.9","tv"},{"Zero Signal","Thriller • Ficção científica","2026","8.2","movie"},{"Moonfall District","Drama","2024","8.0","movie"},{"Dragon Arc","Fantasia • Animação","2026","9.0","tv"},{"Echoes of Nova","Aventura • Drama","2025","8.7","movie"},{"Last Horizon","Ação • Ficção científica","2024","8.3","movie"},{"Violet Circuit","Animação • Ficção científica","2026","8.6","tv"},{"Night Code","Mistério • Thriller","2025","8.1","movie"},{"Solaris 9","Ficção científica","2025","8.5","movie"},{"Aurora Zero","Aventura","2026","8.4","movie"},{"Mythline","Fantasia","2024","8.8","tv"},{"Neon Hearts","Drama • Romance","2026","8.1","movie"}};
                    try(PreparedStatement p=c.prepareStatement("INSERT INTO titles(external_id,name,overview,type,genres,year,rating,source) VALUES(?,?,?,?,?,?,?,?)")){
                        for(String[] x:seed){p.setString(1,"seed-"+x[0].toLowerCase().replace(' ','-'));p.setString(2,x[0]);p.setString(3,"Catálogo demonstrativo do Nebula TV. Conecte o TMDB para buscar artes, sinopse, avaliação e informações atualizadas.");p.setString(4,x[4]);p.setString(5,x[1]);p.setString(6,x[2]);p.setString(7,x[3]);p.setString(8,"local");p.addBatch();}p.executeBatch();
                    }
                }
            }
        } catch (Exception ignored) {}
    }
    public static Connection conn(){return c;}
    public static synchronized void close(){try{if(c!=null)c.close();}catch(Exception ignored){}c=null;}
}
