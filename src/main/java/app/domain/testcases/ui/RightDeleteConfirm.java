package app.domain.testcases.ui;

import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.ParallelTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public final class RightDeleteConfirm {

    private static final Duration ANIM_IN = Duration.millis(160);
    private static final Duration ANIM_OUT = Duration.millis(140);

    private static final double MODAL_W = 320.0;
    private static final double MODAL_MIN_H = 212.0;

    // имя подпапки-мусорки рядом с карточками
    private static final String TRASH_DIR_NAME = "_trash";
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS");

    private final Node rightPaneRoot;      // вся правая зона
    private final StackPane rightStack;    // stack правой зоны (где живёт deleteLayer)

    private final StackPane deleteLayer;   // оверлей слой (dim + modal)
    private final VBox deleteModal;        // сама модалка (VBox)
    private final Button btnDelete;        // кнопка корзины (триггер)
    private final Button btnCancel;
    private final Button btnConfirm;

    private BooleanSupplier canOpenSupplier = () -> true;

    // откуда брать текущий файл карточки
    private Supplier<Path> currentFileSupplier = () -> null;

    // что сделать после успешного перемещения в trash
    private Runnable afterDeleted = () -> {};

    private boolean open = false;

    // фиксируем файл на момент открытия модалки
    private Path pendingFile;

    private ParallelTransition anim;

    public RightDeleteConfirm(
            Node rightPaneRoot,
            StackPane rightStack,
            StackPane deleteLayer,
            VBox deleteModal,
            Button btnDelete,
            Button btnCancel,
            Button btnConfirm
    ) {
        this.rightPaneRoot = rightPaneRoot;
        this.rightStack = rightStack;
        this.deleteLayer = deleteLayer;
        this.deleteModal = deleteModal;
        this.btnDelete = btnDelete;
        this.btnCancel = btnCancel;
        this.btnConfirm = btnConfirm;

        ensureUiTuned();
        installHandlers();
    }

    public void setCanOpenSupplier(BooleanSupplier s) {
        this.canOpenSupplier = (s == null) ? () -> true : s;
    }

    public void setCurrentFileSupplier(Supplier<Path> s) {
        this.currentFileSupplier = (s == null) ? () -> null : s;
    }

    public void setAfterDeleted(Runnable r) {
        this.afterDeleted = (r == null) ? () -> {} : r;
    }

    public boolean isOpen() {
        return open;
    }

    public void refreshAvailability(boolean enabled) {
        btnDelete.setDisable(!enabled);

        // если внезапно режим сменился (NEW/закрыли right) — модалку принудительно закрываем
        if (!enabled) close();
    }

    public void toggle() {
        if (open) {
            close();
            return;
        }
        if (!canOpenSupplier.getAsBoolean()) return;
        open();
    }

    public void open() {
        if (open) return;
        if (!canOpenSupplier.getAsBoolean()) return;

        // фиксируем файл на момент открытия
        pendingFile = safeGetCurrentFile();
        if (pendingFile == null) return;

        open = true;

        deleteLayer.setManaged(true);
        deleteLayer.setVisible(true);

        if (anim != null) anim.stop();

        deleteLayer.setOpacity(0.0);
        deleteModal.setTranslateY(10.0);

        FadeTransition fade = new FadeTransition(ANIM_IN, deleteLayer);
        fade.setFromValue(0.0);
        fade.setToValue(1.0);
        fade.setInterpolator(Interpolator.EASE_OUT);

        TranslateTransition tr = new TranslateTransition(ANIM_IN, deleteModal);
        tr.setFromY(10.0);
        tr.setToY(0.0);
        tr.setInterpolator(Interpolator.EASE_OUT);

        anim = new ParallelTransition(fade, tr);
        anim.playFromStart();
    }

    public void close() {
        if (!open) return;
        open = false;

        // pending сбрасываем
        pendingFile = null;

        if (anim != null) anim.stop();

        FadeTransition fade = new FadeTransition(ANIM_OUT, deleteLayer);
        fade.setFromValue(deleteLayer.getOpacity());
        fade.setToValue(0.0);
        fade.setInterpolator(Interpolator.EASE_IN);

        TranslateTransition tr = new TranslateTransition(ANIM_OUT, deleteModal);
        tr.setFromY(deleteModal.getTranslateY());
        tr.setToY(10.0);
        tr.setInterpolator(Interpolator.EASE_IN);

        anim = new ParallelTransition(fade, tr);
        anim.setOnFinished(ev -> {
            deleteLayer.setVisible(false);
            deleteLayer.setManaged(false);
            deleteLayer.setOpacity(0.0);
            deleteModal.setTranslateY(0.0);
        });

        anim.playFromStart();
    }

    // ===================== UI TUNE =====================

    private void ensureUiTuned() {
        ConfirmModalUi.apply(deleteModal, MODAL_W, MODAL_MIN_H);

        // на случай, если в FXML стояли фикс-значения высоты — сбрасываем в computed,
        // но НЕ даём расти до экрана:
        deleteModal.setPrefHeight(Region.USE_COMPUTED_SIZE);
        deleteModal.setMaxHeight(Region.USE_PREF_SIZE);
    }


    private void installHandlers() {
        if (btnDelete != null) {
            btnDelete.setOnAction(e -> toggle());
        }

        if (btnCancel != null) {
            btnCancel.setOnAction(e -> close());
        }

        if (btnConfirm != null) {
            btnConfirm.setOnAction(e -> {
                try {
                    boolean moved = movePendingFileToTrash();
                    close();
                    if (moved) afterDeleted.run();
                } catch (Exception ex) {
                    System.err.println("[RightDeleteConfirm] delete failed: " + ex);
                    close();
                }
            });
        }

        // клик по dim — закрыть
        deleteLayer.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
            if (!open) return;

            Node t = (e.getTarget() instanceof Node) ? (Node) e.getTarget() : null;

            // внутри модалки — не закрываем тут (кнопки обработают)
            if (t != null && isDescendant(t, deleteModal)) return;

            // по кнопке корзины — не закрываем тут (toggle обрабатывает btnDelete)
            if (t != null && isDescendant(t, btnDelete)) return;

            close();
            e.consume();
        });

        // Глобальный фильтр кликов (как у тебя)
        Platform.runLater(() -> {
            if (rightPaneRoot == null) return;
            if (rightPaneRoot.getScene() == null) return;

            rightPaneRoot.getScene().addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
                if (!open) return;

                Node t = (e.getTarget() instanceof Node) ? (Node) e.getTarget() : null;
                if (t == null) {
                    close();
                    return;
                }

                if (isDescendant(t, btnDelete)) return;
                if (isDescendant(t, deleteModal)) return;

                boolean inRightZone = isDescendant(t, rightPaneRoot);

                if (inRightZone) {
                    close();
                    e.consume();
                } else {
                    close();
                }
            });
        });
    }

    // ===================== DELETE LOGIC =====================

    private Path safeGetCurrentFile() {
        try {
            return currentFileSupplier.get();
        } catch (Exception ex) {
            System.err.println("[RightDeleteConfirm] currentFileSupplier failed: " + ex);
            return null;
        }
    }

    private boolean movePendingFileToTrash() throws IOException {
        Path src = pendingFile;
        if (src == null) return false;
        if (!Files.exists(src)) return false;

        Path parent = src.getParent();
        if (parent == null) return false;

        Path trashDir = parent.resolve(TRASH_DIR_NAME);
        Files.createDirectories(trashDir);

        String fileName = String.valueOf(src.getFileName());
        Path dst = trashDir.resolve(fileName);

        if (Files.exists(dst)) {
            // избегаем коллизий: id.json -> id__yyyyMMdd_HHmmss_SSS.json
            String base = fileName;
            String ext = "";
            int dot = fileName.lastIndexOf('.');
            if (dot > 0 && dot < fileName.length() - 1) {
                base = fileName.substring(0, dot);
                ext = fileName.substring(dot);
            }

            String suffix = "__" + LocalDateTime.now().format(TS);
            dst = trashDir.resolve(base + suffix + ext);
        }

        Files.move(src, dst, StandardCopyOption.ATOMIC_MOVE);
        return true;
    }

    private static boolean isDescendant(Node n, Node ancestor) {
        if (n == null || ancestor == null) return false;
        Node cur = n;
        while (cur != null) {
            if (cur == ancestor) return true;
            cur = cur.getParent();
        }
        return false;
    }
}
