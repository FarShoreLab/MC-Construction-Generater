package org.mcsettlement.planner.preset;

import java.util.*;
import org.mcsettlement.planner.SettlementPlanner.BuildingRequirement;

/** Locked named templates, not size multipliers. Explicit request requirements remain authoritative. */
public final class PresetPalette {
    public static final Set<String> NAMES=Set.of("classic","mixed","compact","spacious","single","crafted","estates");
    private static final List<String> CRAFTED=List.of("meadow_hut","timber_cottage","village_bakery","canal_rowhouse","orchard_farmhouse","artisan_court","courtyard_hostel","cloister_market","stone_bell_tower","garden_manor","merchant_guild","harvest_barn");
    private static final List<String> MIXED=List.of("town_hall","stepped_workshop","l_cottage",
            "t_lodge","courtyard_house","octagonal_lodge","u_inn","cross_hall",
            "long_cottage","courtyard_market","l_warehouse","square_cabin");
    private static final List<String> COMPACT=List.of("square_cabin","long_cottage","t_cabin","l_cottage","octagonal_lodge");
    private static final List<String> SPACIOUS=List.of("cross_hall","l_warehouse","courtyard_house","u_inn","courtyard_market","t_lodge");
    private PresetPalette() {}
    /** Bound estate allocation by dry terrain, requested count and the existing edit budget. */
    public static List<BuildingRequirement> capacityRequirements(org.mcsettlement.planner.terrain.HeightfieldMap map,
            org.mcsettlement.planner.SettlementPlanner.PlanRequest req) {
        int dry=0;
        for(int z=map.getMinZ();z<map.getMinZ()+map.getDepth();z++)for(int x=map.getMinX();x<map.getMinX()+map.getWidth();x++)
            if(!org.mcsettlement.planner.RoadTerrain.wet(map,x,z)&&map.getSlope(x,z)<=req.parcelConfig.maxGroundSlope)dry++;
        int areaLimit=Math.min((int)(dry*.22),(int)(req.searchBudget.groundColumns*.6));
        int editsLimit=(int)(req.searchBudget.constructionEdits*.6),used=0,edits=0;
        List<String> selected=new ArrayList<>();
        int maxEstates=Math.min(3,req.targetPlots/4),shortSide=Math.min(map.getWidth(),map.getDepth());
        for(int size:new int[]{96,64,48}){
            if(selected.size()>=maxEstates||shortSide<size*4||used+size*size>areaLimit||edits+size*size*23>editsLimit)continue;
            // Require an actual dry, gently varying patch before assigning a large exact preset.
            if(!hasEstateSite(map,size,req))continue;
            selected.add("estate_"+size);used+=size*size;edits+=size*size*23;
        }
        List<String> small=List.of("meadow_hut","timber_cottage","village_bakery","orchard_farmhouse");
        List<String> medium=List.of("artisan_court","canal_rowhouse","harvest_barn","courtyard_hostel");
        for(int i=selected.size();i<req.targetPlots;i++){
            String id=i%4==3&&shortSide>=128?medium.get((i/4)%medium.size()):small.get(i%small.size());
            BuildingPreset b=BuildingPresetRegistry.getInstance().getPreset(id);
            if(used+b.footprintArea()>areaLimit||edits+b.footprintArea()*(b.sizeY+1)>editsLimit)id="meadow_hut";
            selected.add(id);b=BuildingPresetRegistry.getInstance().getPreset(id);used+=b.footprintArea();edits+=b.footprintArea()*(b.sizeY+1);
        }
        if(used>areaLimit||edits>editsLimit)throw new IllegalArgumentException("MAP_CAPACITY_EXCEEDED: reduce targetPlots or increase map size");
        List<BuildingRequirement> out=new ArrayList<>();
        for(int i=0;i<selected.size();i++){
            BuildingRequirement q=requirements("single",selected.get(i),1).getFirst();q.id="palette_"+(i+1);q.placement=i==0?"center":"any";out.add(q);
        }
        return out;
    }
    private static boolean hasEstateSite(org.mcsettlement.planner.terrain.HeightfieldMap map,int size,
            org.mcsettlement.planner.SettlementPlanner.PlanRequest req){
        int step=Math.max(8,size/2);
        for(int oz=map.getMinZ()+8;oz+size+8<=map.getMinZ()+map.getDepth();oz+=step)
            for(int ox=map.getMinX()+8;ox+size+8<=map.getMinX()+map.getWidth();ox+=step){
                int min=Integer.MAX_VALUE,max=Integer.MIN_VALUE;boolean usable=true;
                for(int z=oz;z<oz+size&&usable;z++)for(int x=ox;x<ox+size;x++){
                    var obstacle=map.getObstacle(x,z);
                    if(org.mcsettlement.planner.RoadTerrain.wet(map,x,z)||map.getSlope(x,z)>req.parcelConfig.maxGroundSlope||
                        (obstacle==org.mcsettlement.planner.terrain.HeightfieldMap.ObstacleType.PROTECTED||obstacle==org.mcsettlement.planner.terrain.HeightfieldMap.ObstacleType.EXISTING_BUILDING||obstacle==org.mcsettlement.planner.terrain.HeightfieldMap.ObstacleType.STEEP_CLIFF)){usable=false;break;}
                    int y=map.getSurfaceY(x,z);min=Math.min(min,y);max=Math.max(max,y);
                    if(max-min>req.parcelConfig.maxCutBudget+req.parcelConfig.maxFillBudget){usable=false;break;}
                }
                if(usable)return true;
            }
        return false;
    }
    public static List<BuildingRequirement> requirements(String name,String single,int count) {return requirements(name,single,count,null);}
    /** Expert layout modes may change the automatic preset mix; explicit requirements/single presets stay exact. */
    public static List<BuildingRequirement> requirements(String name,String single,int count,String settlementMode) {
        if(!NAMES.contains(name))throw new IllegalArgumentException("UNKNOWN_PRESET_PALETTE");
        if("classic".equals(name))return List.of();
        List<String> ids=new ArrayList<>(switch(name){case "crafted"->CRAFTED;case "mixed"->MIXED;case "compact"->COMPACT;case "spacious"->SPACIOUS;
            default->{if(single==null||BuildingPresetRegistry.getInstance().getPreset(single)==null)throw new IllegalArgumentException("UNKNOWN_SINGLE_PRESET");yield List.of(single);}});
        if(ids.size()>1&&("village".equals(settlementMode)||"city".equals(settlementMode))){
            Comparator<String> urban=Comparator.<String>comparingDouble(PresetPalette::urbanMass).thenComparing(String::compareTo);
            ids.sort("city".equals(settlementMode)?urban.reversed():urban);
        }
        List<BuildingRequirement> out=new ArrayList<>();
        for(int i=0;i<count;i++){
            BuildingPreset b=BuildingPresetRegistry.getInstance().getPreset(ids.get(i%ids.size()));
            BuildingRequirement q=new BuildingRequirement();q.id="palette_"+(i+1);q.presetId=b.id;q.purpose=b.category;
            // Bounds allow every rotation, including the optional exact 45-degree raster; dimensions are never scaled.
            q.minWidth=q.minDepth=3;q.maxWidth=q.maxDepth=Math.max(32,Math.max(b.sizeX,b.sizeZ));q.heightLimit=b.sizeY;
            if(i==0)q.placement="center";
            out.add(q);
        }
        return out;
    }
    private static double urbanMass(String id){
        BuildingPreset b=BuildingPresetRegistry.getInstance().getPreset(id);
        return b==null?0:b.sizeY*2.0+b.footprintArea()*.08;
    }
 }
