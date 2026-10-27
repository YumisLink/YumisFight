package cn.yumis.fight.rules;
import java.math.BigInteger;
import java.util.*;
/** Platform-independent, versioned rules. Values are never inferred from displayed lore. */
public final class Rules {
    public static final int VERSION=7, PROTOCOL=8, MAX_LEVEL=80;
    public static final int SLOTS_PER_LAYER=8, SLOT_COUNT=24;
    public static final double DROP_RATE_BASE=1000;
    private Rules() {
    }
    public enum Stat {
        HEALTH,MP,STRENGTH,INTELLIGENCE,SPIRIT,PHYSICAL_ATTACK,MAGICAL_ATTACK,PHYSICAL_DEFENSE,MAGICAL_DEFENSE,LIGHT,FIRE,ICE,DARK,LIGHT_RESIST,FIRE_RESIST,ICE_RESIST,DARK_RESIST,DROP_RATE,SHIELD,EVASION,ATTACK_SPEED
    }
    public enum ArmorType {
        LEGACY("旧版"),CLOTH("布甲"),LEATHER("皮甲"),LIGHT("轻甲"),HEAVY("重甲"),PLATE("板甲");
        private final String title;
        ArmorType(String title){this.title=title;}
        public String title(){return title;}
    }
    public enum JewelryType {
        LEGACY("旧版"),HEALTH("生命"),MP("魔力"),STRENGTH("力量"),INTELLIGENCE("智力"),SPIRIT("精神"),LIGHT_RESIST("御光"),FIRE_RESIST("耐火"),ICE_RESIST("御寒"),DARK_RESIST("辟暗"),ALL_RESIST("庇护"),DROP_RATE("寻宝");
        private final String title;
        JewelryType(String title){this.title=title;}
        public String title(){return title;}
    }
    public enum Rarity {
        COMMON,RARE,ARTIFACT,LEGENDARY,EPIC,MYTHIC;
        public int randomCount(){
            return switch(this){
                case RARE->1;
                case ARTIFACT->2;
                case LEGENDARY->4;
                default->0;
            }
            ;
        }
        public String title(){
            return new String[]{
                "普通","稀有","神器","传说","史诗","神话"
            }
            [ordinal()];
        }
        public int color(){
            return new int[]{
                0xffffff,0x65aaff,0xb787ff,0xff80c8,0xffa12d,0xffd778
            }
            [ordinal()];
        }
    }
    public enum Slot {
        WEAPON, CHEST,HEAD,LEGS,FEET,BRACELET,NECKLACE,RING,SUB_EQUIPMENT,MAGIC_STONE,EARRING;
        public boolean armor(){
            return ordinal()>=1&&ordinal()<=4;
        }
        public boolean jewelry(){
            return ordinal()>=5&&ordinal()<=7;
        }
        public int minimum(){
            return switch(this){
                case SUB_EQUIPMENT->50;
                case MAGIC_STONE->60;
                case EARRING->70;
                default->1;
            }
            ;
        }
        public int maximumSockets(){
            return this==WEAPON||armor()?2:1;
        }
        public int materialCost(){
            return switch(this){
                case WEAPON->24;
                case CHEST->12;
                case HEAD,FEET->4;
                case LEGS->8;
                case BRACELET,NECKLACE,RING->3;
                default->5;
            }
            ;
        }
        public String title(){
            return new String[]{
                "武器","胸甲","头盔","护腿","战靴","手镯","项链","戒指","辅助装备","魔法石","耳环"
            }
            [ordinal()];
        }
        public String item(){
            return switch(this){
                case CHEST->"chest_armor";
                case HEAD->"head_armor";
                case LEGS->"legs_armor";
                case FEET->"feet_armor";
                default->name().toLowerCase(Locale.ROOT);
            }
            ;
        }
    }
    public enum Weapon {
        KATANA,LIGHTSABER,GREATSWORD,SHORTSWORD,BLUNT;
        public String title(){
            return new String[]{
                "太刀","光剑","巨剑","短剑","钝器"
            }
            [ordinal()];
        }
    }
    public enum Family {
        SWORD,GHOST,WAVE,BLOOD;
        public String title(){
            return new String[]{
                "剑术","鬼神","波动","血气"
            }
            [ordinal()];
        }
    }
    public enum Skill {
        GHOST("鬼斩",1,15,10,.06,6,1.6,Family.GHOST),TRIPLE("三段斩",5,15,10,.08,8,.8,Family.SWORD),
        WAVE("地裂波动剑",10,15,9,.06,5,2,Family.WAVE),MASTERY("武器精通",20,30,7,0,0,0,Family.SWORD),
        OVERDRIVE("破极兵刃",20,30,7,.10,40,.15,Family.SWORD),RISING("破军升龙击",25,40,6,.12,12,1.6,Family.SWORD),
        DRAW("拔刀斩",30,50,5,.14,15,6.5,Family.SWORD),DRAGON("猛龙断空斩",35,50,4,.18,22,2.2,Family.SWORD),
        DANCE("幻影剑舞",40,60,3,.22,30,.7,Family.SWORD),STORM("极鬼剑术·暴风式",50,100,1,.35,90,1,Family.SWORD);
        public final String title;
        public final int unlock,sp,cap;
        public final double mpRatio,cooldown,coefficient;
        public final Family family;
        Skill(String t,int u,int s,int c,double m,double cd,double coefficient,Family f){
            title=t;
            unlock=u;
            sp=s;
            cap=c;
            mpRatio=m;
            cooldown=cd;
            this.coefficient=coefficient;
            family=f;
        }
        public boolean passive(){
            return this==MASTERY;
        }
        public boolean advanced(){
            return ordinal()>=3;
        }
        public int limit(int level,boolean swordmaster){
            return level<unlock||advanced()&&!swordmaster?0:Math.min(cap,1+(level-unlock)/5);
        }
        public int mp(int rank){
            return rank<=0?0:(int)Math.round((100+14*(unlock-1))*mpRatio*Math.pow(1.08,rank-1));
        }
        public double coefficient(int rank){
            return coefficient*Math.pow(1.12,Math.max(0,rank-1));
        }
        public String icon(){
            return name().toLowerCase(Locale.ROOT);
        }
    }
    public enum Difficulty {
        NORMAL(1,1,1),ADVANCED(1.6,1.25,1.2),WARRIOR(2.5,1.6,1.45),KING(4,2.1,1.8),HELL(6,3,2.2);
        public final double health,attack,xp;
        Difficulty(double h,double a,double x){
            health=h;
            attack=a;
            xp=x;
        }
        public String title(){
            return new String[]{
                "普通","冒险","勇士","王者","地狱"
            }
            [ordinal()];
        }
    }
    public static int spAt(int level){
        int result=0;
        for(int l=1;l<=Math.clamp(level,1,80);l++)result+=l<20?15:l<30?25:l<40?40:65;
        return result;
    }
    public static long nextExperience(int level){
        return level>=80?0:(10L+2*level)*(20+5L*level);
    }
    public static double partyMultiplier(int n){
        double[] v={
            1,2.1,3.6,5.6,8.2,11.5,15.6,20.6
        }
        ;
        return n<=8?v[Math.clamp(n-1,0,7)]:20.6+5*(n-8);
    }
    public static double expandedQ(double q,int oldSize,int newSize){
        return 1-(1-q)*partyMultiplier(oldSize)/partyMultiplier(newSize);
    }
    public static double advantage(int attacker,int defender){
        int n=Math.max(0,Math.abs(attacker-defender)-5);
        return attacker>=defender?1+.08*n:Math.max(.1,1-.03*n);
    }
    public static BigInteger professionThreshold(int level){
        return BigInteger.valueOf(100L*level+25L*level*(level-1)/2);
    }
    public static int professionLevel(BigInteger xp){
        int result=0;
        while(result<10&&xp.compareTo(professionThreshold(result+1))>=0)result++;
        return result;
    }
    public static void finite(double value){
        if(!Double.isFinite(value))throw new IllegalArgumentException("数值不是有限值");
    }
    public static double[] defenses(int level,boolean magical){
        double total=(magical?40:80)+(magical?10:15)*(level-1);
        double[] weights={
            .4,.15,.3,.15
        }
        ,out=new double[4];
        int used=0;
        for(int i=0;i<4;i++){
            out[i]=Math.floor(total*weights[i]);
            used+= (int)out[i];
        }
        int[] order={
            0,2,1,3
        }
        ;
        for(int i=0;i<(int)total-used;i++)out[order[i%4]]++;
        return out;
    }
}
