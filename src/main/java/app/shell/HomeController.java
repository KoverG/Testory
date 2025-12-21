// FILE: src/main/java/app/shell/HomeController.java
package app.shell;

import app.core.Router;
import javafx.fxml.FXML;

public class HomeController {

    @FXML public void openTestCases() { Router.get().testCases(); }
    @FXML public void openCycles()    { Router.get().cycles(); }
    @FXML public void openHistory()   { Router.get().history(); }
    @FXML public void openAnalytics() { Router.get().analytics(); }
    @FXML public void openReports()   { Router.get().reports(); }
}