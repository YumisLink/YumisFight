package cn.yumis.fight.modules.dungeons.content;

import cn.yumis.fight.modules.dungeons.api.*;
import java.util.*;
import static cn.yumis.fight.modules.dungeons.api.ClassicEnemyKind.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;

/** mysterious_awareness content; each ordinary pack has one elite followed by nine ordinary enemies. */
public final class MysteriousAwareness {
    public static final String ID="mysterious_awareness";
    public static final ClassicEnemyKind BOSS=MYSTERIOUS_AWARENESS_IRIS;
    public static final String[] AREAS={"时空玩具厅","发条舞台","饥饿回声","心魔镜廊","元素魂之席","人偶协奏厅","艾丽丝的终章"};
    public static final ClassicEnemyKind[][] WAVES={
        new ClassicEnemyKind[]{MYSTERIOUS_AWARENESS_ICE_SOUL,MYSTERIOUS_AWARENESS_SPACE_SOLDIER,MYSTERIOUS_AWARENESS_SPACE_SOLDIER,MYSTERIOUS_AWARENESS_SPACE_SOLDIER,MYSTERIOUS_AWARENESS_SPACE_SOLDIER,MYSTERIOUS_AWARENESS_SPACE_SOLDIER,MYSTERIOUS_AWARENESS_TIME_SOLDIER,MYSTERIOUS_AWARENESS_TIME_SOLDIER,MYSTERIOUS_AWARENESS_TIME_SOLDIER,MYSTERIOUS_AWARENESS_TIME_SOLDIER},
        new ClassicEnemyKind[]{MYSTERIOUS_AWARENESS_FIRE_SOUL,MYSTERIOUS_AWARENESS_AXE_PUPPET,MYSTERIOUS_AWARENESS_AXE_PUPPET,MYSTERIOUS_AWARENESS_AXE_PUPPET,MYSTERIOUS_AWARENESS_WHIP_PUPPET,MYSTERIOUS_AWARENESS_WHIP_PUPPET,MYSTERIOUS_AWARENESS_WHIP_PUPPET,MYSTERIOUS_AWARENESS_GRAMOPHONE,MYSTERIOUS_AWARENESS_GRAMOPHONE,MYSTERIOUS_AWARENESS_GRAMOPHONE},
        new ClassicEnemyKind[]{MYSTERIOUS_AWARENESS_DARU,MYSTERIOUS_AWARENESS_SWORD_PUPPET,MYSTERIOUS_AWARENESS_SWORD_PUPPET,MYSTERIOUS_AWARENESS_SWORD_PUPPET,MYSTERIOUS_AWARENESS_FIGHTER_PUPPET,MYSTERIOUS_AWARENESS_FIGHTER_PUPPET,MYSTERIOUS_AWARENESS_FIGHTER_PUPPET,MYSTERIOUS_AWARENESS_SPACE_SOLDIER,MYSTERIOUS_AWARENESS_SPACE_SOLDIER,MYSTERIOUS_AWARENESS_SPACE_SOLDIER},
        new ClassicEnemyKind[]{MYSTERIOUS_AWARENESS_MIRROR_DEMON,MYSTERIOUS_AWARENESS_AXE_PUPPET,MYSTERIOUS_AWARENESS_AXE_PUPPET,MYSTERIOUS_AWARENESS_AXE_PUPPET,MYSTERIOUS_AWARENESS_SWORD_PUPPET,MYSTERIOUS_AWARENESS_SWORD_PUPPET,MYSTERIOUS_AWARENESS_SWORD_PUPPET,MYSTERIOUS_AWARENESS_TIME_SOLDIER,MYSTERIOUS_AWARENESS_TIME_SOLDIER,MYSTERIOUS_AWARENESS_TIME_SOLDIER},
        new ClassicEnemyKind[]{MYSTERIOUS_AWARENESS_ICE_SOUL,MYSTERIOUS_AWARENESS_WHIP_PUPPET,MYSTERIOUS_AWARENESS_WHIP_PUPPET,MYSTERIOUS_AWARENESS_WHIP_PUPPET,MYSTERIOUS_AWARENESS_FIGHTER_PUPPET,MYSTERIOUS_AWARENESS_FIGHTER_PUPPET,MYSTERIOUS_AWARENESS_FIGHTER_PUPPET,MYSTERIOUS_AWARENESS_GRAMOPHONE,MYSTERIOUS_AWARENESS_GRAMOPHONE,MYSTERIOUS_AWARENESS_GRAMOPHONE},
        new ClassicEnemyKind[]{MYSTERIOUS_AWARENESS_MIRROR_DEMON,MYSTERIOUS_AWARENESS_AXE_PUPPET,MYSTERIOUS_AWARENESS_AXE_PUPPET,MYSTERIOUS_AWARENESS_WHIP_PUPPET,MYSTERIOUS_AWARENESS_WHIP_PUPPET,MYSTERIOUS_AWARENESS_SWORD_PUPPET,MYSTERIOUS_AWARENESS_SWORD_PUPPET,MYSTERIOUS_AWARENESS_SWORD_PUPPET,MYSTERIOUS_AWARENESS_FIGHTER_PUPPET,MYSTERIOUS_AWARENESS_FIGHTER_PUPPET}
    };
    public static Map<ClassicEnemyKind,List<ClassicMove>> moves(){
        Map<ClassicEnemyKind,List<ClassicMove>> moves=new EnumMap<>(ClassicEnemyKind.class);
        moves.put(MYSTERIOUS_AWARENESS_SPACE_SOLDIER,List.of(
        new ClassicMove(MYSTERIOUS_AWARENESS_SPACE_SOLDIER,"throw","投掷",1200,3200,"BOMB",2.5,0.65,1.0,false,-1,"",0,new double[]{0.46}),
        new ClassicMove(MYSTERIOUS_AWARENESS_SPACE_SOLDIER,"aura","气场展开",1450,10000,"CIRCLE",6.0,1.0,1.0,true,-1,"aura",0,new double[]{0.46})
        ));
        moves.put(MYSTERIOUS_AWARENESS_TIME_SOLDIER,List.of(
        new ClassicMove(MYSTERIOUS_AWARENESS_TIME_SOLDIER,"jump","跃起／扑击",1400,3200,"DASH",7.0,1.6,1.0,false,-1,"dash",0,new double[]{0.46}),
        new ClassicMove(MYSTERIOUS_AWARENESS_TIME_SOLDIER,"slash","挥斩",1050,2000,"FAN",3.0,1.0,1.0,false,-1,"",0,new double[]{0.46}),
        new ClassicMove(MYSTERIOUS_AWARENESS_TIME_SOLDIER,"aura","气场展开",1450,10000,"CIRCLE",6.0,1.0,1.0,true,-1,"aura",0,new double[]{0.46})
        ));
        moves.put(MYSTERIOUS_AWARENESS_AXE_PUPPET,List.of(
        new ClassicMove(MYSTERIOUS_AWARENESS_AXE_PUPPET,"slam","蓄力砸地",1550,3350,"WAVE",5.5,1.2,1.0,false,-1,"",0,new double[]{0.46}),
        new ClassicMove(MYSTERIOUS_AWARENESS_AXE_PUPPET,"slash","挥斩",1050,2000,"FAN",3.0,1.0,1.0,false,-1,"",0,new double[]{0.46})
        ));
        moves.put(MYSTERIOUS_AWARENESS_WHIP_PUPPET,List.of(
        new ClassicMove(MYSTERIOUS_AWARENESS_WHIP_PUPPET,"whip","鞭击",1250,2150,"RECT",6.0,1.3,1.0,false,-1,"",0,new double[]{0.46})
        ));
        moves.put(MYSTERIOUS_AWARENESS_SWORD_PUPPET,List.of(
        new ClassicMove(MYSTERIOUS_AWARENESS_SWORD_PUPPET,"slash","挥斩",1050,2000,"FAN",3.0,1.0,1.0,false,-1,"",0,new double[]{0.46}),
        new ClassicMove(MYSTERIOUS_AWARENESS_SWORD_PUPPET,"spin","旋转攻击",1600,2500,"CIRCLE",6.0,1.0,1.0,false,-1,"",0,new double[]{0.46})
        ));
        moves.put(MYSTERIOUS_AWARENESS_FIGHTER_PUPPET,List.of(
        new ClassicMove(MYSTERIOUS_AWARENESS_FIGHTER_PUPPET,"jump","跃起／扑击",1400,3200,"DASH",7.0,1.6,1.0,false,-1,"dash",0,new double[]{0.46}),
        new ClassicMove(MYSTERIOUS_AWARENESS_FIGHTER_PUPPET,"kick","踢击",1050,2000,"FAN",2.6,1.0,1.0,false,-1,"",12,new double[]{0.46})
        ));
        moves.put(MYSTERIOUS_AWARENESS_GRAMOPHONE,List.of(
        new ClassicMove(MYSTERIOUS_AWARENESS_GRAMOPHONE,"music_bite","留声机咬击",1450,2350,"FAN",2.3,1.0,1.0,false,-1,"mouth",0,new double[]{0.46})
        ));
        moves.put(MYSTERIOUS_AWARENESS_ICE_SOUL,List.of(
        new ClassicMove(MYSTERIOUS_AWARENESS_ICE_SOUL,"ice","寒冰施法",1450,2350,"CIRCLE",6.0,1.0,1.0,true,2,"",0,new double[]{0.46}),
        new ClassicMove(MYSTERIOUS_AWARENESS_ICE_SOUL,"aura","气场展开",1450,10000,"CIRCLE",6.0,1.0,1.0,true,-1,"aura",0,new double[]{0.46})
        ));
        moves.put(MYSTERIOUS_AWARENESS_FIRE_SOUL,List.of(
        new ClassicMove(MYSTERIOUS_AWARENESS_FIRE_SOUL,"teleport","瞬移姿态",1450,8000,"NONE",2.6,1.0,0,false,-1,"teleport",0,new double[]{0.46}),
        new ClassicMove(MYSTERIOUS_AWARENESS_FIRE_SOUL,"fire","火焰施法",1450,2350,"CIRCLE",6.0,1.0,1.0,true,1,"",0,new double[]{0.42,0.54,0.66}),
        new ClassicMove(MYSTERIOUS_AWARENESS_FIRE_SOUL,"aura","气场展开",1450,10000,"CIRCLE",6.0,1.0,1.0,true,-1,"aura",0,new double[]{0.46})
        ));
        moves.put(MYSTERIOUS_AWARENESS_DARU,List.of(
        new ClassicMove(MYSTERIOUS_AWARENESS_DARU,"claw","爪击",1450,2350,"FAN",2.6,1.0,1.0,false,-1,"",0,new double[]{0.46}),
        new ClassicMove(MYSTERIOUS_AWARENESS_DARU,"possession","凭依",1450,8000,"NONE",2.6,1.0,0,false,3,"possession",0,new double[]{0.46}),
        new ClassicMove(MYSTERIOUS_AWARENESS_DARU,"inhale","吸附吸血",1450,2350,"CIRCLE",8.0,1.0,0.8,true,3,"inhale",0,new double[]{0.46}),
        new ClassicMove(MYSTERIOUS_AWARENESS_DARU,"charge","冲锋",1300,3200,"DASH",10,1.6,1.0,false,-1,"dash",0,new double[]{0.46})
        ));
        moves.put(MYSTERIOUS_AWARENESS_MIRROR_DEMON,List.of(
        new ClassicMove(MYSTERIOUS_AWARENESS_MIRROR_DEMON,"mirror","映照外形",1450,10000,"NONE",2.6,1.0,0,false,-1,"aura",0,new double[]{0.46}),
        new ClassicMove(MYSTERIOUS_AWARENESS_MIRROR_DEMON,"draw_slash","拔刀斩",1300,2200,"FAN",4.0,1.0,1.0,false,-1,"",0,new double[]{0.46}),
        new ClassicMove(MYSTERIOUS_AWARENESS_MIRROR_DEMON,"raging_dragon","猛龙断空斩",1600,3400,"DASH",10,1.6,1.0,false,-1,"dash",0,new double[]{0.46}),
        new ClassicMove(MYSTERIOUS_AWARENESS_MIRROR_DEMON,"phantom_dance","幻影剑舞",2600,3500,"FAN",2.6,1.0,1.0,false,-1,"",0,new double[]{0.35,0.45,0.55,0.65,0.75})
        ));
        moves.put(MYSTERIOUS_AWARENESS_IRIS,List.of(
        new ClassicMove(MYSTERIOUS_AWARENESS_IRIS,"music","元素乐章",2600,3500,"CIRCLE",7,1.0,1.0,true,-1,"music",0,new double[]{0.42,0.54,0.66}),
        new ClassicMove(MYSTERIOUS_AWARENESS_IRIS,"puppet","支配人偶",2000,10000,"NONE",2.6,1.0,0,false,-1,"puppet",0,new double[]{0.46}),
        new ClassicMove(MYSTERIOUS_AWARENESS_IRIS,"ice","寒冰施法",1450,2350,"CIRCLE",6.0,1.0,1.0,true,2,"",0,new double[]{0.46}),
        new ClassicMove(MYSTERIOUS_AWARENESS_IRIS,"fire","火焰施法",1450,2350,"CIRCLE",6.0,1.0,1.0,true,1,"",0,new double[]{0.42,0.54,0.66}),
        new ClassicMove(MYSTERIOUS_AWARENESS_IRIS,"thunder","落雷施法",1450,2350,"CIRCLE",6.0,1.0,1.0,true,0,"",12,new double[]{0.46}),
        new ClassicMove(MYSTERIOUS_AWARENESS_IRIS,"instrument","乐器升空追踪",2100,10000,"NONE",2.6,1.0,0,false,-1,"instrument",0,new double[]{0.46}),
        new ClassicMove(MYSTERIOUS_AWARENESS_IRIS,"instrument_swing","近身挥琴",1450,2350,"FAN",3.0,1.0,1.0,false,-1,"",0,new double[]{0.46})
        ));
        moves.put(MYSTERIOUS_AWARENESS_MARILETE,List.of(
        new ClassicMove(MYSTERIOUS_AWARENESS_MARILETE,"instrument","乐器升空追踪",2100,10000,"NONE",2.6,1.0,0,false,-1,"instrument",0,new double[]{0.46})
        ));
        return Map.copyOf(moves);
    }
    public static void build(ServerLevel world,BlockPos origin){ClassicDungeonRooms.build(world,origin,ID);}
    private MysteriousAwareness(){}
}
