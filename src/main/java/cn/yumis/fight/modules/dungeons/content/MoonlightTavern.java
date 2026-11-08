package cn.yumis.fight.modules.dungeons.content;

import cn.yumis.fight.modules.dungeons.api.*;
import java.util.*;
import static cn.yumis.fight.modules.dungeons.api.ClassicEnemyKind.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;

/** moonlight_tavern content; each ordinary pack has one elite followed by nine ordinary enemies. */
public final class MoonlightTavern {
    public static final String ID="moonlight_tavern";
    public static final ClassicEnemyKind BOSS=MOONLIGHT_TAVERN_CHANG_SANLANG;
    public static final String[] AREAS={"酒馆后街","斗殴走廊","暗巷阴影","拳斗酒厅","月下鬼阵","浪人之席","长三郎的决斗场"};
    public static final ClassicEnemyKind[][] WAVES={
        new ClassicEnemyKind[]{MOONLIGHT_TAVERN_CITY_THUG,MOONLIGHT_TAVERN_CITY_THUG,MOONLIGHT_TAVERN_CITY_THUG,MOONLIGHT_TAVERN_CITY_THUG,MOONLIGHT_TAVERN_CITY_THUG,MOONLIGHT_TAVERN_CITY_THUG,MOONLIGHT_TAVERN_CITY_THUG,MOONLIGHT_TAVERN_STREET_HOOLIGAN,MOONLIGHT_TAVERN_STREET_HOOLIGAN,MOONLIGHT_TAVERN_STREET_HOOLIGAN},
        new ClassicEnemyKind[]{MOONLIGHT_TAVERN_STREET_HOOLIGAN,MOONLIGHT_TAVERN_CITY_THUG,MOONLIGHT_TAVERN_CITY_THUG,MOONLIGHT_TAVERN_CITY_THUG,MOONLIGHT_TAVERN_STREET_HOOLIGAN,MOONLIGHT_TAVERN_STREET_HOOLIGAN,MOONLIGHT_TAVERN_STREET_HOOLIGAN,MOONLIGHT_TAVERN_DRUNKARD,MOONLIGHT_TAVERN_DRUNKARD,MOONLIGHT_TAVERN_DRUNKARD},
        new ClassicEnemyKind[]{MOONLIGHT_TAVERN_POISON_NIGHTBLADE,MOONLIGHT_TAVERN_PICKPOCKET,MOONLIGHT_TAVERN_PICKPOCKET,MOONLIGHT_TAVERN_PICKPOCKET,MOONLIGHT_TAVERN_PICKPOCKET,MOONLIGHT_TAVERN_PICKPOCKET,MOONLIGHT_TAVERN_CITY_THUG,MOONLIGHT_TAVERN_CITY_THUG,MOONLIGHT_TAVERN_STREET_HOOLIGAN,MOONLIGHT_TAVERN_STREET_HOOLIGAN},
        new ClassicEnemyKind[]{MOONLIGHT_TAVERN_CRAZY_JUNK,MOONLIGHT_TAVERN_DRUNKARD,MOONLIGHT_TAVERN_DRUNKARD,MOONLIGHT_TAVERN_DRUNKARD,MOONLIGHT_TAVERN_PICKPOCKET,MOONLIGHT_TAVERN_PICKPOCKET,MOONLIGHT_TAVERN_PICKPOCKET,MOONLIGHT_TAVERN_STREET_HOOLIGAN,MOONLIGHT_TAVERN_STREET_HOOLIGAN,MOONLIGHT_TAVERN_STREET_HOOLIGAN},
        new ClassicEnemyKind[]{MOONLIGHT_TAVERN_HOLIO,MOONLIGHT_TAVERN_POISON_NIGHTBLADE,MOONLIGHT_TAVERN_POISON_NIGHTBLADE,MOONLIGHT_TAVERN_PICKPOCKET,MOONLIGHT_TAVERN_PICKPOCKET,MOONLIGHT_TAVERN_PICKPOCKET,MOONLIGHT_TAVERN_PICKPOCKET,MOONLIGHT_TAVERN_DRUNKARD,MOONLIGHT_TAVERN_DRUNKARD,MOONLIGHT_TAVERN_DRUNKARD},
        new ClassicEnemyKind[]{MOONLIGHT_TAVERN_AGANZO,MOONLIGHT_TAVERN_CITY_THUG,MOONLIGHT_TAVERN_CITY_THUG,MOONLIGHT_TAVERN_CITY_THUG,MOONLIGHT_TAVERN_STREET_HOOLIGAN,MOONLIGHT_TAVERN_STREET_HOOLIGAN,MOONLIGHT_TAVERN_STREET_HOOLIGAN,MOONLIGHT_TAVERN_POISON_NIGHTBLADE,MOONLIGHT_TAVERN_POISON_NIGHTBLADE,MOONLIGHT_TAVERN_POISON_NIGHTBLADE}
    };
    public static Map<ClassicEnemyKind,List<ClassicMove>> moves(){
        Map<ClassicEnemyKind,List<ClassicMove>> moves=new EnumMap<>(ClassicEnemyKind.class);
        moves.put(MOONLIGHT_TAVERN_CITY_THUG,List.of(
        new ClassicMove(MOONLIGHT_TAVERN_CITY_THUG,"punch","近身打击",1450,2350,"FAN",2.6,1.0,1.0,false,-1,"",0,new double[]{0.55})
        ));
        moves.put(MOONLIGHT_TAVERN_STREET_HOOLIGAN,List.of(
        new ClassicMove(MOONLIGHT_TAVERN_STREET_HOOLIGAN,"slash","短刀斜砍",1050,2000,"FAN",3.0,1.0,1.0,false,-1,"",0,new double[]{0.46}),
        new ClassicMove(MOONLIGHT_TAVERN_STREET_HOOLIGAN,"kick","蹬踢",1050,2000,"FAN",2.6,1.0,1.0,false,-1,"",12,new double[]{0.46})
        ));
        moves.put(MOONLIGHT_TAVERN_DRUNKARD,List.of(
        new ClassicMove(MOONLIGHT_TAVERN_DRUNKARD,"bottle_swing","持瓶挥击",1450,2350,"FAN",3.0,1.0,1.0,false,-1,"",0,new double[]{0.46})
        ));
        moves.put(MOONLIGHT_TAVERN_PICKPOCKET,List.of(
        new ClassicMove(MOONLIGHT_TAVERN_PICKPOCKET,"stealth_stab","现身突刺",1450,2350,"RECT",3.5,1.2,1.0,false,-1,"",0,new double[]{0.46})
        ));
        moves.put(MOONLIGHT_TAVERN_POISON_NIGHTBLADE,List.of(
        new ClassicMove(MOONLIGHT_TAVERN_POISON_NIGHTBLADE,"poison_cut","毒刃切入",1450,2350,"FAN",3.0,1.0,1.0,false,-1,"",0,new double[]{0.46}),
        new ClassicMove(MOONLIGHT_TAVERN_POISON_NIGHTBLADE,"phantom_strike","幻影近击",1450,2350,"RECT",3.5,1.2,1.0,false,3,"",0,new double[]{0.46})
        ));
        moves.put(MOONLIGHT_TAVERN_CRAZY_JUNK,List.of(
        new ClassicMove(MOONLIGHT_TAVERN_CRAZY_JUNK,"duck_dash","俯冲",1450,2350,"NONE",2.6,1.0,0,false,-1,"duck_dash",0,new double[]{0.46}),
        new ClassicMove(MOONLIGHT_TAVERN_CRAZY_JUNK,"sway","摆动",1450,2350,"NONE",2.6,1.0,0,false,-1,"sway",0,new double[]{0.46}),
        new ClassicMove(MOONLIGHT_TAVERN_CRAZY_JUNK,"rapid_punch","快速连拳",1800,2700,"FAN",2.6,1.0,1.0,false,-1,"",0,new double[]{0.3625,0.4875,0.6125,0.7375})
        ));
        moves.put(MOONLIGHT_TAVERN_HOLIO,List.of(
        new ClassicMove(MOONLIGHT_TAVERN_HOLIO,"moonlight_slash","月光斩",1550,2450,"FAN",3.0,1.0,1.0,false,-1,"",0,new double[]{0.46}),
        new ClassicMove(MOONLIGHT_TAVERN_HOLIO,"ghost_array","鬼阵",1800,2700,"CIRCLE",5.5,1.0,1.0,true,3,"ghost_array",0,new double[]{0.42,0.54,0.66}),
        new ClassicMove(MOONLIGHT_TAVERN_HOLIO,"ghost_flash","鬼影闪",1300,3200,"DASH",10,1.6,1.0,false,-1,"dash",0,new double[]{0.46})
        ));
        moves.put(MOONLIGHT_TAVERN_AGANZO,List.of(
        new ClassicMove(MOONLIGHT_TAVERN_AGANZO,"draw_slash","拔刀斩",1300,2200,"FAN",4.0,1.0,1.0,false,-1,"",0,new double[]{0.46}),
        new ClassicMove(MOONLIGHT_TAVERN_AGANZO,"raging_dragon","猛龙断空斩",1600,3400,"DASH",10,1.6,1.0,false,-1,"dash",0,new double[]{0.46}),
        new ClassicMove(MOONLIGHT_TAVERN_AGANZO,"phantom_dance","幻影剑舞",2600,3500,"FAN",2.6,1.0,1.0,false,-1,"",0,new double[]{0.35,0.45,0.55,0.65,0.75})
        ));
        moves.put(MOONLIGHT_TAVERN_CHANG_SANLANG,List.of(
        new ClassicMove(MOONLIGHT_TAVERN_CHANG_SANLANG,"throw_net","抛网",1450,3250,"DART",12.0,1.1,0.7,false,-1,"",12,new double[]{0.46}),
        new ClassicMove(MOONLIGHT_TAVERN_CHANG_SANLANG,"brick_strike","砖击",1450,2350,"FAN",3.0,1.0,1.0,false,-1,"",12,new double[]{0.46}),
        new ClassicMove(MOONLIGHT_TAVERN_CHANG_SANLANG,"grab_burst","抓取爆破",1500,2400,"FAN",2.6,1.0,1.0,false,-1,"",12,new double[]{0.46}),
        new ClassicMove(MOONLIGHT_TAVERN_CHANG_SANLANG,"mount_punch","骑压拳",2300,3200,"FAN",2.6,1.0,1.0,false,-1,"",0,new double[]{0.3833,0.55,0.7167}),
        new ClassicMove(MOONLIGHT_TAVERN_CHANG_SANLANG,"suplex","背摔",1800,2700,"FAN",2.6,1.0,1.0,false,-1,"",12,new double[]{0.46})
        ));
        return Map.copyOf(moves);
    }
    public static void build(ServerLevel world,BlockPos origin){ClassicDungeonRooms.build(world,origin,ID);}
    private MoonlightTavern(){}
}
