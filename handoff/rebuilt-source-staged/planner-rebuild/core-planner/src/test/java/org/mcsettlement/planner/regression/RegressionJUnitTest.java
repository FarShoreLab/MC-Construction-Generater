package org.mcsettlement.planner.regression;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
/** Runs the same assertions under the real JUnit engine when Gradle dependencies are available. */
public class RegressionJUnitTest {
    @Test void boundedPlannerRegressionSuite() throws Exception {
        RegressionMain.main(new String[]{Files.createTempDirectory("planner-regressions-").toString()});
    }
}
