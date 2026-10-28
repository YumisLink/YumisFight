package cn.yumis.fight.modules.professions.api;
import java.util.*;
import java.math.BigInteger;
public final class ProfessionRules {
    private ProfessionRules(){}
    public static BigInteger professionThreshold(int level){
        return BigInteger.valueOf(100L*level+25L*level*(level-1)/2);
    }
    public static int professionLevel(BigInteger xp){
        int result=0;
        while(result<10&&xp.compareTo(professionThreshold(result+1))>=0)result++;
        return result;
    }
}
