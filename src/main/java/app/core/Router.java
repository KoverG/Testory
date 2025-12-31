package app.core;

import javafx.scene.Parent;
import javafx.scene.layout.BorderPane;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Consumer;

public final class Router {
    private static Router INSTANCE;

    private static View initialView = null;

    // ====== restore navigation state across shell reload ======
    private static NavState pendingNavState = null;

    public static final class NavState {
        public final View current;
        public final List<View> historyStackTopFirst;

        public NavState(View current, List<View> historyStackTopFirst) {
            this.current = current;
            this.historyStackTopFirst = historyStackTopFirst;
        }
    }

    public static void setPendingNavState(NavState s) {
        pendingNavState = s;
    }

    public static NavState consumePendingNavState() {
        NavState s = pendingNavState;
        pendingNavState = null;
        return s;
    }
    // =========================================================

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

    // ===================== snapshot / restore =====================

    public NavState snapshot() {
        List<View> h = new ArrayList<>(history); // ArrayDeque iterator gives top-first
        return new NavState(current, h);
    }

    public void restoreSnapshot(NavState s) {
        history.clear();
        if (s != null && s.historyStackTopFirst != null) {
            // keep same order (top-first) for push/pop semantics
            for (int i = s.historyStackTopFirst.size() - 1; i >= 0; i--) {
                View v = s.historyStackTopFirst.get(i);
                if (v != null) history.push(v);
            }
        }
        // current is applied by navigation in ShellController (open/initView),
        // but we still keep it here for consistency:
        if (s != null) current = s.current;
    }
}
