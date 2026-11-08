package cn.yumis.fight.modules.dungeons.content;

import cn.yumis.fight.modules.dungeons.api.*;
import java.util.*;
import static cn.yumis.fight.modules.dungeons.api.ClassicEnemyKind.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;

/** hamelin content; each ordinary pack has one elite followed by nine ordinary enemies. */
public final class Hamelin {
    public static final String ID="hamelin";
    public static final ClassicEnemyKind BOSS=HAMELIN_PETER;
    public static final String[] AREAS={"盗贼村口","破败街巷","盗贼营地","地下鼠道","鼠商集市","魔笛前庭","皮特的鼠群剧场"};
    public static final ClassicEnemyKind[][] WAVES={
        new ClassicEnemyKind[]{HAMELIN_MAD_THIEF,HAMELIN_MAD_THIEF,HAMELIN_MAD_THIEF,HAMELIN_MAD_THIEF,HAMELIN_MAD_THIEF,HAMELIN_MAD_THIEF,HAMELIN_MAD_FEMALE_THIEF,HAMELIN_MAD_FEMALE_THIEF,HAMELIN_MAD_FEMALE_THIEF,HAMELIN_MAD_FEMALE_THIEF},
        new ClassicEnemyKind[]{HAMELIN_MAD_FEMALE_THIEF,HAMELIN_MAD_FEMALE_THIEF,HAMELIN_MAD_FEMALE_THIEF,HAMELIN_MAD_FEMALE_THIEF,HAMELIN_MAD_FEMALE_THIEF,HAMELIN_GIANT_RAT,HAMELIN_GIANT_RAT,HAMELIN_GIANT_RAT,HAMELIN_GIANT_RAT,HAMELIN_GIANT_RAT},
        new ClassicEnemyKind[]{HAMELIN_ENCHANTED_BANDIT,HAMELIN_ENCHANTED_BANDIT,HAMELIN_ENCHANTED_BANDIT,HAMELIN_ENCHANTED_BANDIT,HAMELIN_GIANT_RAT,HAMELIN_GIANT_RAT,HAMELIN_GIANT_RAT,HAMELIN_MAN_EATING_RAT,HAMELIN_MAN_EATING_RAT,HAMELIN_MAN_EATING_RAT},
        new ClassicEnemyKind[]{HAMELIN_SIWOO,HAMELIN_GIANT_RAT,HAMELIN_GIANT_RAT,HAMELIN_GIANT_RAT,HAMELIN_GIANT_RAT,HAMELIN_GIANT_RAT,HAMELIN_MAN_EATING_RAT,HAMELIN_MAN_EATING_RAT,HAMELIN_MAN_EATING_RAT,HAMELIN_MAN_EATING_RAT},
        new ClassicEnemyKind[]{HAMELIN_ROKSA,HAMELIN_MAD_THIEF,HAMELIN_MAD_THIEF,HAMELIN_MAD_THIEF,HAMELIN_ENCHANTED_BANDIT,HAMELIN_ENCHANTED_BANDIT,HAMELIN_ENCHANTED_BANDIT,HAMELIN_MAN_EATING_RAT,HAMELIN_MAN_EATING_RAT,HAMELIN_MAN_EATING_RAT},
        new ClassicEnemyKind[]{HAMELIN_SIWOO,HAMELIN_MAN_EATING_RAT,HAMELIN_MAN_EATING_RAT,HAMELIN_MAN_EATING_RAT,HAMELIN_MAN_EATING_RAT,HAMELIN_MAN_EATING_RAT,HAMELIN_GIANT_RAT,HAMELIN_GIANT_RAT,HAMELIN_GIANT_RAT,HAMELIN_GIANT_RAT}
    };
    public static Map<ClassicEnemyKind,List<ClassicMove>> moves(){
        Map<ClassicEnemyKind,List<ClassicMove>> moves=new EnumMap<>(ClassicEnemyKind.class);
        moves.put(HAMELIN_MAD_THIEF,List.of(
        new ClassicMove(HAMELIN_MAD_THIEF,"stab","突刺",900,2000,"RECT",3.5,1.2,1.0,false,-1,"",0,new double[]{0.46}),
        new ClassicMove(HAMELIN_MAD_THIEF,"jump","跃起／扑击",1400,3200,"DASH",7.0,1.6,1.0,false,-1,"dash",0,new double[]{0.46})
        ));
        moves.put(HAMELIN_MAD_FEMALE_THIEF,List.of(
        new ClassicMove(HAMELIN_MAD_FEMALE_THIEF,"slash","挥斩",1050,2000,"FAN",3.0,1.0,1.0,false,-1,"",0,new double[]{0.46}),
        new ClassicMove(HAMELIN_MAD_FEMALE_THIEF,"throw","投掷",1200,3200,"DART",16.0,0.4,1.0,false,-1,"",0,new double[]{0.46})
        ));
        moves.put(HAMELIN_ENCHANTED_BANDIT,List.of(
        new ClassicMove(HAMELIN_ENCHANTED_BANDIT,"stab","突刺",900,2000,"RECT",3.5,1.2,1.0,false,-1,"",0,new double[]{0.46}),
        new ClassicMove(HAMELIN_ENCHANTED_BANDIT,"charge","冲锋",1300,3200,"DASH",10,1.6,1.0,false,-1,"dash",0,new double[]{0.46})
        ));
        moves.put(HAMELIN_GIANT_RAT,List.of(
        new ClassicMove(HAMELIN_GIANT_RAT,"bite","咬击",1450,2350,"FAN",2.6,1.0,1.0,false,-1,"",0,new double[]{0.46}),
        new ClassicMove(HAMELIN_GIANT_RAT,"burrow","钻地",1650,8000,"NONE",2.6,1.0,0,false,-1,"burrow",0,new double[]{0.8}),
        new ClassicMove(HAMELIN_GIANT_RAT,"jump","跃起／扑击",1400,3200,"DASH",7.0,1.6,1.0,false,-1,"dash",0,new double[]{0.46})
        ));
        moves.put(HAMELIN_MAN_EATING_RAT,List.of(
        new ClassicMove(HAMELIN_MAN_EATING_RAT,"bite","咬击",1450,2350,"FAN",2.6,1.0,1.0,false,-1,"",0,new double[]{0.46}),
        new ClassicMove(HAMELIN_MAN_EATING_RAT,"burrow","钻地",1650,8000,"NONE",2.6,1.0,0,false,-1,"burrow",0,new double[]{0.8})
        ));
        moves.put(HAMELIN_SIWOO,List.of(
        new ClassicMove(HAMELIN_SIWOO,"slash","挥斩",1050,2000,"FAN",3.0,1.0,1.0,false,-1,"",0,new double[]{0.46}),
        new ClassicMove(HAMELIN_SIWOO,"charge","冲锋",1300,3200,"DASH",10,1.6,1.0,false,-1,"dash",0,new double[]{0.46})
        ));
        moves.put(HAMELIN_ROKSA,List.of(
        new ClassicMove(HAMELIN_ROKSA,"summon","召唤",1800,10000,"NONE",2.6,1.0,0,false,-1,"summon",0,new double[]{0.46}),
        new ClassicMove(HAMELIN_ROKSA,"guard","防御姿态",1450,10000,"NONE",2.6,1.0,0,false,-1,"guard",0,new double[]{0.46})
        ));
        moves.put(HAMELIN_PETER,List.of(
        new ClassicMove(HAMELIN_PETER,"flute","吹笛",2500,4300,"DART",16.0,0.55,1.0,true,-1,"flute",0,new double[]{0.46}),
        new ClassicMove(HAMELIN_PETER,"rat_wave","释放鼠波",2000,3800,"DART",16.0,0.85,1.0,true,-1,"rat_wave",0,new double[]{0.46}),
        new ClassicMove(HAMELIN_PETER,"mark","点名倒数",2000,2900,"CIRCLE",2.2,0.55,1.0,true,-1,"mark",12,new double[]{0.46}),
        new ClassicMove(HAMELIN_PETER,"rat_tower","登上鼠塔",2500,10000,"NONE",2.6,1.0,0,false,-1,"rat_tower",0,new double[]{0.46}),
        new ClassicMove(HAMELIN_PETER,"revive","倒地与复活",2800,3700,"NONE",2.6,1.0,0,false,-1,"revive",0,new double[]{0.96}),
        new ClassicMove(HAMELIN_PETER,"summon","召唤",1800,10000,"NONE",2.6,1.0,0,false,-1,"summon",0,new double[]{0.46})
        ));
        return Map.copyOf(moves);
    }
    public static void build(ServerLevel world,BlockPos origin){ClassicDungeonRooms.build(world,origin,ID);}
    private Hamelin(){}
}
