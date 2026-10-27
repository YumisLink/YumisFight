package cn.yumis.fight.industry;

/** Stable tier identities; pagination never moves the stored stacks. */
public final class StorageRules {
    public static final int[] CAPACITIES={8,16,32,48,64,88,112,136,160,192,224,256,296,336,376,416};
    public static final String[] NAMES={"破烂的木箱","生锈的铜箱","铜箱","破烂的银箱","银箱","黄金箱","耀眼的黄金箱","华丽的黄金箱","钻石箱","华丽的金刚石箱","泰拉石箱","华丽泰拉石箱","魔刹石箱","华丽魔刹石箱","时空石箱","华丽时空石箱"};
    private StorageRules(){}
    public static int migrateRow(int schema,int page){return Math.max(0,schema==1?page*64/9:schema==2?page*8/9:page);}
    public static int capacity(int tier){return CAPACITIES[Math.clamp(tier,1,16)-1];}
    public static int pages(int tier){return (capacity(tier)+63)/64;}
    public static int visible(int tier,int page){return Math.max(0,Math.min(64,capacity(tier)-Math.max(0,page)*64));}
    public static String material(int next){return next<=3?"c:ingots/copper":next<=5?"c:ingots/iron":next<=8?"c:ingots/gold":next<=10?"c:gems/diamond":next<=12?"c:ingots/terra":next<=14?"yumis_dungeon_and_fight:magic_stones":"c:ingots/spacetime";}
    public static boolean fancy(int tier){return NAMES[tier-1].contains("华丽")||NAMES[tier-1].contains("耀眼");}
}
