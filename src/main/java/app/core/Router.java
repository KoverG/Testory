package app.core;

import javafx.scene.Parent;
import javafx.scene.layout.BorderPane;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Consumer;

public final class Router {
    private static Router INSTANCE;

    private static View initialView = null;

    private final BorderPane root;
    private final Deque<View> history = new ArrayDeque<>();
    private View current = null;

    private Consumer<String> onHeaderTitle;

    private Router(BorderPane root) {
        this.root = root;
    }

    public static void init(BorderPane root) {
        INSTANCE = new Router(root);
    }

    public static Router get() {
        if (INSTANCE == null) throw new IllegalStateException("Router not initialized");
        return INSTANCE;
    }

    public static void setInitialView(View v) {
        initialView = v;
    }

    public static View consumeInitialView() {
        View v = initialView;
        initialView = null;
        return v;
    }

    public View currentView() { return current; }

    public void setOnHeaderTitle(Consumer<String> c) {
        this.onHeaderTitle = c;
    }

    public void home() { navigate(View.HOME, false); }

    public void open(View v) { navigate(v, false); }

    public void testCases() { navigate(View.TEST_CASES, true); }
    public void cycles() { navigate(View.CYCLES, true); }
    public void history() { navigate(View.HISTORY, true); }
    public void analytics() { navigate(View.ANALYTICS, true); }
    public void reports() { navigate(View.REPORTS, true); }

    public boolean canGoBack() { return !history.isEmpty(); }

    public void back() {
        if (history.isEmpty()) return;
        View v = history.pop();
        navigate(v, false);
    }

    private void navigate(View view, boolean pushToHistory) {
        if (pushToHistory && current != null) history.push(current);

        Parent content = Fxml.load(view.fxml());
        root.setCenter(content);

        current = view;

        if (onHeaderTitle != null) onHeaderTitle.accept(view.title());
    }
}
