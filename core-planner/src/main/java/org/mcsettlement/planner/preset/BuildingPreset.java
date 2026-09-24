package org.mcsettlement.planner.preset;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.*;

/**
 * Standard Single-Building Preset Model.
 * Encapsulates 3D voxel layer architecture, block palette, entrance constraints,
 * and 3D spatial rotation & theme adaptation capabilities.
 */
public class BuildingPreset {
    public String id;
    public String name;
    public String category = "residential";
    public List<String> tags = new ArrayList<>();
    public List<String> styles = new ArrayList<>();
    public String author = "MCSettlement";
    public String description = "";
    /** Empty means the legacy full rectangle. '#' occupies a column; '.' is untouched terrain. */
    public List<String> footprintMask = new ArrayList<>();
    public String footprintShape = "rectangle";
    public String sizeTier = "standard";
    public int sizeX;
    public int sizeY;
    public int sizeZ;
    public EntranceSpec entrance = new EntranceSpec();
    public List<EntranceSpec> entrances = new ArrayList<>(); // alternatives; empty preserves the legacy entrance
    public List<ComponentSpec> components = new ArrayList<>(); // semantic masks, all inside the reserved footprint
    public boolean archived;
    public int selectedEntrance;
    public Map<String, String> palette = new HashMap<>();
    public Map<String, Map<String, String>> themeReplacements = new HashMap<>();
    public List<List<String>> layers = new ArrayList<>(); // layers[y][z] -> string of length sizeX

    public static class EntranceSpec {
        public String facing = "SOUTH"; // "NORTH", "SOUTH", "EAST", "WEST"
        public int x = 0;
        public int z = 0;

        public EntranceSpec() {}
        public EntranceSpec(String facing, int x, int z) {
            this.facing = facing;
            this.x = x;
            this.z = z;
        }

        public EntranceSpec copy() {
            return new EntranceSpec(this.facing, this.x, this.z);
        }
    }

    public static class ComponentSpec {
        public String id,role; // main, annex, courtyard, path, garden
        public List<String> mask = new ArrayList<>();
    }
    public int entranceCount(){return entrances.isEmpty()?1:entrances.size();}
    public BuildingPreset selectEntrance(int index){
        if(index<0||index>=entranceCount())throw new IllegalArgumentException("INVALID_PRESET_ENTRANCE_INDEX");
        BuildingPreset copy=copy();copy.selectedEntrance=index;
        if(!copy.entrances.isEmpty())copy.entrance=copy.entrances.get(index).copy();
        return copy;
    }

    public BuildingPreset() {}

    public boolean occupies(int x, int z) {
        if (x < 0 || z < 0 || x >= sizeX || z >= sizeZ) return false;
        return footprintMask == null || footprintMask.isEmpty() || footprintMask.get(z).charAt(x) == '#';
    }
    public int footprintArea() {
        int n=0; for(int z=0;z<sizeZ;z++)for(int x=0;x<sizeX;x++)if(occupies(x,z))n++; return n;
    }
    /** Reject, never trim, malformed explicit masks or voxels outside the declared envelope. */
    public void validateFootprint() {
        if(sizeX<1||sizeX>96||sizeZ<1||sizeZ>96||sizeY<3||sizeY>48)
            throw new IllegalArgumentException("INVALID_PRESET_SIZE: "+id);
        if(footprintMask==null||footprintMask.isEmpty())return;
        if(footprintMask.size()!=sizeZ)throw new IllegalArgumentException("MASK_DEPTH: "+id);
        for(String row:footprintMask)if(row==null||row.length()!=sizeX||!row.matches("[.#]+"))
            throw new IllegalArgumentException("MASK_ROW: "+id);
        if(entrances==null||entrances.size()>8||components==null)throw new IllegalArgumentException("INVALID_PRESET_OPTIONS: "+id);
        for(EntranceSpec door:entrances){
            if(door==null||!Set.of("NORTH","EAST","SOUTH","WEST").contains(door.facing)||!occupies(door.x,door.z))throw new IllegalArgumentException("INVALID_CANDIDATE_DOOR: "+id);
            int[] dir=PlannedBuilding.direction(door.facing);
            if(occupies(door.x+dir[0],door.z+dir[1]))throw new IllegalArgumentException("DOOR_MUST_FACE_OUTSIDE_MASK: "+id);
        }
        Set<String> partIds=new HashSet<>();
        for(ComponentSpec part:components){
            if(part==null||part.id==null||!partIds.add(part.id)||!Set.of("main","annex","courtyard","path","garden").contains(part.role)||part.mask.size()!=sizeZ)throw new IllegalArgumentException("INVALID_COMPONENT: "+id);
            for(int z=0;z<sizeZ;z++){String row=part.mask.get(z);if(row.length()!=sizeX||!row.matches("[.#]+"))throw new IllegalArgumentException("INVALID_COMPONENT_MASK: "+id);
                for(int x=0;x<sizeX;x++)if(row.charAt(x)=='#'&&!occupies(x,z))throw new IllegalArgumentException("COMPONENT_OUTSIDE_FOOTPRINT: "+id);}
        }
        if(entrance==null||!occupies(entrance.x,entrance.z))throw new IllegalArgumentException("MASK_DOOR: "+id);
        if(layers==null||layers.size()!=sizeY)throw new IllegalArgumentException("MASK_LAYERS: "+id);
        for(var layer:layers){if(layer.size()!=sizeZ)throw new IllegalArgumentException("LAYER_DEPTH: "+id);
            for(int z=0;z<sizeZ;z++){String row=layer.get(z);if(row.length()!=sizeX)throw new IllegalArgumentException("LAYER_WIDTH: "+id);
                for(int x=0;x<sizeX;x++){String block=palette.get(String.valueOf(row.charAt(x)));
                    if(block==null||!occupies(x,z)&&!"minecraft:air".equals(block))throw new IllegalArgumentException("VOXEL_OUTSIDE_MASK: "+id);}}}
        Set<Integer> seen=new HashSet<>();ArrayDeque<Integer> q=new ArrayDeque<>();
        int start=entrance.z*sizeX+entrance.x;q.add(start);seen.add(start);
        while(!q.isEmpty()){int k=q.remove(),x=k%sizeX,z=k/sizeX;
            for(int[] d:new int[][]{{1,0},{-1,0},{0,1},{0,-1}}){int xx=x+d[0],zz=z+d[1],v=zz*sizeX+xx;
                if(occupies(xx,zz)&&seen.add(v))q.add(v);}}
        if(seen.size()!=footprintArea())throw new IllegalArgumentException("DISCONNECTED_MASK: "+id);
    }

    /**
     * Deep copy this preset.
     */
    public BuildingPreset copy() {
        Gson gson = new Gson();
        return gson.fromJson(gson.toJson(this), BuildingPreset.class);
    }

    /**
     * Converts layer-based representation to a 3D block array [x][y][z].
     */
    public String[][][] toBlockGrid(String theme) {
        String[][][] grid = new String[sizeX][sizeY][sizeZ];
        Map<String, String> currentThemeMap = (theme != null && themeReplacements != null) ?
                themeReplacements.get(theme) : null;

        for (int y = 0; y < sizeY; y++) {
            List<String> layerRows = (y < layers.size()) ? layers.get(y) : Collections.emptyList();
            for (int z = 0; z < sizeZ; z++) {
                String row = (z < layerRows.size()) ? layerRows.get(z) : "";
                for (int x = 0; x < sizeX; x++) {
                    String blockId = "minecraft:air";
                    if (x < row.length()) {
                        String key = String.valueOf(row.charAt(x));
                        blockId = palette.getOrDefault(key, "minecraft:air");
                    }

                    if (currentThemeMap != null && currentThemeMap.containsKey(blockId)) {
                        blockId = currentThemeMap.get(blockId);
                    }
                    grid[x][y][z] = blockId;
                }
            }
        }
        return grid;
    }

    /**
     * Rotates this preset so its entrance faces the target facing ("NORTH", "SOUTH", "EAST", "WEST").
     */
    public BuildingPreset rotateToFacing(String targetFacing) {
        if (targetFacing == null || targetFacing.equalsIgnoreCase(this.entrance.facing)) {
            return this.copy();
        }

        int currentTurns = facingToTurns(this.entrance.facing);
        int targetTurns = facingToTurns(targetFacing);
        int diffTurns = (targetTurns - currentTurns + 4) % 4;

        return rotateClockwise(diffTurns * 90);
    }

    /**
     * Rotates preset clockwise by 0, 90, 180, or 270 degrees.
     */
    public BuildingPreset rotateClockwise(int degrees) {
        int turns = ((degrees % 360) + 360) % 360 / 90;
        if (turns == 0) return this.copy();

        BuildingPreset rotated = this.copy();
        for (int t = 0; t < turns; t++) {
            rotated = rotateOnce90(rotated);
        }
        return rotated;
    }

    private static BuildingPreset rotateOnce90(BuildingPreset src) {
        BuildingPreset dst = new BuildingPreset();
        dst.id = src.id;
        dst.name = src.name;
        dst.category = src.category;
        dst.tags = new ArrayList<>(src.tags);
        dst.styles = new ArrayList<>(src.styles);
        dst.author = src.author;
        dst.archived=src.archived;dst.selectedEntrance=src.selectedEntrance;
        dst.description = src.description;
        dst.footprintShape = src.footprintShape;
        dst.sizeTier = src.sizeTier;
        dst.palette = new HashMap<>(src.palette);
        dst.themeReplacements = new HashMap<>(src.themeReplacements);

        dst.sizeY = src.sizeY;
        dst.sizeX = src.sizeZ;
        dst.sizeZ = src.sizeX;

        if(src.footprintMask!=null&&!src.footprintMask.isEmpty()) {
            for(int newZ=0;newZ<dst.sizeZ;newZ++) {
                StringBuilder row=new StringBuilder();
                for(int newX=0;newX<dst.sizeX;newX++)row.append(src.occupies(newZ,src.sizeZ-1-newX)?'#':'.');
                dst.footprintMask.add(row.toString());
            }
        }

        // Entrance rotation: Clockwise 90
        // (x, z) in (src.sizeX, src.sizeZ) -> (src.sizeZ - 1 - z, x)
        dst.entrance = new EntranceSpec();
        dst.entrance.facing = rotateFacing90(src.entrance.facing);
        dst.entrance.x = src.sizeZ - 1 - src.entrance.z;
        dst.entrance.z = src.entrance.x;
        for(EntranceSpec e:src.entrances)dst.entrances.add(new EntranceSpec(rotateFacing90(e.facing),src.sizeZ-1-e.z,e.x));
        for(ComponentSpec part:src.components){ComponentSpec rotated=new ComponentSpec();rotated.id=part.id;rotated.role=part.role;
            for(int z=0;z<dst.sizeZ;z++){StringBuilder row=new StringBuilder();for(int x=0;x<dst.sizeX;x++)row.append(part.mask.get(src.sizeZ-1-x).charAt(z));rotated.mask.add(row.toString());}dst.components.add(rotated);}

        // Rotate layers
        for (int y = 0; y < src.sizeY; y++) {
            List<String> srcRows = src.layers.get(y);
            List<String> dstRows = new ArrayList<>(dst.sizeZ);

            for (int newZ = 0; newZ < dst.sizeZ; newZ++) {
                StringBuilder rowSb = new StringBuilder();
                int oldX = newZ;
                for (int newX = 0; newX < dst.sizeX; newX++) {
                    int oldZ = src.sizeZ - 1 - newX;
                    char ch = '.';
                    if (oldZ >= 0 && oldZ < srcRows.size()) {
                        String r = srcRows.get(oldZ);
                        if (oldX >= 0 && oldX < r.length()) {
                            ch = r.charAt(oldX);
                        }
                    }
                    rowSb.append(ch);
                }
                dstRows.add(rowSb.toString());
            }
            dst.layers.add(dstRows);
        }

        // Update directional block mappings in palette if any
        Map<String, String> newPalette = new HashMap<>();
        for (Map.Entry<String, String> entry : src.palette.entrySet()) {
            newPalette.put(entry.getKey(), rotateBlockString(entry.getValue()));
        }
        dst.palette = newPalette;

        return dst;
    }

    private static String rotateFacing90(String f) {
        if (f == null) return "SOUTH";
        return switch (f.toUpperCase()) {
            case "NORTH" -> "EAST";
            case "EAST" -> "SOUTH";
            case "SOUTH" -> "WEST";
            case "WEST" -> "NORTH";
            default -> f;
        };
    }

    private static int facingToTurns(String f) {
        if (f == null) return 0;
        return switch (f.toUpperCase()) {
            case "NORTH" -> 0;
            case "EAST" -> 1;
            case "SOUTH" -> 2;
            case "WEST" -> 3;
            default -> 0;
        };
    }

    public static String rotateBlockString(String blockId) {
        if (blockId == null) return "minecraft:air";
        String res = blockId;
        // Rotate facing property
        if (res.contains("facing=north")) res = res.replace("facing=north", "facing=TEMP_E");
        if (res.contains("facing=east")) res = res.replace("facing=east", "facing=TEMP_S");
        if (res.contains("facing=south")) res = res.replace("facing=south", "facing=TEMP_W");
        if (res.contains("facing=west")) res = res.replace("facing=west", "facing=TEMP_N");

        res = res.replace("facing=TEMP_E", "facing=east")
                 .replace("facing=TEMP_S", "facing=south")
                 .replace("facing=TEMP_W", "facing=west")
                 .replace("facing=TEMP_N", "facing=north");

        // Rotate axis property
        if (res.contains("axis=x")) res = res.replace("axis=x", "axis=TEMP_Z");
        if (res.contains("axis=z")) res = res.replace("axis=z", "axis=TEMP_X");
        res = res.replace("axis=TEMP_Z", "axis=z").replace("axis=TEMP_X", "axis=x");

        return res;
    }

    public static BuildingPreset fromJson(String json) {
        return new Gson().fromJson(json, BuildingPreset.class);
    }

    public String toJson(boolean pretty) {
        Gson gson = pretty ? new GsonBuilder().setPrettyPrinting().create() : new Gson();
        return gson.toJson(this);
    }
}
