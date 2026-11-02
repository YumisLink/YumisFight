package cn.yumis.fight.modules.industry.api;

import java.util.*;

/** Independent deposit rolls; raw extraction is distinct from manufacturing. */
public final class MineralCatalog {
    private MineralCatalog(){}
    public record Mineral(int tier,int chance,int ticks,long power,String item,int count){}
    public static boolean universal(String id){return id.equals("cobblestone");}
    public static Map<String,Mineral> defaults(){
        var m=new LinkedHashMap<String,Mineral>();
        add(m,"cobblestone",1,100,400,80,"minecraft:cobblestone",1);
        add(m,"copper",1,35,300,80,"minecraft:raw_copper",1);
        add(m,"coal",1,30,400,2000,"minecraft:coal",1);
        add(m,"iron",1,15,400,80,"minecraft:raw_iron",1);
        add(m,"lead",1,12,400,320,"lead",1);
        add(m,"orichalcum",1,12,500,400,"orichalcum",1);
        add(m,"gold",2,8,600,12800*20/600,"minecraft:raw_gold",1);
        add(m,"redstone",2,12,400,640,"minecraft:redstone",4);
        add(m,"lapis",2,10,400,640,"minecraft:lapis_lazuli",4);
        add(m,"diamond",2,4,1200,854,"minecraft:diamond",1);
        add(m,"vanadium",2,10,600,800,"vanadium",1);
        add(m,"magic",2,10,800,1200,"energy_crystal_3",2);
        add(m,"amethyst",2,15,400,320,"minecraft:amethyst_shard",2);
        add(m,"titanium",3,6,1200,2134,"titanium",1);
        add(m,"molybdenum",3,8,1000,2000,"molybdenum",1);
        add(m,"zirconium",3,8,1000,1800,"zirconium",1);
        add(m,"mithril",3,5,1200,3200,"mithril",1);
        add(m,"niobium_tantalum",4,3,1600,6400,"niobium_tantalum",1);
        add(m,"adamantite",4,2,2000,12800,"adamantite",1);
        add(m,"uranium",4,2,2000,10800,"uranium",1);
        add(m,"iridium",5,1,2400,25600,"iridium",1);
        return m;
    }
    private static void add(Map<String,Mineral> m,String id,int tier,int chance,int ticks,long power,String item,int count){m.put(id,new Mineral(tier,chance,ticks,power,item.contains(":")?item:"yumis_dungeon_and_fight:"+(item.startsWith("energy_")?item:"raw_"+item),count));}
}
