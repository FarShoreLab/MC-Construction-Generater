package org.mcsettlement.planner.preset;

import java.util.*;
import org.mcsettlement.planner.SettlementPlanner.BuildingRequirement;

/** Locked named templates, not size multipliers. Explicit request requirements remain authoritative. */
public final class PresetPalette {
    public static final Set<String> NAMES=Set.of("classic","mixed","compact","spacious","single");
    private static final List<String> MIXED=List.of("town_hall","stepped_workshop","l_cottage",
            "t_lodge","courtyard_house","octagonal_lodge","u_inn","cross_hall",
            "long_cottage","courtyard_market","l_warehouse","square_cabin");
    private static final List<String> COMPACT=List.of("square_cabin","long_cottage","t_cabin","l_cottage","octagonal_lodge");
    private static final List<String> SPACIOUS=List.of("cross_hall","l_warehouse","courtyard_house","u_inn","courtyard_market","t_lodge");
    private PresetPalette() {}
    public static List<BuildingRequirement> requirements(String name,String single,int count) {
        if(!NAMES.contains(name))throw new IllegalArgumentException("UNKNOWN_PRESET_PALETTE");
        if("classic".equals(name))return List.of();
        List<String> ids=switch(name){case "mixed"->MIXED;case "compact"->COMPACT;case "spacious"->SPACIOUS;
            default->{if(single==null||BuildingPresetRegistry.getInstance().getPreset(single)==null)throw new IllegalArgumentException("UNKNOWN_SINGLE_PRESET");yield List.of(single);}};
        List<BuildingRequirement> out=new ArrayList<>();
        for(int i=0;i<count;i++){
            BuildingPreset b=BuildingPresetRegistry.getInstance().getPreset(ids.get(i%ids.size()));
            BuildingRequirement q=new BuildingRequirement();q.id="palette_"+(i+1);q.presetId=b.id;q.purpose=b.category;
            // Bounds allow every rotation, including the optional exact 45-degree raster; dimensions are never scaled.
            q.minWidth=q.minDepth=3;q.maxWidth=q.maxDepth=32;q.heightLimit=b.sizeY;
            if(i==0)q.placement="center";
            out.add(q);
        }
        return out;
    }
}
