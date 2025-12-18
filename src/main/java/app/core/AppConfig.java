package app.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class AppConfig {
    private static final Properties P = new Properties();

    static {
        Path external = Path.of("config", "app.properties");
        boolean loaded = false;

        if (Files.exists(external)) {
            try (InputStream is = Files.newInputStream(external)) {
                P.load(new InputStreamReader(is, StandardCharsets.UTF_8));
                loaded = true;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        if (!loaded) {
            try (InputStream is = AppConfig.class.getResourceAsStream("/app.properties")) {
                if (is != null) P.load(is);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private AppConfig() {}

    public static String version() { return P.getProperty("app.version", "0.0.0"); }
    public static String name()    { return P.getProperty("app.name", "Testory"); }
    public static String title()   { return P.getProperty("app.title", "Testory"); }
}
