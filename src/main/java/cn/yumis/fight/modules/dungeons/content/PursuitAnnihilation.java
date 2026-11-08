package cn.yumis.fight.modules.dungeons.content;

import cn.yumis.fight.modules.dungeons.api.*;
import java.util.*;
import static cn.yumis.fight.modules.dungeons.api.ClassicEnemyKind.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;

/** pursuit content; each ordinary pack has one elite followed by nine ordinary enemies. */
public final class PursuitAnnihilation {
    public static final String ID="pursuit";
    public static final ClassicEnemyKind BOSS=PURSUIT_GIZEL;
    public static final String[] AREAS={"卡勒特哨线","强袭阵地","潜行封锁区","改造实验所","分裂机械库","最后碉堡","吉赛尔的机械平台"};
    public static final ClassicEnemyKind[][] WAVES={
        new ClassicEnemyKind[]{PURSUIT_OLD_SERGEANT,PURSUIT_CARTEL_SOLDIER,PURSUIT_CARTEL_SOLDIER,PURSUIT_CARTEL_SOLDIER,PURSUIT_CARTEL_SOLDIER,PURSUIT_CARTEL_SOLDIER,PURSUIT_CARTEL_RIFLEMAN,PURSUIT_CARTEL_RIFLEMAN,PURSUIT_CARTEL_RIFLEMAN,PURSUIT_CARTEL_RIFLEMAN},
        new ClassicEnemyKind[]{PURSUIT_MERON,PURSUIT_CARTEL_RIFLEMAN,PURSUIT_CARTEL_RIFLEMAN,PURSUIT_CARTEL_RIFLEMAN,PURSUIT_CARTEL_RIFLEMAN,PURSUIT_CARTEL_RIFLEMAN,PURSUIT_CARTEL_ARSONIST,PURSUIT_CARTEL_ARSONIST,PURSUIT_CARTEL_ARSONIST,PURSUIT_CARTEL_ARSONIST},
        new ClassicEnemyKind[]{PURSUIT_KAMOKA,PURSUIT_CARTEL_SOLDIER,PURSUIT_CARTEL_SOLDIER,PURSUIT_CARTEL_SOLDIER,PURSUIT_MODIFIED_SOLDIER,PURSUIT_MODIFIED_SOLDIER,PURSUIT_MODIFIED_SOLDIER,PURSUIT_MODIFIED_SOLDIER,PURSUIT_ROCKET_TURRET,PURSUIT_ROCKET_TURRET},
        new ClassicEnemyKind[]{PURSUIT_MODIFIED_SOLDIER,PURSUIT_REINFORCED_SOLDIER,PURSUIT_REINFORCED_SOLDIER,PURSUIT_REINFORCED_SOLDIER,PURSUIT_CARTEL_ARSONIST,PURSUIT_CARTEL_ARSONIST,PURSUIT_CARTEL_ARSONIST,PURSUIT_TANK_BUNKER,PURSUIT_TANK_BUNKER,PURSUIT_TANK_BUNKER},
        new ClassicEnemyKind[]{PURSUIT_SPARK_GAMMA,PURSUIT_MODIFIED_SOLDIER,PURSUIT_MODIFIED_SOLDIER,PURSUIT_MODIFIED_SOLDIER,PURSUIT_MODIFIED_SOLDIER,PURSUIT_CARTEL_RIFLEMAN,PURSUIT_CARTEL_RIFLEMAN,PURSUIT_CARTEL_RIFLEMAN,PURSUIT_ROCKET_TURRET,PURSUIT_ROCKET_TURRET},
        new ClassicEnemyKind[]{PURSUIT_REINFORCED_SOLDIER,PURSUIT_REINFORCED_SOLDIER,PURSUIT_REINFORCED_SOLDIER,PURSUIT_REINFORCED_SOLDIER,PURSUIT_REINFORCED_SOLDIER,PURSUIT_CARTEL_RIFLEMAN,PURSUIT_CARTEL_RIFLEMAN,PURSUIT_CARTEL_RIFLEMAN,PURSUIT_TANK_BUNKER,PURSUIT_TANK_BUNKER}
    };
    public static Map<ClassicEnemyKind,List<ClassicMove>> moves(){
        Map<ClassicEnemyKind,List<ClassicMove>> moves=new EnumMap<>(ClassicEnemyKind.class);
        moves.put(PURSUIT_CARTEL_SOLDIER,List.of(
        new ClassicMove(PURSUIT_CARTEL_SOLDIER,"kick","踢击",1050,2000,"FAN",2.6,1.0,1.0,false,-1,"",12,new double[]{0.46}),
        new ClassicMove(PURSUIT_CARTEL_SOLDIER,"charge","冲锋",1300,3200,"DASH",10,1.6,1.0,false,-1,"dash",0,new double[]{0.46})
        ));
        moves.put(PURSUIT_CARTEL_RIFLEMAN,List.of(
        new ClassicMove(PURSUIT_CARTEL_RIFLEMAN,"shoot","枪械射击",1050,3200,"DART",16.0,0.55,1.0,false,-1,"",0,new double[]{0.365,0.535,0.705}),
        new ClassicMove(PURSUIT_CARTEL_RIFLEMAN,"stab","突刺",900,2000,"RECT",3.5,1.2,1.0,false,-1,"",0,new double[]{0.46})
        ));
        moves.put(PURSUIT_CARTEL_ARSONIST,List.of(
        new ClassicMove(PURSUIT_CARTEL_ARSONIST,"throw","投掷",1200,3200,"BOMB",2.5,0.65,1.0,false,-1,"",0,new double[]{0.46}),
        new ClassicMove(PURSUIT_CARTEL_ARSONIST,"stab","突刺",900,2000,"RECT",3.5,1.2,1.0,false,-1,"",0,new double[]{0.46})
        ));
        moves.put(PURSUIT_MODIFIED_SOLDIER,List.of(
        new ClassicMove(PURSUIT_MODIFIED_SOLDIER,"shoot","枪械射击",1050,3200,"DART",16.0,0.55,1.0,false,-1,"",0,new double[]{0.365,0.535,0.705}),
        new ClassicMove(PURSUIT_MODIFIED_SOLDIER,"stab","突刺",900,2000,"RECT",3.5,1.2,1.0,false,-1,"",0,new double[]{0.46}),
        new ClassicMove(PURSUIT_MODIFIED_SOLDIER,"guard","防御姿态",1450,10000,"NONE",2.6,1.0,0,false,-1,"guard",0,new double[]{0.46})
        ));
        moves.put(PURSUIT_REINFORCED_SOLDIER,List.of(
        new ClassicMove(PURSUIT_REINFORCED_SOLDIER,"shoot","枪械射击",1050,3200,"DART",16.0,0.55,1.0,false,-1,"",0,new double[]{0.365,0.535,0.705}),
        new ClassicMove(PURSUIT_REINFORCED_SOLDIER,"stab","突刺",900,2000,"RECT",3.5,1.2,1.0,false,-1,"",0,new double[]{0.46})
        ));
        moves.put(PURSUIT_ROCKET_TURRET,List.of(
        new ClassicMove(PURSUIT_ROCKET_TURRET,"shoot","枪械射击",1050,3200,"DART",16.0,0.55,1.0,false,-1,"",0,new double[]{0.365,0.535,0.705}),
        new ClassicMove(PURSUIT_ROCKET_TURRET,"missile","发射导弹",1800,3600,"BOMB",2.5,0.7,1.0,true,1,"",0,new double[]{0.46})
        ));
        moves.put(PURSUIT_TANK_BUNKER,List.of(
        new ClassicMove(PURSUIT_TANK_BUNKER,"shoot","枪械射击",1050,3200,"DART",16.0,0.55,1.0,false,-1,"",0,new double[]{0.365,0.535,0.705})
        ));
        moves.put(PURSUIT_OLD_SERGEANT,List.of(
        new ClassicMove(PURSUIT_OLD_SERGEANT,"kick","踢击",1050,2000,"FAN",2.6,1.0,1.0,false,-1,"",12,new double[]{0.46}),
        new ClassicMove(PURSUIT_OLD_SERGEANT,"teleport","瞬移姿态",1450,8000,"NONE",2.6,1.0,0,false,-1,"teleport",0,new double[]{0.46}),
        new ClassicMove(PURSUIT_OLD_SERGEANT,"stab","突刺",900,2000,"RECT",3.5,1.2,1.0,false,-1,"",0,new double[]{0.46})
        ));
        moves.put(PURSUIT_MERON,List.of(
        new ClassicMove(PURSUIT_MERON,"shoot","枪械射击",1050,3200,"DART",16.0,0.55,1.0,false,-1,"",0,new double[]{0.365,0.535,0.705}),
        new ClassicMove(PURSUIT_MERON,"jump","跃起／扑击",1400,3200,"DASH",7.0,1.6,1.0,false,-1,"dash",0,new double[]{0.46})
        ));
        moves.put(PURSUIT_KAMOKA,List.of(
        new ClassicMove(PURSUIT_KAMOKA,"slash","挥斩",1050,2000,"FAN",3.0,1.0,1.0,false,-1,"",0,new double[]{0.46}),
        new ClassicMove(PURSUIT_KAMOKA,"teleport","瞬移姿态",1450,8000,"NONE",2.6,1.0,0,false,-1,"teleport",0,new double[]{0.46}),
        new ClassicMove(PURSUIT_KAMOKA,"summon","召唤",1800,10000,"NONE",2.6,1.0,0,false,-1,"summon",0,new double[]{0.46})
        ));
        moves.put(PURSUIT_SPARK_GAMMA,List.of(
        new ClassicMove(PURSUIT_SPARK_GAMMA,"split","四向分裂",1700,2600,"NONE",2.6,1.0,0,false,-1,"split",0,new double[]{0.46}),
        new ClassicMove(PURSUIT_SPARK_GAMMA,"bombard","分裂体轰炸",1450,3250,"BOMB",2.5,0.7,1.0,true,1,"",0,new double[]{0.46})
        ));
        moves.put(PURSUIT_GIZEL,List.of(
        new ClassicMove(PURSUIT_GIZEL,"saw","电锯挥扫",1450,2350,"FAN",4.2,1.0,1.2,false,-1,"",0,new double[]{0.36,0.48,0.6}),
        new ClassicMove(PURSUIT_GIZEL,"charge","冲锋",1300,3200,"DASH",10,1.6,1.0,false,-1,"dash",0,new double[]{0.46}),
        new ClassicMove(PURSUIT_GIZEL,"missile","发射导弹",1800,3600,"BOMB",2.5,0.7,1.0,true,1,"",0,new double[]{0.46}),
        new ClassicMove(PURSUIT_GIZEL,"support_call","开启支援舱",2000,10000,"NONE",2.6,1.0,0,false,-1,"support_call",0,new double[]{0.46}),
        new ClassicMove(PURSUIT_GIZEL,"time_call","召唤时间机器",2100,10000,"NONE",2.6,1.0,0,false,-1,"time_call",0,new double[]{0.46}),
        new ClassicMove(PURSUIT_GIZEL,"repair_stance","受维修姿态",1450,2350,"NONE",2.6,1.0,0,false,-1,"repair_stance",0,new double[]{0.46})
        ));
        moves.put(PURSUIT_ENGINEER,List.of(
        new ClassicMove(PURSUIT_ENGINEER,"repair","修理",1800,2700,"NONE",2.6,1.0,0,false,-1,"repair",0,new double[]{0.46}),
        new ClassicMove(PURSUIT_ENGINEER,"stab","突刺",900,2000,"RECT",3.5,1.2,1.0,false,-1,"",0,new double[]{0.46})
        ));
        moves.put(PURSUIT_SPARK_BETA,List.of(
        new ClassicMove(PURSUIT_SPARK_BETA,"aura","气场展开",1450,10000,"CIRCLE",6.0,1.0,1.0,true,-1,"",0,new double[]{0.46})
        ));
        moves.put(PURSUIT_MECHANICAL_MOUSE,List.of(
        new ClassicMove(PURSUIT_MECHANICAL_MOUSE,"charge","冲锋",1300,3200,"DASH",10,1.6,1.0,false,-1,"dash",0,new double[]{0.46}),
        new ClassicMove(PURSUIT_MECHANICAL_MOUSE,"explode","自爆准备",1450,2350,"CIRCLE",6.0,1.0,1.0,true,1,"",0,new double[]{0.46})
        ));
        moves.put(PURSUIT_TIME_MACHINE,List.of(
        new ClassicMove(PURSUIT_TIME_MACHINE,"time","时间机器启动",2600,3500,"NONE",2.6,1.0,0,false,-1,"time",0,new double[]{0.46}),
        new ClassicMove(PURSUIT_TIME_MACHINE,"core","核心暴露／打断",1450,10000,"NONE",2.6,1.0,0,false,-1,"core",0,new double[]{0.46})
        ));
        return Map.copyOf(moves);
    }
    public static void build(ServerLevel world,BlockPos origin){ClassicDungeonRooms.build(world,origin,ID);}
    private PursuitAnnihilation(){}
}
