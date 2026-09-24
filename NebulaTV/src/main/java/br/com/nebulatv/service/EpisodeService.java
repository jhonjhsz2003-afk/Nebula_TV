package br.com.nebulatv.service;

import br.com.nebulatv.dao.Database;
import br.com.nebulatv.model.Episode;

import java.sql.*;
import java.util.*;

public final class EpisodeService {
    public List<Integer> seasons(long titleId) {
        List<Integer> out = new ArrayList<>();
        String sql = "SELECT DISTINCT season_number FROM episodes WHERE title_id=? ORDER BY season_number";
        try (PreparedStatement p = Database.conn().prepareStatement(sql)) {
            p.setLong(1, titleId);
            try (ResultSet r = p.executeQuery()) {
                while (r.next()) out.add(r.getInt(1));
            }
        } catch (SQLException e) { throw new RuntimeException(e); }
        return out;
    }

    public List<Episode> bySeason(long titleId, int season) {
        List<Episode> out = new ArrayList<>();
        String sql = "SELECT id,title_id,season_number,episode_number,name,overview,still,runtime,stream_url FROM episodes WHERE title_id=? AND season_number=? ORDER BY episode_number";
        try (PreparedStatement p = Database.conn().prepareStatement(sql)) {
            p.setLong(1, titleId); p.setInt(2, season);
            try (ResultSet r = p.executeQuery()) {
                while (r.next()) out.add(read(r));
            }
        } catch (SQLException e) { throw new RuntimeException(e); }
        return out;
    }

    public Episode find(long id) {
        String sql = "SELECT id,title_id,season_number,episode_number,name,overview,still,runtime,stream_url FROM episodes WHERE id=?";
        try (PreparedStatement p = Database.conn().prepareStatement(sql)) {
            p.setLong(1, id);
            try (ResultSet r = p.executeQuery()) { return r.next() ? read(r) : null; }
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    public Map<Long, Episode> loadProgress(long profileId) {
        Map<Long, Episode> out = new HashMap<>();
        String sql = "SELECT e.id,e.title_id,e.season_number,e.episode_number,e.name,e.overview,e.still,e.runtime,e.stream_url,COALESCE(p.seconds,0),COALESCE(p.completed,0) FROM episodes e LEFT JOIN episode_progress p ON p.episode_id=e.id AND p.profile_id=?";
        try (PreparedStatement p = Database.conn().prepareStatement(sql)) {
            p.setLong(1, profileId);
            try (ResultSet r = p.executeQuery()) {
                while (r.next()) {
                    Episode e = read(r);
                    e.progressSeconds = r.getInt(10);
                    e.watched = r.getInt(11) == 1;
                    out.put(e.id, e);
                }
            }
        } catch (SQLException e) { throw new RuntimeException(e); }
        return out;
    }

    public void markWatched(long profileId, long episodeId, boolean watched) {
        String sql = "INSERT INTO episode_progress(profile_id,episode_id,seconds,completed,last_played) VALUES(?,?,?,?,CURRENT_TIMESTAMP) ON CONFLICT(profile_id,episode_id) DO UPDATE SET completed=excluded.completed,last_played=CURRENT_TIMESTAMP";
        try (PreparedStatement p = Database.conn().prepareStatement(sql)) {
            p.setLong(1, profileId); p.setLong(2, episodeId); p.setInt(3, watched ? Integer.MAX_VALUE : 0); p.setInt(4, watched ? 1 : 0); p.executeUpdate();
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    public void saveProgress(long profileId, long episodeId, int seconds, int duration) {
        boolean completed = duration > 0 && seconds >= Math.max(1, duration - 5);
        String sql = "INSERT INTO episode_progress(profile_id,episode_id,seconds,completed,last_played) VALUES(?,?,?,?,CURRENT_TIMESTAMP) ON CONFLICT(profile_id,episode_id) DO UPDATE SET seconds=excluded.seconds,completed=excluded.completed,last_played=CURRENT_TIMESTAMP";
        try (PreparedStatement p = Database.conn().prepareStatement(sql)) {
            p.setLong(1, profileId); p.setLong(2, episodeId); p.setInt(3, seconds); p.setInt(4, completed ? 1 : 0); p.executeUpdate();
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    public void seedIfMissing() {
        try (PreparedStatement count = Database.conn().prepareStatement("SELECT COUNT(*) FROM episodes")) {
            try (ResultSet r = count.executeQuery()) {
                if (r.next() && r.getInt(1) > 0) return;
            }
            List<br.com.nebulatv.model.Title> series = new TitleService().allByType("tv");
            String sql = "INSERT INTO episodes(title_id,season_number,episode_number,name,overview,still,runtime,stream_url) VALUES(?,?,?,?,?,?,?,?)";
            try (PreparedStatement p = Database.conn().prepareStatement(sql)) {
                int added = 0;
                for (br.com.nebulatv.model.Title t : series) {
                    for (int ep = 1; ep <= 8 && added < 40; ep++) {
                        p.setLong(1, t.id); p.setInt(2, 1); p.setInt(3, ep);
                        p.setString(4, "Episódio " + ep);
                        p.setString(5, "Episódio demonstrativo. Adicione uma URL de reprodução pelo painel Admin quando quiser.");
                        p.setString(6, t.backdrop); p.setInt(7, 45); p.setString(8, ""); p.addBatch(); added++;
                    }
                }
                p.executeBatch();
            }
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    private Episode read(ResultSet r) throws SQLException {
        return new Episode(r.getLong(1), r.getLong(2), r.getInt(3), r.getInt(4), r.getString(5), r.getString(6), r.getString(7), r.getInt(8), r.getString(9));
    }
}
