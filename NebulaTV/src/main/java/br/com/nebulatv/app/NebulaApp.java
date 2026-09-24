package br.com.nebulatv.app;

import br.com.nebulatv.dao.Database;
import br.com.nebulatv.service.ConfigService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import br.com.nebulatv.ui.NebulaShell;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

public final class NebulaApp extends Application {
    public static Stage STAGE;
    @Override public void start(Stage stage){
        STAGE=stage;
        Database.init();
        loadApiFile();
        Services.EPISODES.seedIfMissing();
        NebulaShell shell=new NebulaShell();
        Scene scene=new Scene(shell.root(),1500,940);
        scene.getStylesheets().add(getClass().getResource("/css/nebula.css").toExternalForm());
        stage.setTitle("Nebula TV");
        Image icon=new Image(getClass().getResource("/images/nebula-brand.png").toExternalForm(),64,64,true,true);
        stage.getIcons().add(icon);
        stage.setMinWidth(1180);stage.setMinHeight(720);stage.setScene(scene);stage.show();
        shell.start();
        if(ConfigService.bool("startup.autoSync", true)) shell.scheduleStartupRefresh();
    }
    private static void loadApiFile(){
        Path p=Path.of("data","apis.json");
        if(!Files.exists(p))return;
        try{JsonNode n=new ObjectMapper().readTree(Files.readString(p));JsonNode tmdb=n.path("tmdb");if(tmdb.has("readAccessToken")){String token=tmdb.path("readAccessToken").asText("").trim();if(!token.isBlank()&&!token.equals("COLE_SEU_TOKEN_AQUI"))ConfigService.set("tmdb.token",token);}if(tmdb.has("language"))ConfigService.set("app.language",tmdb.path("language").asText("pt-BR"));}catch(Exception ignored){}
    }

    @Override public void stop(){Services.IMAGES.shutdown();Database.close();}
}
