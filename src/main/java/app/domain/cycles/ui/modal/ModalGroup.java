package app.domain.cycles.ui.modal;

/**
 * Modal grouping policy for exclusive/open behavior.
 *
 * PRIMARY: only one PRIMARY modal should be open at a time (exclusive group).
 * SUBMODAL: can be opened over PRIMARY without closing it.
 */
public enum ModalGroup {
    PRIMARY,
    SUBMODAL
}
