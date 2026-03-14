package app.core;

public final class CardNavigationBridge {

    public record PendingCycleHistoryNavigation(String cycleId, String sourceCaseId) {
    }

    public record PendingCaseRestore(String caseId) {
    }

    private static PendingCycleHistoryNavigation pendingCycleHistoryNavigation;
    private static PendingCaseRestore pendingCaseRestore;

    private CardNavigationBridge() {
    }

    public static void requestCycleHistoryNavigation(String cycleId, String sourceCaseId) {
        pendingCycleHistoryNavigation = new PendingCycleHistoryNavigation(
                safe(cycleId),
                safe(sourceCaseId)
        );
    }

    public static PendingCycleHistoryNavigation consumePendingCycleHistoryNavigation() {
        PendingCycleHistoryNavigation navigation = pendingCycleHistoryNavigation;
        pendingCycleHistoryNavigation = null;
        return navigation;
    }

    public static void requestCaseRestore(String caseId) {
        pendingCaseRestore = new PendingCaseRestore(safe(caseId));
    }

    public static PendingCaseRestore consumePendingCaseRestore() {
        PendingCaseRestore restore = pendingCaseRestore;
        pendingCaseRestore = null;
        return restore;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}