// Ported from the user-owned YDNF visual reference, as requested on 2026-10-26.
package cn.yumis.fight.rules;
import java.util.SplittableRandom;
/** Shared deterministic presentation schedule. It does not calculate or approve damage. */
public final class StormChoreography {
    public static final int SWORDS=24, SLASHES=24;
    public static final long DEPLOY=1000, INTERVAL=250, FINISH=7200, END=8000;
    public static final double RADIUS=5, CENTER_OFFSET=3, UPPER_HEIGHT=4;
    private StormChoreography(){
    }
    public record Point(double x,double y,double z) {
        public Point lerp(Point other,double amount){
            return new Point(x+(other.x-x)*amount,y+(other.y-y)*amount,z+(other.z-z)*amount);
        }
    }
    public static int[] order(long seed){
        int[] result=new int[24];
        boolean[] used=new boolean[24];
        var random=new SplittableRandom(seed);
        result[0]=random.nextInt(12);
        used[result[0]]=true;
        for(int step=1;step<24;step++){
            int offset=step%2==1?12:0,chosen=-1,ties=0;
            double best=Double.POSITIVE_INFINITY;
            double previous=angle(result[step-1]);
            for(int i=offset;i<offset+12;i++)if(!used[i]){
                double delta=Math.abs(Math.atan2(Math.sin(angle(i)-previous-Math.PI),Math.cos(angle(i)-previous-Math.PI)));
                if(delta<best-1e-9){
                    best=delta;
                    chosen=i;
                    ties=1;
                }
                else if(Math.abs(delta-best)<1e-9&&random.nextInt(++ties)==0)chosen=i;
            }
            result[step]=chosen;
            used[chosen]=true;
        }
        return result;
    }
    private static double angle(int index){
        return index%12*Math.PI/6+(index>=12?Math.PI/12:0);
    }
    public static Point sword(int index){
        double angle=(index%12)*Math.PI/6+(index>=12?Math.PI/12:0);
        return new Point(Math.cos(angle)*RADIUS,index<12?UPPER_HEIGHT:0,Math.sin(angle)*RADIUS+CENTER_OFFSET);
    }
    public static int completed(long elapsed){
        return (int)Math.clamp((elapsed-DEPLOY)/INTERVAL+(elapsed>=DEPLOY?1:0),0,24);
    }
    public static Point actor(long seed,long elapsed){
        int[] sequence=order(seed);
        Point origin=new Point(0,0,0);
        if(elapsed<800)return origin;
        if(elapsed<DEPLOY)return origin.lerp(sword(sequence[0]),smooth((elapsed-800)/200.0));
        if(elapsed>=7000)return origin;
        int index=(int)((elapsed-DEPLOY)/INTERVAL);
        double t=smooth(((elapsed-DEPLOY)%INTERVAL)/(double)INTERVAL);
        return sword(sequence[index]).lerp(index==23?origin:sword(sequence[index+1]),t);
    }
    private static double smooth(double x){
        return x*x*(3-2*x);
    }
    public static boolean contains(double x,double y,double z){
        return x*x+(z-CENTER_OFFSET)*(z-CENTER_OFFSET)<=RADIUS*RADIUS&&y>=-1&&y<=4;
    }
}
