package org.mcsettlement.planner.terrain;

/** Stateless, non-table 64-bit spatial hashing and quintic value noise.
 * No coordinate modulus, permutation period, scan-order RNG, or external dependency.
 */
public final class SpatialNoise {
    private SpatialNoise() {}
    public static long mix(long v) {
        v=(v^(v>>>30))*0xbf58476d1ce4e5b9L;
        v=(v^(v>>>27))*0x94d049bb133111ebL;
        return v^(v>>>31);
    }
    public static long hash(long seed,long x,long z,long channel) {
        return mix(seed ^ mix(x*0x9e3779b97f4a7c15L+channel)
                ^ Long.rotateLeft(mix(z*0xd1b54a32d192ed03L-channel),29));
    }
    public static double unit(long seed,long x,long z,long channel) {
        return (hash(seed,x,z,channel)>>>11)*0x1.0p-53;
    }
    private static double fade(double t) {return t*t*t*(t*(t*6-15)+10);}
    private static double lerp(double a,double b,double t) {return a+(b-a)*t;}
    public static double noise(long seed,double x,double z,long channel) {
        long ix=(long)StrictMath.floor(x),iz=(long)StrictMath.floor(z);
        double u=fade(x-ix),v=fade(z-iz);
        double a=lerp(unit(seed,ix,iz,channel),unit(seed,ix+1,iz,channel),u);
        double b=lerp(unit(seed,ix,iz+1,channel),unit(seed,ix+1,iz+1,channel),u);
        return lerp(a,b,v)*2-1;
    }
}
