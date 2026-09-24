import java.nio.file.*;
import java.util.*;
import java.util.zip.GZIPOutputStream;
import java.nio.charset.StandardCharsets;
import com.google.gson.*;
import org.mcsettlement.planner.*;
import org.mcsettlement.planner.simulation.*;
import org.mcsettlement.planner.regression.TerrainAudit;

/** Version-neutral evidence harness: compile this SAME file against either source snapshot. */
public final class RouteRichnessExport {
    public static void main(String[] args)throws Exception {
        if(args.length!=12)throw new IllegalArgumentException("out width depth relief type terrainSeed planSeed plots directions diagonal palette singlePresetId");
        Path out=Path.of(args[0]);Files.createDirectories(out);
        int w=Integer.parseInt(args[1]),d=Integer.parseInt(args[2]),relief=Integer.parseInt(args[3]);String type=args[4];
        long ts=Long.parseLong(args[5]),ps=Long.parseLong(args[6]);int count=Integer.parseInt(args[7]),directions=Integer.parseInt(args[8]);
        boolean diagonal=Boolean.parseBoolean(args[9]);String palette=args[10],single=args[11];
        var result=SimulatedSettlementPipeline.run(w,d,58,relief,type,ts,ps,count,directions,diagonal,new SettlementPlanner.SearchBudget(),palette,single);
        var req=new SettlementPlanner.PlanRequest();req.seed=ps;req.targetPlots=count;req.roadDirections=directions;req.diagonalBuildings=diagonal;req.presetPalette=palette;req.singlePresetId=single;
        var audit=TerrainAudit.audit(result.heightfield,req,result.plan);
        Gson gson=new GsonBuilder().setPrettyPrinting().create();
        Files.writeString(out.resolve("PlanningIR.json"),gson.toJson(result.plan));
        Map<String,Object> s=new LinkedHashMap<>();
        s.put("config",List.of(w,d,58,relief,type,ts,ps,count,directions,diagonal,palette,single));
        s.put("status",result.plan.status);s.put("plots",result.plan.plots.size());s.put("originalTerrainHash",result.originalTerrainHash);s.put("planHash",result.planHash);
        s.put("constructedWorldHash",SimulatedSettlementPipeline.hashWorld(result.worldAfter));s.put("search",result.plan.search);s.put("audit",audit);s.put("network",result.plan.transportNetwork.metrics);
        s.put("earthworks",result.plan.earthworks);s.put("constructionEdits",result.constructionEdits);s.put("stairBlocks",result.stairBlocks);
        s.put("roadColumnCount",result.plan.groundColumns.stream().filter(c->"road".equals(c.kind)).count());
        Files.writeString(out.resolve("summary.json"),gson.toJson(s));
        int[][] fields=new int[3][w*d];for(int z=0;z<d;z++)for(int x=0;x<w;x++){
            int i=z*w+x;fields[0][i]=result.heightfield.getSurfaceY(x,z);fields[1][i]=result.heightfield.getWaterY(x,z);fields[2][i]=result.heightfield.getObstacle(x,z).ordinal();}
        byte[] bytes=new Gson().toJson(Map.of("width",w,"depth",d,"channels",List.of("height","waterY","obstacleOrdinal"),"fields",fields)).getBytes(StandardCharsets.UTF_8);
        try(var zip=new GZIPOutputStream(Files.newOutputStream(out.resolve("terrain.json.gz")))){zip.write(bytes);}
        System.out.println(gson.toJson(s));
        if(audit.violations()!=0||audit.reachable()!=result.plan.plots.size())throw new AssertionError("Hard audit failed: "+audit);
    }
}
