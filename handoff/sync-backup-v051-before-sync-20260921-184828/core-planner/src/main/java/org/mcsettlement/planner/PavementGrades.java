package org.mcsettlement.planner;

import java.util.*;
import org.mcsettlement.planner.ir.PlanningIR.*;
import org.mcsettlement.planner.SettlementPlanner.PlanRequest;
import org.mcsettlement.planner.terrain.HeightfieldMap;
import static org.mcsettlement.planner.RoadGeometry.*;

/** Bounded integer 1-Lipschitz extension of center/access anchors over the WHOLE swept pavement.
 * This solves conflicting brush heights before committing anything, rather than last-writer wins.
 */
final class PavementGrades {
    private record Bound(int i,int value) {}
    private PavementGrades() {}
    private static Map<Long,GroundColumn> fail(SearchStats stats,String code){stats.gradeRejections.merge(code,1,Integer::sum);return null;}
    static Map<Long,GroundColumn> solve(HeightfieldMap map,PlanRequest req,Set<Long> footprint,
            Map<Long,GroundColumn> fixed,List<RoadStep> centers,List<List<RoadStep>> accesses,
            List<List<RoadStep>> previousRoutes,int[] entry,SearchStats stats) {
        TreeSet<Long> keys=new TreeSet<>(footprint);keys.addAll(fixed.keySet());
        if(keys.size()>req.searchBudget.groundColumns){exhausted(stats,"GROUND_COLUMNS");return null;}
        int n=keys.size();long[] cells=new long[n];Map<Long,Integer> index=new HashMap<>();int pos=0;
        for(long k:keys){cells[pos]=k;index.put(k,pos++);}
        int[] lower=new int[n],upper=new int[n],natural=new int[n];
        for(int i=0;i<n;i++){
            int x=x(cells[i]),z=z(cells[i]);if(!RoadTerrain.allowed(map,req,x,z))return null;
            natural[i]=RoadTerrain.natural(map,x,z);GroundColumn c=fixed.get(cells[i]);
            lower[i]=c==null?RoadTerrain.lower(map,req,x,z):c.targetY;
            upper[i]=c==null?RoadTerrain.upper(map,req,x,z):c.targetY;
        }
        for(RoadStep s:centers){Integer i=index.get(key(s.x,s.z));if(i==null||s.y<lower[i]||s.y>upper[i])return fail(stats,"CENTER_ANCHOR");lower[i]=upper[i]=s.y;}
        if(!propagate(cells,index,lower,false,stats)||!propagate(cells,index,upper,true,stats))return null;
        for(int i=0;i<n;i++)if(lower[i]>upper[i])return fail(stats,"WIDTH_LIPSCHITZ_CONFLICT");
        int[] preferred=natural;
        if(req.expert!=null){
            // Laterally level road cross-sections: terrain still bounds cut/fill, but must not
            // introduce arbitrary side bumps that force contradictory stair faces.
            preferred=natural.clone();boolean[] assigned=new boolean[n];ArrayDeque<Integer> wave=new ArrayDeque<>();
            for(int i=0;i<n;i++)if(lower[i]==upper[i]){preferred[i]=lower[i];assigned[i]=true;wave.add(i);}
            while(!wave.isEmpty()){if(!work(stats))return null;int i=wave.remove();for(int[] d:CARDINAL){Integer j=index.get(key(x(cells[i])+d[0],z(cells[i])+d[1]));
                if(j!=null&&!assigned[j]){assigned[j]=true;preferred[j]=preferred[i];wave.add(j);}}}
        }
        int[] chosen=new int[n];for(int i=0;i<n;i++)chosen[i]=Math.max(lower[i],Math.min(upper[i],preferred[i]));
        if(!propagate(cells,index,chosen,true,stats))return null;
        Map<Long,GroundColumn> out=new TreeMap<>();
        for(int i=0;i<n;i++){
            int y=Math.max(lower[i],chosen[i]);GroundColumn old=fixed.get(cells[i]);
            GroundColumn c=old==null?RoadTerrain.column(map,x(cells[i]),z(cells[i]),y,"road",y+3):copy(old);
            if(footprint.contains(cells[i])&&!"foundation".equals(c.kind))c.kind="road";
            if(!RoadTerrain.raised(c))c.structure="surface";c.facing=null;out.put(cells[i],c);
        }
        if(!stairs(out,accesses,entry,req.expert==null?List.of():centers,req.expert==null?List.of():previousRoutes,stats))return fail(stats,"STAIRS_FLOOD_OR_ACCESS");
        for(List<RoadStep> route:previousRoutes)if(!verifySegments(out,route,req.roadWidth,stats))return fail(stats,"PREVIOUS_SEGMENTS");
        if(!verifySegments(out,centers,req.roadWidth,stats))return fail(stats,"NEW_SEGMENTS");
        return out;
    }
    private static boolean work(SearchStats s) {
        if(s.gradeRelaxations>=s.gradeLimit){exhausted(s,"GRADE_RELAXATIONS");return false;}
        s.gradeRelaxations++;return true;
    }
    private static boolean propagate(long[] cells,Map<Long,Integer> index,int[] values,boolean minimum,SearchStats stats) {
        Comparator<Bound> order=minimum?Comparator.comparingInt(Bound::value):Comparator.comparingInt(Bound::value).reversed();
        PriorityQueue<Bound> queue=new PriorityQueue<>(order.thenComparingInt(Bound::i));
        for(int i=0;i<cells.length;i++)queue.add(new Bound(i,values[i]));
        while(!queue.isEmpty()){
            if(!work(stats))return false;
            Bound b=queue.remove();if(values[b.i]!=b.value)continue;
            long k=cells[b.i];
            for(int[] d:CARDINAL){Integer j=index.get(key(x(k)+d[0],z(k)+d[1]));if(j==null)continue;
                int v=b.value+(minimum?1:-1);
                if(minimum?v<values[j]:v>values[j]){values[j]=v;queue.add(new Bound(j,v));}
            }
            if(queue.size()>200000){exhausted(stats,"GRADE_QUEUE");return false;}
        }
        return true;
    }
    /** Reserve real uphill stair faces. Level landings permit turns. A foundation door stays flat. */
    private static boolean connect(GroundColumn a,GroundColumn b,Map<Long,Integer> highExits) {
        if(a==null||b==null||Math.abs(a.x-b.x)+Math.abs(a.z-b.z)!=1)return false;
        if(a.targetY==b.targetY)return true;
        if(Math.abs(a.targetY-b.targetY)!=1)return false;
        GroundColumn lower=a.targetY<b.targetY?a:b,higher=lower==a?b:a;
        String f=facing(higher.x-lower.x,higher.z-lower.z);
        int bit=switch(f){case "EAST"->1;case "SOUTH"->2;case "WEST"->4;default->8;};
        if(RoadTerrain.raised(higher)||"foundation".equals(higher.kind)||stair(higher)&&!f.equals(higher.facing)||stair(lower)&&!f.equals(lower.facing))return false;
        int needs=highExits.getOrDefault(key(higher.x,higher.z),0);
        if(needs!=0&&needs!=bit)return false;
        higher.structure="stair";higher.facing=f;
        highExits.merge(key(lower.x,lower.z),bit,(a1,b1)->a1|b1);
        return true;
    }
    static boolean validAccess(HeightfieldMap map,PlanRequest req,List<RoadStep> path){
        Map<Long,GroundColumn> cols=new TreeMap<>();Map<Long,Integer> exits=new HashMap<>();
        for(int i=0;i<path.size();i++){var p=path.get(i);cols.put(key(p.x,p.z),RoadTerrain.column(map,p.x,p.z,p.y,i==0?"foundation":"access",p.y+3));}
        for(int i=1;i<path.size();i++){var a=path.get(i-1);var b=path.get(i);if(!connect(cols.get(key(a.x,a.z)),cols.get(key(b.x,b.z)),exits))return false;}
        for(int i=1;i<path.size();i++){var a=path.get(i-1);var b=path.get(i);if(!canWalk(cols.get(key(a.x,a.z)),cols.get(key(b.x,b.z))))return false;}
        return true;
    }
    private static boolean stairs(Map<Long,GroundColumn> out,List<List<RoadStep>> accesses,int[] entry,List<RoadStep> centers,List<List<RoadStep>> previous,SearchStats stats) {
        Map<Long,Integer> highExits=new HashMap<>();
        for(List<RoadStep> access:accesses)for(int i=1;i<access.size();i++){
            RoadStep a=access.get(i-1),b=access.get(i);
            if(!connect(out.get(key(a.x,a.z)),out.get(key(b.x,b.z)),highExits)){stats.gradeRejections.merge("ACCESS_STAIR",1,Integer::sum);return false;}
        }
        List<List<RoadStep>> critical=new ArrayList<>(previous);critical.add(centers);
        for(var path:critical)for(int i=1;i<path.size();i++){RoadStep a=path.get(i-1),b=path.get(i);
            if(Math.abs(a.x-b.x)+Math.abs(a.z-b.z)==1&&!connect(out.get(key(a.x,a.z)),out.get(key(b.x,b.z)),highExits)){stats.gradeRejections.merge("CENTER_STAIR",1,Integer::sum);return false;}}
        long root=key(entry[0],entry[2]);if(!out.containsKey(root))return false;
        Set<Long> reached=new HashSet<>();ArrayDeque<Long> queue=new ArrayDeque<>();queue.add(root);reached.add(root);
        while(!queue.isEmpty()){
            long k=queue.remove();GroundColumn a=out.get(k);
            // Flood same-level landings first; the deterministic order also fixes ties at stair junctions.
            for(int pass=0;pass<2;pass++)for(int[] d:CARDINAL){long next=key(a.x+d[0],a.z+d[1]);GroundColumn b=out.get(next);
                if(b==null||reached.contains(next)||(pass==0)!=(a.targetY==b.targetY))continue;
                if(connect(a,b,highExits)){reached.add(next);queue.add(next);}
            }
        }
        if(reached.size()!=out.size()){stats.gradeRejections.merge("FLOOD_DISCONNECTED",1,Integer::sum);return false;}
        // Check after all assignments: no later conversion may invalidate a previously reserved face.
        for(List<RoadStep> access:accesses)for(int i=1;i<access.size();i++){
            RoadStep a=access.get(i-1),b=access.get(i);if(!canWalk(out.get(key(a.x,a.z)),out.get(key(b.x,b.z))))return false;
        }
        return true;
    }
    static boolean verifySegments(Map<Long,GroundColumn> columns,List<RoadStep> path,int width,SearchStats stats) {
        for(RoadStep s:path){GroundColumn c=columns.get(key(s.x,s.z));if(c==null||c.targetY!=s.y)return false;}
        for(int i=1;i<path.size();i++){
            RoadStep a=path.get(i-1),b=path.get(i);Set<Long> allowed=new HashSet<>();
            for(int[] d:stencil(b.x-a.x,b.z-a.z,width))allowed.add(key(a.x+d[0],a.z+d[1]));
            long goal=key(b.x,b.z),start=key(a.x,a.z);Set<Long> seen=new HashSet<>();ArrayDeque<Long> queue=new ArrayDeque<>();seen.add(start);queue.add(start);
            while(!queue.isEmpty()&&!seen.contains(goal)){
                if(!work(stats))return false;
                long k=queue.remove();for(int[] d:CARDINAL){long next=key(x(k)+d[0],z(k)+d[1]);
                    if(allowed.contains(next)&&!seen.contains(next)&&canWalk(columns.get(k),columns.get(next))){seen.add(next);queue.add(next);}}
            }
            if(!seen.contains(goal))return false;
        }
        return true;
    }
}
