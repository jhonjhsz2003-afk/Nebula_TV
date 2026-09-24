package br.com.nebulatv.app;

import br.com.nebulatv.service.*;

public final class Services {
    private Services() {}
    public static final AccountService ACCOUNTS = new AccountService();
    public static final TitleService TITLES = new TitleService();
    public static final EpisodeService EPISODES = new EpisodeService();
    public static final TmdbService TMDB = new TmdbService();
    public static final ImageCacheService IMAGES = new ImageCacheService();
    public static final AddonService ADDONS = new AddonService();
    public static final LocalAiService AI = new LocalAiService();
    public static final CustomApiService CUSTOM = new CustomApiService();
    public static final ApiSourceService APIS = new ApiSourceService();
}
