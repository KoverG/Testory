package app.ui;

import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;

public final class UiScroll {

    private UiScroll() {}

    /** Обрезает содержимое контейнера по скруглённым углам (скроллбары/ползунки не вылезают). */
    public static void clipRounded(Region node, double radius) {
        if (node == null) return;

        Rectangle clip = new Rectangle();
        clip.setArcWidth(radius * 2.0);
        clip.setArcHeight(radius * 2.0);

        clip.widthProperty().bind(node.widthProperty());
        clip.heightProperty().bind(node.heightProperty());

        node.setClip(clip);
    }

    /** Удобный шорткат именно для sheet-ов (StackPane/Region), которые содержат ScrollPane. */
    public static void clipRoundedSheet(StackPane sheet, double radius) {
        clipRounded(sheet, radius);
    }

    /** Если нужно прямо к ScrollPane применить (реже полезно, но бывает). */
    public static void clipRoundedScroll(ScrollPane sp, double radius) {
        clipRounded(sp, radius);
    }
}
