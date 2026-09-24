package br.com.nebulatv.model;

/** Fonte de API administrada localmente pelo dono da instalação. */
public class ApiSource {
    public long id;
    public String name = "Nova API";
    public String url = "";
    public String token = "";
    public String kind = "json";
    public boolean enabled = true;

    public ApiSource() {}

    public ApiSource(long id, String name, String url, String token, String kind, boolean enabled) {
        this.id = id;
        this.name = name;
        this.url = url;
        this.token = token;
        this.kind = kind;
        this.enabled = enabled;
    }
}
