package br.com.nebulatv.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Title {
    public long id;
    public String externalId = "";
    public String name = "";
    public String overview = "";
    public String poster = "";
    public String backdrop = "";
    public String type = "movie";
    public String genres = "";
    public String year = "";
    public String rating = "";
    public String source = "local";
    public String streamUrl = "";
    public int progress;
    public int duration;
    public boolean watched;
    public boolean favorite;
    public int runtime;

    public Title() {}
    public Title(long id, String name, String type, String poster, String backdrop, String overview,
                  String genres, String year, String rating, String source) {
        this.id=id; this.name=name; this.type=type; this.poster=poster; this.backdrop=backdrop;
        this.overview=overview; this.genres=genres; this.year=year; this.rating=rating; this.source=source;
    }
}
