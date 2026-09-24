package br.com.nebulatv.service;

import br.com.nebulatv.dao.Database;
import br.com.nebulatv.model.ApiSource;

import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** CRUD das fontes de API configuráveis pelo administrador. */
public final class ApiSourceService {
    public List<ApiSource> all() {
        List<ApiSource> out = new ArrayList<>();
        String sql = "SELECT id,name,url,token,kind,enabled FROM api_sources ORDER BY id";
        try (PreparedStatement p = Database.conn().prepareStatement(sql); ResultSet r = p.executeQuery()) {
            while (r.next()) out.add(read(r));
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return out;
    }

    public void upsert(ApiSource a) {
        String sql = "INSERT INTO api_sources(id,name,url,token,kind,enabled) VALUES(?,?,?,?,?,?) "
                + "ON CONFLICT(id) DO UPDATE SET name=excluded.name,url=excluded.url,token=excluded.token,kind=excluded.kind,enabled=excluded.enabled";
        try (PreparedStatement p = Database.conn().prepareStatement(sql)) {
            if (a.id > 0) p.setLong(1, a.id); else p.setNull(1, Types.INTEGER);
            p.setString(2, a.name == null ? "API" : a.name.trim());
            p.setString(3, a.url == null ? "" : a.url.trim());
            p.setString(4, a.token == null ? "" : a.token.trim());
            p.setString(5, a.kind == null ? "json" : a.kind.trim());
            p.setInt(6, a.enabled ? 1 : 0);
            p.executeUpdate();
            if (a.id == 0) {
                try (PreparedStatement q = Database.conn().prepareStatement("SELECT last_insert_rowid()")) {
                    try (ResultSet r = q.executeQuery()) { if (r.next()) a.id = r.getLong(1); }
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void delete(long id) {
        try (PreparedStatement p = Database.conn().prepareStatement("DELETE FROM api_sources WHERE id=?")) {
            p.setLong(1, id);
            p.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public CompletableFuture<Boolean> test(ApiSource a) {
        return ServicesBridge.test(a);
    }

    private ApiSource read(ResultSet r) throws SQLException {
        return new ApiSource(r.getLong(1), r.getString(2), r.getString(3), r.getString(4), r.getString(5), r.getInt(6) == 1);
    }

    /** Adaptador interno para manter uma única implementação HTTP. */
    static final class ServicesBridge {
        static CompletableFuture<Boolean> test(ApiSource a) {
            return java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                try {
                    java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                            .connectTimeout(java.time.Duration.ofSeconds(5)).build();
                    var builder = java.net.http.HttpRequest.newBuilder(java.net.URI.create(a.url.trim()))
                            .timeout(java.time.Duration.ofSeconds(8)).GET()
                            .header("accept", "application/json");
                    if (a.token != null && !a.token.isBlank()) builder.header("Authorization", "Bearer " + a.token.trim());
                    var response = client.send(builder.build(), java.net.http.HttpResponse.BodyHandlers.ofString());
                    return response.statusCode() >= 200 && response.statusCode() < 300;
                } catch (Exception e) {
                    return false;
                }
            });
        }
    }
}
