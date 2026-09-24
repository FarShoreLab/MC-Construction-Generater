package org.mcsettlement.planner;

import java.util.*;
import org.mcsettlement.planner.SettlementPlanner.PlanRequest;
import org.mcsettlement.planner.civil.PlanConstruction;
import org.mcsettlement.planner.ir.PlanningIR;
import org.mcsettlement.planner.ir.PlanningIR.GroundColumn;
import org.mcsettlement.planner.ir.PlanningIR.LandUseArea;
import org.mcsettlement.planner.terrain.HeightfieldMap;
import static org.mcsettlement.planner.RoadGeometry.*;

/** Bounded terrain-following farmland/pasture flood regions. No rectangle is used as authority. */
final class TerrainLandUsePlanner {
    private record Cell(int x,int z,int score) {}
    private TerrainLandUsePlanner() {}

    static void add(HeightfieldMap map,PlanRequest req,PlanningIR ir){
        if(req.expert==null||ir.plots.isEmpty())return;
        Set<Long> occupied=new HashSet<>();for(GroundColumn c:ir.groundColumns)occupied.add(key(c.x,c.z));
        Set<Long> blocked=new HashSet<>(occupied);
        for(long k:occupied)for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++)if(map.inBounds(x(k)+dx,z(k)+dz))blocked.add(key(x(k)+dx,z(k)+dz));
        double cx=ir.plots.stream().mapToDouble(p->p.origin2D[0]+p.builder.footprintSize[0]*.5).average().orElse(map.getMinX()+map.getWidth()*.5);
        double cz=ir.plots.stream().mapToDouble(p->p.origin2D[1]+p.builder.footprintSize[1]*.5).average().orElse(map.getMinZ()+map.getDepth()*.5);
        long seed=req.expert.effectiveSiteSeed(req.seed)^0x6a09e667f3bcc909L;
        grow(map,req,ir,blocked,cx,cz,"farmland",Math.min(96,28+ir.plots.size()*7),seed);
        grow(map,req,ir,blocked,cx,cz,"pasture",Math.min(128,34+ir.plots.size()*8),seed^0xbb67ae8584caa73bL);
        addFarmHut(map,req,ir,blocked);
        ir.sitePlanning.farmlandCells=ir.landUses.stream().filter(a->"farmland".equals(a.type)).mapToInt(a->a.cells.size()).sum();
        ir.sitePlanning.pastureCells=ir.landUses.stream().filter(a->"pasture".equals(a.type)).mapToInt(a->a.cells.size()).sum();
    }
    private static void addFarmHut(HeightfieldMap map,PlanRequest req,PlanningIR ir,Set<Long> blocked){
        LandUseArea farm=ir.landUses.stream().filter(a->"farmland".equals(a.type)).findFirst().orElse(null);
        if(farm==null)return;
        var source=org.mcsettlement.planner.preset.BuildingPresetRegistry.getInstance().getPreset("meadow_hut");
        double cx=farm.cells.stream().mapToInt(c->c[0]).average().orElse(0),cz=farm.cells.stream().mapToInt(c->c[1]).average().orElse(0);
        var north=source.rotateToFacing("NORTH");
        int bestX=0,bestZ=0,bestY=0;double best=Double.POSITIVE_INFINITY;String facing="SOUTH";
        // A small bounded search close to the field; flat, dry and separated from committed works.
        for(int z=Math.max(map.getMinZ()+1,(int)cz-18);z<=Math.min(map.getMinZ()+map.getDepth()-8,(int)cz+18);z++)
            for(int x=Math.max(map.getMinX()+1,(int)cx-18);x<=Math.min(map.getMinX()+map.getWidth()-8,(int)cx+18);x++){
                double dist=Math.hypot(x+2-cx,z+3-cz);if(dist>18||dist>=best)continue;
                String f=cz>=z+3?"SOUTH":"NORTH";var preset="NORTH".equals(f)?north:source;int y=map.getSurfaceY(x,z);boolean valid=true;
                for(int dz=-1;dz<=preset.sizeZ&&valid;dz++)for(int dx=-1;dx<=preset.sizeX;dx++){
                    int xx=x+dx,zz=z+dz;
                    if(!map.inBounds(xx,zz)||blocked.contains(key(xx,zz))||RoadTerrain.wet(map,xx,zz)||map.getObstacle(xx,zz)!=HeightfieldMap.ObstacleType.NONE||map.getSurfaceY(xx,zz)!=y){valid=false;break;}
                }
                if(valid){best=dist;bestX=x;bestZ=z;bestY=y;facing=f;}
            }
        if(!Double.isFinite(best)){ir.auditLog.warnings.add("FARM_HUT_SKIPPED: no flat dry site beside field");return;}
        var preset=source.rotateToFacing(facing);String[][][] grid=org.mcsettlement.planner.preset.PlannedBuilding.grid(preset,null);
        int count=preset.footprintArea(),edits=count*preset.sizeY;
        if(ir.groundColumns.size()+count>req.searchBudget.groundColumns||ir.search.constructionEdits+edits>req.searchBudget.constructionEdits){ir.auditLog.warnings.add("FARM_HUT_SKIPPED: construction budget");return;}
        LandUseArea hut=new LandUseArea();hut.id="farm_hut_0";hut.type="farm_hut";hut.minY=hut.maxY=bestY;
        for(int z=0;z<preset.sizeZ;z++)for(int x=0;x<preset.sizeX;x++){
            GroundColumn c=new GroundColumn(bestX+x,bestZ+z,bestY,bestY,bestY+preset.sizeY-1,"farm_hut");
            for(int y=1;y<preset.sizeY;y++)c.aboveBlocks.add(grid[x][y][z]);
            ir.groundColumns.add(c);ir.search.constructionEdits+=PlanConstruction.columnEditCount(c);
            hut.cells.add(new int[]{c.x,c.z});if(x==0||z==0||x==preset.sizeX-1||z==preset.sizeZ-1)hut.boundary2D.add(new int[]{c.x,c.z});
        }
        ir.landUses.add(hut);
    }
    private static void grow(HeightfieldMap map,PlanRequest req,PlanningIR ir,Set<Long> blocked,double cx,double cz,String type,int target,long seed){
        int remainingCols=req.searchBudget.groundColumns-ir.groundColumns.size();
        int remainingEdits=req.searchBudget.constructionEdits-ir.search.constructionEdits;
        target=Math.min(target,Math.max(0,Math.min(remainingCols,remainingEdits/3)));if(target<12)return;
        Cell start=null;double radius="farmland".equals(type)?.18:.27, best=Double.POSITIVE_INFINITY;
        double scale=Math.max(map.getWidth(),map.getDepth());
        for(int z=map.getMinZ()+2;z<map.getMinZ()+map.getDepth()-2;z++)for(int x=map.getMinX()+2;x<map.getMinX()+map.getWidth()-2;x++){
            if(!usable(map,blocked,x,z,type))continue;double d=Math.hypot(x-cx,z-cz)/scale;
            double s=Math.abs(d-radius)*40+map.getSlope(x,z)*3+noise(seed,x,z)*.035;
            if(s<best){best=s;start=new Cell(x,z,(int)Math.round(s*100));}
        }
        if(start==null)return;
        Comparator<Cell> order=Comparator.comparingInt(Cell::score).thenComparingInt(Cell::x).thenComparingInt(Cell::z);
        PriorityQueue<Cell> queue=new PriorityQueue<>(order);Set<Long> queued=new HashSet<>(),chosen=new LinkedHashSet<>();
        queue.add(start);queued.add(key(start.x,start.z));int sy=map.getSurfaceY(start.x,start.z);
        while(!queue.isEmpty()&&chosen.size()<target){Cell c=queue.remove();long k=key(c.x,c.z);if(blocked.contains(k)||!usable(map,blocked,c.x,c.z,type))continue;
            chosen.add(k);
            for(int[] d:CARDINAL){int nx=c.x+d[0],nz=c.z+d[1];long nk=key(nx,nz);if(!map.inBounds(nx,nz)||!queued.add(nk)||!usable(map,blocked,nx,nz,type))continue;
                int terrain=(int)Math.round(map.getSlope(nx,nz)*8+Math.abs(map.getSurfaceY(nx,nz)-sy)*3);
                int distance=Math.abs(nx-start.x)+Math.abs(nz-start.z);int ragged=noise(seed,nx,nz);
                queue.add(new Cell(nx,nz,distance*6+terrain*5+ragged));
            }
        }
        if(chosen.size()<12)return;
        LandUseArea area=new LandUseArea();area.id=type+"_0";area.type=type;area.minY=Integer.MAX_VALUE;area.maxY=Integer.MIN_VALUE;
        List<Long> sorted=new ArrayList<>(chosen);Collections.sort(sorted);
        for(long k:sorted){int x=x(k),z=z(k),y=map.getSurfaceY(x,z);area.cells.add(new int[]{x,z});area.minY=Math.min(area.minY,y);area.maxY=Math.max(area.maxY,y);
            boolean boundary=false;for(int[] d:CARDINAL)if(!chosen.contains(key(x+d[0],z+d[1]))){boundary=true;break;}if(boundary)area.boundary2D.add(new int[]{x,z});
            GroundColumn c=new GroundColumn(x,z,y,y,y+2,type);c.structure="surface";ir.groundColumns.add(c);blocked.add(k);ir.search.constructionEdits+=PlanConstruction.columnEditCount(c);
        }
        // A solid rectangle is explicitly not an accepted natural region.
        int minX=area.cells.stream().mapToInt(a->a[0]).min().orElse(0),maxX=area.cells.stream().mapToInt(a->a[0]).max().orElse(0);
        int minZ=area.cells.stream().mapToInt(a->a[1]).min().orElse(0),maxZ=area.cells.stream().mapToInt(a->a[1]).max().orElse(0);
        if(area.cells.size()==(maxX-minX+1)*(maxZ-minZ+1)){ // deterministically notch one boundary cell
            int[] notch=area.boundary2D.getLast();long nk=key(notch[0],notch[1]);area.cells.removeIf(a->a[0]==notch[0]&&a[1]==notch[1]);
            GroundColumn removed=ir.groundColumns.stream().filter(c->c.x==notch[0]&&c.z==notch[1]&&type.equals(c.kind)).findFirst().orElse(null);
            if(removed!=null){ir.groundColumns.remove(removed);ir.search.constructionEdits-=PlanConstruction.columnEditCount(removed);}
            blocked.remove(nk);
        }
        // Recompute from the final cell set so the boundary never references a notched-away cell.
        Set<Long> finalCells=new HashSet<>();for(int[] cell:area.cells)finalCells.add(key(cell[0],cell[1]));
        area.boundary2D.clear();area.minY=Integer.MAX_VALUE;area.maxY=Integer.MIN_VALUE;
        for(int[] cell:area.cells){int y=map.getSurfaceY(cell[0],cell[1]);area.minY=Math.min(area.minY,y);area.maxY=Math.max(area.maxY,y);boolean boundary=false;for(int[] d:CARDINAL)if(!finalCells.contains(key(cell[0]+d[0],cell[1]+d[1]))){boundary=true;break;}if(boundary)area.boundary2D.add(cell.clone());}
        if("farmland".equals(type)){Set<Long> border=new HashSet<>();for(int[] cell:area.boundary2D)border.add(key(cell[0],cell[1]));
            for(GroundColumn c:ir.groundColumns)if("farmland".equals(c.kind)&&border.contains(key(c.x,c.z)))c.surfaceMaterial="coarse_dirt";
        }
        ir.landUses.add(area);
    }
    private static boolean usable(HeightfieldMap map,Set<Long> blocked,int x,int z,String type){
        if(!map.inBounds(x,z)||blocked.contains(key(x,z))||RoadTerrain.wet(map,x,z))return false;
        if(map.getObstacle(x,z)!=HeightfieldMap.ObstacleType.NONE)return false;
        double limit="farmland".equals(type)?1.55:2.8;return map.getSlope(x,z)<=limit;
    }
    private static int noise(long seed,int x,int z){long v=seed^((long)x*0x9e3779b97f4a7c15L)^((long)z*0xc2b2ae3d27d4eb4fL);v^=v>>>33;v*=0xff51afd7ed558ccdL;v^=v>>>33;return (int)(v&63L);}
}
