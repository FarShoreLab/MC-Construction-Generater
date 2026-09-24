package org.mcsettlement.planner;

import java.util.*;
import org.mcsettlement.planner.terrain.HeightfieldMap;
import org.mcsettlement.planner.terrain.SpatialNoise;
import static org.mcsettlement.planner.RoadGeometry.*;

/** A sparse quadratic arc used only as a routing cost field, never as final/render-only geometry. */
final class OrganicGuide {
    final int[] distance;
    final List<int[]> controls;
    OrganicGuide(HeightfieldMap map,int ax,int az,int bx,int bz,long seed,int variant) {
        this(map,ax,az,bx,bz,seed,variant,4);
    }
    OrganicGuide(HeightfieldMap map,int ax,int az,int bx,int bz,long seed,int variant,double minimumOffset) {
        double dx=bx-ax,dz=bz-az,length=StrictMath.hypot(dx,dz);
        double sign=(SpatialNoise.hash(seed,ax+bx,az+bz,0x6617)&1)==0?1:-1;
        if(variant%2==1)sign=-sign;
        double offset=Math.min(42,Math.max(minimumOffset,length*0.22))*sign;
        double cx=(ax+bx)*.5-dz/Math.max(1,length)*offset;
        double cz=(az+bz)*.5+dx/Math.max(1,length)*offset;
        cx=Math.max(map.getMinX()+3,Math.min(map.getMinX()+map.getWidth()-4,cx));
        cz=Math.max(map.getMinZ()+3,Math.min(map.getMinZ()+map.getDepth()-4,cz));
        controls=List.of(new int[]{ax,az},new int[]{(int)StrictMath.round(cx),(int)StrictMath.round(cz)},new int[]{bx,bz});
        Set<Long> samples=new HashSet<>();int n=Math.max(8,(int)StrictMath.ceil(length*2));
        for(int i=0;i<=n;i++){double t=i/(double)n,u=1-t;
            int x=(int)StrictMath.round(u*u*ax+2*u*t*cx+t*t*bx);
            int z=(int)StrictMath.round(u*u*az+2*u*t*cz+t*t*bz);
            if(map.inBounds(x,z))samples.add(key(x,z));}
        distance=distanceField(map,samples);
    }
    int cost(int cell) {return Math.min(100,distance[cell]/3);}
}
