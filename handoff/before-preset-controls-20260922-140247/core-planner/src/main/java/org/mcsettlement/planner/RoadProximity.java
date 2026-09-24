package org.mcsettlement.planner;

import java.util.*;
import org.mcsettlement.planner.ir.PlanningIR.*;
import org.mcsettlement.planner.terrain.HeightfieldMap;
import static org.mcsettlement.planner.RoadGeometry.*;

/** Bounded multi-source Manhattan field around committed CENTERLINES, not full-width shoulders.
 * Each cell is settled once. A nearby lane is a soft penalty, never an authorization to move it.
 * Crossings, separate elevations and endpoint approach space remain unpenalized.
 */
final class RoadProximity {
    private final int radius;
    private final int[] distance, heights, vx, vz;
    RoadProximity(HeightfieldMap map,List<List<RoadStep>> routes,int radius,SearchStats stats){
        this.radius=radius;int w=map.getWidth(),d=map.getDepth(),n=w*d;
        distance=new int[n];heights=new int[n];vx=new int[n];vz=new int[n];Arrays.fill(distance,Integer.MAX_VALUE);
        if(radius<=0)return;
        int[] queue=new int[n];int head=0,tail=0;
        // Stable order supplies stable nearest-source ties; the authoritative road heights
        // cannot conflict in a valid committed manifest.
        for(List<RoadStep> path:routes)for(int i=0;i<path.size();i++){
            RoadStep a=path.get(i),before=path.get(Math.max(0,i-1)),after=path.get(Math.min(path.size()-1,i+1));
            if(!map.inBounds(a.x,a.z))continue;
            int at=(a.z-map.getMinZ())*w+a.x-map.getMinX();
            if(distance[at]==0)continue;
            distance[at]=0;heights[at]=a.y;vx[at]=after.x-before.x;vz[at]=after.z-before.z;queue[tail++]=at;
        }
        while(head<tail){int at=queue[head++];if(stats!=null)stats.roadProximityCellVisits++;
            if(distance[at]>=radius)continue;int x=at%w,z=at/w;
            for(int[] v:CARDINAL){int xx=x+v[0],zz=z+v[1];if(xx<0||xx>=w||zz<0||zz>=d)continue;int next=zz*w+xx;
                if(distance[next]!=Integer.MAX_VALUE)continue;
                distance[next]=distance[at]+1;heights[next]=heights[at];vx[next]=vx[at];vz[next]=vz[at];queue[tail++]=next;
            }
        }
    }
    boolean center(int cell){return distance[cell]==0;}
    int cost(int cell,int y,int dx,int dz,boolean endpoint,boolean crossLink){
        if(radius<=0||endpoint||distance[cell]>radius||Math.abs(y-heights[cell])>1)return 0;
        double dot=(double)dx*vx[cell]+(double)dz*vz[cell];
        double product=((double)dx*dx+(double)dz*dz)*((double)vx[cell]*vx[cell]+(double)vz[cell]*vz[cell]);
        if(product==0||dot*dot<product*.72)return 0; // abs cosine >= ~0.85: near parallel only
        if(distance[cell]==0)return crossLink?28:0;
        return 12+(radius-distance[cell])*3;
    }
}
