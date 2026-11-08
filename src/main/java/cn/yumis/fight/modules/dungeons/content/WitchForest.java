package cn.yumis.fight.modules.dungeons.content;

import cn.yumis.fight.modules.dungeons.api.*;
import java.util.*;
import static cn.yumis.fight.modules.dungeons.api.ClassicEnemyKind.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;

/** central_park content; each ordinary pack has one elite followed by nine ordinary enemies. */
public final class WitchForest {
    public static final String ID="central_park";
    public static final ClassicEnemyKind BOSS=CENTRAL_PARK_BLUE_WITCH;
    public static final String[] AREAS={"兽人林缘","落花小径","朽木伐场","稻草迷径","根须禁区","魔女庭院","蓝色魔女的使魔舞台"};
    public static final ClassicEnemyKind[][] WAVES={
        new ClassicEnemyKind[]{CENTRAL_PARK_NATURE_GUARDIAN,CENTRAL_PARK_GIZA_GUARD,CENTRAL_PARK_GIZA_GUARD,CENTRAL_PARK_GIZA_GUARD,CENTRAL_PARK_GIZA_GUARD,CENTRAL_PARK_GIZA_GUARD,CENTRAL_PARK_LUKA_GUARD,CENTRAL_PARK_LUKA_GUARD,CENTRAL_PARK_LUKA_GUARD,CENTRAL_PARK_LUKA_GUARD},
        new ClassicEnemyKind[]{CENTRAL_PARK_SUNISHAL,CENTRAL_PARK_CHAMCHAM,CENTRAL_PARK_CHAMCHAM,CENTRAL_PARK_CHAMCHAM,CENTRAL_PARK_PERIYA,CENTRAL_PARK_PERIYA,CENTRAL_PARK_PERIYA,CENTRAL_PARK_LALOF_YELLOW,CENTRAL_PARK_LALOF_YELLOW,CENTRAL_PARK_LALOF_YELLOW},
        new ClassicEnemyKind[]{CENTRAL_PARK_GLAIDE_IKOS,CENTRAL_PARK_ROTTING_WOODCUTTER,CENTRAL_PARK_ROTTING_WOODCUTTER,CENTRAL_PARK_ROTTING_WOODCUTTER,CENTRAL_PARK_ROTTING_WOODCUTTER,CENTRAL_PARK_HILL_GUARD,CENTRAL_PARK_HILL_GUARD,CENTRAL_PARK_HILL_GUARD,CENTRAL_PARK_LALOF_BLUE,CENTRAL_PARK_LALOF_BLUE},
        new ClassicEnemyKind[]{CENTRAL_PARK_SCARECROW,CENTRAL_PARK_HILL_GUARD,CENTRAL_PARK_HILL_GUARD,CENTRAL_PARK_HILL_GUARD,CENTRAL_PARK_HUNTING_GRASS,CENTRAL_PARK_HUNTING_GRASS,CENTRAL_PARK_HUNTING_GRASS,CENTRAL_PARK_LALOF_PURPLE,CENTRAL_PARK_LALOF_PURPLE,CENTRAL_PARK_LALOF_PURPLE},
        new ClassicEnemyKind[]{CENTRAL_PARK_NATURE_GUARDIAN,CENTRAL_PARK_LUKA_GUARD,CENTRAL_PARK_LUKA_GUARD,CENTRAL_PARK_LUKA_GUARD,CENTRAL_PARK_PERIYA,CENTRAL_PARK_PERIYA,CENTRAL_PARK_PERIYA,CENTRAL_PARK_HUNTING_GRASS,CENTRAL_PARK_HUNTING_GRASS,CENTRAL_PARK_HUNTING_GRASS},
        new ClassicEnemyKind[]{CENTRAL_PARK_SUNISHAL,CENTRAL_PARK_GIZA_GUARD,CENTRAL_PARK_GIZA_GUARD,CENTRAL_PARK_CHAMCHAM,CENTRAL_PARK_CHAMCHAM,CENTRAL_PARK_LALOF_BLUE,CENTRAL_PARK_LALOF_BLUE,CENTRAL_PARK_LALOF_PURPLE,CENTRAL_PARK_LALOF_PURPLE,CENTRAL_PARK_LALOF_PURPLE}
    };
    public static Map<ClassicEnemyKind,List<ClassicMove>> moves(){
        Map<ClassicEnemyKind,List<ClassicMove>> moves=new EnumMap<>(ClassicEnemyKind.class);
        moves.put(CENTRAL_PARK_GIZA_GUARD,List.of(
        new ClassicMove(CENTRAL_PARK_GIZA_GUARD,"jump","跃起／扑击",1400,3200,"DASH",7.0,1.6,1.0,false,-1,"dash",0,new double[]{0.46}),
        new ClassicMove(CENTRAL_PARK_GIZA_GUARD,"slash","挥斩",1050,2000,"FAN",3.0,1.0,1.0,false,-1,"",0,new double[]{0.46})
        ));
        moves.put(CENTRAL_PARK_LUKA_GUARD,List.of(
        new ClassicMove(CENTRAL_PARK_LUKA_GUARD,"approach_hop","接近跃步",1450,3250,"DASH",7.0,1.6,0,false,-1,"dash",0,new double[]{0.46}),
        new ClassicMove(CENTRAL_PARK_LUKA_GUARD,"claw","爪击",1450,2350,"FAN",2.6,1.0,1.0,false,-1,"",0,new double[]{0.46})
        ));
        moves.put(CENTRAL_PARK_CHAMCHAM,List.of(
        new ClassicMove(CENTRAL_PARK_CHAMCHAM,"throw","投掷",1200,3200,"DART",16.0,0.4,1.0,false,-1,"",0,new double[]{0.46})
        ));
        moves.put(CENTRAL_PARK_LALOF_YELLOW,List.of(
        new ClassicMove(CENTRAL_PARK_LALOF_YELLOW,"swallow","吞食",1450,2350,"FAN",3.8,1.0,1.0,false,-1,"",12,new double[]{0.46})
        ));
        moves.put(CENTRAL_PARK_LALOF_BLUE,List.of(
        new ClassicMove(CENTRAL_PARK_LALOF_BLUE,"poison","毒弹／药瓶",1450,3250,"BOMB",2.5,0.65,1.0,true,3,"",0,new double[]{0.46})
        ));
        moves.put(CENTRAL_PARK_LALOF_PURPLE,List.of(
        new ClassicMove(CENTRAL_PARK_LALOF_PURPLE,"swallow","吞食",1450,2350,"FAN",3.8,1.0,1.0,false,-1,"",12,new double[]{0.46}),
        new ClassicMove(CENTRAL_PARK_LALOF_PURPLE,"poison","毒弹／药瓶",1450,3250,"BOMB",2.5,0.65,1.0,true,3,"",0,new double[]{0.46})
        ));
        moves.put(CENTRAL_PARK_PERIYA,List.of(
        new ClassicMove(CENTRAL_PARK_PERIYA,"whip","鞭击",1250,2150,"RECT",6.0,1.3,1.0,false,-1,"",0,new double[]{0.46})
        ));
        moves.put(CENTRAL_PARK_ROTTING_WOODCUTTER,List.of(
        new ClassicMove(CENTRAL_PARK_ROTTING_WOODCUTTER,"slash","挥斩",1050,2000,"FAN",3.0,1.0,1.0,false,-1,"",0,new double[]{0.46})
        ));
        moves.put(CENTRAL_PARK_HILL_GUARD,List.of(
        new ClassicMove(CENTRAL_PARK_HILL_GUARD,"slash","挥斩",1050,2000,"FAN",3.0,1.0,1.0,false,-1,"",0,new double[]{0.46})
        ));
        moves.put(CENTRAL_PARK_NATURE_GUARDIAN,List.of(
        new ClassicMove(CENTRAL_PARK_NATURE_GUARDIAN,"double_slam","双拳砸地",1600,3400,"WAVE",5.5,1.2,1.0,false,-1,"",0,new double[]{0.46}),
        new ClassicMove(CENTRAL_PARK_NATURE_GUARDIAN,"root_ring","根须环",1800,9000,"WAVE",6.0,1.2,1.0,true,-1,"",0,new double[]{0.46})
        ));
        moves.put(CENTRAL_PARK_SUNISHAL,List.of(
        new ClassicMove(CENTRAL_PARK_SUNISHAL,"whip_combo","连鞭",1900,2800,"RECT",6.0,1.3,1.0,false,-1,"",0,new double[]{0.4,0.66}),
        new ClassicMove(CENTRAL_PARK_SUNISHAL,"thorn_line","直线荆棘",1450,9000,"RECT",10.0,1.8,1.0,true,-1,"",0,new double[]{0.46})
        ));
        moves.put(CENTRAL_PARK_HUNTING_GRASS,List.of(
        new ClassicMove(CENTRAL_PARK_HUNTING_GRASS,"swallow","吞食",1450,2350,"FAN",3.8,1.0,1.0,false,-1,"",12,new double[]{0.46}),
        new ClassicMove(CENTRAL_PARK_HUNTING_GRASS,"poison","毒弹／药瓶",1450,3250,"BOMB",2.5,0.65,1.0,true,3,"",0,new double[]{0.46})
        ));
        moves.put(CENTRAL_PARK_GLAIDE_IKOS,List.of(
        new ClassicMove(CENTRAL_PARK_GLAIDE_IKOS,"tree_cage","树木围困",1450,9000,"NONE",6.0,1.0,0,true,-1,"tree_cage",0,new double[]{0.46}),
        new ClassicMove(CENTRAL_PARK_GLAIDE_IKOS,"triple_slam","三连砸地",2500,9000,"WAVE",5.5,1.2,1.0,false,-1,"",0,new double[]{0.209,0.489,0.769})
        ));
        moves.put(CENTRAL_PARK_SCARECROW,List.of(
        new ClassicMove(CENTRAL_PARK_SCARECROW,"pole_sweep","长柄横扫",1450,2350,"FAN",4.0,1.0,1.0,false,-1,"",0,new double[]{0.46}),
        new ClassicMove(CENTRAL_PARK_SCARECROW,"wood_spikes","四向木刺",1700,9000,"RECT",10.0,1.8,1.0,true,-1,"",12,new double[]{0.46})
        ));
        moves.put(CENTRAL_PARK_BLUE_WITCH,List.of(
        new ClassicMove(CENTRAL_PARK_BLUE_WITCH,"roots","根须召唤",1450,2350,"CIRCLE",6.0,1.0,1.0,true,-1,"",0,new double[]{0.46}),
        new ClassicMove(CENTRAL_PARK_BLUE_WITCH,"throw","投掷",1200,3200,"BOMB",2.5,0.65,1.0,false,-1,"",0,new double[]{0.46}),
        new ClassicMove(CENTRAL_PARK_BLUE_WITCH,"teleport","瞬移姿态",1450,8000,"NONE",2.6,1.0,0,false,-1,"teleport",0,new double[]{0.46}),
        new ClassicMove(CENTRAL_PARK_BLUE_WITCH,"summon","召唤",1800,10000,"NONE",2.6,1.0,0,false,-1,"summon",0,new double[]{0.46}),
        new ClassicMove(CENTRAL_PARK_BLUE_WITCH,"thunder","落雷施法",1450,2350,"CIRCLE",6.0,1.0,1.0,true,0,"",12,new double[]{0.46}),
        new ClassicMove(CENTRAL_PARK_BLUE_WITCH,"ice","寒冰施法",1450,2350,"CIRCLE",6.0,1.0,1.0,true,2,"",0,new double[]{0.46})
        ));
        moves.put(CENTRAL_PARK_CRAZY_LION,List.of(
        new ClassicMove(CENTRAL_PARK_CRAZY_LION,"short_charge","短距冲撞",1450,3250,"DASH",10,1.6,1.0,false,-1,"dash",0,new double[]{0.46}),
        new ClassicMove(CENTRAL_PARK_CRAZY_LION,"double_claw","双爪横扫",1300,2200,"FAN",2.6,1.0,1.0,false,-1,"",0,new double[]{0.425,0.675})
        ));
        moves.put(CENTRAL_PARK_CRAZY_LION_CAT,List.of(
        new ClassicMove(CENTRAL_PARK_CRAZY_LION_CAT,"jump","跃起／扑击",1400,3200,"DASH",7.0,1.6,1.0,false,-1,"dash",0,new double[]{0.46})
        ));
        moves.put(CENTRAL_PARK_TOTTO,List.of(
        new ClassicMove(CENTRAL_PARK_TOTTO,"transform_pounce","变身扑咬",1850,3650,"DASH",10,1.6,1.0,false,-1,"dash",0,new double[]{0.46}),
        new ClassicMove(CENTRAL_PARK_TOTTO,"landing_wave","落地冲击波",1750,9000,"WAVE",8,1.2,1.0,false,-1,"",12,new double[]{0.46})
        ));
        moves.put(CENTRAL_PARK_TOTTO_PUPPY,List.of(
        new ClassicMove(CENTRAL_PARK_TOTTO_PUPPY,"jump","跃起／扑击",1400,3200,"DASH",7.0,1.6,1.0,false,-1,"dash",0,new double[]{0.46})
        ));
        return Map.copyOf(moves);
    }
    public static void build(ServerLevel world,BlockPos origin){ClassicDungeonRooms.build(world,origin,ID);}
    private WitchForest(){}
}
