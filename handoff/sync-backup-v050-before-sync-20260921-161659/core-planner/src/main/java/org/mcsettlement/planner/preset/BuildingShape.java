package org.mcsettlement.planner.preset;

import java.util.*;

/** An immutable, fully rasterized preset variant. All consumers use this same footprint.
 *  45-degree variants are nearest-cell rasterizations of the entire declared occupancy mask,
 *  NOT a rotated preview over an axis-aligned foundation. Fine voxel details may resample.
 */
public final class BuildingShape {
    public final BuildingPreset preset;
    public final String sourceFacing;
    public final boolean diagonal;
    public final int sizeX, sizeZ, sizeY, entranceX, entranceZ;
    public final String facing;
    public final List<int[]> cells;
    public final List<int[]> hull;
    private final int[][] sourceX, sourceZ;
    private final int passageX, passageZ;

    public BuildingShape(BuildingPreset oriented, boolean diagonal) {
        oriented.validateFootprint();
        preset=oriented; sourceFacing=oriented.entrance.facing; this.diagonal=diagonal; sizeY=oriented.sizeY;
        if (sizeY<3 || oriented.sizeX<1 || oriented.sizeZ<1) throw new IllegalArgumentException("INVALID_PRESET_SIZE");
        List<int[]> raw=new ArrayList<>();
        int minX=0,minZ=0,maxX=oriented.sizeX-1,maxZ=oriented.sizeZ-1;
        double k=StrictMath.sqrt(0.5);
        if (!diagonal) {
            for(int x=0;x<oriented.sizeX;x++)for(int z=0;z<oriented.sizeZ;z++)if(oriented.occupies(x,z))raw.add(new int[]{x,z,x,z});
        } else {
            minX=Integer.MAX_VALUE;minZ=Integer.MAX_VALUE;maxX=Integer.MIN_VALUE;maxZ=Integer.MIN_VALUE;
            int extent=oriented.sizeX+oriented.sizeZ+2;
            for(int x=-extent;x<=extent;x++)for(int z=-1;z<=extent;z++) {
                double sx=(x+z)*k, sz=(z-x)*k;
                if(sx < -0.5-1e-9 || sx >= oriented.sizeX-0.5+1e-9 || sz < -0.5-1e-9 || sz >= oriented.sizeZ-0.5+1e-9)continue;
                int ix=Math.max(0,Math.min(oriented.sizeX-1,(int)StrictMath.floor(sx+0.5)));
                int iz=Math.max(0,Math.min(oriented.sizeZ-1,(int)StrictMath.floor(sz+0.5)));
                if(!oriented.occupies(ix,iz))continue;
                raw.add(new int[]{x,z,ix,iz});minX=Math.min(minX,x);minZ=Math.min(minZ,z);maxX=Math.max(maxX,x);maxZ=Math.max(maxZ,z);
            }
        }
        sizeX=maxX-minX+1;sizeZ=maxZ-minZ+1;
        sourceX=new int[sizeX][sizeZ];sourceZ=new int[sizeX][sizeZ];
        for(int[] a:sourceX)Arrays.fill(a,-1);
        List<int[]> occupied=new ArrayList<>();
        for(int[] a:raw){int x=a[0]-minX,z=a[1]-minZ;sourceX[x][z]=a[2];sourceZ[x][z]=a[3];occupied.add(new int[]{x,z});}
        occupied.sort(Comparator.<int[]>comparingInt(a->a[0]).thenComparingInt(a->a[1]));cells=Collections.unmodifiableList(occupied);
        hull=Collections.unmodifiableList(convexHull(occupied));
        int ex=oriented.entrance.x,ez=oriented.entrance.z;
        String chosen=sourceFacing;
        if(diagonal) {
            int targetX=(int)StrictMath.round((ex-ez)*k)-minX,targetZ=(int)StrictMath.round((ex+ez)*k)-minZ;
            int best=Integer.MAX_VALUE;
            for(int[] a:occupied){int d=Math.abs(a[0]-targetX)+Math.abs(a[1]-targetZ);if(d<best){best=d;ex=a[0];ez=a[1];}}
            // The two cardinal exit rays nearest the rotated outward normal. Choose shortest,
            // so the repaired two-high door tunnel exits the actual raster boundary.
            String[] exits=switch(sourceFacing){
                case "NORTH"->new String[]{"NORTH","EAST"};case "EAST"->new String[]{"EAST","SOUTH"};
                case "SOUTH"->new String[]{"SOUTH","WEST"};case "WEST"->new String[]{"WEST","NORTH"};
                default->throw new IllegalArgumentException("INVALID_FACING");};
            int shortest=Integer.MAX_VALUE;
            for(String f:exits){int[] d=PlannedBuilding.direction(f);int n=0;while(contains(ex+d[0]*n,ez+d[1]*n))n++;
                if(n<shortest){shortest=n;chosen=f;}}
        }
        passageX=ex;passageZ=ez;
        if(diagonal){int[] d=PlannedBuilding.direction(chosen);while(contains(ex+d[0],ez+d[1])){ex+=d[0];ez+=d[1];}}
        entranceX=ex;entranceZ=ez;facing=chosen;
        if(!contains(entranceX,entranceZ))throw new IllegalArgumentException("INVALID_RASTER_DOOR");
    }
    /** Inclusive local rectangle intersection, O(1) after lazy integral-image construction. */
    private int[][] prefix;
    public boolean intersects(int x0,int z0,int x1,int z1) {
        if(x1<0||z1<0||x0>=sizeX||z0>=sizeZ)return false;
        if(prefix==null){prefix=new int[sizeX+1][sizeZ+1];for(int x=0;x<sizeX;x++)for(int z=0;z<sizeZ;z++)prefix[x+1][z+1]=prefix[x][z+1]+prefix[x+1][z]-prefix[x][z]+(contains(x,z)?1:0);}
        int ax=Math.max(0,x0),az=Math.max(0,z0),bx=Math.min(sizeX-1,x1)+1,bz=Math.min(sizeZ-1,z1)+1;
        return prefix[bx][bz]-prefix[ax][bz]-prefix[bx][az]+prefix[ax][az]>0;
    }
    public boolean contains(int x,int z){return x>=0&&z>=0&&x<sizeX&&z<sizeZ&&sourceX[x][z]>=0;}
    public String[][][] grid(String theme) {
        String[][][] src=PlannedBuilding.grid(preset,theme),out=new String[sizeX][sizeY][sizeZ];
        for(int x=0;x<sizeX;x++)for(int y=0;y<sizeY;y++)Arrays.fill(out[x][y],"minecraft:air");
        for(int[] c:cells)for(int y=0;y<sizeY;y++)out[c[0]][y][c[1]]=src[sourceX[c[0]][c[1]]][y][sourceZ[c[0]][c[1]]];
        int[] d=PlannedBuilding.direction(facing);int x=passageX-d[0],z=passageZ-d[1];
        if(!contains(x,z)){x=passageX;z=passageZ;}
        while(contains(x,z)){out[x][0][z]="minecraft:cobblestone";out[x][1][z]=out[x][2][z]="minecraft:air";x+=d[0];z+=d[1];}
        return out;
    }
    private static long cross(int[] o,int[] a,int[] b){return (long)(a[0]-o[0])*(b[1]-o[1])-(long)(a[1]-o[1])*(b[0]-o[0]);}
    private static List<int[]> convexHull(List<int[]> points) {
        List<int[]> out=new ArrayList<>();
        for(int[] p:points){while(out.size()>=2&&cross(out.get(out.size()-2),out.getLast(),p)<=0)out.removeLast();out.add(p.clone());}
        int n=out.size();
        for(int i=points.size()-2;i>=0;i--){int[] p=points.get(i);while(out.size()>n&&cross(out.get(out.size()-2),out.getLast(),p)<=0)out.removeLast();out.add(p.clone());}
        if(out.size()>1)out.removeLast();return out;
    }
}
