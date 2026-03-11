package app.domain.cycles.ui.modal;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Global registry for RightAnchoredModal instances inside Cycles right zone.
 *
 * Goal:
 * - For PRIMARY modals: enforce "only one open at a time".
 * - For SUBMODAL: allow stacking over PRIMARY (future use case).
 *
 * Uses weak keys to avoid memory leaks.
 */
final class ModalRegistry {

    private static final Set<RightAnchoredModal> MODALS =
            Collections.newSetFromMap(new WeakHashMap<>());

    private ModalRegistry() {}

    static void register(RightAnchoredModal modal) {
        if (modal == null) return;
        MODALS.add(modal);
    }

    static void unregister(RightAnchoredModal modal) {
        if (modal == null) return;
        MODALS.remove(modal);
    }

    static void beforeOpen(RightAnchoredModal opening) {
        if (opening == null) return;

        // Only PRIMARY modals should close other PRIMARY modals.
        if (opening.getGroup() != ModalGroup.PRIMARY) return;

        for (RightAnchoredModal m : MODALS) {
            if (m == null) continue;
            if (m == opening) continue;
            if (m.getGroup() != ModalGroup.PRIMARY) continue;

            if (m.isOpen()) {
                // Close immediately to avoid two modals visible at once.
                m.closeImmediately();
            }
        }
    }
}
