package br.com.nebulatv.ui;

import br.com.nebulatv.app.AppState;
import br.com.nebulatv.app.NebulaApp;
import br.com.nebulatv.app.Services;
import br.com.nebulatv.dao.Database;
import br.com.nebulatv.model.*;
import br.com.nebulatv.service.ConfigService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.animation.*;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.*;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.image.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.scene.media.*;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

import java.awt.Desktop;
import java.io.File;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Shell principal do Nebula TV. A interface é construída em JavaFX puro para
 * diminuir pontos de falha do carregamento de FXML e permitir telas dinâmicas.
 */
public final class NebulaShell {
    private final StackPane root = new StackPane();
    private final BorderPane frame = new BorderPane();
    private final StackPane content = new StackPane();
    private final ImageView ambientA = ambientLayer();
    private final ImageView ambientB = ambientLayer();
    private final Label status = new Label("LOCAL • PRONTO");
    private final TextField globalSearch = new TextField();
    private final Map<Button, Runnable> navigation = new LinkedHashMap<>();
    private final PauseTransition searchDebounce = new PauseTransition(Duration.millis(300));
    private final AtomicBoolean refreshing = new AtomicBoolean(false);
    private final Map<Long, Boolean> watched = new HashMap<>();
    private final Map<Long, Integer> progress = new HashMap<>();
    private final Map<Long, Boolean> favorites = new HashMap<>();
    private List<Title> heroPool = new ArrayList<>();
    private int heroIndex;
    private ImageView heroImage;
    private Label heroTitle;
    private Label heroMeta;
    private Label heroOverview;
    private Timeline heroTimer;

    public NebulaShell() {
        root.getStyleClass().add("root-shell");
        root.getChildren().addAll(ambientA, ambientB, frame);
        ambientA.setOpacity(0.10);
        ambientB.setOpacity(0.0);
        frame.setCenter(content);
    }

    private ImageView ambientLayer() {
        ImageView v = new ImageView();
        v.setPreserveRatio(true);
        v.setSmooth(true);
        v.setMouseTransparent(true);
        v.fitWidthProperty().bind(root.widthProperty());
        v.fitHeightProperty().bind(root.heightProperty());
        return v;
    }

    public StackPane root() { return root; }

    public void start() {
        if (Services.ACCOUNTS.current() == null) {
            showLogin();
        } else {
            buildMainShell();
        }
    }

    public void scheduleStartupRefresh() {
        if (!Services.TMDB.configured() || !ConfigService.bool("startup.autoSync", true)) return;
        PauseTransition t = new PauseTransition(Duration.seconds(2.5));
        t.setOnFinished(e -> refreshCatalogAsync(false));
        t.play();
    }

    /* ============================ AUTH ============================ */

    private void showLogin() {
        VBox page = authPage("Entrar na sua conta", "Seu universo de filmes, séries, animes e preferências.");
        VBox card = authCard();
        TextField login = field("Usuário ou e-mail");
        PasswordField password = password("Senha");
        CheckBox remember = new CheckBox("Manter sessão neste PC");
        Button enter = actionButton("Entrar", true);
        Button create = actionButton("Criar conta", false);
        Label message = errorLabel();

        enter.setOnAction(e -> {
            try {
                Services.ACCOUNTS.login(login.getText(), password.getText());
                AppState.account = Services.ACCOUNTS.current();
                List<Profile> ps = Services.ACCOUNTS.profiles();
                AppState.profile = ps.isEmpty() ? Services.ACCOUNTS.createProfile(AppState.account.displayName, "profile-01.png", false, "") : ps.get(0);
                ConfigService.setBool("session.remember", remember.isSelected());
                buildMainShell();
            } catch (Exception ex) {
                message.setText(safeMessage(ex));
            }
        });
        create.setOnAction(e -> showRegister());

        card.getChildren().addAll(login, password, remember, enter, create, message);
        page.getChildren().add(card);
        content.getChildren().setAll(page);
        frame.setLeft(null);
        frame.setTop(null);
    }

    private void showRegister() {
        VBox page = authPage("Criar conta", "Uma conta local separa favoritos, histórico, perfis e configurações.");
        VBox card = authCard();
        TextField user = field("Nome de usuário");
        TextField email = field("E-mail");
        TextField display = field("Nome exibido");
        PasswordField pass = password("Senha");
        PasswordField pass2 = password("Confirmar senha");
        Label avatarTitle = new Label("Escolha seu avatar"); avatarTitle.getStyleClass().add("kicker");
        ToggleGroup avatarGroup = new ToggleGroup();
        FlowPane avatarsPane = new FlowPane(8, 8);
        avatarsPane.setPrefWrapLength(480);
        for (int i = 1; i <= 20; i++) {
            ToggleButton b = new ToggleButton();
            b.setToggleGroup(avatarGroup);
            b.setUserData(String.format("profile-%02d.png", i));
            b.getStyleClass().add("avatar-choice");
            setAvatarGraphic(b, (String) b.getUserData(), 54, 54);
            if (i == 1) b.setSelected(true);
            avatarsPane.getChildren().add(b);
        }
        ScrollPane avatarScroll = new ScrollPane(avatarsPane);
        avatarScroll.setFitToWidth(true);
        avatarScroll.setPrefHeight(122);
        avatarScroll.getStyleClass().add("clean-scroll");
        CheckBox local = new CheckBox("Armazenar meus dados localmente neste PC");
        local.setSelected(true);
        Button create = actionButton("Criar minha conta", true);
        Button back = actionButton("Voltar", false);
        Label message = errorLabel();

        create.setOnAction(e -> {
            try {
                if (!local.isSelected()) throw new IllegalArgumentException("O armazenamento local precisa estar ativo.");
                if (!pass.getText().equals(pass2.getText())) throw new IllegalArgumentException("As senhas não coincidem.");
                ToggleButton selected = (ToggleButton) avatarGroup.getSelectedToggle();
                String avatar = selected == null ? "profile-01.png" : String.valueOf(selected.getUserData());
                Services.ACCOUNTS.register(user.getText(), email.getText(), display.getText(), pass.getText(), avatar);
                AppState.account = Services.ACCOUNTS.current();
                AppState.profile = Services.ACCOUNTS.profiles().get(0);
                buildMainShell();
            } catch (Exception ex) {
                message.setText(safeMessage(ex));
            }
        });
        back.setOnAction(e -> showLogin());

        card.getChildren().addAll(user, email, display, pass, pass2, avatarTitle, avatarScroll, local, create, back, message);
        page.getChildren().add(card);
        content.getChildren().setAll(page);
    }

    private VBox authPage(String title, String subtitle) {
        VBox page = new VBox(12);
        page.setAlignment(Pos.CENTER);
        page.getStyleClass().add("auth-root");
        Label mark = new Label("N"); mark.getStyleClass().add("auth-mark");
        Label brand = new Label("NEBULA TV"); brand.getStyleClass().add("auth-brand");
        Label slogan = new Label("Seu universo. Seu catálogo. Seu controle."); slogan.getStyleClass().add("auth-slogan");
        page.getChildren().addAll(mark, brand, slogan);
        return page;
    }

    private VBox authCard() {
        VBox box = new VBox(10);
        box.setMaxWidth(610);
        box.getStyleClass().add("auth-card");
        return box;
    }

    /* ============================ SHELL ============================ */

    private void buildMainShell() {
        applyTheme(ConfigService.get("theme", "Nebula Dark"));
        navigation.clear();
        frame.setLeft(buildSidebar());
        frame.setTop(buildTopbar());
        showHome();
    }

    private VBox buildSidebar() {
        VBox side = new VBox(7);
        side.setPrefWidth(236);
        side.setPadding(new Insets(18, 14, 14, 14));
        side.getStyleClass().add("sidebar");

        HBox brand = new HBox(9);
        brand.setAlignment(Pos.CENTER_LEFT);
        Label n = new Label("N"); n.getStyleClass().add("brand-n");
        Label title = new Label("NEBULA"); title.getStyleClass().add("brand");
        brand.getChildren().addAll(n, title);
        Label tv = new Label("TV"); tv.getStyleClass().add("brand-tv");
        side.getChildren().addAll(brand, tv);

        addNav(side, "⌂   Início", this::showHome);
        addNav(side, "▣   Filmes", this::showMovies);
        addNav(side, "▤   Séries", this::showSeries);
        addNav(side, "✦   Animes", this::showAnime);
        addNav(side, "⌕   Explorar", this::showExplore);
        addNav(side, "♡   Minha lista", this::showList);
        addNav(side, "▷   Continuar", this::showContinue);
        side.getChildren().add(separator());
        addNav(side, "◔   Estatísticas", this::showStats);
        addNav(side, "⚙   Configurações", this::showSettings);
        addNav(side, "◇   Add-ons", this::showAddons);
        if (AppState.account != null && AppState.account.admin) addNav(side, "▣   Admin", this::showAdmin);

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        side.getChildren().add(spacer);

        Button profile = actionButton("◉   " + AppState.profile.name, false);
        profile.setOnAction(e -> showProfileManager());
        side.getChildren().add(profile);
        Label account = new Label(AppState.account.username + " • conta local");
        account.getStyleClass().add("muted-small");
        side.getChildren().add(account);
        return side;
    }

    private HBox buildTopbar() {
        HBox bar = new HBox(10);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(10, 16, 10, 16));
        bar.getStyleClass().add("topbar");
        Label context = new Label("NEBULA"); context.getStyleClass().add("top-context");
        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
        globalSearch.setPromptText("Pesquisar filmes, séries e animes...");
        globalSearch.setPrefWidth(390);
        globalSearch.getStyleClass().add("top-search");
        if (!globalSearch.getProperties().containsKey("bound")) {
            globalSearch.textProperty().addListener((obs, old, val) -> {
                searchDebounce.stop();
                searchDebounce.setOnFinished(e -> showExploreWithQuery(val));
                if (val != null && !val.isBlank()) searchDebounce.playFromStart();
            });
            globalSearch.getProperties().put("bound", true);
        }
        Button ai = smallButton("✦ IA"); ai.setTooltip(new Tooltip("Nebula IA local")); ai.setOnAction(e -> showAi());
        Button refresh = smallButton("↻"); refresh.setTooltip(new Tooltip("Atualizar catálogos em segundo plano")); refresh.setOnAction(e -> refreshCatalogAsync(true));
        Button profile = smallButton("◉"); profile.setTooltip(new Tooltip("Trocar perfil")); profile.setOnAction(e -> showProfileManager());
        bar.getChildren().addAll(context, spacer, globalSearch, ai, refresh, profile, status);
        return bar;
    }

    private void addNav(VBox side, String label, Runnable action) {
        Button button = new Button(label);
        button.setMaxWidth(Double.MAX_VALUE);
        button.getStyleClass().add("nav");
        button.setOnAction(e -> {
            for (Button b : navigation.keySet()) b.getStyleClass().remove("active");
            button.getStyleClass().add("active");
            action.run();
        });
        side.getChildren().add(button);
        navigation.put(button, action);
    }

    private Separator separator() { Separator s = new Separator(); s.getStyleClass().add("soft-separator"); return s; }

    private VBox page(String title, String subtitle) {
        VBox p = new VBox(18);
        p.getStyleClass().add("page");
        Label t = new Label(title); t.getStyleClass().add("page-title");
        p.getChildren().add(t);
        if (subtitle != null && !subtitle.isBlank()) {
            Label s = new Label(subtitle); s.setWrapText(true); s.getStyleClass().add("page-subtitle");
            p.getChildren().add(s);
        }
        return p;
    }

    private ScrollPane scroll(Node child) {
        ScrollPane s = new ScrollPane(child);
        s.setFitToWidth(true);
        s.setPannable(true);
        s.getStyleClass().add("clean-scroll");
        return s;
    }

    private void swap(Node node) {
        node.setOpacity(0);
        content.getChildren().setAll(node);
        FadeTransition fade = new FadeTransition(Duration.millis(240), node);
        fade.setToValue(1);
        fade.play();
    }

    /* ============================ HOME ============================ */

    private void showHome() {
        refreshUserState();
        VBox p = new VBox(20); p.getStyleClass().add("page");
        p.getChildren().add(buildHero());
        List<Title> all = Services.TITLES.all();
        List<Title> continueList = Services.TITLES.progress(AppState.profile.id, false);
        if (!continueList.isEmpty()) p.getChildren().add(rail("Acompanhando", continueList, true));
        p.getChildren().add(rail("Em alta agora", all.stream().sorted((a,b) -> Double.compare(score(b), score(a))).limit(18).toList(), true));
        p.getChildren().add(rail("Filmes", all.stream().filter(t -> "movie".equals(t.type)).limit(18).toList(), true));
        p.getChildren().add(rail("Séries populares", all.stream().filter(t -> "tv".equals(t.type)).limit(18).toList(), true));
        p.getChildren().add(rail("Anime • para você", all.stream().filter(this::looksAnime).limit(18).toList(), true));
        p.getChildren().add(rail("Mais bem avaliados", all.stream().sorted((a,b) -> Double.compare(num(b.rating), num(a.rating))).limit(18).toList(), true));
        swap(scroll(p));
        loadHeroAsync();
    }

    private StackPane buildHero() {
        StackPane hero = new StackPane();
        hero.setPrefHeight(470); hero.setMinHeight(420);
        hero.getStyleClass().add("hero");

        heroImage = new ImageView();
        heroImage.setPreserveRatio(true);
        heroImage.setSmooth(true);
        heroImage.fitWidthProperty().bind(hero.widthProperty());
        heroImage.fitHeightProperty().bind(hero.heightProperty());
        StackPane imageLayer = new StackPane(heroImage);

        Region topGlow = new Region(); topGlow.getStyleClass().add("hero-glow");
        Region gradient = new Region(); gradient.getStyleClass().add("hero-gradient");

        VBox info = new VBox(9);
        info.setMaxWidth(680);
        info.setPadding(new Insets(34));
        info.setAlignment(Pos.BOTTOM_LEFT);
        StackPane.setAlignment(info, Pos.BOTTOM_LEFT);
        heroMeta = new Label(); heroMeta.getStyleClass().add("hero-meta");
        heroTitle = new Label(); heroTitle.getStyleClass().add("hero-name"); heroTitle.setWrapText(true);
        heroOverview = new Label(); heroOverview.getStyleClass().add("hero-overview"); heroOverview.setWrapText(true); heroOverview.setMaxHeight(86);

        HBox actions = new HBox(9);
        Button watch = actionButton("▷   Assistir", true);
        Button add = actionButton("＋   Minha lista", false);
        Button more = actionButton("ⓘ   Detalhes", false);
        watch.setOnAction(e -> openWatchChooser(currentHero()));
        add.setOnAction(e -> { Title t = currentHero(); if (t != null) { Services.TITLES.toggleFavorite(AppState.profile.id, t.id); showHome(); } });
        more.setOnAction(e -> { Title t = currentHero(); if (t != null) showDetails(t); });
        actions.getChildren().addAll(watch, add, more);
        info.getChildren().addAll(heroMeta, heroTitle, heroOverview, actions);

        Button left = heroArrow("‹");
        Button right = heroArrow("›");
        StackPane.setAlignment(left, Pos.CENTER_LEFT); StackPane.setMargin(left, new Insets(0, 0, 0, 12));
        StackPane.setAlignment(right, Pos.CENTER_RIGHT); StackPane.setMargin(right, new Insets(0, 12, 0, 0));
        left.setOnAction(e -> changeHero(-1)); right.setOnAction(e -> changeHero(1));

        hero.getChildren().addAll(imageLayer, topGlow, gradient, info, left, right);
        return hero;
    }

    private Button heroArrow(String label) { Button b = new Button(label); b.getStyleClass().add("hero-arrow"); return b; }
    private Title currentHero() { return heroPool.isEmpty() ? null : heroPool.get(Math.max(0, Math.min(heroIndex, heroPool.size() - 1))); }

    private void loadHeroAsync() {
        CompletableFuture<List<Title>> f = Services.TMDB.configured()
                ? Services.TMDB.hero()
                : CompletableFuture.completedFuture(Services.TITLES.all().stream().filter(t -> !t.backdrop.isBlank()).limit(7).toList());
        f.exceptionally(e -> List.of()).thenAccept(items -> Platform.runLater(() -> {
            heroPool = new ArrayList<>(items);
            heroIndex = 0;
            renderHero();
            startHeroTimer();
        }));
    }

    private void startHeroTimer() {
        if (heroTimer != null) heroTimer.stop();
        heroTimer = new Timeline(new KeyFrame(Duration.seconds(8), e -> changeHero(1)));
        heroTimer.setCycleCount(Animation.INDEFINITE);
        heroTimer.play();
    }

    private void changeHero(int delta) {
        if (heroPool.isEmpty()) return;
        heroIndex = (heroIndex + delta + heroPool.size()) % heroPool.size();
        renderHero();
    }

    private void renderHero() {
        Title t = currentHero();
        if (t == null || heroImage == null) return;
        heroTitle.setText(t.name);
        heroMeta.setText((t.year.isBlank() ? "" : t.year + " • ") + ("tv".equals(t.type) ? "Série" : "Filme") + " • ★ " + safeRating(t.rating));
        heroOverview.setText(t.overview.isBlank() ? "Descubra este título no Nebula TV." : t.overview);
        transitionImage(heroImage, t.backdrop, 1600, 900);
        if (!t.backdrop.isBlank()) transitionImage(ambientA.getOpacity() > 0.05 ? ambientB : ambientA, t.backdrop, 1600, 900);
    }

    private VBox rail(String title, List<Title> titles, boolean arrows) {
        VBox box = new VBox(8);
        HBox header = new HBox(8); header.setAlignment(Pos.CENTER_LEFT);
        Label label = new Label(title); label.getStyleClass().add("section-title");
        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
        header.getChildren().addAll(label, spacer);
        HBox row = new HBox(12); row.setPadding(new Insets(3));
        for (Title t : titles) row.getChildren().add(card(t));
        ScrollPane rail = new ScrollPane(row); rail.setFitToHeight(true); rail.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER); rail.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER); rail.getStyleClass().add("rail-scroll"); rail.setPrefHeight(292);
        if (arrows) {
            Button back = smallButton("‹"); Button next = smallButton("›");
            back.setOnAction(e -> rail.setHvalue(Math.max(0, rail.getHvalue() - 0.45)));
            next.setOnAction(e -> rail.setHvalue(Math.min(1, rail.getHvalue() + 0.45)));
            header.getChildren().addAll(back, next);
        }
        box.getChildren().addAll(header, rail);
        return box;
    }

    private VBox card(Title t) {
        VBox card = new VBox(5);
        card.setPrefWidth(158); card.setMaxWidth(158);
        card.getStyleClass().add("title-card");

        StackPane poster = new StackPane(); poster.setPrefSize(158, 224); poster.getStyleClass().add("poster-wrap");
        ImageView iv = new ImageView(); iv.setFitWidth(158); iv.setFitHeight(224); iv.setPreserveRatio(false); poster.getChildren().add(iv);
        loadingSkeleton(poster);
        loadImage(iv, t.poster, 158, 224);

        Label type = new Label("tv".equals(t.type) ? "SÉRIE" : "FILME"); type.getStyleClass().add("type-pill");
        StackPane.setAlignment(type, Pos.TOP_LEFT); StackPane.setMargin(type, new Insets(8)); poster.getChildren().add(type);
        if (watched.getOrDefault(t.id, false) || t.watched) {
            Label done = new Label("✓ VISTO"); done.getStyleClass().add("watched-pill");
            StackPane.setAlignment(done, Pos.BOTTOM_LEFT); StackPane.setMargin(done, new Insets(8)); poster.getChildren().add(done);
        }

        ProgressBar pb = new ProgressBar(); pb.setMaxWidth(Double.MAX_VALUE); pb.getStyleClass().add("progress");
        int sec = progress.getOrDefault(t.id, t.progress); int dur = t.duration;
        if (sec > 0 && dur > 0 && !t.watched) pb.setProgress(Math.min(1, sec / (double) dur)); else pb.setVisible(false);

        Label n = new Label(t.name); n.setWrapText(true); n.getStyleClass().add("card-name");
        Label meta = new Label((t.year.isBlank() ? "" : t.year + " • ") + "★ " + safeRating(t.rating)); meta.getStyleClass().add("muted-small");
        card.getChildren().addAll(poster, pb, n, meta);

        PauseTransition hoverDelay = new PauseTransition(Duration.millis(520));
        card.setOnMouseEntered(e -> {
            card.getStyleClass().add("card-hover");
            hoverDelay.setOnFinished(x -> showCardPreview(card, t));
            hoverDelay.playFromStart();
        });
        card.setOnMouseExited(e -> { hoverDelay.stop(); hideCardPreview(card); card.getStyleClass().remove("card-hover"); });
        card.setOnMouseClicked(e -> showDetails(t));
        return card;
    }

    private void showCardPreview(VBox card, Title t) {
        if (card.getProperties().containsKey("preview")) return;
        StackPane poster = (StackPane) card.getChildren().get(0);
        VBox preview = new VBox(5);
        preview.getStyleClass().add("card-preview");
        Label title = new Label(t.name); title.getStyleClass().add("card-preview-title");
        Label summary = new Label(t.overview.isBlank() ? "Sem sinopse disponível." : t.overview); summary.setWrapText(true); summary.setMaxHeight(68);
        Button play = smallButton("▷"); Button info = smallButton("ⓘ"); Button fav = smallButton(favorites.getOrDefault(t.id, false) ? "♥" : "♡");
        play.setOnAction(e -> openWatchChooser(t)); info.setOnAction(e -> showDetails(t)); fav.setOnAction(e -> { Services.TITLES.toggleFavorite(AppState.profile.id, t.id); refreshUserState(); fav.setText(favorites.getOrDefault(t.id, false) ? "♥" : "♡"); });
        HBox actions = new HBox(6, play, info, fav);
        preview.getChildren().addAll(title, summary, actions);
        StackPane.setAlignment(preview, Pos.BOTTOM_LEFT); StackPane.setMargin(preview, new Insets(10));
        poster.getChildren().add(preview);
        card.getProperties().put("preview", preview);
    }

    private void hideCardPreview(VBox card) {
        Object o = card.getProperties().remove("preview");
        if (o instanceof Node n && card.getChildren().get(0) instanceof StackPane p) p.getChildren().remove(n);
    }

    /* ============================ LIBRARIES ============================ */

    private void showMovies() { showLibrary("Filmes", "movie"); }
    private void showSeries() { showLibrary("Séries", "tv"); }
    private void showAnime() {
        List<Title> list = Services.TITLES.all().stream().filter(this::looksAnime).collect(Collectors.toList());
        showLibrary("Animes", list);
    }

    private void showLibrary(String name, String type) { showLibrary(name, Services.TITLES.allByType(type)); }
    private void showLibrary(String name, List<Title> titles) {
        VBox p = page(name, "Catálogo organizado e separado por tipo, com navegação rápida e cartões interativos.");
        HBox chips = new HBox(8, chip("Todos", true), chip("Recentes", false), chip("Mais vistos", false), chip("Melhor avaliados", false));
        p.getChildren().add(chips);
        p.getChildren().add(grid(titles));
        swap(scroll(p));
    }

    private ToggleButton chip(String text, boolean selected) { ToggleButton b = new ToggleButton(text); if (selected) b.getStyleClass().add("chip-active"); return b; }

    private FlowPane grid(List<Title> titles) {
        FlowPane f = new FlowPane(14, 16); f.setPadding(new Insets(4));
        for (Title t : titles) f.getChildren().add(card(t));
        if (titles.isEmpty()) {
            Label empty = new Label("Nenhum título encontrado. Conecte uma fonte ou ajuste os filtros."); empty.getStyleClass().add("muted"); f.getChildren().add(empty);
        }
        return f;
    }

    /* ============================ EXPLORE ============================ */

    private void showExplore() { showExploreWithQuery(""); }

    private void showExploreWithQuery(String query) {
        VBox p = page("Explorar", "Busca local primeiro. O TMDB só é consultado em segundo plano quando necessário.");
        HBox searchRow = new HBox(8);
        TextField q = field("Digite um título, gênero ou nome..."); q.setText(query == null ? "" : query); HBox.setHgrow(q, Priority.ALWAYS);
        Button go = actionButton("Pesquisar", true); searchRow.getChildren().addAll(q, go);

        ComboBox<String> type = new ComboBox<>(FXCollections.observableArrayList("Tudo", "Filmes", "Séries", "Animes")); type.setValue("Tudo");
        ComboBox<String> sort = new ComboBox<>(FXCollections.observableArrayList("Relevância", "A-Z", "Ano", "Avaliação")); sort.setValue("Relevância");
        ComboBox<String> genre = new ComboBox<>(FXCollections.observableArrayList("Todos os gêneros", "Ação", "Aventura", "Comédia", "Drama", "Fantasia", "Ficção científica", "Mistério", "Romance", "Thriller")); genre.setValue("Todos os gêneros");
        ComboBox<String> year = new ComboBox<>(FXCollections.observableArrayList("Todos os anos", "2026", "2025", "2024", "2023", "2022", "2021")); year.setValue("Todos os anos");
        Slider rating = new Slider(0, 10, 0); rating.setPrefWidth(150); Label ratingLabel = new Label("Nota ≥ 0"); rating.valueProperty().addListener((o,a,b) -> ratingLabel.setText(String.format("Nota ≥ %.1f", b.doubleValue())));
        HBox filters = new HBox(8, type, sort, genre, year, ratingLabel, rating);
        FlowPane results = new FlowPane(14, 16); results.setPadding(new Insets(4));

        Runnable run = () -> {
            String term = q.getText().trim();
            List<Title> local = filterType(Services.TITLES.search(term), type.getValue());
            if (!"Todos os gêneros".equals(genre.getValue())) local = local.stream().filter(t -> (t.genres == null ? "" : t.genres).toLowerCase().contains(genre.getValue().toLowerCase())).toList();
            if (!"Todos os anos".equals(year.getValue())) local = local.stream().filter(t -> year.getValue().equals(t.year)).toList();
            local = local.stream().filter(t -> num(t.rating) >= rating.getValue()).collect(Collectors.toList());
            List<Title> finalLocal = sortTitles(local, sort.getValue());
            results.getChildren().setAll(finalLocal.stream().map(this::card).toList());
            if (finalLocal.isEmpty() && !term.isBlank() && Services.TMDB.configured()) {
                results.getChildren().add(progressHint("Consultando TMDB em segundo plano…"));
                Services.TMDB.search(term).thenAccept(items -> {
                    items.forEach(Services.TITLES::upsert);
                    Platform.runLater(() -> {
                        results.getChildren().clear();
                        filterType(items, type.getValue()).forEach(t -> results.getChildren().add(card(t)));
                    });
                });
            }
        };
        go.setOnAction(e -> run.run()); type.setOnAction(e -> run.run()); sort.setOnAction(e -> run.run()); genre.setOnAction(e -> run.run()); year.setOnAction(e -> run.run());
        p.getChildren().addAll(searchRow, filters, results);
        swap(scroll(p));
        run.run();
    }

    private List<Title> filterType(List<Title> titles, String k) {
        return titles.stream().filter(t -> switch (k) { case "Filmes" -> "movie".equals(t.type); case "Séries" -> "tv".equals(t.type); case "Animes" -> looksAnime(t); default -> true; }).toList();
    }

    private List<Title> sortTitles(List<Title> titles, String sort) {
        List<Title> out = new ArrayList<>(titles);
        if ("A-Z".equals(sort)) out.sort(Comparator.comparing(t -> t.name.toLowerCase()));
        else if ("Ano".equals(sort)) out.sort(Comparator.comparing((Title t) -> t.year, Comparator.reverseOrder()));
        else if ("Avaliação".equals(sort)) out.sort((a,b) -> Double.compare(num(b.rating), num(a.rating)));
        return out;
    }

    /* ============================ DETAILS ============================ */

    private void showDetails(Title t) {
        refreshUserState();
        VBox p = page("", "");
        HBox backRow = new HBox(8); backRow.setAlignment(Pos.CENTER_LEFT);
        Button back = iconBack("‹"); back.setOnAction(e -> showHome());
        Label breadcrumb = new Label(("tv".equals(t.type) ? "Séries" : "Filmes") + "  /  " + t.name); breadcrumb.getStyleClass().add("muted");
        backRow.getChildren().addAll(back, breadcrumb);

        StackPane poster = new StackPane();
        ImageView pv = new ImageView(); pv.setFitWidth(300); pv.setFitHeight(442); pv.setPreserveRatio(false); loadImage(pv, t.poster, 300, 442); poster.getChildren().add(pv);
        if (watched.getOrDefault(t.id, false) || t.watched) { Label done = new Label("✓ VISTO"); done.getStyleClass().add("watched-pill"); StackPane.setAlignment(done, Pos.BOTTOM_LEFT); StackPane.setMargin(done, new Insets(10)); poster.getChildren().add(done); }

        VBox info = new VBox(12); info.setMaxWidth(860);
        Label title = new Label(t.name); title.getStyleClass().add("detail-title");
        Label meta = new Label((t.year.isBlank() ? "" : t.year + "  •  ") + ("tv".equals(t.type) ? "Série" : "Filme") + "  •  ★ " + safeRating(t.rating)); meta.getStyleClass().add("muted");
        Label overview = new Label(t.overview); overview.setWrapText(true); overview.getStyleClass().add("detail-overview");
        HBox actions = new HBox(8);
        Button watchBtn = actionButton("▷   Assistir", true);
        Button fav = actionButton(favorites.getOrDefault(t.id, false) ? "♥   Na minha lista" : "＋   Minha lista", false);
        Button seen = actionButton(watched.getOrDefault(t.id, false) ? "✓   Assistido" : "○   Marcar como assistido", false);
        watchBtn.setOnAction(e -> openWatchChooser(t));
        fav.setOnAction(e -> { Services.TITLES.toggleFavorite(AppState.profile.id, t.id); showDetails(t); });
        seen.setOnAction(e -> { Services.TITLES.saveProgress(AppState.profile.id, t.id, 1, 1, !watched.getOrDefault(t.id, false)); showDetails(t); });
        actions.getChildren().addAll(watchBtn, fav, seen);
        info.getChildren().addAll(title, meta, overview, actions, detailTabs(t));

        HBox body = new HBox(24, poster, info); body.setAlignment(Pos.TOP_LEFT);
        p.getChildren().addAll(backRow, body);
        swap(scroll(p));
    }

    private TabPane detailTabs(Title t) {
        TabPane pane = new TabPane(); pane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE); pane.getStyleClass().add("detail-tabs");
        Tab about = new Tab("Visão geral", aboutNode(t));
        pane.getTabs().add(about);
        if ("tv".equals(t.type)) pane.getTabs().add(new Tab("Temporadas", seasonsView(t)));
        pane.getTabs().add(new Tab("Relacionados", relatedNode(t)));
        return pane;
    }

    private Node aboutNode(Title t) {
        VBox box = new VBox(10); box.getStyleClass().add("panel");
        box.getChildren().add(new Label("Gêneros: " + (t.genres.isBlank() ? "—" : t.genres)));
        box.getChildren().add(new Label("Fonte: " + t.source));
        box.getChildren().add(new Label("Runtime: " + (t.runtime > 0 ? t.runtime + " min" : "não informado")));
        return box;
    }

    private Node relatedNode(Title t) {
        List<Title> related = Services.TITLES.all().stream().filter(x -> x.id != t.id && sharesGenre(x, t)).limit(8).toList();
        return grid(related);
    }

    private VBox seasonsView(Title t) {
        VBox box = new VBox(10); box.getStyleClass().add("panel");
        ComboBox<Integer> seasons = new ComboBox<>();
        List<Integer> ids = Services.EPISODES.seasons(t.id);
        if (ids.isEmpty()) ids = List.of(1);
        seasons.getItems().setAll(ids); seasons.setValue(ids.get(0));
        FlowPane episodes = new FlowPane(10, 10);
        Runnable load = () -> loadSeasonAsync(t, seasons.getValue(), episodes);
        seasons.setOnAction(e -> load.run());
        box.getChildren().addAll(new HBox(8, new Label("Temporada"), seasons), episodes);
        load.run();
        return box;
    }

    private void loadSeasonAsync(Title t, int season, FlowPane target) {
        target.getChildren().setAll(progressHint("Carregando episódios…"));
        CompletableFuture<List<Episode>> future = CompletableFuture.supplyAsync(() -> Services.EPISODES.bySeason(t.id, season));
        future.thenAccept(local -> {
            if (!local.isEmpty()) { Platform.runLater(() -> renderEpisodes(t, local, target)); return; }
            long ext = safeLong(t.externalId);
            if (Services.TMDB.configured() && ext > 0) {
                Services.TMDB.episodes(ext, season).thenAccept(items -> {
                    items.forEach(e -> ensureEpisode(t, e));
                    Platform.runLater(() -> renderEpisodes(t, Services.EPISODES.bySeason(t.id, season), target));
                });
            } else Platform.runLater(() -> target.getChildren().setAll(new Label("Nenhum episódio local. Conecte o TMDB para importar a temporada.")));
        });
    }

    private void renderEpisodes(Title t, List<Episode> eps, FlowPane target) {
        Map<Long, Episode> progress = Services.EPISODES.loadProgress(AppState.profile.id);
        target.getChildren().clear();
        for (Episode e : eps) target.getChildren().add(episodeCard(t, e, progress.get(e.id)));
    }

    private VBox episodeCard(Title t, Episode e, Episode state) {
        VBox box = new VBox(6); box.setPrefWidth(290); box.getStyleClass().add("episode-card");
        HBox row = new HBox(8);
        ImageView iv = new ImageView(); iv.setFitWidth(112); iv.setFitHeight(64); iv.setPreserveRatio(false); loadImage(iv, e.still.isBlank() ? t.backdrop : e.still, 112, 64);
        VBox info = new VBox(3); HBox.setHgrow(info, Priority.ALWAYS);
        Label title = new Label(String.format("EP %02d • %s", e.episodeNumber, e.name)); title.setWrapText(true); title.getStyleClass().add("episode-name");
        Label duration = new Label((e.runtime > 0 ? e.runtime + " min" : "") + (state != null && state.watched ? " • ✓ Visto" : "")); duration.getStyleClass().add("muted-small");
        info.getChildren().addAll(title, duration); row.getChildren().addAll(iv, info);
        Button play = smallButton("▷ Assistir"); Button seen = smallButton(state != null && state.watched ? "✓ Visto" : "Marcar visto");
        play.setOnAction(x -> openEpisodePlayer(t, e));
        seen.setOnAction(x -> { Services.EPISODES.markWatched(AppState.profile.id, e.id, !(state != null && state.watched)); showDetails(t); });
        HBox actions = new HBox(6, play, seen);
        box.getChildren().addAll(row, actions);
        return box;
    }

    private void ensureEpisode(Title t, Episode e) {
        try (var p = Database.conn().prepareStatement("INSERT INTO episodes(title_id,season_number,episode_number,name,overview,still,runtime,stream_url) VALUES(?,?,?,?,?,?,?,?) ON CONFLICT(title_id,season_number,episode_number) DO UPDATE SET name=excluded.name,overview=excluded.overview,still=excluded.still,runtime=excluded.runtime")) {
            p.setLong(1, t.id); p.setInt(2, e.seasonNumber); p.setInt(3, e.episodeNumber); p.setString(4, e.name); p.setString(5, e.overview); p.setString(6, e.still); p.setInt(7, e.runtime); p.setString(8, e.streamUrl); p.executeUpdate();
        } catch (Exception ignored) {}
    }

    /* ============================ COLLECTIONS & STATS ============================ */

    private void showList() { showLibrary("Minha lista", Services.TITLES.favorites(AppState.profile.id)); }
    private void showContinue() { showLibrary("Continuar assistindo", Services.TITLES.progress(AppState.profile.id, false)); }

    private void showStats() {
        List<Title> watchedList = Services.TITLES.progress(AppState.profile.id, true);
        List<Title> continueList = Services.TITLES.progress(AppState.profile.id, false);
        VBox p = page("Estatísticas", "Visão local do que você assiste e dos gêneros que mais aparecem no seu histórico.");
        HBox stats = new HBox(12, stat("Assistidos", watchedList.size(), "✓"), stat("Continuando", continueList.size(), "▷"), stat("Na lista", Services.TITLES.favorites(AppState.profile.id).size(), "♡"));
        HBox charts = new HBox(16);
        PieChart pie = new PieChart(); pie.setTitle("Gêneros assistidos"); pie.setLegendVisible(true); pie.setPrefSize(450, 320);
        Map<String, Long> genres = watchedList.stream().flatMap(t -> Arrays.stream(splitGenres(t.genres))).filter(x -> !x.isBlank()).collect(Collectors.groupingBy(x -> x, Collectors.counting()));
        genres.entrySet().stream().sorted(Map.Entry.<String,Long>comparingByValue().reversed()).limit(8).forEach(e -> pie.getData().add(new PieChart.Data(e.getKey(), e.getValue())));
        BarChart<String, Number> bar = new BarChart<>(new CategoryAxis(), new NumberAxis()); bar.setTitle("Top títulos vistos"); bar.setLegendVisible(false); bar.setPrefSize(580, 320);
        XYChart.Series<String,Number> series = new XYChart.Series<>();
        watchedList.stream().limit(7).forEach(t -> series.getData().add(new XYChart.Data<>(trim(t.name, 18), 1)));
        bar.getData().add(series);
        charts.getChildren().addAll(pie, bar);
        p.getChildren().addAll(stats, charts, new Label("Histórico recente"), grid(watchedList.stream().limit(12).toList()));
        swap(scroll(p));
    }

    private VBox stat(String label, long value, String icon) { VBox b = new VBox(4, new Label(icon), new Label(label), new Label(String.valueOf(value))); b.getStyleClass().add("stat-card"); b.getChildren().get(0).getStyleClass().add("stat-icon"); b.getChildren().get(2).getStyleClass().add("stat-number"); return b; }

    /* ============================ SETTINGS ============================ */

    private void showSettings() {
        VBox p = page("Configurações", "Tudo dividido por categorias para evitar a antiga tela amontoada.");
        TabPane tabs = new TabPane(); tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE); tabs.getStyleClass().add("settings-tabs");
        tabs.getTabs().add(tab("Geral", generalSettings()));
        tabs.getTabs().add(tab("Aparência", appearanceSettings()));
        tabs.getTabs().add(tab("Reprodução", playbackSettings()));
        tabs.getTabs().add(tab("Legendas", subtitleSettings()));
        tabs.getTabs().add(tab("Teclado", keyboardSettings()));
        tabs.getTabs().add(tab("APIs", apiSettings()));
        tabs.getTabs().add(tab("Add-ons", addonsSettings()));
        tabs.getTabs().add(tab("Conta", accountSettings()));
        tabs.getTabs().add(tab("Backup", backupSettings()));
        p.getChildren().add(tabs); swap(scroll(p));
    }

    private Tab tab(String name, Node content) { Tab t = new Tab(name, content); t.setClosable(false); return t; }
    private VBox settingsBox() { VBox b = new VBox(12); b.setPadding(new Insets(14)); b.getStyleClass().add("panel"); return b; }

    private Node generalSettings() {
        VBox v = settingsBox();
        ComboBox<String> language = new ComboBox<>(FXCollections.observableArrayList("Português (Brasil)", "English", "Español", "Français", "Deutsch", "日本語"));
        language.setValue(ConfigService.get("app.language.label", "Português (Brasil)"));
        language.setOnAction(e -> ConfigService.set("app.language.label", language.getValue()));
        ComboBox<String> startup = new ComboBox<>(FXCollections.observableArrayList("Abrir na Home", "Abrir no último local", "Abrir no Continuar"));
        startup.setValue(ConfigService.get("startup.page", "Abrir na Home")); startup.setOnAction(e -> ConfigService.set("startup.page", startup.getValue()));
        CheckBox sync = new CheckBox("Permitir sincronização automática do catálogo"); sync.setSelected(ConfigService.bool("startup.autoSync", true)); sync.setOnAction(e -> ConfigService.setBool("startup.autoSync", sync.isSelected()));
        v.getChildren().addAll(new Label("Idioma do aplicativo"), language, new Label("Inicialização"), startup, sync, new Label("Privacidade: contas, progresso e listas ficam no banco local por instalação."));
        return v;
    }

    private Node appearanceSettings() {
        VBox v = settingsBox();
        ComboBox<String> theme = new ComboBox<>(FXCollections.observableArrayList("Nebula Dark", "AMOLED", "Light Glass")); theme.setValue(ConfigService.get("theme", "Nebula Dark"));
        theme.setOnAction(e -> { ConfigService.set("theme", theme.getValue()); applyTheme(theme.getValue()); });
        Slider glow = new Slider(0, 1, Double.parseDouble(ConfigService.get("appearance.glow", "0.45"))); Label glowLabel = new Label("Glow: " + Math.round(glow.getValue()*100) + "%"); glow.valueProperty().addListener((o,a,b) -> { glowLabel.setText("Glow: " + Math.round(b.doubleValue()*100) + "%"); ConfigService.set("appearance.glow", String.valueOf(b.doubleValue())); });
        CheckBox ambient = new CheckBox("Fundo ambiente com capas do catálogo"); ambient.setSelected(ConfigService.bool("appearance.ambient", true)); ambient.setOnAction(e -> ConfigService.setBool("appearance.ambient", ambient.isSelected()));
        v.getChildren().addAll(new Label("Tema"), theme, ambient, glowLabel, glow);
        return v;
    }

    private Node playbackSettings() {
        VBox v = settingsBox();
        ComboBox<String> player = new ComboBox<>(FXCollections.observableArrayList("Nebula Player", "Player externo do sistema")); player.setValue(ConfigService.get("player.mode", "Nebula Player")); player.setOnAction(e -> ConfigService.set("player.mode", player.getValue()));
        CheckBox autoplay = new CheckBox("Reproduzir automaticamente o próximo episódio"); autoplay.setSelected(ConfigService.bool("player.autoplay", true)); autoplay.setOnAction(e -> ConfigService.setBool("player.autoplay", autoplay.isSelected()));
        CheckBox binge = new CheckBox("Modo maratona • contagem regressiva de 10 segundos"); binge.setSelected(ConfigService.bool("player.binge", true)); binge.setOnAction(e -> ConfigService.setBool("player.binge", binge.isSelected()));
        TextField command = field("Comando do player externo (use {url})"); command.setText(ConfigService.get("player.external.command", "")); Button save = actionButton("Salvar player externo", false); save.setOnAction(e -> ConfigService.set("player.external.command", command.getText()));
        v.getChildren().addAll(new Label("Player padrão"), player, autoplay, binge, new Label("Comando opcional"), command, save);
        return v;
    }

    private Node subtitleSettings() {
        VBox v = settingsBox();
        ColorPicker color = new ColorPicker(parseColor(ConfigService.get("subtitle.color", "#FFFFFF"))); ColorPicker shadow = new ColorPicker(parseColor(ConfigService.get("subtitle.shadow", "#000000")));
        Slider size = new Slider(16, 42, Double.parseDouble(ConfigService.get("subtitle.size", "26"))); Label sizeLabel = new Label("Tamanho: " + Math.round(size.getValue()) + " px");
        size.valueProperty().addListener((o,a,b) -> { sizeLabel.setText("Tamanho: " + Math.round(b.doubleValue()) + " px"); ConfigService.set("subtitle.size", String.valueOf(Math.round(b.doubleValue()))); });
        color.setOnAction(e -> ConfigService.set("subtitle.color", toHex(color.getValue()))); shadow.setOnAction(e -> ConfigService.set("subtitle.shadow", toHex(shadow.getValue())));
        CheckBox bg = new CheckBox("Fundo semi-transparente"); bg.setSelected(ConfigService.bool("subtitle.background", true)); bg.setOnAction(e -> ConfigService.setBool("subtitle.background", bg.isSelected()));
        CheckBox bold = new CheckBox("Texto em negrito"); bold.setSelected(ConfigService.bool("subtitle.bold", true)); bold.setOnAction(e -> ConfigService.setBool("subtitle.bold", bold.isSelected()));
        v.getChildren().addAll(new Label("Cor do texto"), color, new Label("Cor da sombra"), shadow, sizeLabel, size, bg, bold, new Label("Legendas SRT/VTT podem ser carregadas no player."));
        return v;
    }

    private Node keyboardSettings() {
        VBox v = settingsBox();
        String[] keys = {"playpause", "seekBack", "seekForward", "fullscreen", "mute", "nextEpisode", "prevEpisode", "escape"};
        String[] labels = {"Play/Pause", "Voltar 10s", "Avançar 10s", "Tela cheia", "Mute", "Próximo episódio", "Episódio anterior", "Sair da tela cheia"};
        GridPane g = new GridPane(); g.setHgap(10); g.setVgap(10);
        for (int i=0;i<keys.length;i++) {
            TextField key = field("Tecla"); key.setText(ConfigService.get("key."+keys[i], defaultKey(keys[i]))); key.setPrefWidth(130);
            final String setting = "key."+keys[i]; key.setOnKeyPressed(e -> { key.setText(e.getCode().getName()); e.consume(); });
            Button save = smallButton("Salvar"); save.setOnAction(e -> ConfigService.set(setting, key.getText().trim().toUpperCase()));
            g.addRow(i, new Label(labels[i]), key, save);
        }
        v.getChildren().addAll(new Label("Clique em um campo e pressione a tecla desejada."), g);
        return v;
    }

    private Node apiSettings() {
        VBox root = settingsBox();
        Label hint = new Label("Salvar token apenas grava a credencial local. A sincronização só começa ao testar/atualizar."); hint.getStyleClass().add("muted");
        PasswordField token = new PasswordField(); token.setPromptText("TMDB Read Access Token"); token.setText(ConfigService.get("tmdb.token", ""));
        Button save = actionButton("Salvar token", true); Button test = actionButton("Testar conexão", false); Button sync = actionButton("Atualizar catálogo", false);
        Label msg = new Label(); msg.getStyleClass().add("muted");
        save.setOnAction(e -> { ConfigService.set("tmdb.token", token.getText().trim()); msg.setText("✓ Token salvo localmente. Nenhuma sincronização iniciada."); });
        test.setOnAction(e -> { test.setDisable(true); msg.setText("Testando em segundo plano…"); Services.TMDB.validate(token.getText()).whenComplete((ok,ex)->Platform.runLater(()->{test.setDisable(false); msg.setText(Boolean.TRUE.equals(ok) ? "✓ TMDB conectado" : "✕ Token inválido ou sem conexão");})); });
        sync.setOnAction(e -> refreshCatalogAsync(true));

        TextField name = field("Nome da API"); TextField url = field("URL JSON"); PasswordField apiToken = new PasswordField(); apiToken.setPromptText("Token opcional"); ComboBox<String> kind = new ComboBox<>(FXCollections.observableArrayList("json", "catalog")); kind.setValue("json");
        VBox list = new VBox(8); renderApiSources(list);
        Button add = actionButton("Adicionar API", false);
        add.setOnAction(e -> { try { if (name.getText().isBlank() || url.getText().isBlank()) throw new IllegalArgumentException("Nome e URL são obrigatórios."); ApiSource a = new ApiSource(); a.name=name.getText(); a.url=url.getText(); a.token=apiToken.getText(); a.kind=kind.getValue(); a.enabled=true; Services.APIS.upsert(a); renderApiSources(list); name.clear(); url.clear(); apiToken.clear(); msg.setText("✓ API adicionada sem iniciar chamadas."); } catch(Exception ex) { msg.setText(safeMessage(ex)); } });
        root.getChildren().addAll(new Label("TMDB"), token, new HBox(8, save, test, sync), hint, msg, new Separator(), new Label("Outras APIs • somente administrador deve configurar tokens"), name, url, apiToken, kind, add, list);
        return root;
    }

    private void renderApiSources(VBox list) {
        list.getChildren().clear();
        for (ApiSource a : Services.APIS.all()) {
            HBox row = new HBox(8); row.getStyleClass().add("panel");
            Label n = new Label(a.name); Label u = new Label(a.url); u.getStyleClass().add("muted-small");
            Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);
            CheckBox enabled = new CheckBox("Ativa"); enabled.setSelected(a.enabled); enabled.setOnAction(e -> { a.enabled=enabled.isSelected(); Services.APIS.upsert(a); });
            Button test = smallButton("Testar"); test.setOnAction(e -> { test.setDisable(true); Services.APIS.test(a).whenComplete((ok,ex)->Platform.runLater(()->{test.setDisable(false); new Alert(Boolean.TRUE.equals(ok) ? Alert.AlertType.INFORMATION : Alert.AlertType.WARNING, Boolean.TRUE.equals(ok) ? "API respondeu corretamente." : "Falha ao consultar a API.").showAndWait(); })); });
            Button sync = smallButton("Sincronizar"); sync.setOnAction(e -> { sync.setDisable(true); Services.CUSTOM.fetch(a).thenAccept(items -> { items.forEach(Services.TITLES::upsert); Platform.runLater(() -> { sync.setDisable(false); u.setText(a.url + " • " + items.size() + " itens"); }); }); });
            Button del = smallButton("Excluir"); del.setOnAction(e -> { Services.APIS.delete(a.id); renderApiSources(list); });
            row.getChildren().addAll(n, u, sp, enabled, test, sync, del); list.getChildren().add(row);
        }
    }

    private Node addonsSettings() {
        VBox v = settingsBox(); Button open = actionButton("Abrir gerenciador de Add-ons", true); open.setOnAction(e -> showAddons());
        v.getChildren().addAll(new Label("Catálogos externos podem ser adicionados por manifesto. O Nebula mantém cada fonte separada do catálogo local."), open);
        return v;
    }

    private Node accountSettings() {
        VBox v = settingsBox();
        Button profiles = actionButton("Gerenciar perfis", false); profiles.setOnAction(e -> showProfileManager());
        Button logout = actionButton("Sair da conta", false); logout.setOnAction(e -> { Services.ACCOUNTS.logout(); AppState.account=null; AppState.profile=null; showLogin(); });
        v.getChildren().addAll(new Label("Conta: " + AppState.account.username), new Label("E-mail: " + AppState.account.email), new Label(AppState.account.admin ? "Administrador local" : "Conta padrão"), profiles, logout);
        return v;
    }

    private Node backupSettings() {
        VBox v = settingsBox(); Button export = actionButton("Exportar backup JSON", true); Button importBtn = actionButton("Importar backup JSON", false);
        export.setOnAction(e -> exportBackup()); importBtn.setOnAction(e -> importBackup());
        v.getChildren().addAll(export, importBtn, new Label("O backup não inclui senhas ou tokens de API.")); return v;
    }

    /* ============================ PROFILES ============================ */

    private void showProfileManager() {
        VBox p = page("Perfis", "Cada perfil possui histórico, favoritos e recomendações independentes.");
        FlowPane grid = new FlowPane(14, 14);
        for (Profile pr : Services.ACCOUNTS.profiles()) {
            VBox box = new VBox(8); box.setPrefWidth(170); box.setAlignment(Pos.CENTER); box.getStyleClass().add("profile-card");
            ImageView iv = new ImageView(); iv.setFitWidth(92); iv.setFitHeight(92); setAvatar(iv, pr.avatar, 92, 92);
            Label name = new Label(pr.name); Button use = actionButton(pr.id == AppState.profile.id ? "✓ Atual" : "Usar perfil", true);
            use.setOnAction(e -> { if (!pr.pin.isBlank()) { TextInputDialog d=new TextInputDialog(); d.setTitle("PIN"); d.setHeaderText("Perfil protegido"); Optional<String> pin=d.showAndWait(); if(pin.isEmpty() || !pin.get().equals(pr.pin)) return; } AppState.profile=pr; frame.setLeft(buildSidebar()); showHome(); });
            box.getChildren().addAll(iv, name, use); grid.getChildren().add(box);
        }
        Button add = actionButton("＋ Novo perfil", true); add.setOnAction(e -> createProfileDialog());
        p.getChildren().addAll(grid, add); swap(scroll(p));
    }

    private void createProfileDialog() {
        Dialog<ButtonType> d = new Dialog<>(); d.initOwner(NebulaApp.STAGE); d.setTitle("Novo perfil");
        TextField name = field("Nome"); TextField pin = field("PIN opcional"); CheckBox child = new CheckBox("Perfil infantil");
        d.getDialogPane().setContent(new VBox(10, name, pin, child)); d.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        d.showAndWait().ifPresent(r -> { if (r == ButtonType.OK && !name.getText().isBlank()) { Services.ACCOUNTS.createProfile(name.getText(), "profile-01.png", child.isSelected(), pin.getText()); showProfileManager(); } });
    }

    /* ============================ ADD-ONS ============================ */

    private void showAddons() {
        VBox p = page("Add-ons", "Gerencie fontes externas por manifesto sem misturar o catálogo principal.");
        HBox input = new HBox(8); TextField url = field("URL do manifest.json"); HBox.setHgrow(url, Priority.ALWAYS); Button add = actionButton("Adicionar", true); input.getChildren().addAll(url, add);
        Label message = new Label(); message.getStyleClass().add("muted"); VBox list = new VBox(10); renderAddons(list);
        add.setOnAction(e -> { add.setDisable(true); message.setText("Validando em segundo plano…"); Services.ADDONS.inspect(url.getText()).whenComplete((a,ex)->Platform.runLater(()->{ add.setDisable(false); if(ex!=null){message.setText("✕ Manifesto inválido ou indisponível.");return;} message.setText("✓ "+a.name+" instalado."); url.clear(); renderAddons(list); })); });
        p.getChildren().addAll(input, message, list); swap(scroll(p));
    }

    private void renderAddons(VBox list) {
        list.getChildren().clear();
        for (Addon a : Services.ADDONS.all()) {
            HBox row = new HBox(10); row.getStyleClass().add("panel");
            VBox info = new VBox(3); Label n=new Label(a.name); Label d=new Label(a.description); d.setWrapText(true); d.getStyleClass().add("muted"); info.getChildren().addAll(n,d);
            Region sp=new Region(); HBox.setHgrow(sp,Priority.ALWAYS); Label state=new Label(a.enabled?"ATIVO":"DESATIVADO"); state.getStyleClass().add("muted-small");
            Button sync=smallButton("Sincronizar"); sync.setOnAction(e->{sync.setDisable(true); Services.ADDONS.catalog(a).thenAccept(items->{items.forEach(Services.TITLES::upsert);Platform.runLater(()->{sync.setDisable(false);state.setText("ATIVO • "+items.size()+" itens");});});});
            Button remove=smallButton("Remover"); remove.setOnAction(e->{Services.ADDONS.remove(a.id);renderAddons(list);});
            row.getChildren().addAll(info,sp,state,sync,remove); list.getChildren().add(row);
        }
    }

    /* ============================ ADMIN ============================ */

    private void showAdmin() {
        VBox p = page("Administração", "CRUD do catálogo, fontes, reprodução e manutenção do conteúdo local.");
        GridPane form = new GridPane(); form.setHgap(9); form.setVgap(9); form.getStyleClass().add("panel"); form.setPadding(new Insets(14));
        TextField name=field("Título"); ComboBox<String> type=new ComboBox<>(FXCollections.observableArrayList("movie","tv")); type.setValue("movie"); TextField poster=field("Poster URL"); TextField backdrop=field("Backdrop URL"); TextField stream=field("URL de vídeo / arquivo local"); TextField year=field("Ano"); TextField rating=field("Avaliação"); TextField genres=field("Gêneros"); TextArea overview=new TextArea(); overview.setPromptText("Sinopse"); overview.setPrefRowCount(5);
        Button save=actionButton("Adicionar título", true); VBox list=new VBox(8);
        form.addRow(0,new Label("Título"),name); form.addRow(1,new Label("Tipo"),type); form.addRow(2,new Label("Poster"),poster); form.addRow(3,new Label("Backdrop"),backdrop); form.addRow(4,new Label("Vídeo"),stream); form.addRow(5,new Label("Ano"),year); form.addRow(6,new Label("Nota"),rating); form.addRow(7,new Label("Gêneros"),genres); form.addRow(8,new Label("Sinopse"),overview); form.addRow(9,save);
        save.setOnAction(e->{ try{ Title t=new Title(0,name.getText().trim(),type.getValue(),poster.getText().trim(),backdrop.getText().trim(),overview.getText().trim(),genres.getText().trim(),year.getText().trim(),rating.getText().trim(),"admin"); t.streamUrl=stream.getText().trim(); Services.TITLES.upsert(t); renderAdmin(list); } catch(Exception ex){new Alert(Alert.AlertType.ERROR,safeMessage(ex)).showAndWait();} });
        renderAdmin(list); p.getChildren().addAll(form,new Separator(),list); swap(scroll(p));
    }

    private void renderAdmin(VBox list) {
        list.getChildren().clear();
        for (Title t : Services.TITLES.all()) {
            HBox row=new HBox(8); row.getStyleClass().add("panel"); Label n=new Label(t.name); Label src=new Label(t.source); src.getStyleClass().add("muted-small"); Region sp=new Region(); HBox.setHgrow(sp,Priority.ALWAYS); Button edit=smallButton("Editar"); Button del=smallButton("Excluir");
            edit.setOnAction(e -> editTitleDialog(t,list)); del.setOnAction(e -> {Services.TITLES.delete(t.id);renderAdmin(list);}); row.getChildren().addAll(n,src,sp,edit,del); list.getChildren().add(row);
        }
    }

    private void editTitleDialog(Title t, VBox list) {
        Dialog<ButtonType> d=new Dialog<>(); d.initOwner(NebulaApp.STAGE); d.setTitle("Editar título");
        TextField name=field("Título"); name.setText(t.name); ComboBox<String> type=new ComboBox<>(FXCollections.observableArrayList("movie","tv")); type.setValue(t.type); TextField poster=field("Poster"); poster.setText(t.poster); TextField backdrop=field("Backdrop"); backdrop.setText(t.backdrop); TextField stream=field("Vídeo"); stream.setText(t.streamUrl); TextField year=field("Ano"); year.setText(t.year); TextField rating=field("Avaliação"); rating.setText(t.rating); TextField genres=field("Gêneros"); genres.setText(t.genres); TextArea overview=new TextArea(t.overview); overview.setPrefRowCount(5);
        d.getDialogPane().setContent(new VBox(8,name,type,poster,backdrop,stream,year,rating,genres,overview)); d.getDialogPane().getButtonTypes().addAll(ButtonType.OK,ButtonType.CANCEL);
        d.showAndWait().ifPresent(r->{ if(r==ButtonType.OK){ t.name=name.getText();t.type=type.getValue();t.poster=poster.getText();t.backdrop=backdrop.getText();t.streamUrl=stream.getText();t.year=year.getText();t.rating=rating.getText();t.genres=genres.getText();t.overview=overview.getText();Services.TITLES.upsert(t);renderAdmin(list); }});
    }

    /* ============================ AI ============================ */

    private void showAi() {
        Dialog<Void> d=new Dialog<>(); d.initOwner(NebulaApp.STAGE); d.setTitle("Nebula IA"); d.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        VBox box=new VBox(10); TextArea out=new TextArea(); out.setEditable(false); out.setWrapText(true); Button analyze=actionButton("Analisar meu perfil",true);
        analyze.setOnAction(e->{ List<Title> watchedList=Services.TITLES.progress(AppState.profile.id,true); out.setText(Services.AI.recommend(watchedList,Services.TITLES.all())); });
        box.getChildren().addAll(new Label("A IA local usa somente seu catálogo e histórico; não envia seu perfil para um serviço externo."), analyze, out); d.getDialogPane().setContent(box); d.showAndWait();
    }

    /* ============================ WATCH / PLAYER ============================ */

    private void openWatchChooser(Title t) {
        if (t == null) return;
        if ("tv".equals(t.type)) { openEpisodeChooser(t); return; }
        List<PlayerSource> sources = new ArrayList<>();
        if (t.streamUrl != null && !t.streamUrl.isBlank()) sources.add(new PlayerSource("Nebula Player • fonte do título", t.streamUrl, false));
        if (ConfigService.get("player.external.command", "").isBlank()) {
            // não cria falsa fonte externa quando ela não está configurada
        } else if (!t.streamUrl.isBlank()) sources.add(new PlayerSource("Player externo", t.streamUrl, true));
        if (sources.isEmpty()) { FileChooser fc=new FileChooser(); fc.setTitle("Escolher vídeo"); File f=fc.showOpenDialog(NebulaApp.STAGE); if(f!=null) sources.add(new PlayerSource("Arquivo local", f.toURI().toString(), false)); }
        if (sources.isEmpty()) { new Alert(Alert.AlertType.INFORMATION,"Nenhuma fonte de vídeo configurada. Use o Admin ou escolha um arquivo local.").showAndWait(); return; }
        ChoiceDialog<PlayerSource> d=new ChoiceDialog<>(sources.get(0),sources); d.setTitle("Escolha como assistir"); d.setHeaderText(t.name); d.setContentText("Fonte / player:"); d.showAndWait().ifPresent(s -> openPlayer(t,s,null));
    }

    private void openEpisodeChooser(Title t) {
        List<Integer> seasons=Services.EPISODES.seasons(t.id); if(seasons.isEmpty()) seasons=List.of(1);
        ComboBox<Integer> season=new ComboBox<>(FXCollections.observableArrayList(seasons)); season.setValue(seasons.get(0)); ListView<Episode> list=new ListView<>(); list.setPrefHeight(320); HBox.setHgrow(list,Priority.ALWAYS);
        Runnable refresh=()->list.getItems().setAll(Services.EPISODES.bySeason(t.id,season.getValue())); season.setOnAction(e->refresh.run()); refresh.run();
        Dialog<ButtonType> dialog=new Dialog<>(); dialog.initOwner(NebulaApp.STAGE); dialog.setTitle("Escolher episódio • "+t.name); dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK,ButtonType.CANCEL); dialog.getDialogPane().setContent(new VBox(10,new HBox(8,new Label("Temporada"),season),list));
        dialog.showAndWait().ifPresent(r->{if(r==ButtonType.OK&&list.getSelectionModel().getSelectedItem()!=null)openEpisodePlayer(t,list.getSelectionModel().getSelectedItem());});
    }

    private void openEpisodePlayer(Title t, Episode e) {
        if (e.streamUrl == null || e.streamUrl.isBlank()) {
            FileChooser fc=new FileChooser(); fc.setTitle("Escolher vídeo do episódio"); File f=fc.showOpenDialog(NebulaApp.STAGE); if(f!=null) openPlayer(t,new PlayerSource("Arquivo local",f.toURI().toString(),false),e);
        } else openPlayer(t,new PlayerSource("Episódio",e.streamUrl,false),e);
    }

    private void openPlayer(Title t, PlayerSource source, Episode ep) {
        if (source.external()) { openExternalPlayer(source.url()); return; }
        Stage stage=new Stage(StageStyle.DECORATED); stage.initOwner(NebulaApp.STAGE); stage.setTitle("Nebula Player • "+t.name);
        BorderPane root=new BorderPane(); root.getStyleClass().add("player-root");
        StackPane surface=new StackPane(); surface.getStyleClass().add("video-surface");
        MediaPlayer player;
        try { player=new MediaPlayer(new Media(source.url())); } catch(Exception ex){new Alert(Alert.AlertType.ERROR,"Não foi possível abrir o vídeo. Tente outra fonte/player.").showAndWait();return;}
        MediaView view=new MediaView(player); view.setPreserveRatio(true); view.fitWidthProperty().bind(surface.widthProperty()); view.fitHeightProperty().bind(surface.heightProperty());
        Label subtitle=new Label(); subtitle.getStyleClass().add("player-subtitle"); subtitle.setVisible(false); StackPane.setAlignment(subtitle,Pos.BOTTOM_CENTER); StackPane.setMargin(subtitle,new Insets(0,0,55,0));
        applySubtitleStyle(subtitle); surface.getChildren().addAll(view,subtitle);
        Button play=smallButton("▶"); Button back10=smallButton("↶ 10s"); Button forward10=smallButton("10s ↷"); Button mute=smallButton("🔊"); Button cc=smallButton("CC"); Button audio=smallButton("Áudio"); Button full=smallButton("⛶"); Button next=smallButton("Próximo"); Slider seek=new Slider(); Slider volume=new Slider(0,1,0.85); Label time=new Label("00:00 / 00:00"); Region spacer=new Region(); HBox.setHgrow(seek,Priority.ALWAYS); HBox.setHgrow(spacer,Priority.NEVER);
        HBox controls=new HBox(7,play,back10,forward10,mute,volume,cc,audio,seek,time,spacer,next,full); controls.getStyleClass().add("player-controls");
        play.setOnAction(e->{if(player.getStatus()==MediaPlayer.Status.PLAYING)player.pause();else player.play();}); back10.setOnAction(e->player.seek(Duration.seconds(Math.max(0,player.getCurrentTime().toSeconds()-10)))); forward10.setOnAction(e->player.seek(Duration.seconds(Math.min(player.getTotalDuration().toSeconds(),player.getCurrentTime().toSeconds()+10)))); mute.setOnAction(e->player.setMute(!player.isMute())); volume.valueProperty().addListener((o,a,b)->player.setVolume(b.doubleValue())); full.setOnAction(e->stage.setFullScreen(!stage.isFullScreen())); cc.setOnAction(e->chooseSubtitle(stage,player,subtitle)); audio.setOnAction(e->showAudioInfo(player));
        next.setOnAction(e->showNextEpisode(t,ep,stage));
        player.setOnReady(()->{ seek.setMax(Math.max(1,player.getTotalDuration().toSeconds())); player.play(); });
        player.currentTimeProperty().addListener((o,a,b)->{if(!seek.isValueChanging())seek.setValue(b.toSeconds());time.setText(fmt(b.toSeconds())+" / "+fmt(player.getTotalDuration().toSeconds()));});
        seek.valueProperty().addListener((o,a,b)->{if(seek.isValueChanging())player.seek(Duration.seconds(b.doubleValue()));});
        root.setCenter(surface); root.setBottom(controls);
        Scene scene=new Scene(root,1320,760); stage.setScene(scene); installPlayerKeys(scene,player,stage,ep); stage.show();
        stage.setOnCloseRequest(e->{savePlayerProgress(player,t,ep); player.dispose();});
        player.setOnEndOfMedia(()->{ savePlayerProgress(player,t,ep); if(ep!=null && ConfigService.bool("player.binge",true)) showNextEpisode(t,ep,stage); });
        player.setOnError(()->Platform.runLater(()->new Alert(Alert.AlertType.ERROR,"Falha no player: "+String.valueOf(player.getError())).showAndWait()));
    }

    private void showAudioInfo(MediaPlayer player) {
        String tracks = player.getMedia().getTracks().isEmpty() ? "O arquivo não expôs trilhas de áudio selecionáveis ao JavaFX." : player.getMedia().getTracks().stream().map(Track::getName).collect(Collectors.joining("\n"));
        new Alert(Alert.AlertType.INFORMATION,"Trilhas detectadas:\n\n"+tracks+"\n\nA troca efetiva de faixa depende do formato e do player utilizado.").showAndWait();
    }

    private void showNextEpisode(Title t, Episode current, Stage stage) {
        if (current == null) return;
        List<Integer> seasons=Services.EPISODES.seasons(t.id); for(int s:seasons){ List<Episode> eps=Services.EPISODES.bySeason(t.id,s); for(int i=0;i<eps.size();i++){ if(eps.get(i).id==current.id && i+1<eps.size()){ stage.close(); openEpisodePlayer(t,eps.get(i+1)); return; } }}
    }

    private void chooseSubtitle(Stage stage, MediaPlayer player, Label label) {
        FileChooser fc=new FileChooser(); fc.setTitle("Carregar legenda"); fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Legendas","*.srt","*.vtt")); File f=fc.showOpenDialog(stage); if(f!=null) SubtitleEngine.attach(f,player,label);
    }

    private void applySubtitleStyle(Label label) {
        String color=ConfigService.get("subtitle.color","#FFFFFF"); String shadow=ConfigService.get("subtitle.shadow","#000000"); String size=ConfigService.get("subtitle.size","26"); boolean bg=ConfigService.bool("subtitle.background",true); boolean bold=ConfigService.bool("subtitle.bold",true);
        label.setTextFill(parseColor(color)); label.setStyle("-fx-font-size:"+size+"px;-fx-font-weight:"+(bold?"900":"500")+";-fx-background-color:"+(bg?"rgba(0,0,0,.58)":"transparent")+";-fx-effect:dropshadow(gaussian,"+toHex(parseColor(shadow))+",4,.75,0,1);-fx-padding:5 12;");
    }

    private void installPlayerKeys(Scene scene, MediaPlayer player, Stage stage, Episode ep) {
        scene.setOnKeyPressed(e->{ String key=e.getCode().getName().toUpperCase(); if(matches("playpause",key,"SPACE")){if(player.getStatus()==MediaPlayer.Status.PLAYING)player.pause();else player.play();} else if(matches("seekBack",key,"LEFT")){player.seek(Duration.seconds(Math.max(0,player.getCurrentTime().toSeconds()-10)));} else if(matches("seekForward",key,"RIGHT")){player.seek(Duration.seconds(player.getCurrentTime().toSeconds()+10));} else if(matches("fullscreen",key,"F")){stage.setFullScreen(!stage.isFullScreen());} else if(matches("mute",key,"M")){player.setMute(!player.isMute());} else if(matches("escape",key,"ESCAPE")&&stage.isFullScreen()){stage.setFullScreen(false);} });
    }

    private boolean matches(String action,String actual,String fallback){return actual.equalsIgnoreCase(ConfigService.get("key."+action,fallback));}
    private void savePlayerProgress(MediaPlayer p, Title t, Episode e){try{int sec=(int)p.getCurrentTime().toSeconds();int dur=(int)p.getTotalDuration().toSeconds();if(e!=null)Services.EPISODES.saveProgress(AppState.profile.id,e.id,sec,dur);else Services.TITLES.saveProgress(AppState.profile.id,t.id,sec,dur,dur>0&&sec>=dur-5);}catch(Exception ignored){}}
    private void openExternalPlayer(String url){String command=ConfigService.get("player.external.command","").trim();try{if(command.contains("{url}")){List<String>parts=Arrays.stream(command.split("\\s+")).map(x->x.replace("{url}",url)).toList();new ProcessBuilder(parts).start();}else if(!command.isBlank())new ProcessBuilder(command,url).start();else if(Desktop.isDesktopSupported())Desktop.getDesktop().browse(URI.create(url));}catch(Exception e){new Alert(Alert.AlertType.ERROR,"Falha ao abrir player externo: "+e.getMessage()).showAndWait();}}

    /* ============================ CATALOG SYNC ============================ */

    private void refreshCatalogAsync(boolean manual) {
        if (!Services.TMDB.configured()) { status.setText("TMDB • configure em Configurações"); return; }
        if (!refreshing.compareAndSet(false,true)) { status.setText("TMDB • atualização já em andamento"); return; }
        status.setText("TMDB • atualizando em segundo plano…");
        CompletableFuture<Void> f=CompletableFuture.allOf(Services.TMDB.trending().thenAccept(this::saveTitles),Services.TMDB.popularMovies().thenAccept(this::saveTitles),Services.TMDB.popularTv().thenAccept(this::saveTitles),Services.TMDB.popularAnime().thenAccept(this::saveTitles));
        f.orTimeout(20,java.util.concurrent.TimeUnit.SECONDS).whenComplete((v,e)->Platform.runLater(()->{refreshing.set(false);status.setText(e==null?"TMDB • catálogo atualizado":"TMDB • falha, mantendo cache/local");if(manual&&e==null)showHome();}));
    }
    private void saveTitles(List<Title> list){for(Title t:list){try{Services.TITLES.upsert(t);}catch(Exception ignored){}}}

    /* ============================ BACKUP ============================ */

    private void exportBackup(){try{Files.createDirectories(Path.of("data","exports"));Map<String,Object> root=new LinkedHashMap<>();root.put("titles",Services.TITLES.all());root.put("exportedAt",java.time.Instant.now().toString());new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(Path.of("data","exports","nebulatv-backup.json").toFile(),root);new Alert(Alert.AlertType.INFORMATION,"Backup exportado em data/exports/nebulatv-backup.json").showAndWait();}catch(Exception e){new Alert(Alert.AlertType.ERROR,safeMessage(e)).showAndWait();}}
    private void importBackup(){FileChooser fc=new FileChooser();fc.setTitle("Importar backup");File f=fc.showOpenDialog(NebulaApp.STAGE);if(f==null)return;try{JsonNode root=new ObjectMapper().readTree(f);JsonNode titles=root.path("titles");if(!titles.isArray())throw new IllegalArgumentException("Arquivo sem a coleção 'titles'.");for(JsonNode n:titles){Title t=new ObjectMapper().treeToValue(n,Title.class);Services.TITLES.upsert(t);}showExplore();}catch(Exception e){new Alert(Alert.AlertType.ERROR,"Backup inválido: "+safeMessage(e)).showAndWait();}}

    /* ============================ UTILS ============================ */

    private void refreshUserState(){watched.clear();favorites.clear();progress.clear();if(AppState.profile==null)return;for(Title t:Services.TITLES.progress(AppState.profile.id,true)){watched.put(t.id,true);progress.put(t.id,t.progress);}for(Title t:Services.TITLES.progress(AppState.profile.id,false))progress.put(t.id,t.progress);for(Title t:Services.TITLES.favorites(AppState.profile.id))favorites.put(t.id,true);}
    private void loadImage(ImageView v,String url,double w,double h){if(url==null||url.isBlank()){v.setImage(null);return;}v.setOpacity(0);Services.IMAGES.load(url,w,h).thenAccept(img->Platform.runLater(()->{if(img!=null){v.setImage(img);FadeTransition f=new FadeTransition(Duration.millis(360),v);f.setToValue(1);f.play();} }));}
    private void transitionImage(ImageView v,String url,double w,double h){if(url==null||url.isBlank()){v.setImage(null);return;}Services.IMAGES.load(url,w,h).thenAccept(img->Platform.runLater(()->{if(img==null)return;ImageView next=new ImageView(img);next.setPreserveRatio(true);next.setSmooth(true);next.setOpacity(0); if(v.getParent() instanceof Pane p){ int idx=p.getChildren().indexOf(v); p.getChildren().add(idx+1,next); next.fitWidthProperty().bind(v.fitWidthProperty()); next.fitHeightProperty().bind(v.fitHeightProperty()); FadeTransition in=new FadeTransition(Duration.millis(500),next); FadeTransition out=new FadeTransition(Duration.millis(500),v); in.setToValue(1);out.setToValue(0);in.play();out.play();out.setOnFinished(e->{v.setImage(img);v.setOpacity(1);p.getChildren().remove(next);});}}));}
    private void loadingSkeleton(StackPane p){Region r=new Region();r.getStyleClass().add("loading-skeleton");StackPane.setAlignment(r,Pos.CENTER);p.getChildren().add(r);PauseTransition wait=new PauseTransition(Duration.millis(120));wait.setOnFinished(e->p.getChildren().remove(r));wait.play();}
    private Node progressHint(String s){Label l=new Label(s);l.getStyleClass().add("muted");return l;}
    private boolean looksAnime(Title t){String g=(t.genres==null?"":t.genres).toLowerCase();return "tv".equals(t.type)&& (g.contains("anima")||g.contains("anime"));}
    private boolean sharesGenre(Title a,Title b){Set<String> ga=new HashSet<>(Arrays.asList(splitGenres(a.genres)));Set<String> gb=new HashSet<>(Arrays.asList(splitGenres(b.genres)));ga.retainAll(gb);return !ga.isEmpty();}
    private String[] splitGenres(String s){return (s==null?"":s.replace("["," ").replace("]"," ").replace("\"","").split("•|,|\\|/"));}
    private double score(Title t){return num(t.rating)+(!t.backdrop.isBlank()?0.1:0);}
    private double num(String s){try{return Double.parseDouble(s);}catch(Exception e){return 0;}}
    private String safeRating(String s){return s==null||s.isBlank()?"—":s;}
    private long safeLong(String s){try{return Long.parseLong(s);}catch(Exception e){return 0;}}
    private String trim(String s,int n){return s!=null&&s.length()>n?s.substring(0,n-1)+"…":s;}
    private String safeMessage(Exception e){return e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();}
    private Color parseColor(String s){try{return Color.web(s);}catch(Exception e){return Color.WHITE;}}
    private String toHex(Color c){return String.format("#%02X%02X%02X",Math.round((float)c.getRed()*255),Math.round((float)c.getGreen()*255),Math.round((float)c.getBlue()*255));}
    private String fmt(double sec){if(Double.isNaN(sec)||Double.isInfinite(sec))return "00:00";int s=(int)Math.max(0,sec);return String.format("%02d:%02d",s/60,s%60);}
    private Label errorLabel(){Label l=new Label();l.getStyleClass().add("danger");l.setWrapText(true);return l;}
    private TextField field(String prompt){TextField f=new TextField();f.setPromptText(prompt);f.setPrefHeight(42);return f;}
    private PasswordField password(String prompt){PasswordField p=new PasswordField();p.setPromptText(prompt);p.setPrefHeight(42);return p;}
    private Button actionButton(String text,boolean primary){Button b=new Button(text);b.getStyleClass().add(primary?"primary":"secondary");b.setMaxWidth(Double.MAX_VALUE);return b;}
    private Button smallButton(String text){Button b=new Button(text);b.getStyleClass().add("small-button");return b;}
    private Button iconBack(String text){Button b=new Button(text);b.getStyleClass().add("icon-back");b.setTooltip(new Tooltip("Voltar"));return b;}
    private String defaultKey(String key){return switch(key){case "playpause"->"SPACE";case "seekBack"->"LEFT";case "seekForward"->"RIGHT";case "fullscreen"->"F";case "mute"->"M";case "nextEpisode"->"N";case "prevEpisode"->"P";default->"ESCAPE";};}
    private void applyTheme(String theme){root.getStyleClass().removeAll("theme-amoled","theme-light");if("AMOLED".equals(theme))root.getStyleClass().add("theme-amoled");if("Light Glass".equals(theme))root.getStyleClass().add("theme-light");}
    private void setAvatarGraphic(ToggleButton b,String name,double w,double h){ImageView v=new ImageView();setAvatar(v,name,w,h);b.setGraphic(v);}
    private void setAvatar(ImageView v,String name,double w,double h){try{v.setImage(new Image(getClass().getResource("/images/profiles/"+name).toExternalForm(),w,h,true,true));}catch(Exception ignored){}}
    private void setAvatar(ImageView v,String name){setAvatar(v,name,96,96);}
}

final class SubtitleEngine {
    private SubtitleEngine() {}
    static void attach(File file, MediaPlayer player, Label label) {
        try {
            List<Sub> subs = parse(Files.readString(file.toPath(), StandardCharsets.UTF_8));
            label.setVisible(true);
            AnimationTimer timer = new AnimationTimer() {
                @Override public void handle(long now) {
                    double sec = player.getCurrentTime().toSeconds();
                    Sub current = null;
                    for (Sub s : subs) if (sec >= s.start && sec <= s.end) { current = s; break; }
                    label.setText(current == null ? "" : current.text);
                }
            };
            timer.start();
            player.setOnEndOfMedia(timer::stop);
        } catch (Exception e) {
            new Alert(Alert.AlertType.ERROR,"Não foi possível carregar a legenda: "+e.getMessage()).showAndWait();
        }
    }
    private static List<Sub> parse(String text) {
        String normalized = text.replace("\r", ""); String[] blocks = normalized.split("\\n\\s*\\n"); List<Sub> out = new ArrayList<>();
        for(String block:blocks){String[] lines=block.split("\\n");if(lines.length<2)continue;int idx=lines[0].contains("-->")?0:1;if(lines.length<=idx)continue;String[] times=lines[idx].split("-->");if(times.length<2)continue;double a=time(times[0].trim()),b=time(times[1].trim());String body=Arrays.stream(lines).skip(idx+1).collect(Collectors.joining("\n"));out.add(new Sub(a,b,body));}return out;
    }
    private static double time(String s){String[] p=s.replace(',','.').split(":");try{if(p.length==3)return Integer.parseInt(p[0])*3600+Integer.parseInt(p[1])*60+Double.parseDouble(p[2]);if(p.length==2)return Integer.parseInt(p[0])*60+Double.parseDouble(p[1]);}catch(Exception ignored){}return 0;}
    private record Sub(double start,double end,String text){}
}
