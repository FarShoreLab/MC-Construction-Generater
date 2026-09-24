package org.mcsettlement.llm;

import org.junit.jupiter.api.Test;

/** Runs the offline bridge assertions with the real JUnit engine when dependencies are available. */
public class BridgeRegressionJUnitTest {
    @Test void intentAndPresetIntegration() throws Exception {
        BridgeRegressionMain.main(new String[0]);
    }
}
