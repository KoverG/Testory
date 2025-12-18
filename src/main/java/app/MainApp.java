package app;

import app.core.AppConfig;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.nio.file.Files;
import java.nio.file.Path;

public class MainApp extends Application {

    @Override
    public void start(Stage stage) throws Exception {
        fixWorkingDirectoryToAppRoot();

        FXMLLoader fxml = new FXMLLoader(getClass().getResource("/ui/shell.fxml"));
        Scene scene = new Scene(fxml.load(), 1200, 800);
        scene.getStylesheets().add(getClass().getResource("/ui/styles.css").toExternalForm());

        stage.setTitle(AppConfig.title() + " v" + AppConfig.version());
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) { launch(args); }

    private static void fixWorkingDirectoryToAppRoot() {
        try {
            Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
            Path jarPath = Path.of(MainApp.class.getProtectionDomain().getCodeSource().getLocation().toURI())
                    .toAbsolutePath().normalize();

            Path jarDir = Files.isDirectory(jarPath) ? jarPath : jarPath.getParent();
            Path appRoot = findDirWithAppSetting(current, jarDir);

            if (appRoot != null) {
                System.setProperty("user.dir", appRoot.toString());
                System.out.println("[BOOT] user.dir = " + appRoot);
            } else {
                System.out.println("[BOOT] app-setting.json not found; keep user.dir = " + current);
            }
        } catch (Exception e) {
            System.out.println("[BOOT] Failed to fix working directory: " + e.getMessage());
        }
    }

    private static Path findDirWithAppSetting(Path currentDir, Path jarDir) {
        Path[] candidates = new Path[] {
                currentDir,
                jarDir,
                jarDir != null ? jarDir.getParent() : null,
                jarDir != null && jarDir.getParent() != null ? jarDir.getParent().getParent() : null
        };
        for (Path dir : candidates) {
            if (dir == null) continue;
            if (Files.exists(dir.resolve("app-setting.json"))) return dir;
        }
        return null;
    }
}
