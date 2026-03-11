package app.domain.cycles.ui.modal;

import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.ParallelTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.effect.DropShadow;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.util.Duration;

import java.util.function.Supplier;

/**
 * Universal right-card modal launcher:
 * - creates overlay layer inside provided {@link StackPane} root (rightRoot)
 * - positions modal under an anchor node, aligned by anchor RIGHT edge (default)
 * - backdrop starts below the anchor (so top-row buttons stay clickable)
 * - supports open/close animation and outside-click close (scene-level)
 *
 * Content and business logic are provided by caller via {@code modalFactory}.
 *
 * IMPORTANT:
 * - Positioning is done via relocate(x,y) on UNMANAGED nodes
 * - Animation uses translateX relative to relocated position
 */
public final class RightAnchoredModal {

    private static final double ANIM_DX = 22.0;
    private static final Duration ANIM_DUR = Duration.millis(220);

    private static final double GAP_PX = 6.0;

    private final StackPane rightRoot;
    private final Node anchor;
    private final Supplier<? extends Region> modalFactory;

    private StackPane layer;
    private Region backdrop;

    /**
     * modalHost - wrapper, which we position/animate (shadow is applied here).
     * modalContent - actual content returned by modalFactory (interactive controls live here).
     */
    private StackPane modalHost;
    private Region modalContent;

    private boolean outsideCloseInstalled = false;

    private ParallelTransition animIn;
    private ParallelTransition animOut;

    private Runnable onBeforeOpen = () -> {};
    private Runnable onAfterOpen = () -> {};

    private boolean suspendOverlayRelayout = false;

    private Double pinnedX = null;
    private Double pinnedY = null;

    // Group: PRIMARY by default (exclusive)
    private ModalGroup group = ModalGroup.PRIMARY;

    /**
     * Positioning policy:
     * - false (default): align by anchor RIGHT edge (legacy behavior)
     * - true: align by anchor LEFT edge (to keep modal pinned under a left-side chip)
     */
    private boolean alignByLeftEdge = false;

    /**
     * Special positioning mode (OFF by default):
     * If enabled, when there is not enough space below the anchor inside rightRoot,
     * the modal will appear ABOVE the anchor (flip up), similar to ComboBox popup logic.
     *
     * This must be enabled only for specific modals (e.g. case comment modal).
     */
    private boolean flipUpWhenNoSpace = false;

    public RightAnchoredModal(StackPane rightRoot, Node anchor, Supplier<? extends Region> modalFactory) {
        this.rightRoot = rightRoot;
        this.anchor = anchor;
        this.modalFactory = modalFactory;
    }

    public void setOnBeforeOpen(Runnable r) {
        this.onBeforeOpen = (r == null) ? () -> {} : r;
    }

    public void setOnAfterOpen(Runnable r) {
        this.onAfterOpen = (r == null) ? () -> {} : r;
    }

    /**
     * Default is PRIMARY.
     * For future "подмодалка" use-case: set SUBMODAL so it doesn't close PRIMARY.
     */
    public void setGroup(ModalGroup group) {
        this.group = (group == null) ? ModalGroup.PRIMARY : group;
    }

    /**
     * If true: modal is aligned to anchor LEFT edge (instead of RIGHT edge).
     * Useful when the anchor (chip) is located on the left, so clamping won't "jump" the modal to x=0.
     */
    public void setAlignByLeftEdge(boolean alignByLeftEdge) {
        this.alignByLeftEdge = alignByLeftEdge;
        // if open -> relayout (keep pinned if pinned is active)
        layoutOverlay();
    }

    /**
     * Enable/disable "flip up when no space below".
     * Default: false (legacy behavior).
     */
    public void setFlipUpWhenNoSpace(boolean flipUpWhenNoSpace) {
        this.flipUpWhenNoSpace = flipUpWhenNoSpace;
        layoutOverlay();
    }

    ModalGroup getGroup() {
        return group;
    }

    public void install() {
        ensureOverlay();
        installOutsideClose();
        installRelayoutHooks();

        // ✅ close modal automatically when anchor is detached/hidden (card switch / recreate)
        installAnchorLifecycleCloseHooks();

        // ✅ register in global registry
        ModalRegistry.register(this);
    }

    public boolean isOpen() {
        return layer != null && layer.isVisible();
    }

    public void open() {
        if (rightRoot == null) return;

        ensureOverlay();
        if (layer == null || modalHost == null) return;

        if (isOpen()) return;

        // ✅ close other PRIMARY modals first (no overlap)
        ModalRegistry.beforeOpen(this);

        clearPinnedPos();

        try { onBeforeOpen.run(); } catch (Exception ignore) {}

        layer.setVisible(true);
        layer.setManaged(true);
        layer.toFront();

        // ✅ Important: DO NOT pin before animation.
        // We do: layout -> next pulse layout -> then start animation.
        scheduleOpenLayoutAndAnimate(0);
    }

    public void close() {
        if (!isOpen()) return;
        playCloseAnim();
    }

    public void toggle() {
        if (isOpen()) close();
        else open();
    }

    /**
     * Request overlay relayout (size/position recalculation).
     * IMPORTANT: if content size changes (auto-grow TextArea), we must allow Y to change
     * so the modal can:
     * - "grow up" when shown above the anchor
     * - flip from below to above when it stops fitting below
     */
    public void requestRelayout() {
        if (!isOpen()) return;
        Platform.runLater(() -> {
            clearPinnedPos();     // ✅ allow reposition on content growth
            layoutOverlay();
        });
    }

    /**
     * Close instantly (no animation) — used by ModalRegistry and anchor detach hooks.
     */
    void closeImmediately() {
        if (!isOpen()) return;

        stopAnims();
        suspendOverlayRelayout = false;

        if (layer != null) {
            layer.setVisible(false);
            layer.setManaged(false);
        }

        if (modalHost != null) {
            modalHost.setOpacity(1.0);
            modalHost.setTranslateX(0.0);
        }
        if (backdrop != null) {
            backdrop.setOpacity(1.0);
        }

        clearPinnedPos();
    }

    // ===================== INTERNAL =====================

    private void ensureOverlay() {
        if (rightRoot == null) return;
        if (layer != null) return;

        layer = new StackPane();
        layer.setVisible(false);
        layer.setManaged(false);
        layer.setPickOnBounds(false); // top area must not intercept clicks (anchor stays clickable)
        layer.setMaxWidth(Double.MAX_VALUE);
        layer.setMaxHeight(Double.MAX_VALUE);
        StackPane.setAlignment(layer, Pos.TOP_LEFT);

        backdrop = new Region();
        backdrop.getStyleClass().add("cy-menu-backdrop");
        backdrop.setManaged(false);
        backdrop.addEventHandler(MouseEvent.MOUSE_PRESSED, e -> close());

        // --- content (interactive) ---
        modalContent = modalFactory.get();
        modalContent.setManaged(false);

        // --- host (we relocate/animate this, and shadow is applied here) ---
        modalHost = new StackPane();
        modalHost.setManaged(false);

        // ✅ key: do not pick on host bounds (so shadow area doesn't steal clicks)
        modalHost.setPickOnBounds(false);
        StackPane.setAlignment(modalHost, Pos.TOP_LEFT);

        // add content
        modalHost.getChildren().add(modalContent);

        // ✅ Shadow from your CSS, but on host (non-interactive area won't intercept due to pickOnBounds=false)
        DropShadow ds = new DropShadow();
        ds.setRadius(20.0);
        ds.setSpread(0.16);
        ds.setOffsetX(0.0);
        ds.setOffsetY(8.0);
        ds.setColor(Color.rgb(0, 0, 0, 0.26));
        modalHost.setEffect(ds);

        layer.getChildren().addAll(backdrop, modalHost);
        rightRoot.getChildren().add(layer);
    }

    private void installRelayoutHooks() {
        if (rightRoot == null) return;

        rightRoot.widthProperty().addListener((obs, ov, nv) -> {
            clearPinnedPos();
            layoutOverlay();
        });
        rightRoot.heightProperty().addListener((obs, ov, nv) -> {
            clearPinnedPos();
            layoutOverlay();
        });

        if (anchor != null) {
            anchor.boundsInParentProperty().addListener((obs, ov, nv) -> layoutOverlay());
        }

        // ✅ dynamic content resize: if modal content changes its layout bounds (auto-grow),
        // update overlay geometry so host size follows content size.
        if (modalContent != null) {
            modalContent.layoutBoundsProperty().addListener((obs, ov, nv) -> {
                if (isOpen()) layoutOverlay();
            });
        }
    }

    /**
     * When user switches cards, UI often replaces header nodes (anchor button).
     * Old anchor becomes detached (parent=null) or removed from scene (scene=null),
     * but overlay stays visible unless we close it.
     */
    private void installAnchorLifecycleCloseHooks() {
        if (anchor == null) return;

        // If anchor removed from scene -> close immediately
        anchor.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (!isOpen()) return;
            if (newScene == null) {
                closeImmediately();
                return;
            }
            // If anchor moved to another scene (rare), also close
            if (oldScene != null && oldScene != newScene) {
                closeImmediately();
            }
        });

        // If anchor detached from parent -> close immediately
        anchor.parentProperty().addListener((obs, oldParent, newParent) -> {
            if (!isOpen()) return;
            if (newParent == null) closeImmediately();
        });

        // If anchor becomes invisible -> close (card switched / header hidden)
        anchor.visibleProperty().addListener((obs, ov, nv) -> {
            if (!isOpen()) return;
            if (!nv) closeImmediately();
        });
    }

    private void scheduleOpenLayoutAndAnimate(int attempt) {
        Platform.runLater(() -> {
            if (!isOpen()) return;

            layoutOverlay();

            boolean rootSized = rightRoot.getWidth() > 1.0 && rightRoot.getHeight() > 1.0;
            boolean anchorReady = anchor != null && anchor.getScene() != null && anchor.isVisible() && anchor.getParent() != null;

            if ((!rootSized || !anchorReady) && attempt < 3) {
                scheduleOpenLayoutAndAnimate(attempt + 1);
                return;
            }

            // ✅ extra pulse BEFORE animation, while relayout is still allowed
            Platform.runLater(() -> {
                if (!isOpen()) return;
                layoutOverlay();
                Platform.runLater(this::playOpenAnim);
            });
        });
    }

    private void layoutOverlay() {
        if (suspendOverlayRelayout) return;

        if (rightRoot == null) return;
        if (layer == null || modalHost == null || backdrop == null || modalContent == null) return;
        if (!rightRoot.isVisible()) return;

        // If anchor is gone while open -> close (safety)
        if (isOpen() && (anchor == null || anchor.getScene() == null || anchor.getParent() == null || !anchor.isVisible())) {
            closeImmediately();
            return;
        }

        try {
            if (anchor instanceof Parent p) {
                p.applyCss();
                p.layout();
            } else if (anchor != null) {
                anchor.applyCss();
            }
        } catch (Exception ignore) {}

        rightRoot.applyCss();
        rightRoot.layout();

        if (anchor == null) return;

        Bounds anchorScene = anchor.localToScene(anchor.getBoundsInLocal());
        Bounds rootScene = rightRoot.localToScene(rightRoot.getBoundsInLocal());

        double anchorTopY = anchorScene.getMinY() - rootScene.getMinY();
        double anchorBottomY = anchorScene.getMaxY() - rootScene.getMinY();

        double topDown = Math.max(0.0, anchorBottomY + GAP_PX);

        double rightW = rightRoot.getWidth();
        double rightH = rightRoot.getHeight();

        // backdrop starts below anchor (top row stays clickable)
        double backdropH = Math.max(0.0, rightH - topDown);
        backdrop.resizeRelocate(0.0, topDown, rightW, backdropH);

        modalContent.applyCss();
        modalContent.autosize();

        double modalW = modalContent.getWidth();
        double modalH = modalContent.getHeight();

        if (modalW <= 0) {
            double pref = modalContent.prefWidth(-1);
            modalW = (pref > 0) ? pref : 300.0;
        }
        if (modalH <= 0) {
            double pref = modalContent.prefHeight(modalW);
            modalH = (pref > 0) ? pref : 120.0;
        }

        double anchorLeftX = anchorScene.getMinX() - rootScene.getMinX();
        double anchorRightX = anchorScene.getMaxX() - rootScene.getMinX();

        // ✅ align by left edge when enabled, otherwise legacy right-edge alignment
        double x = alignByLeftEdge ? anchorLeftX : (anchorRightX - modalW);
        x = clamp(x, 0.0, Math.max(0.0, rightW - modalW));

        // Available space below/above within rightRoot
        double spaceDown = Math.max(0.0, rightH - topDown);
        double spaceUp = Math.max(0.0, anchorTopY - GAP_PX);

        // ✅ Flip UP only when enabled and it doesn't fit down AND there is more space above
        boolean flipUp = flipUpWhenNoSpace && (modalH > spaceDown) && (spaceUp > spaceDown);

        double availableH = flipUp ? spaceUp : spaceDown;
        modalH = Math.min(modalH, availableH);

        double desiredY = flipUp
                ? (anchorTopY - GAP_PX - modalH)
                : topDown;

        double yClamped = clamp(desiredY, 0.0, Math.max(0.0, rightH - modalH));

        // keep stable pinned position while open
        if (isOpen() && pinnedX != null && pinnedY != null) {
            x = pinnedX;
            yClamped = pinnedY;
        }

        // host size equals content size
        modalHost.resize(modalW, modalH);
        modalHost.relocate(x, yClamped);

        // content fills host
        modalContent.resizeRelocate(0.0, 0.0, modalW, modalH);
    }

    private void playOpenAnim() {
        if (layer == null || modalHost == null) return;

        stopAnims();
        suspendOverlayRelayout = true;

        modalHost.setOpacity(0.0);
        modalHost.setTranslateX(-ANIM_DX);

        if (backdrop != null) backdrop.setOpacity(0.0);

        FadeTransition fadeBackdrop = null;
        if (backdrop != null) {
            fadeBackdrop = new FadeTransition(ANIM_DUR, backdrop);
            fadeBackdrop.setFromValue(0.0);
            fadeBackdrop.setToValue(1.0);
            fadeBackdrop.setInterpolator(Interpolator.EASE_OUT);
        }

        FadeTransition fade = new FadeTransition(ANIM_DUR, modalHost);
        fade.setFromValue(0.0);
        fade.setToValue(1.0);
        fade.setInterpolator(Interpolator.EASE_OUT);

        TranslateTransition slide = new TranslateTransition(ANIM_DUR, modalHost);
        slide.setFromX(-ANIM_DX);
        slide.setToX(0.0);
        slide.setInterpolator(Interpolator.EASE_OUT);

        animIn = (fadeBackdrop == null)
                ? new ParallelTransition(fade, slide)
                : new ParallelTransition(fadeBackdrop, fade, slide);

        animIn.setOnFinished(e -> {
            modalHost.setOpacity(1.0);
            modalHost.setTranslateX(0.0);
            if (backdrop != null) backdrop.setOpacity(1.0);

            animIn = null;
            suspendOverlayRelayout = false;

            // pin position for stability during open (ONLY after animation)
            pinnedX = modalHost.getLayoutX();
            pinnedY = modalHost.getLayoutY();

            try { onAfterOpen.run(); } catch (Exception ignore) {}
        });

        animIn.playFromStart();
    }

    private void playCloseAnim() {
        if (layer == null || modalHost == null) return;

        stopAnims();
        suspendOverlayRelayout = true;

        FadeTransition fadeBackdrop = null;
        if (backdrop != null) {
            fadeBackdrop = new FadeTransition(ANIM_DUR, backdrop);
            fadeBackdrop.setFromValue(backdrop.getOpacity());
            fadeBackdrop.setToValue(0.0);
            fadeBackdrop.setInterpolator(Interpolator.EASE_OUT);
        }

        FadeTransition fade = new FadeTransition(ANIM_DUR, modalHost);
        fade.setFromValue(modalHost.getOpacity());
        fade.setToValue(0.0);
        fade.setInterpolator(Interpolator.EASE_OUT);

        TranslateTransition slide = new TranslateTransition(ANIM_DUR, modalHost);
        slide.setFromX(modalHost.getTranslateX());
        slide.setToX(-ANIM_DX);
        slide.setInterpolator(Interpolator.EASE_OUT);

        animOut = (fadeBackdrop == null)
                ? new ParallelTransition(fade, slide)
                : new ParallelTransition(fadeBackdrop, fade, slide);

        animOut.setOnFinished(e -> {
            layer.setVisible(false);
            layer.setManaged(false);

            modalHost.setOpacity(1.0);
            modalHost.setTranslateX(0.0);
            if (backdrop != null) backdrop.setOpacity(1.0);

            animOut = null;
            suspendOverlayRelayout = false;

            clearPinnedPos();
        });

        animOut.playFromStart();
    }

    private void stopAnims() {
        if (animIn != null) {
            try { animIn.stop(); } catch (Exception ignore) {}
            animIn = null;
        }
        if (animOut != null) {
            try { animOut.stop(); } catch (Exception ignore) {}
            animOut = null;
        }
    }

    private void installOutsideClose() {
        if (outsideCloseInstalled) return;
        if (rightRoot == null) return;

        Scene sc = rightRoot.getScene();
        if (sc == null) return;

        outsideCloseInstalled = true;

        sc.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
            if (!isOpen()) return;

            Object t = e.getTarget();
            if (!(t instanceof Node node)) {
                close();
                return;
            }

            if (isDescendantOf(node, anchor)) return;
            if (modalHost != null && isDescendantOf(node, modalHost)) return;

            close();
        });
    }

    private static boolean isDescendantOf(Node node, Node root) {
        if (node == null || root == null) return false;
        Node cur = node;
        while (cur != null) {
            if (cur == root) return true;
            cur = cur.getParent();
        }
        return false;
    }

    private void clearPinnedPos() {
        pinnedX = null;
        pinnedY = null;
    }

    private static double clamp(double v, double min, double max) {
        if (v < min) return min;
        if (v > max) return max;
        return v;
    }
}
