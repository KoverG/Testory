package app.core;

import javafx.scene.Parent;
import javafx.scene.layout.BorderPane;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Consumer;

public final class Router {
    private static Router INSTANCE;

    private final BorderPane root;
    private final Deque<View> history = new ArrayDeque<>();
    private View current = null;

    private Consumer<String> onHeaderTitle;
    private Consumer<String> onFooterTitle;

    private Router(BorderPane root) { this.root = root; }

    public static void init(BorderPane root) { INSTANCE = new Router(root); }

    public static Router get() {
        if (INSTANCE == null) throw new IllegalStateException("Router not initialized");
        return INSTANCE;
    }

    public void setOnHeaderTitle(Consumer<String> cb) { this.onHeaderTitle = cb; }
    public void setOnFooterTitle(Consumer<String> cb) { this.onFooterTitle = cb; }

    public View current() { return current; }

    public void go(View view) { navigate(view, true); }

    public void goTo(View view) { navigate(view, true); }

    public void home() {
        history.clear();
        navigate(View.HOME, false);
    }

    public void back() {
        if (!history.isEmpty()) navigate(history.pop(), false);
        else home();
    }

    public boolean canGoBack() { return !history.isEmpty(); }

    private void navigate(View view, boolean pushToHistory) {
        if (pushToHistory && current != null) history.push(current);

        Parent content = Fxml.load(view.fxml());
        root.setCenter(content);

        current = view;

        if (onHeaderTitle != null) onHeaderTitle.accept(view.title());
        if (onFooterTitle != null) onFooterTitle.accept(view.title());
    }
}
