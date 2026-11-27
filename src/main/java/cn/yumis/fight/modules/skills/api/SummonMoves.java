package cn.yumis.fight.modules.skills.api;

import cn.yumis.fight.modules.attributes.api.Stats;
import cn.yumis.fight.modules.dungeons.api.TowerBattleCard;
import com.google.gson.Gson;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Shared summon choreography and contact times for combat, reference damage and art. */
public final class SummonMoves {
    public record Move(String id,int duration,int[] contacts,String shape,int element,double range,double width,double radius,int pierces,double velocity,boolean slow){
        public boolean projectile(){return shape.equals("projectile")||shape.equals("meteor");}
        public double reach(){
            return projectile()&&!id.equals("sword_force")
                ?cn.yumis.fight.modules.combat.api.ProjectileSpawn.extendedRange(range):range;
        }
    }
    public record Cue(long at,String move,double share,boolean stone){}
    private static final Map<String,Move> MOVES;
    static {
        try(var in=Objects.requireNonNull(SummonMoves.class.getResourceAsStream("/assets/yumis_dungeon_and_fight/summons/block/moves.json"));
            var reader=new InputStreamReader(in,StandardCharsets.UTF_8)){
            var values=new LinkedHashMap<String,Move>();
            for(var move:new Gson().fromJson(reader,Move[].class))values.put(move.id(),move);
            MOVES=Map.copyOf(values);
        }catch(IOException e){throw new ExceptionInInitializerError(e);}
    }
    private SummonMoves(){}
    public static Move require(String id){return Objects.requireNonNull(MOVES.get(id),id);}
    public static Move find(String id){return MOVES.get(id);}
    public static List<Cue> cues(SummonKind kind,SkillParameters.Snapshot p,long duration,double speed){
        return cues(kind,p,null,duration,speed);
    }
    public static List<Cue> cues(SummonKind kind,SkillParameters.Snapshot p,Stats stats,long duration,double speed){
        var result=new ArrayList<Cue>();
        double safeSpeed=Math.max(.01,speed);
        if(kind==SummonKind.HERDER){
            boolean cannon=TowerBattleCard.SUMMONER_HERDER_CANNON.held(stats);
            boolean squad=TowerBattleCard.SUMMONER_HERDER_SQUAD.held(stats);
            if(cannon||squad){
                String move=cannon?"herder_cannon":"bomb";
                double intervalMs=(cannon?TowerBattleCard.SUMMONER_HERDER_CANNON.value("interval")*1000d:1800d)/safeSpeed;
                double first=Math.min(400d,intervalMs);
                int shotCount=Math.max(1,(int)Math.floor((duration-first)/intervalMs)+1);
                double share=herderShotShare(p,stats,duration,safeSpeed,shotCount);
                for(double t=first;t<duration;t+=intervalMs){
                    result.add(new Cue(Math.round(t),move,share,true));
                }
                return List.copyOf(result);
            }
            var attacks=MageSkills.attacks(p,duration,safeSpeed);int volley=0,swings=0,heavyEvery=Math.max(1,(int)p.get("heavyEvery",3)),bombEvery=Math.max(1,(int)p.get("bombEvery",3));
            for(int i=0;i<attacks.size();i++){
                var a=attacks.get(i);
                if(!a.stone()){result.add(new Cue(a.at(),++swings%heavyEvery==0?"heavy":"club",1,false));continue;}
                int count=1;
                while(i+1<attacks.size()&&attacks.get(i+1).stone()&&attacks.get(i+1).at()-attacks.get(i).at()<=Math.max(10,Math.round(p.get("stoneGap",.2)*1000/safeSpeed))){i++;count++;}
                result.add(new Cue(a.at(),++volley%bombEvery==0?"bomb":count>1?"triple_throw":"stone",count,true));
            }
        }else if(kind==SummonKind.KING&&TowerBattleCard.SUMMONER_KING_LASER.held(stats)){
            double baseInterval=10000d*(1d-TowerBattleCard.SUMMONER_KING_LASER.ratio("cdr"));
            double step=Math.max(250d,baseInterval/safeSpeed);
            double share=kingMegaLaserShare();
            for(double t=Math.min(step,1000d);t<Math.min(duration,120000);t+=step){
                result.add(new Cue(Math.round(t),"king_mega_laser",share,false));
            }
        }else {
            boolean ascend=SummonerSkills.lowerSpirit(kind)&&TowerBattleCard.SUMMONER_SPIRITS_ASCEND.held(stats);
            boolean louiseInferno=kind==SummonKind.LOUISE&&TowerBattleCard.SUMMONER_LOUISE_INFERNO.held(stats);
            boolean louiseFrost=kind==SummonKind.LOUISE&&TowerBattleCard.SUMMONER_LOUISE_FROST.held(stats);
            int n=0,basic=0;double step=kind.interval*1000/(kind==SummonKind.KASIJAS?1:safeSpeed);
            for(double t=step;t<Math.min(duration,kind==SummonKind.KASIJAS?19000:120000);t+=step){
                n++;boolean special=kind==SummonKind.LOUISE?n%4==0:(kind==SummonKind.SANDOR||kind==SummonKind.KING||kind==SummonKind.KASIJAS)&&n%5==0;
                String move=switch(kind){
                    case FIRE->ascend?"fire_blast":"flame_double";
                    case ICE->ascend?"ice_burst":(n%2==1?"ice_touch":"water_bolt");
                    case LIGHT->ascend?"chain_light":"light_contact";
                    case DARK->ascend?"dark_wave":"shadow_claw";
                    case LOUISE->switch(n%4){
                        case 1->"ice_needle";
                        case 2->louiseInferno?"fireball_volley":"fireball";
                        case 3->louiseFrost?"ice_pillar_tri":"ice_pillar";
                        default->"meteor";
                    };
                    case SANDOR->special?"sword_force":++basic%2==0?"sword_thrust":"sword_swing";
                    case KING->special?"king_laser":switch(n%5){case 1->"element_touch";case 3->"king_lightning";default->"king_wave";};
                    case KASIJAS->special?"dash_slash":++basic%2==0?"wide_slash":"draw_slash";
                    default->throw new IllegalArgumentException("Unknown summon choreography: "+kind);
                };
                double share=switch(kind){case FIRE->.25/79;case ICE->.25/59;case LIGHT->.25/119;case DARK->.25/47;case LOUISE->special?.4/14:.6/45;case SANDOR->special?.4/13:.6/53;case KING->special?.4/11:.6/48;case KASIJAS->special?.3/3:.3/15;default->0;};
                if(ascend)share*=1+TowerBattleCard.SUMMONER_SPIRITS_ASCEND.ratio("damage");
                if(louiseInferno&&move.equals("fireball_volley"))share*=TowerBattleCard.SUMMONER_LOUISE_INFERNO.count("fireballs");
                if(louiseInferno&&move.equals("meteor"))share*=1+TowerBattleCard.SUMMONER_LOUISE_INFERNO.ratio("damage");
                if(louiseFrost&&move.equals("ice_pillar_tri"))share*=TowerBattleCard.SUMMONER_LOUISE_FROST.count("pillars")*.7;
                result.add(new Cue(Math.round(t),move,share,false));
            }
            if(kind==SummonKind.KASIJAS&&duration>19000){result.add(new Cue(18600,"ghost_dance",.3,false));result.add(new Cue(19500,"falling_swords",.1,false));}
        }
        return List.copyOf(result);
    }
    public static double herderShotShare(SkillParameters.Snapshot p,Stats stats,long duration,double speed,int shotCount){
        double vanillaStoneUnits=0;
        for(var a:MageSkills.attacks(p,duration,Math.max(.01,speed))){
            vanillaStoneUnits+=a.stone()?1d:(1d/1.2);
        }
        double perShotBase=Math.max(1d,vanillaStoneUnits/Math.max(1,shotCount));
        return perShotBase*SummonerSkills.herderBombDamageMultiplier(stats);
    }
    public static double kingMegaLaserShare(){
        return (1d/24d)*(1+TowerBattleCard.SUMMONER_KING_LASER.ratio("damage"));
    }
    public static Cue signatureUltimate(SummonKind kind,SkillParameters.Snapshot p,Stats stats){
        boolean ascend=SummonerSkills.lowerSpirit(kind)&&TowerBattleCard.SUMMONER_SPIRITS_ASCEND.held(stats);
        double spiritMult=ascend?1+TowerBattleCard.SUMMONER_SPIRITS_ASCEND.ratio("damage"):1d;
        return switch(kind){
            case HERDER->{
                boolean cannon=TowerBattleCard.SUMMONER_HERDER_CANNON.held(stats);
                boolean squad=TowerBattleCard.SUMMONER_HERDER_SQUAD.held(stats);
                double share=cannon||squad
                    ?herderShotShare(p,stats,120000,1d,cannon?48:67)
                    :(p.rank()>=10?Math.max(1,p.get("stoneVolley",3)):1d);
                yield new Cue(0,cannon?"herder_cannon":"bomb",share,true);
            }
            case FIRE->new Cue(0,"fire_blast",(.25/79)*spiritMult*3d,false);
            case ICE->new Cue(0,"ice_burst",(.25/59)*spiritMult*3d,false);
            case LIGHT->new Cue(0,"chain_light",(.25/119)*spiritMult*4d,false);
            case DARK->new Cue(0,"dark_wave",(.25/47)*spiritMult*3d,false);
            case LOUISE->new Cue(0,"meteor",(.4/14)*(TowerBattleCard.SUMMONER_LOUISE_INFERNO.held(stats)?1+TowerBattleCard.SUMMONER_LOUISE_INFERNO.ratio("damage"):1d),false);
            case SANDOR->new Cue(0,"sword_force",.4/13,false);
            case KING->TowerBattleCard.SUMMONER_KING_LASER.held(stats)
                ?new Cue(0,"king_mega_laser",kingMegaLaserShare(),false)
                :new Cue(0,"king_laser",.4/11,false);
            case KASIJAS->new Cue(0,"ghost_dance",.3,false);
        };
    }
}
