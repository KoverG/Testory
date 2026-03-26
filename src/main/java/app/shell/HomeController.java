// FILE: src/main/java/app/shell/HomeController.java
package app.shell;

import app.core.I18n;
import app.core.Router;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.StackPane;

public class HomeController {

    @FXML private Button btnAnalytics;
    @FXML private StackPane analyticsButtonHost;

    @FXML
    private void initialize() {
        String hint = I18n.t("home.analytics.comingSoon");
        if (btnAnalytics != null) {
            btnAnalytics.setDisable(true);
        }
        if (analyticsButtonHost != null) {
            Tooltip.install(analyticsButtonHost, new Tooltip(hint));
        }
    }

    @FXML public void openTestCases() { Router.get().testCases(); }
    @FXML public void openCycles()    { Router.get().cycles(); }
    @FXML public void openHistory()   { Router.get().history(); }
    @FXML public void openAnalytics() { Router.get().analytics(); }
    @FXML public void openReports()   { Router.get().reports(); }
}
