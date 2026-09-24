package org.mcsettlement.planner.render;

import org.mcsettlement.planner.ir.PlanningIR;
import org.mcsettlement.planner.ir.PlanningIR.Plot;
import org.mcsettlement.planner.preset.PlannedBuilding;
import org.mcsettlement.planner.terrain.HeightfieldMap;
import org.mcsettlement.planner.terrain.HeightfieldMap.ObstacleType;
import java.util.*;

/** Inspection outputs share the exact occupied raster and authoritative pavement, not hull bounding boxes. */
public class PlanPreviewRenderer {
    public static List<int[]> localFootprint(Plot p){
        if(p.footprint!=null&&!p.footprint.isEmpty())return p.footprint;
        List<int[]> cells=new ArrayList<>();int w=p.builder.footprintSize[0],d=p.builder.footprintSize[1];
        for(int x=0;x<w;x++)for(int z=0;z<d;z++)cells.add(new int[]{x,z});return cells;
    }
    public static int[] renderPixelBuffer(HeightfieldMap map,PlanningIR ir){
        int w=map.getWidth(),depth=map.getDepth();int[] pixels=new int[w*depth];
        for(int z=0;z<depth;z++)for(int x=0;x<w;x++){var o=map.getLocalObstacle(x,z);float slope=map.getLocalSlope(x,z);int shade=Math.min(255,Math.max(100,140+map.getLocalSurfaceY(x,z)*2));
            int color=0xFF000000|((shade/2)<<16)|(shade<<8)|(shade/3);
            if(o==ObstacleType.WATER||o==ObstacleType.WATER_DEEP)color=0xFF2B5B84;
            else if(o==ObstacleType.EXISTING_BUILDING||o==ObstacleType.PROTECTED)color=0xFF6B506B;
            else if(o==ObstacleType.TREE_TRUNK)color=0xFF1E5128;
            else if(o==ObstacleType.STEEP_CLIFF||slope>1.2f)color=0xFF5D4037;else if(slope>.4f)color=0xFF9E9D24;pixels[z*w+x]=color;}
        if(ir==null)return pixels;
        for(var c:ir.groundColumns){int x=c.x-map.getMinX(),z=c.z-map.getMinZ();if(map.inLocalBounds(x,z))pixels[z*w+x]="foundation".equals(c.kind)?0xFFFFEB3B:"stair".equals(c.structure)?0xFFFFA000:0xFFEDE0D4;}
        // Legacy inspection only; no construction authority is inferred from these graphics.
        if(ir.groundColumns.isEmpty())for(Plot p:ir.plots){int ox=PlannedBuilding.originX(p)-map.getMinX(),oz=PlannedBuilding.originZ(p)-map.getMinZ();for(int[] c:localFootprint(p))if(map.inLocalBounds(ox+c[0],oz+c[1]))pixels[(oz+c[1])*w+ox+c[0]]=0xFFFFEB3B;}
        return pixels;
    }
    public static String renderAscii(HeightfieldMap map,PlanningIR ir){
        int w=map.getWidth(),d=map.getDepth();int[] pixels=renderPixelBuffer(map,ir);char[][] canvas=new char[d][w];
        for(int z=0;z<d;z++)for(int x=0;x<w;x++)canvas[z][x]=switch(pixels[z*w+x]){case 0xFF2B5B84->'~';case 0xFF6B506B->'X';case 0xFF1E5128->'T';case 0xFF5D4037->'^';case 0xFF9E9D24->':';case 0xFFFFEB3B->'B';case 0xFFFFA000->'#';case 0xFFEDE0D4->'*';default->'.';};
        for(Plot p:ir.plots){int x=p.entrance.accessPoint[0]-map.getMinX(),z=p.entrance.accessPoint[2]-map.getMinZ();if(map.inLocalBounds(x,z))canvas[z][x]='E';}
        StringBuilder out=new StringBuilder("=== Settlement Preview ["+w+"x"+d+"] Seed: "+ir.metadata.randomSeed+" ===\nLegend: . ground : slope ^ cliff ~ water X protected * road # stair B footprint E entrance\n");for(char[] row:canvas)out.append(row).append('\n');return out.toString();
    }
    public static String renderSvg(HeightfieldMap map,PlanningIR ir){
        int w=map.getWidth(),d=map.getDepth(),scale=8;int[] pixels=renderPixelBuffer(map,ir);
        StringBuilder out=new StringBuilder("<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 "+w*scale+" "+d*scale+"'>\n");
        // Run-length rows reduce SVG size without decimating any diagonal cells.
        for(int z=0;z<d;z++)for(int x=0;x<w;){int next=x+1,color=pixels[z*w+x];while(next<w&&pixels[z*w+next]==color)next++;out.append(String.format(Locale.ROOT,"<rect x='%d' y='%d' width='%d' height='%d' fill='#%06x'/>%n",x*scale,z*scale,(next-x)*scale,scale,color&0xFFFFFF));x=next;}
        for(Plot p:ir.plots){String id=p.id==null?"":p.id.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");out.append("<text x='").append((PlannedBuilding.originX(p)-map.getMinX())*scale).append("' y='").append((PlannedBuilding.originZ(p)-map.getMinZ())*scale+12).append("' font-size='10'>").append(id).append(" Y=").append(p.elevation.baseElevation).append("</text>\n");}
        return out.append("</svg>\n").toString();
    }
}
