package org.mcsettlement.planner;
import org.junit.jupiter.api.Test;
/** The same executable evidence suite also participates in ordinary Gradle/JUnit runs. */
public class AdaptiveTerrainJUnitTest {
    @Test void terrainAdaptiveInvariants() throws Exception {
        AdaptiveTerrainMain.main(new String[]{"build/adaptive-junit-evidence"});
    }
}
