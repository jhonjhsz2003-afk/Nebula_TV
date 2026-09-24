package br.com.nebulatv.service;

import br.com.nebulatv.dao.Database;
import java.sql.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Configuração local com cache em memória para reduzir consultas repetidas ao SQLite. */
public final class ConfigService {
    private static final Map<String,String> CACHE = new ConcurrentHashMap<>();
    private ConfigService() {}

    public static String get(String key, String fallback) {
        String cached = CACHE.get(key);
        if (cached != null) return cached;
        try (PreparedStatement p = Database.conn().prepareStatement("SELECT v FROM settings WHERE k=?")) {
            p.setString(1, key);
            try (ResultSet r = p.executeQuery()) {
                String value = r.next() ? r.getString(1) : fallback;
                CACHE.put(key, value);
                return value;
            }
        } catch (Exception e) {
            return fallback;
        }
    }

    public static void set(String key, String value) {
        String safe = value == null ? "" : value;
        try (PreparedStatement p = Database.conn().prepareStatement("INSERT INTO settings(k,v) VALUES(?,?) ON CONFLICT(k) DO UPDATE SET v=excluded.v")) {
            p.setString(1, key);
            p.setString(2, safe);
            p.executeUpdate();
            CACHE.put(key, safe);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static boolean bool(String key, boolean fallback) { return Boolean.parseBoolean(get(key, String.valueOf(fallback))); }
    public static void setBool(String key, boolean value) { set(key, String.valueOf(value)); }
    public static void clearCache() { CACHE.clear(); }
}
