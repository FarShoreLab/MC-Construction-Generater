package org.mcsettlement.planner;

import java.util.*;
import org.mcsettlement.planner.SettlementPlanner.PlanRequest;
import org.mcsettlement.planner.ir.PlanningIR;
import org.mcsettlement.planner.civil.PlanConstruction;
import org.mcsettlement.planner.terrain.HeightfieldMap;
import org.mcsettlement.planner.terrain.HeightfieldMap.ObstacleType;

/** Dry-gap end-to-end fixture: ordinary fill cannot cross the eight-block risers. */
public final class LandBridgeMain {
    private static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private static PlanRequest request(){
        var r=new PlanRequest();r.targetPlots=3;r.entry=new int[]{2,64,46};r.presetPalette="single";r.singlePresetId="square_cabin";
        r.expert=new ExpertSettings();r.expert.autoDock=false;
        r.expert.pins=ExpertSettings.parsePins("[{\"id\":\"home\",\"x\":12,\"z\":20,\"facing\":\"EAST\",\"connectTo\":\"other\"},{\"id\":\"other\",\"x\":76,\"z\":72,\"facing\":\"WEST\"},{\"id\":\"third\",\"x\":76,\"z\":10,\"facing\":\"WEST\"}]");return r;
    }
    private static HeightfieldMap terrain(int bed){
        var m=new HeightfieldMap(0,0,96,96);
        for(int x=0;x<96;x++)for(int z=0;z<96;z++)m.setSurfaceY(x,z,x>=44&&x<=51?bed:64);
        m.computeSlopes();return m;
    }
    private static void rejected(PlanningIR ir){require("REJECTED".equals(ir.status)&&ir.groundColumns.isEmpty()&&ir.plots.isEmpty(),"expected atomic rejection");}
    public static void main(String[] args){
        var m=terrain(56);var r=request();var ir=SettlementPlanner.plan(m,r);
        require("COMPLETE".equals(ir.status),ir.sitePlanning.reasons+" "+ir.sitePlanning.attempts);
        ExpertTerrainAudit.validate(m,r,ir);var edits=PlanConstruction.prepare(ir,"medieval");
        long bridges=ir.groundColumns.stream().filter(RoadTerrain::landBridge).count();require(bridges>0,"no dry span");
        for(var c:ir.groundColumns)if(RoadTerrain.landBridge(c)){
            require(RoadTerrain.matchesFreshTerrain(m,c,true)&&!RoadTerrain.matchesFreshTerrain(m,c,false),"fresh terrain preflight");
            require(edits.stream().anyMatch(e->e.x()==c.x&&e.z()==c.z&&e.y()==c.targetY&&e.block().equals("minecraft:spruce_planks")),"missing deck");
            for(int y=c.originalY+1;y<c.targetY;y++){final int yy=y;boolean pile=edits.stream().anyMatch(e->e.x()==c.x&&e.z()==c.z&&e.y()==yy&&e.block().equals("minecraft:oak_log"));require(pile==c.support,"dry gap infilled or missing pier");}
        }
        require(edits.equals(PlanConstruction.prepare(SettlementPlanner.plan(m,request()),"medieval")),"nondeterministic edit program");
        System.out.println("PASS dry gap COMPLETE; bridge columns="+bridges+" edits="+edits.size());
        r=request();r.expert.allowBridges=false;rejected(SettlementPlanner.plan(m,r));System.out.println("PASS disabled bridges");
        for(var obstacle:List.of(ObstacleType.PROTECTED,ObstacleType.EXISTING_BUILDING,ObstacleType.STEEP_CLIFF)){
            var blocked=terrain(56);for(int z=0;z<96;z++)blocked.setObstacle(47,z,obstacle);rejected(SettlementPlanner.plan(blocked,request()));
            var c=ir.groundColumns.stream().filter(a->a.x==47&&RoadTerrain.landBridge(a)).findFirst().orElseThrow();require(!RoadTerrain.matchesFreshTerrain(blocked,c,true),"fresh obstacle ignored");System.out.println("PASS obstacle "+obstacle);
        }
        rejected(SettlementPlanner.plan(terrain(40),request()));System.out.println("PASS overheight gap");
        for(String budget:List.of("path","grade","columns","edits")){
            r=request();switch(budget){case "path"->r.searchBudget.pathExpanded=1;case "grade"->r.searchBudget.gradeRelaxations=1;case "columns"->r.searchBudget.groundColumns=1;default->r.searchBudget.constructionEdits=1;}
            rejected(SettlementPlanner.plan(m,r));System.out.println("PASS budget "+budget);
        }
        var c=ir.groundColumns.stream().filter(RoadTerrain::landBridge).findFirst().orElseThrow();c.support=!c.support;
        try{PlanConstruction.prepare(ir,"medieval");throw new AssertionError("forged support accepted");}catch(IllegalArgumentException expected){System.out.println("PASS forged support");}
    }
}
