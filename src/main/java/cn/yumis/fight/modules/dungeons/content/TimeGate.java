package cn.yumis.fight.modules.dungeons.content;

import cn.yumis.fight.modules.dungeons.api.*;
import java.util.*;
import static cn.yumis.fight.modules.dungeons.api.ClassicEnemyKind.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;

/** time_rift content; each ordinary pack has one elite followed by nine ordinary enemies. */
public final class TimeGate {
    public static final String ID="time_rift";
    public static final ClassicEnemyKind BOSS=TIME_RIFT_YOUNG_SKASA;
    public static final String[] AREAS={"大火灾","传染病","卡勒特之初","暗黑圣战","绝密区域","昔日悲鸣","凛冬的年轻冰龙"};
    public static final ClassicEnemyKind[][] WAVES={
        new ClassicEnemyKind[]{TIME_RIFT_UTARA,TIME_RIFT_ARSONIST,TIME_RIFT_ARSONIST,TIME_RIFT_ARSONIST,TIME_RIFT_JUNGLE_WARRIOR,TIME_RIFT_JUNGLE_WARRIOR,TIME_RIFT_JUNGLE_WARRIOR,TIME_RIFT_MINOTAUR_GUARD,TIME_RIFT_MINOTAUR_GUARD,TIME_RIFT_MINOTAUR_GUARD},
        new ClassicEnemyKind[]{TIME_RIFT_SKELETON_KNIGHT,TIME_RIFT_BLOOD_GHOUL,TIME_RIFT_BLOOD_GHOUL,TIME_RIFT_BLOOD_GHOUL,TIME_RIFT_INFECTED,TIME_RIFT_INFECTED,TIME_RIFT_INFECTED,TIME_RIFT_PLAGUE_MELTER,TIME_RIFT_PLAGUE_MELTER,TIME_RIFT_PLAGUE_MELTER},
        new ClassicEnemyKind[]{TIME_RIFT_YOUNG_BELAIT,TIME_RIFT_AXE_OUTLAW,TIME_RIFT_AXE_OUTLAW,TIME_RIFT_AXE_OUTLAW,TIME_RIFT_GOBLIN_GUNNER,TIME_RIFT_GOBLIN_GUNNER,TIME_RIFT_GOBLIN_GUNNER,TIME_RIFT_HUMAN_HUNTER,TIME_RIFT_HUMAN_HUNTER,TIME_RIFT_HUMAN_HUNTER},
        new ClassicEnemyKind[]{TIME_RIFT_NILVAS,TIME_RIFT_SCISSOR_IMP,TIME_RIFT_SCISSOR_IMP,TIME_RIFT_SCISSOR_IMP,TIME_RIFT_PINCER_IMP,TIME_RIFT_PINCER_IMP,TIME_RIFT_PINCER_IMP,TIME_RIFT_CHAOS_SPIRIT,TIME_RIFT_CHAOS_SPIRIT,TIME_RIFT_CHAOS_SPIRIT},
        new ClassicEnemyKind[]{TIME_RIFT_CERBERUS,TIME_RIFT_IMPERIAL_SOLDIER,TIME_RIFT_IMPERIAL_SOLDIER,TIME_RIFT_IMPERIAL_SOLDIER,TIME_RIFT_IMPERIAL_RESEARCHER,TIME_RIFT_IMPERIAL_RESEARCHER,TIME_RIFT_IMPERIAL_RESEARCHER,TIME_RIFT_GHOST_CAT,TIME_RIFT_GHOST_CAT,TIME_RIFT_GHOST_CAT},
        new ClassicEnemyKind[]{TIME_RIFT_KANE,TIME_RIFT_MIST_SOLDIER,TIME_RIFT_MIST_SOLDIER,TIME_RIFT_MIST_SOLDIER,TIME_RIFT_MIST_SPELLCASTER,TIME_RIFT_MIST_SPELLCASTER,TIME_RIFT_MIST_SPELLCASTER,TIME_RIFT_GRIEF_FIREBEE,TIME_RIFT_GRIEF_FIREBEE,TIME_RIFT_GRIEF_FIREBEE}
    };
    public static Map<ClassicEnemyKind,List<ClassicMove>> moves(){
        Map<ClassicEnemyKind,List<ClassicMove>> moves=new EnumMap<>(ClassicEnemyKind.class);
        moves.put(TIME_RIFT_ARSONIST,List.of(
        new ClassicMove(TIME_RIFT_ARSONIST,"throw","投掷",1200,3200,"BOMB",2.5,0.65,1.0,false,-1,"",0,new double[]{0.46})
        ));
        moves.put(TIME_RIFT_JUNGLE_WARRIOR,List.of(
        new ClassicMove(TIME_RIFT_JUNGLE_WARRIOR,"jump","跃起／扑击",1400,3200,"DASH",7.0,1.6,1.0,false,-1,"dash",0,new double[]{0.46}),
        new ClassicMove(TIME_RIFT_JUNGLE_WARRIOR,"slash","挥斩",1050,2000,"FAN",3.0,1.0,1.0,false,-1,"",0,new double[]{0.46})
        ));
        moves.put(TIME_RIFT_MINOTAUR_GUARD,List.of(
        new ClassicMove(TIME_RIFT_MINOTAUR_GUARD,"slash","挥斩",1050,2000,"FAN",3.0,1.0,1.0,false,-1,"",0,new double[]{0.46})
        ));
        moves.put(TIME_RIFT_UTARA,List.of(
        new ClassicMove(TIME_RIFT_UTARA,"charge","冲锋",1300,3200,"DASH",10,1.6,1.0,false,-1,"dash",0,new double[]{0.46}),
        new ClassicMove(TIME_RIFT_UTARA,"slam","蓄力砸地",1550,3350,"WAVE",5.5,1.2,1.0,false,-1,"",0,new double[]{0.46}),
        new ClassicMove(TIME_RIFT_UTARA,"summon","召唤",1800,10000,"NONE",2.6,1.0,0,false,-1,"summon",0,new double[]{0.46}),
        new ClassicMove(TIME_RIFT_UTARA,"breath","吐息",2200,3100,"RECT",13.0,4.0,1.0,true,-1,"",0,new double[]{0.42,0.54,0.66})
        ));
        moves.put(TIME_RIFT_BLOOD_GHOUL,List.of(
        new ClassicMove(TIME_RIFT_BLOOD_GHOUL,"poison","毒弹／药瓶",1450,3250,"BOMB",2.5,0.65,1.0,true,3,"",0,new double[]{0.46}),
        new ClassicMove(TIME_RIFT_BLOOD_GHOUL,"claw","爪击",1450,2350,"FAN",2.6,1.0,1.0,false,-1,"",0,new double[]{0.46}),
        new ClassicMove(TIME_RIFT_BLOOD_GHOUL,"explode","自爆准备",1450,2350,"CIRCLE",6.0,1.0,1.0,true,1,"",0,new double[]{0.46})
        ));
        moves.put(TIME_RIFT_INFECTED,List.of(
        new ClassicMove(TIME_RIFT_INFECTED,"punch","体术连拳",1450,2350,"FAN",2.6,1.0,1.0,false,-1,"",0,new double[]{0.425,0.675})
        ));
        moves.put(TIME_RIFT_PLAGUE_MELTER,List.of(
        new ClassicMove(TIME_RIFT_PLAGUE_MELTER,"slam","蓄力砸地",1550,3350,"WAVE",5.5,1.2,1.0,false,-1,"",0,new double[]{0.46}),
        new ClassicMove(TIME_RIFT_PLAGUE_MELTER,"poison","毒弹／药瓶",1450,3250,"BOMB",2.5,0.65,1.0,true,3,"",0,new double[]{0.46}),
        new ClassicMove(TIME_RIFT_PLAGUE_MELTER,"explode","自爆准备",1450,2350,"CIRCLE",6.0,1.0,1.0,true,1,"",0,new double[]{0.46})
        ));
        moves.put(TIME_RIFT_SKELETON_KNIGHT,List.of(
        new ClassicMove(TIME_RIFT_SKELETON_KNIGHT,"stab","突刺",900,2000,"RECT",3.5,1.2,1.0,false,-1,"",0,new double[]{0.46}),
        new ClassicMove(TIME_RIFT_SKELETON_KNIGHT,"charge","冲锋",1300,3200,"DASH",10,1.6,1.0,false,-1,"dash",0,new double[]{0.46}),
        new ClassicMove(TIME_RIFT_SKELETON_KNIGHT,"summon","召唤",1800,10000,"NONE",2.6,1.0,0,false,-1,"summon",0,new double[]{0.46}),
        new ClassicMove(TIME_RIFT_SKELETON_KNIGHT,"thunder","落雷施法",1450,2350,"CIRCLE",6.0,1.0,1.0,true,0,"",12,new double[]{0.46}),
        new ClassicMove(TIME_RIFT_SKELETON_KNIGHT,"guard","防御姿态",1450,10000,"NONE",2.6,1.0,0,false,-1,"guard",0,new double[]{0.46})
        ));
        moves.put(TIME_RIFT_AXE_OUTLAW,List.of(
        new ClassicMove(TIME_RIFT_AXE_OUTLAW,"slash","挥斩",1050,2000,"FAN",3.0,1.0,1.0,false,-1,"",0,new double[]{0.46})
        ));
        moves.put(TIME_RIFT_GOBLIN_GUNNER,List.of(
        new ClassicMove(TIME_RIFT_GOBLIN_GUNNER,"shoot","枪械射击",1050,3200,"DART",16.0,0.55,1.0,false,-1,"",0,new double[]{0.365,0.535,0.705})
        ));
        moves.put(TIME_RIFT_HUMAN_HUNTER,List.of(
        new ClassicMove(TIME_RIFT_HUMAN_HUNTER,"poison","毒弹／药瓶",1450,3250,"BOMB",2.5,0.65,1.0,true,3,"",0,new double[]{0.46}),
        new ClassicMove(TIME_RIFT_HUMAN_HUNTER,"stab","突刺",900,2000,"RECT",3.5,1.2,1.0,false,-1,"",0,new double[]{0.46})
        ));
        moves.put(TIME_RIFT_YOUNG_BELAIT,List.of(
        new ClassicMove(TIME_RIFT_YOUNG_BELAIT,"shoot","枪械射击",1050,3200,"DART",16.0,0.55,1.0,false,-1,"",0,new double[]{0.365,0.535,0.705}),
        new ClassicMove(TIME_RIFT_YOUNG_BELAIT,"charge","冲锋",1300,3200,"DASH",10,1.6,1.0,false,-1,"dash",0,new double[]{0.46}),
        new ClassicMove(TIME_RIFT_YOUNG_BELAIT,"spin","旋转攻击",1600,2500,"CIRCLE",6.0,1.0,1.0,false,-1,"",0,new double[]{0.46}),
        new ClassicMove(TIME_RIFT_YOUNG_BELAIT,"teleport","瞬移姿态",1450,8000,"NONE",2.6,1.0,0,false,-1,"teleport",0,new double[]{0.46})
        ));
        moves.put(TIME_RIFT_SCISSOR_IMP,List.of(
        new ClassicMove(TIME_RIFT_SCISSOR_IMP,"slash","挥斩",1050,2000,"FAN",3.0,1.0,1.0,false,-1,"",0,new double[]{0.46})
        ));
        moves.put(TIME_RIFT_PINCER_IMP,List.of(
        new ClassicMove(TIME_RIFT_PINCER_IMP,"throw","投掷",1200,3200,"BOMB",2.5,0.65,1.0,false,-1,"",0,new double[]{0.46}),
        new ClassicMove(TIME_RIFT_PINCER_IMP,"slash","挥斩",1050,2000,"FAN",3.0,1.0,1.0,false,-1,"",0,new double[]{0.46})
        ));
        moves.put(TIME_RIFT_CHAOS_SPIRIT,List.of(
        new ClassicMove(TIME_RIFT_CHAOS_SPIRIT,"aura","气场展开",1450,10000,"CIRCLE",6.0,1.0,1.0,true,-1,"aura",0,new double[]{0.46})
        ));
        moves.put(TIME_RIFT_NILVAS,List.of(
        new ClassicMove(TIME_RIFT_NILVAS,"mine","布设地雷",1450,3250,"BOMB",2.5,0.7,1.0,true,-1,"",0,new double[]{0.46}),
        new ClassicMove(TIME_RIFT_NILVAS,"claw","爪击",1450,2350,"FAN",2.6,1.0,1.0,false,-1,"",0,new double[]{0.46}),
        new ClassicMove(TIME_RIFT_NILVAS,"charge","冲锋",1300,3200,"DASH",10,1.6,1.0,false,-1,"dash",0,new double[]{0.46}),
        new ClassicMove(TIME_RIFT_NILVAS,"spin","旋转攻击",1600,2500,"CIRCLE",6.0,1.0,1.0,false,-1,"",0,new double[]{0.46})
        ));
        moves.put(TIME_RIFT_IMPERIAL_SOLDIER,List.of(
        new ClassicMove(TIME_RIFT_IMPERIAL_SOLDIER,"stab","突刺",900,2000,"RECT",3.5,1.2,1.0,false,-1,"",0,new double[]{0.46}),
        new ClassicMove(TIME_RIFT_IMPERIAL_SOLDIER,"guard","防御姿态",1450,10000,"NONE",2.6,1.0,0,false,-1,"guard",0,new double[]{0.46})
        ));
        moves.put(TIME_RIFT_IMPERIAL_RESEARCHER,List.of(
        new ClassicMove(TIME_RIFT_IMPERIAL_RESEARCHER,"poison","毒弹／药瓶",1450,3250,"BOMB",2.5,0.65,1.0,true,3,"",0,new double[]{0.46}),
        new ClassicMove(TIME_RIFT_IMPERIAL_RESEARCHER,"throw","投掷",1200,3200,"BOMB",2.5,0.65,1.0,false,-1,"",0,new double[]{0.46})
        ));
        moves.put(TIME_RIFT_GHOST_CAT,List.of(
        new ClassicMove(TIME_RIFT_GHOST_CAT,"claw","爪击",1450,2350,"FAN",2.6,1.0,1.0,false,-1,"",0,new double[]{0.46}),
        new ClassicMove(TIME_RIFT_GHOST_CAT,"jump","跃起／扑击",1400,3200,"DASH",7.0,1.6,1.0,false,-1,"dash",0,new double[]{0.46}),
        new ClassicMove(TIME_RIFT_GHOST_CAT,"bite","咬击",1450,2350,"FAN",2.6,1.0,1.0,false,-1,"",0,new double[]{0.46})
        ));
        moves.put(TIME_RIFT_CERBERUS,List.of(
        new ClassicMove(TIME_RIFT_CERBERUS,"charge","冲锋",1300,3200,"DASH",10,1.6,1.0,false,-1,"dash",0,new double[]{0.46}),
        new ClassicMove(TIME_RIFT_CERBERUS,"bite","咬击",1450,2350,"FAN",2.6,1.0,1.0,false,-1,"",0,new double[]{0.46}),
        new ClassicMove(TIME_RIFT_CERBERUS,"aura","气场展开",1450,10000,"CIRCLE",6.0,1.0,1.0,true,-1,"",0,new double[]{0.46})
        ));
        moves.put(TIME_RIFT_MIST_SOLDIER,List.of(
        new ClassicMove(TIME_RIFT_MIST_SOLDIER,"punch","体术连拳",1450,2350,"FAN",2.6,1.0,1.0,false,-1,"",0,new double[]{0.425,0.675}),
        new ClassicMove(TIME_RIFT_MIST_SOLDIER,"guard","防御姿态",1450,10000,"NONE",2.6,1.0,0,false,-1,"guard",0,new double[]{0.46})
        ));
        moves.put(TIME_RIFT_MIST_SPELLCASTER,List.of(
        new ClassicMove(TIME_RIFT_MIST_SPELLCASTER,"slash","挥斩",1050,2000,"FAN",3.0,1.0,1.0,false,-1,"",0,new double[]{0.46}),
        new ClassicMove(TIME_RIFT_MIST_SPELLCASTER,"slam","蓄力砸地",1550,3350,"WAVE",5.5,1.2,1.0,false,-1,"",0,new double[]{0.46})
        ));
        moves.put(TIME_RIFT_GRIEF_FIREBEE,List.of(
        new ClassicMove(TIME_RIFT_GRIEF_FIREBEE,"touch","近身触击",1450,2350,"FAN",1.9,1.0,1.0,false,-1,"",0,new double[]{0.46}),
        new ClassicMove(TIME_RIFT_GRIEF_FIREBEE,"charge","冲锋",1300,3200,"DASH",10,1.6,1.0,false,-1,"dash",0,new double[]{0.46}),
        new ClassicMove(TIME_RIFT_GRIEF_FIREBEE,"explode","自爆准备",1450,2350,"CIRCLE",6.0,1.0,1.0,true,1,"",0,new double[]{0.46})
        ));
        moves.put(TIME_RIFT_KANE,List.of(
        new ClassicMove(TIME_RIFT_KANE,"slash","挥斩",1050,2000,"FAN",3.0,1.0,1.0,false,-1,"",0,new double[]{0.46}),
        new ClassicMove(TIME_RIFT_KANE,"teleport","瞬移姿态",1450,8000,"NONE",2.6,1.0,0,false,-1,"teleport",0,new double[]{0.46}),
        new ClassicMove(TIME_RIFT_KANE,"charge","冲锋",1300,3200,"DASH",10,1.6,1.0,false,-1,"dash",0,new double[]{0.46}),
        new ClassicMove(TIME_RIFT_KANE,"throw","投掷",1200,3200,"BOMB",2.5,0.65,1.0,false,-1,"",0,new double[]{0.46}),
        new ClassicMove(TIME_RIFT_KANE,"aura","气场展开",1450,10000,"CIRCLE",6.0,1.0,1.0,true,-1,"aura",0,new double[]{0.46})
        ));
        moves.put(TIME_RIFT_YOUNG_SKASA,List.of(
        new ClassicMove(TIME_RIFT_YOUNG_SKASA,"slam","蓄力砸地",1550,3350,"WAVE",5.5,1.2,1.2,false,-1,"",0,new double[]{0.46}),
        new ClassicMove(TIME_RIFT_YOUNG_SKASA,"breath","吐息",2200,3100,"RECT",13.0,4.0,1.2,true,2,"",0,new double[]{0.42,0.54,0.66}),
        new ClassicMove(TIME_RIFT_YOUNG_SKASA,"takeoff","起飞与空中转向",2200,8000,"NONE",4.0,1.0,0,false,-1,"takeoff",0,new double[]{0.46}),
        new ClassicMove(TIME_RIFT_YOUNG_SKASA,"charge","冲锋",1300,3200,"DASH",10,1.6,1.2,false,-1,"dash",0,new double[]{0.46})
        ));
        moves.put(TIME_RIFT_DISEASE_SOURCE,List.of(
        new ClassicMove(TIME_RIFT_DISEASE_SOURCE,"core","核心暴露／打断",1450,10000,"NONE",2.6,1.0,0,false,-1,"core",0,new double[]{0.46})
        ));
        moves.put(TIME_RIFT_RESEARCH_CAGE,List.of(
        new ClassicMove(TIME_RIFT_RESEARCH_CAGE,"core","核心暴露／打断",1450,10000,"NONE",2.6,1.0,0,false,-1,"core",0,new double[]{0.46})
        ));
        return Map.copyOf(moves);
    }
    public static void build(ServerLevel world,BlockPos origin){ClassicDungeonRooms.build(world,origin,ID);}
    private TimeGate(){}
}
