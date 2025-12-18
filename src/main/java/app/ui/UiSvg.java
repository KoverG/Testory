package app.ui;

import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.shape.SVGPath;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class UiSvg {

    private static final Pattern PATH_D = Pattern.compile("<path[^>]*\\sd\\s*=\\s*\"([^\"]+)\"[^>]*/?>", Pattern.CASE_INSENSITIVE);

    private UiSvg() {}

    public static void setButtonSvg(Button btn, String iconFileName, double sizePx) {
        SVGPath p = loadSvgPath(iconFileName);
        if (p == null) return;

        p.getStyleClass().add("svg-path");

        double scale = sizePx / 16.0;
        p.setScaleX(scale);
        p.setScaleY(scale);

        btn.setGraphic(p);
    }

    public static Node createSvg(String iconFileName, double sizePx) {
        SVGPath p = loadSvgPath(iconFileName);
        if (p == null) return null;

        p.getStyleClass().add("svg-path");

        double scale = sizePx / 16.0;
        p.setScaleX(scale);
        p.setScaleY(scale);

        return p;
    }

    private static SVGPath loadSvgPath(String iconFileName) {
        String res = "/icons/" + iconFileName;
        String svg = readResource(res);
        if (svg == null || svg.isBlank()) return null;

        Matcher m = PATH_D.matcher(svg);
        if (!m.find()) return null;

        String d = m.group(1);

        SVGPath p = new SVGPath();
        p.setContent(d);
        return p;
    }

    private static String readResource(String path) {
        try (InputStream is = UiSvg.class.getResourceAsStream(path)) {
            if (is == null) return null;
            byte[] bytes = is.readAllBytes();
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }
}
