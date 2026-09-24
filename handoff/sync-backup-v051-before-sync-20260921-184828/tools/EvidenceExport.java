import java.nio.file.*;
import java.util.*;
import com.google.gson.*;
import org.mcsettlement.planner.*;
import org.mcsettlement.planner.ir.PlanningIR;
import org.mcsettlement.planner.simulation.*;
import org.mcsettlement.planner.regression.TerrainAudit;
/** Version-neutral evidence export, compiled against baseline or implementation. */
public class EvidenceExport {
 public static void main(String[] args)throws Exception{
  int w=Integer.parseInt(args[1]),d=Integer.parseInt(args[2]),relief=Integer.parseInt(args[3]);String type=args[4];long ts=Long.parseLong(args[5]),ps=Long.parseLong(args[6]);int count=Integer.parseInt(args[7]);
  String palette=args.length>8?args[8]:"classic";Path out=Path.of(args[0]);Files.createDirectories(out);
  var b=new SettlementPlanner.SearchBudget();long start=System.nanoTime();SimulatedSettlementPipeline.PipelineResult r;
  if("classic".equals(palette))r=SimulatedSettlementPipeline.run(w,d,58,relief,type,ts,ps,count,8,false,b);
  else r=(SimulatedSettlementPipeline.PipelineResult)SimulatedSettlementPipeline.class.getMethod("run",int.class,int.class,int.class,int.class,String.class,long.class,long.class,int.class,int.class,boolean.class,SettlementPlanner.SearchBudget.class,String.class,String.class).invoke(null,w,d,58,relief,type,ts,ps,count,8,false,b,palette,null);
  long elapsed=(System.nanoTime()-start)/1000000;
  Gson gson=new GsonBuilder().setPrettyPrinting().create();Files.writeString(out.resolve("PlanningIR.json"),gson.toJson(r.plan));
  var req=new SettlementPlanner.PlanRequest();req.seed=ps;req.targetPlots=count;
  var audit=TerrainAudit.audit(r.heightfield,req,r.plan);
  Map<String,Object> summary=new LinkedHashMap<>();summary.put("scene",List.of(w,d,58,relief,type,ts,ps,count,palette));summary.put("status",r.plan.status);summary.put("plots",r.plan.plots.size());summary.put("elapsed_ms",elapsed);summary.put("originalTerrainHash",r.originalTerrainHash);summary.put("planHash",r.planHash);summary.put("search",r.plan.search);summary.put("earthworks",r.plan.earthworks);summary.put("audit",audit);
  summary.put("corridorCount",r.plan.transportNetwork.corridors.size());try{summary.put("network",r.plan.transportNetwork.getClass().getField("metrics").get(r.plan.transportNetwork));}catch(NoSuchFieldException ignored){}
  Files.writeString(out.resolve("summary.json"),gson.toJson(summary));
  int[][] fields=new int[4][w*d];for(int z=0;z<d;z++)for(int x=0;x<w;x++){int i=z*w+x,h=r.heightfield.getSurfaceY(x,z);fields[0][i]=h;fields[1][i]=r.worldBefore.getBlock(x,h,z).id;fields[2][i]=r.heightfield.getWaterY(x,z)>=0?1:0;fields[3][i]=r.worldBefore.getBlock(x,h+1,z)==SimulatedVoxelWorld.VoxelType.OAK_LOG?1:0;}
  Files.writeString(out.resolve("terrain-fields.json"),new Gson().toJson(Map.of("width",w,"depth",d,"channels",List.of("height","material","water","trunk"),"fields",fields)));
  System.out.println(gson.toJson(summary));
 }
}
