package app.shell;

import app.core.Router;
import app.core.View;
import javafx.fxml.FXML;

public class HomeController {

    @FXML public void openTestCases() { Router.get().go(View.TEST_CASES); }
    @FXML public void openCycles()    { Router.get().go(View.CYCLES); }
    @FXML public void openHistory()   { Router.get().go(View.HISTORY); }
    @FXML public void openAnalytics() { Router.get().go(View.ANALYTICS); }
    @FXML public void openReports()   { Router.get().go(View.REPORTS); }
}
