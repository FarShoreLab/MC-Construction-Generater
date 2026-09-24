import org.mcsettlement.planner.SettlementPlanner;
import org.mcsettlement.planner.regression.RegressionMain;
import org.mcsettlement.planner.terrain.HeightfieldMap.ObstacleType;
import java.util.*;
/** Deterministic supplemental invariants; not a statistical performance study. */
public class FuzzAudit {
    public static void main(String[] args) throws Exception {
        StringBuilder cases=new StringBuilder("seed,status,allocated,violations,candidates,path_expanded,exhausted,network_status,network_reasons\n");
        int placed=0,complete=0,partial=0,none=0;long start=System.nanoTime();
        String[] purposes={"residential","government","workshop","military","culture","agriculture"};
        for(int seed=0;seed<100;seed++) {
            Random random=new Random(seed);var map=RegressionMain.flat(-24,-24,48,48,64);
            for(int x=-24;x<24;x++)for(int z=-24;z<24;z++)map.setSurfaceY(x,z,64+(x+24)/(12+seed%5)+(z+24)/24);
            for(int i=0;i<6;i++){int x=random.nextInt(44)-24,z=random.nextInt(44)-24;for(int dx=0;dx<4;dx++)for(int dz=0;dz<4;dz++)map.setObstacle(x+dx,z+dz,i%2==0?ObstacleType.EXISTING_BUILDING:ObstacleType.WATER);}
            map.computeSlopes();
            var request=RegressionMain.request(RegressionMain.demand("a",purposes[seed%6],1),RegressionMain.demand("homes","residential",2));
            request.seed=seed;request.roadWidth=1+seed%5;request.parcelConfig.roadSetback=seed%5;request.parcelConfig.minPlotSpacing=seed%6;
            request.searchBudget.candidateChecks=5000;request.searchBudget.pathExpanded=50000;
            var plan=SettlementPlanner.plan(map,request);var audit=RegressionMain.audit(map,request,plan);
            if(audit.violations()!=0)throw new AssertionError("seed="+seed+" violations="+audit.violations());
            if(plan.search.candidateChecks>5000||plan.search.pathExpanded>50000)throw new AssertionError("Budget overflow");
            cases.append(seed).append(',').append(plan.status).append(',').append(plan.plots.size()).append(',').append(audit.violations()).append(',').append(plan.search.candidateChecks).append(',').append(plan.search.pathExpanded).append(',').append(String.join("|",plan.search.exhaustedBudgets)).append(',').append(plan.transportNetwork.metrics.status).append(',').append(String.join("|",plan.transportNetwork.metrics.reasons).replace(',',';')).append('\n');
            placed+=plan.plots.size();if(plan.status.equals("COMPLETE"))complete++;else if(plan.status.equals("PARTIAL"))partial++;else none++;
        }
        if(args.length>0)java.nio.file.Files.writeString(java.nio.file.Path.of(args[0]),cases);
        System.out.printf(Locale.ROOT,"PASS 100 deterministic randomized invariant cases; allocated=%d complete=%d partial=%d no_build=%d elapsed_ms=%.3f%n",placed,complete,partial,none,(System.nanoTime()-start)/1e6);
    }
}
