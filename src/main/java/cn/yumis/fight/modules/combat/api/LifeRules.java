package cn.yumis.fight.modules.combat.api;
import static cn.yumis.fight.modules.attributes.api.AttributeNumbers.finite;
public final class LifeRules {
    private LifeRules(){}
    public static Receipt noDamageReceipt(Life life){return new Receipt(!life.dead,life.dead?"目标已死亡":"",0,life.q,life.revision,"");}
    public static Receipt apply(Life life,double fraction,boolean heal){
        finite(fraction);
        if(fraction<0)throw new IllegalArgumentException("份额不可为负");
        if(life.dead)return new Receipt(false,"目标已死亡",0,life.q,life.revision,"");
        double actual=Math.min(heal?1-life.q:life.q,fraction);
        life.q=Math.clamp(life.q+(heal?actual:-actual),0,1);
        life.revision++;
        life.dead=life.q<=0;
        return new Receipt(true,"",actual,life.q,life.revision,life.dead?life.target+"/"+life.generation:"");
    }
}
