package cn.yumis.fight.industry;

import java.math.BigInteger;
import java.util.*;

/** Integer accounting shared by the server and deterministic rule tests. */
public final class EnergyMath {
    private EnergyMath(){}
    public static long crystal(int tier){
        if(tier<1||tier>10)throw new IllegalArgumentException("水晶阶位必须为1～10");
        return 100L << (3*(tier-1));
    }
    public static boolean accepts(int machineTier,int crystalTier){return machineTier>=1&&machineTier<=5&&crystalTier>=machineTier*2-1&&crystalTier<=machineTier*2;}
    public record Allocation(long[] grants,int nextCursor){}
    public static Allocation allocate(long available,long[] demands,int cursor){
        if(available<0)throw new IllegalArgumentException("负魔能");
        BigInteger total=BigInteger.ZERO;
        for(long d:demands){if(d<0)throw new IllegalArgumentException("负需求");total=total.add(BigInteger.valueOf(d));}
        long[] grants=new long[demands.length];
        if(total.signum()==0||demands.length==0)return new Allocation(grants,cursor);
        BigInteger supply=BigInteger.valueOf(available);
        if(total.compareTo(supply)<=0)return new Allocation(demands.clone(),cursor);
        long spent=0;
        for(int i=0;i<demands.length;i++)spent+=grants[i]=supply.multiply(BigInteger.valueOf(demands[i])).divide(total).longValueExact();
        int next=Math.floorMod(cursor,demands.length);
        for(long remaining=available-spent;remaining>0;){
            if(grants[next]<demands[next]){grants[next]++;remaining--;}
            next=(next+1)%demands.length;
        }
        return new Allocation(grants,next);
    }
    public static long wind(int y){return y<60?0:50L+Math.floorDiv(y-60,5);}
    public static int solar(long day,boolean rain,boolean thunder,boolean sky,boolean applicable,int[] periods){
        int t=(int)Math.floorMod(day,24000);
        if(!sky||!applicable||thunder||t>=periods[3])return 0;
        return rain?1:t>=periods[1]&&t<periods[2]?2:1;
    }
    public static long ceilProduct(long a,long b,long denominator){
        if(a<0||b<0||denominator<=0)throw new IllegalArgumentException();
        return BigInteger.valueOf(a).multiply(BigInteger.valueOf(b)).add(BigInteger.valueOf(denominator-1)).divide(BigInteger.valueOf(denominator)).longValueExact();
    }
}
