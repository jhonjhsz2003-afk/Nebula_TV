package br.com.nebulatv.model;

public class Episode {
    public long id;
    public long titleId;
    public int seasonNumber;
    public int episodeNumber;
    public String name = "Episódio";
    public String overview = "";
    public String still = "";
    public int runtime;
    public String streamUrl = "";
    public int progressSeconds;
    public boolean watched;

    public Episode() {}

    public Episode(long id, long titleId, int seasonNumber, int episodeNumber, String name,
                   String overview, String still, int runtime, String streamUrl) {
        this.id = id;
        this.titleId = titleId;
        this.seasonNumber = seasonNumber;
        this.episodeNumber = episodeNumber;
        this.name = name;
        this.overview = overview;
        this.still = still;
        this.runtime = runtime;
        this.streamUrl = streamUrl;
    }

    @Override public String toString() {
        return String.format("T%d • EP%02d — %s", seasonNumber, episodeNumber, name);
    }
}

