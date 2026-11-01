package cn.yumis.fight.modules.skills.api;

import com.google.gson.Gson;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Shared summon choreography and contact times for combat, reference damage and art. */
public final class SummonMoves {
    public record Move(String id,int duration,int[] contacts,String shape,int element,double range,double width,double radius,int pierces,double velocity,boolean slow){}
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
    public static List<Cue> cues(SummonKind kind,int rank,long duration,double speed){
        var result=new ArrayList<Cue>();
        if(kind==SummonKind.HERDER){
            var attacks=MageSkills.attacks(rank,duration,speed);int volley=0,swings=0;
            for(int i=0;i<attacks.size();i++){
                var a=attacks.get(i);
                if(!a.stone()){result.add(new Cue(a.at(),++swings%3==0?"heavy":"club",1,false));continue;}
                int count=1;
                while(i+1<attacks.size()&&attacks.get(i+1).stone()&&attacks.get(i+1).at()-attacks.get(i).at()<=Math.max(10,Math.round(200/speed))){i++;count++;}
                result.add(new Cue(a.at(),++volley%3==0?"bomb":count>1?"triple_throw":"stone",count,true));
            }
        }else {
            int n=0,basic=0;double step=kind.interval*1000/(kind==SummonKind.KASIJAS?1:Math.max(.01,speed));
            for(double t=step;t<Math.min(duration,kind==SummonKind.KASIJAS?19000:120000);t+=step){
                n++;boolean special=kind==SummonKind.LOUISE?n%4==0:(kind==SummonKind.SANDOR||kind==SummonKind.KING||kind==SummonKind.KASIJAS)&&n%5==0;
                String move=switch(kind){
                    case FIRE->"flame_double";case ICE->n%2==1?"ice_touch":"water_bolt";
                    case LIGHT->"light_contact";case DARK->"shadow_claw";
                    case LOUISE->switch(n%4){case 1->"ice_needle";case 2->"fireball";case 3->"ice_pillar";default->"meteor";};
                    case SANDOR->special?"sword_force":++basic%2==0?"sword_thrust":"sword_swing";
                    case KING->special?"king_laser":switch(n%5){case 1->"element_touch";case 3->"king_lightning";default->"king_wave";};
                    case KASIJAS->special?"dash_slash":++basic%2==0?"wide_slash":"draw_slash";
                    default->throw new IllegalArgumentException("Unknown summon choreography: "+kind);
                };
                double share=switch(kind){case FIRE->.25/79;case ICE->.25/59;case LIGHT->.25/119;case DARK->.25/47;case LOUISE->special?.4/14:.6/45;case SANDOR->special?.4/13:.6/53;case KING->special?.4/11:.6/48;case KASIJAS->special?.3/3:.3/15;default->0;};
                result.add(new Cue(Math.round(t),move,share,false));
            }
            if(kind==SummonKind.KASIJAS&&duration>19000){result.add(new Cue(18600,"ghost_dance",.3,false));result.add(new Cue(19500,"falling_swords",.1,false));}
        }
        return List.copyOf(result);
    }
}
