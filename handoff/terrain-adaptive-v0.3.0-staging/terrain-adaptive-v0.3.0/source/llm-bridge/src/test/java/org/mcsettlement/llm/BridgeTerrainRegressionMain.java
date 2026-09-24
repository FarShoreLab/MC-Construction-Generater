package org.mcsettlement.llm;
import org.mcsettlement.planner.SettlementPlanner;
import org.mcsettlement.planner.terrain.HeightfieldMap;
import org.mcsettlement.planner.preset.*;
import org.mcsettlement.planner.civil.PlanConstruction;
import java.util.Arrays;
/** Offline locked-preset bridge and diagonal construction integration. No network fixture needed. */
public final class BridgeTerrainRegressionMain {
    public static void main(String[] args) {
        var map=new HeightfieldMap(0,0,72,72);
        for(int x=0;x<72;x++)for(int z=0;z<72;z++)map.setSurfaceY(x,z,64+x/20);
        map.computeSlopes();var req=new SettlementPlanner.PlanRequest();
        req.roadDirections=12;req.diagonalBuildings=true;req.seed=43;
        var p=BuildingPresetRegistry.getInstance().getPreset("nordic_cottage");
        var shape=new BuildingShape(p.rotateToFacing("NORTH"),true);
        var r=new SettlementPlanner.BuildingRequirement();r.id="diagonal_homes";r.purpose="residential";r.count=2;
        r.presetId="nordic_cottage";r.minWidth=r.maxWidth=shape.sizeX;r.minDepth=r.maxDepth=shape.sizeZ;
        req.requirements.add(r);var plan=SettlementPlanner.plan(map,req);
        if(!plan.status.equals("COMPLETE")||plan.plots.stream().anyMatch(q->!q.builder.diagonal45))throw new AssertionError("Missing diagonal buildings");
        var config=new LlmStrategyManager.LlmConfig();config.preferLocalAgy=false;
        for(var plot:plan.plots){var model=AiBuildingArchitect.designBuildingForPlot(plot,"medieval_rustic","ignore unrelated forge keywords",config);
            if(!Arrays.deepEquals(model.blocks,PlannedBuilding.grid(plot,"medieval_rustic")))throw new AssertionError("Bridge grid differs");}
        var edits=PlanConstruction.prepare(plan,"medieval_rustic");
        if(edits.isEmpty())throw new AssertionError("Empty edit program");
        System.out.println("PASS diagonal locked-preset bridge; buildings="+plan.plots.size()+" edits="+edits.size()+" modelCalls="+plan.search.modelCalls);
    }
}
