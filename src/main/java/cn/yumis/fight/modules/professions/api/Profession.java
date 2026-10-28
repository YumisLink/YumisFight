package cn.yumis.fight.modules.professions.api;
import static cn.yumis.fight.modules.professions.api.ProfessionRules.professionLevel;

import java.util.*;
import java.math.BigInteger;
/** professions模块拥有的数据合同；仅接收当前存档代际。 */
public class Profession {
    public BigInteger experience=BigInteger.ZERO,coins=BigInteger.ZERO;
    public int level(){
        return professionLevel(experience);
    }
    public void award(long xp,boolean admin){
        if(xp<=0)throw new IllegalArgumentException("奖励必须为正");
        experience=experience.add(BigInteger.valueOf(xp));
        if(!admin)coins=coins.add(BigInteger.valueOf(xp));
    }
}
