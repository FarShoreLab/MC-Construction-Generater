package org.mcsettlement.planner;

import java.util.*;
import org.mcsettlement.planner.ir.PlanningIR.*;
import org.mcsettlement.planner.terrain.HeightfieldMap;

/** Coverage from buildings alone. PCA/hull stop a diagonal string from gaming a large AABB. */
public final class SiteMetrics {
    private SiteMetrics() {}
    /** Normalized distance reference; scale and population, not a minimum buildable gap. */
    public static double spacingReference(int count){return .6/Math.sqrt(Math.max(1,count));}
    public static void measure(HeightfieldMap m,List<Plot> plots,ExpertSettings settings,SitePlanning out) {
        out.minBBoxCoverage=settings.minBBoxCoverage;out.minMinorAxisRatio=settings.minMinorAxisRatio;
        out.minHullCoverage=plots.size()>=3?settings.minBBoxCoverage*.35:0;
        out.spacingReference=spacingReference(plots.size());out.closePairs=out.crowdedBuildings=0;
        out.crowdingPenalty=out.nearestNeighborMin=out.nearestNeighborMean=0;
        if(plots.isEmpty()){out.coveragePenalty=1;return;}
        int ax=Integer.MAX_VALUE,az=ax,bx=Integer.MIN_VALUE,bz=bx;List<double[]> centers=new ArrayList<>();Set<Integer> sectors=new HashSet<>();
        for(Plot p:plots) {
            int lx=Integer.MAX_VALUE,lz=lx,hx=Integer.MIN_VALUE,hz=hx;
            for(int[] c:p.footprint){int x=p.origin2D[0]+c[0],z=p.origin2D[1]+c[1];lx=Math.min(lx,x);hx=Math.max(hx,x);lz=Math.min(lz,z);hz=Math.max(hz,z);}
            ax=Math.min(ax,lx);az=Math.min(az,lz);bx=Math.max(bx,hx);bz=Math.max(bz,hz);
            double x=((lx+hx)*.5-m.getMinX())/m.getWidth(),z=((lz+hz)*.5-m.getMinZ())/m.getDepth();centers.add(new double[]{x,z});sectors.add(Math.min(2,(int)(x*3))+3*Math.min(2,(int)(z*3)));
        }
        out.bounds=new int[]{ax,az,bx,bz};out.spanX=(bx-ax+1)/(double)m.getWidth();out.spanZ=(bz-az+1)/(double)m.getDepth();out.bboxCoverage=out.spanX*out.spanZ;out.coveredSectors=sectors.size();
        double mx=centers.stream().mapToDouble(a->a[0]).average().orElse(0),mz=centers.stream().mapToDouble(a->a[1]).average().orElse(0),xx=0,zz=0,xz=0;
        for(double[] c:centers){xx+=(c[0]-mx)*(c[0]-mx);zz+=(c[1]-mz)*(c[1]-mz);xz+=(c[0]-mx)*(c[1]-mz);}
        double delta=Math.sqrt((xx-zz)*(xx-zz)+4*xz*xz),large=(xx+zz+delta)/2,small=Math.max(0,(xx+zz-delta)/2);
        out.minorAxisRatio=large>1e-12?Math.sqrt(small/large):0;out.hullCoverage=hullArea(centers);
        if(centers.size()>1){
            double[] nearest=new double[centers.size()];Arrays.fill(nearest,Double.POSITIVE_INFINITY);
            boolean[] crowded=new boolean[centers.size()];
            for(int i=0;i<centers.size();i++)for(int j=i+1;j<centers.size();j++){
                double d=Math.hypot(centers.get(i)[0]-centers.get(j)[0],centers.get(i)[1]-centers.get(j)[1]);
                nearest[i]=Math.min(nearest[i],d);nearest[j]=Math.min(nearest[j],d);
                if(d<out.spacingReference){out.closePairs++;crowded[i]=crowded[j]=true;out.crowdingPenalty+=square(1-d/out.spacingReference);}
            }
            out.nearestNeighborMin=Arrays.stream(nearest).min().orElse(0);
            out.nearestNeighborMean=Arrays.stream(nearest).average().orElse(0);
            for(boolean b:crowded)if(b)out.crowdedBuildings++;
        }
        out.coveragePenalty=square(Math.max(0,settings.minBBoxCoverage-out.bboxCoverage))+square(Math.max(0,out.minHullCoverage-out.hullCoverage));
        if(plots.size()>=3)out.coveragePenalty+=square(Math.max(0,settings.minMinorAxisRatio-out.minorAxisRatio));
    }
    public static List<String> failures(int n,SitePlanning s) {
        List<String> r=new ArrayList<>();if(s.bboxCoverage+1e-9<s.minBBoxCoverage)r.add("BUILDING_BBOX_COVERAGE_BELOW_MIN");
        if(n>=3&&s.minorAxisRatio+1e-9<s.minMinorAxisRatio)r.add("BUILDING_DISTRIBUTION_TOO_LINEAR");
        if(n>=3&&s.hullCoverage+1e-9<s.minHullCoverage)r.add("BUILDING_CENTER_HULL_TOO_SMALL");return r;
    }
    private static double square(double a){return a*a;}
    private static double cross(double[] a,double[] b,double[] c){return (b[0]-a[0])*(c[1]-a[1])-(b[1]-a[1])*(c[0]-a[0]);}
    private static double hullArea(List<double[]> input){if(input.size()<3)return 0;List<double[]> p=new ArrayList<>(input);p.sort(Comparator.<double[]>comparingDouble(a->a[0]).thenComparingDouble(a->a[1]));List<double[]> h=new ArrayList<>();
        for(double[] c:p){while(h.size()>=2&&cross(h.get(h.size()-2),h.getLast(),c)<=0)h.removeLast();h.add(c);}int lower=h.size();
        for(int i=p.size()-2;i>=0;i--){double[] c=p.get(i);while(h.size()>lower&&cross(h.get(h.size()-2),h.getLast(),c)<=0)h.removeLast();h.add(c);}double area=0;for(int i=1;i<h.size();i++)area+=h.get(i-1)[0]*h.get(i)[1]-h.get(i)[0]*h.get(i-1)[1];return Math.abs(area)/2;}
}
