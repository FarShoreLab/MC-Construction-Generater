package org.mcsettlement.planner.singlegrade;
import org.mcsettlement.planner.SettlementPlanner.PlanRequest;
import org.mcsettlement.planner.ir.PlanningIR;
import org.mcsettlement.planner.terrain.HeightfieldMap;
/** The supplied handoff algorithm, frozen for equal-demand earthwork comparisons. */
public final class SingleGradeReference {
    private SingleGradeReference() {}
    public static PlanningIR plan(HeightfieldMap map, PlanRequest request) {
        return BoundedSettlementPlanner.plan(map, request);
    }
}
