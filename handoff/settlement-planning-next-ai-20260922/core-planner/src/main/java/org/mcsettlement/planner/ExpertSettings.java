package org.mcsettlement.planner;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.mcsettlement.planner.preset.BuildingPresetRegistry;

/** Small deterministic rule configuration. No model calls; pins are hard spatial constraints. */
public final class ExpertSettings {
    public double minBBoxCoverage=.25, minMinorAxisRatio=.30;
    public boolean allowBridges=true, autoDock=true;
    public int maxBridgeSpan=48;
    public double buildingRepulsion=1.0; // soft spacing penalty; exact human pins take precedence
    public int roadMergeDistance=7; // 0 disables the proximity field and explicit centerline reuse
    public List<Pin> pins=new ArrayList<>();
    public static final class Pin {
        public String id, kind="building", presetId="square_cabin", facing="NORTH", medium="land", connectTo="network";
        public int x,z;
        public Integer y;
    }
    public void validate(int width,int depth,int minX,int minZ,int target) {
        if(!Double.isFinite(minBBoxCoverage)||minBBoxCoverage<0||minBBoxCoverage>.85||
           !Double.isFinite(minMinorAxisRatio)||minMinorAxisRatio<0||minMinorAxisRatio>.9||maxBridgeSpan<4||maxBridgeSpan>96)
            throw new IllegalArgumentException("INVALID_EXPERT_SETTINGS");
        if(!Double.isFinite(buildingRepulsion)||buildingRepulsion<0||buildingRepulsion>3||roadMergeDistance<0||roadMergeDistance>16)
            throw new IllegalArgumentException("INVALID_NETWORK_DIVERSITY_SETTINGS");
        if(pins==null||pins.size()>8)throw new IllegalArgumentException("PINS_LIMIT_8");
        Set<String> ids=new HashSet<>();int buildings=0;
        for(Pin p:pins) {
            if(p==null||p.id==null||!p.id.matches("[a-zA-Z][a-zA-Z0-9_-]{0,31}")||Set.of("network","entry").contains(p.id)||!ids.add(p.id))throw new IllegalArgumentException("INVALID_OR_DUPLICATE_PIN_ID");
            if(!Set.of("building","dock").contains(String.valueOf(p.kind))||!Set.of("land","water").contains(String.valueOf(p.medium))||!Set.of("NORTH","EAST","SOUTH","WEST").contains(String.valueOf(p.facing))||p.connectTo==null||p.id.equals(p.connectTo))throw new IllegalArgumentException("INVALID_PIN_OPTIONS: "+p.id);
            if(p.x<minX||p.x>=minX+width||p.z<minZ||p.z>=minZ+depth||p.y!=null&&(p.y< -2000||p.y>2000))throw new IllegalArgumentException("PIN_OUT_OF_BOUNDS: "+p.id);
            if("building".equals(p.kind)) {buildings++;if(BuildingPresetRegistry.getInstance().getPreset(p.presetId)==null)throw new IllegalArgumentException("UNKNOWN_PIN_PRESET: "+p.id);}
            if(("water".equals(p.medium)||"dock".equals(p.kind))&&!allowBridges)throw new IllegalArgumentException("WATER_PIN_REQUIRES_BRIDGES: "+p.id);
        }
        if(buildings>target)throw new IllegalArgumentException("PIN_BUILDINGS_EXCEED_TARGET");
        for(Pin p:pins)if(!Set.of("network","entry").contains(p.connectTo)&&!ids.contains(p.connectTo))throw new IllegalArgumentException("UNKNOWN_CONNECTION_TARGET: "+p.connectTo);
    }
    /** Strict bounded input; unknown fields and fractional integer coordinates are rejected. */
    public static List<Pin> parsePins(String json) {
        if(json==null||json.getBytes(StandardCharsets.UTF_8).length>8192)throw new IllegalArgumentException("PIN_JSON_LIMIT");
        List<Pin> out=new ArrayList<>();
        try(JsonReader reader=new JsonReader(new StringReader(json))) {
            reader.setLenient(false);
            if(reader.peek()!=JsonToken.BEGIN_ARRAY)throw new IllegalArgumentException("PINS_MUST_BE_ARRAY_MAX_8");
            reader.beginArray();
            while(reader.hasNext()) {
                if(out.size()>=8||reader.peek()!=JsonToken.BEGIN_OBJECT)throw new IllegalArgumentException("PIN_MUST_BE_OBJECT_MAX_8");
                Pin p=new Pin();Set<String> keys=new HashSet<>();reader.beginObject();
                while(reader.hasNext()) {
                    String k=reader.nextName();if(!keys.add(k))throw new IllegalArgumentException("DUPLICATE_PIN_FIELD: "+k);
                    switch(k) {
                        case "x" -> p.x=integer(reader);
                        case "z" -> p.z=integer(reader);
                        case "y" -> {if(reader.peek()==JsonToken.NULL)reader.nextNull();else p.y=integer(reader);}
                        case "id" -> p.id=text(reader);
                        case "kind" -> p.kind=text(reader);
                        case "presetId" -> p.presetId=text(reader);
                        case "facing" -> p.facing=text(reader);
                        case "medium" -> p.medium=text(reader);
                        case "connectTo" -> p.connectTo=text(reader);
                        default -> throw new IllegalArgumentException("UNKNOWN_PIN_FIELD: "+k);
                    }
                }
                reader.endObject();if(!keys.containsAll(Set.of("id","x","z")))throw new IllegalArgumentException("PIN_REQUIRES_ID_X_Z");out.add(p);
            }
            reader.endArray();if(reader.peek()!=JsonToken.END_DOCUMENT)throw new IllegalArgumentException("TRAILING_PIN_JSON");
        }catch(IOException|NumberFormatException|ArithmeticException ex){throw new IllegalArgumentException("INVALID_PIN_JSON: "+ex.getMessage(),ex);}
        return out;
    }
    private static String text(JsonReader reader)throws IOException {
        if(reader.peek()!=JsonToken.STRING)throw new IllegalArgumentException("PIN_STRING_REQUIRED");return reader.nextString();
    }
    private static int integer(JsonReader reader)throws IOException {
        if(reader.peek()!=JsonToken.NUMBER)throw new IllegalArgumentException("PIN_INTEGER_REQUIRED");return new BigDecimal(reader.nextString()).intValueExact();
    }
}
