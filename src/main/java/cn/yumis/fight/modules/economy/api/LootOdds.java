package cn.yumis.fight.modules.economy.api;
import cn.yumis.fight.modules.equipment.api.Rarity;
import cn.yumis.fight.modules.dungeons.api.Difficulty;
import static cn.yumis.fight.modules.attributes.api.AttributeNumbers.DROP_RATE_BASE;
import java.util.Random;
public final class LootOdds {
    private LootOdds(){}
    public static Rarity rarity(Difficulty difficulty,boolean boss,boolean nativeBoss,double luck,Random random){
        double[] low=boss?new double[]{
            45,30,17,6,1.8,.2
        }
        :new double[]{
            88,10,1.8,.2,0,0
        }
        ;
        double[] high=boss?new double[]{
            12,26,30,19,10,3
        }
        :new double[]{
            78,18,3.5,.5,0,0
        }
        ;
        double[] w=new double[6];
        double sum=0;
        for(int i=0;i<6;i++){
            w[i]=(low[i]+(high[i]-low[i])*difficulty.ordinal()/4d)*Math.pow(1+Math.max(0,luck)/DROP_RATE_BASE,i);
            if(nativeBoss&&i>=4)w[i]=0;
            sum+=w[i];
        }
        double draw=random.nextDouble()*sum;
        for(int i=0;i<6;i++){
            draw-=w[i];
            if(draw<0)return Rarity.values()[i];
        }
        return Rarity.COMMON;
    }
}
