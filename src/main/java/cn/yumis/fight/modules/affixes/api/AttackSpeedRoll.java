package cn.yumis.fight.modules.affixes.api;
public final class AttackSpeedRoll {
    private AttackSpeedRoll(){}
    public static double sample(int level,double roll){
        double t=Math.clamp((level-1)/79d,0,1),q=Math.clamp((roll-.8)/.4,0,1);
        return 3+7*t+(2+3*t)*q;
    }
}
