package cn.yumis.fight.modules.dungeons.api;

import cn.yumis.fight.platform.ItemData;
import com.google.gson.Gson;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Supplier;

/** Data-pack ritual altar boss definitions. One immutable snapshot travels with the application rule bundle. */
public final class RitualBossCatalog {
    public record Clearance(double length,double width,double height){}
    public record Arena(int radius,int spawnAhead,Clearance clearance){}
    public record Damage(double coefficient,String type,String element,Double radius){
        public Damage(double coefficient,String type,String element){this(coefficient,type,element,null);}
    }
    public record Shape(String type,double radius,double range,double angle,double length,double width,double spacing){}
    public record Linger(long ms,long tickMs,double perTickCoefficient,int hitLimit,String type,String element){}
    public record Move(String id,String animation,String trigger,String phase,long cooldownMs,long durationMs,long[] contactMs,
                       String target,Shape shape,int hitLimit,Damage damage,long roundMs,
                       Integer slowSeconds,Integer rootSeconds,Boolean spawnClone,Boolean extinguishLanterns,
                       Integer healDownPercent,Integer healDownSeconds,
                       Double debtSlowPercent,Integer debtSlowStacks,Integer debtSlowSeconds,
                       Integer freezeSeconds,Long exposeBrokenWatchMs,Integer groundedMs,Integer ashSeconds,List<String> safeSpots,
                       Integer sitSeconds,Integer saveSeconds,Integer windowSeconds,Damage missDamage,Integer chantBoost,int[] steps,String group,
                       Boolean blocksBehindCover,String replayGroup,Boolean spawnPullLine,Linger linger){}
    public record Ending(String id,String phase,String windowMove,long windowSeconds,List<String> mechanisms){}
    public record Shell(int[] steps,Integer borrowSeconds,Integer settleSeconds,int[] chipsPerStep){}
    public record Chip(double durabilityPercent,double repairPercent){}
    public record Boss(String id,String name,String grade,int level,String token,String material,long craftMana,long summonMana,
                       Arena arena,double speed,String intro,List<Move> moves,Ending ending,Shell shell,Chip chip,
                       Double chantPerSecond,Double chantDamageRegression,Integer windSlots,Long windIntervalMs,String replayMove,
                       long countdownSeconds,long limitSeconds,long noSurvivorSeconds,double healthMultiplier){
        public Move move(String id){
            for(var move:moves)if(move.id().equals(id))return move;
            throw new IllegalArgumentException("魔王祭坛招式不存在："+this.id+"/"+id);
        }
    }
    public record DefaultsSpec(long countdownSeconds,long limitSeconds,long noSurvivorSeconds,double healthMultiplier){}
    private DefaultsSpec defaults=new DefaultsSpec(3,480,10,6.9);
    private List<Boss> bosses=List.of();
    private transient Map<String,Boss> byId=Map.of();
    private static Supplier<RitualBossCatalog> source=RitualBossCatalog::defaults;
    private static final class Defaults {
        private static final String JSON=read();
        private static String read(){
            try(var input=RitualBossCatalog.class.getResourceAsStream("/data/"+ItemData.NS+"/rules/altar_bosses.json")){
                if(input==null)throw new IllegalStateException("缺少默认魔王祭坛Boss配置");
                return new String(input.readAllBytes(),StandardCharsets.UTF_8);
            }catch(Exception failure){throw new ExceptionInInitializerError(failure);}
        }
    }
    public static RitualBossCatalog defaults(){return parse(Defaults.JSON);}
    public static void source(Supplier<RitualBossCatalog> value){source=Objects.requireNonNull(value);}
    public static RitualBossCatalog current(){return source.get();}
    public static RitualBossCatalog parse(String json){
        var result=new Gson().fromJson(json,RitualBossCatalog.class);
        if(result==null||result.bosses==null||result.bosses.isEmpty()||result.defaults==null)throw new IllegalArgumentException("缺少魔王祭坛Boss定义");
        var fixed=new LinkedHashMap<String,Boss>();
        for(var row:result.bosses){
            String id=row.id();
            var kind=AltarBossKind.find(id);
            if(kind==null)throw new IllegalArgumentException("魔王祭坛Boss ID未登记实体："+id);
            var boss=validate(id,row,result.defaults);
            if(boss.level()!=kind.level)throw new IllegalArgumentException("魔王祭坛Boss等级与实体登记不符："+id);
            if(fixed.putIfAbsent(id,boss)!=null)throw new IllegalArgumentException("魔王祭坛Boss定义重复："+id);
        }
        for(var kind:AltarBossKind.values())if(!fixed.containsKey(kind.id))throw new IllegalArgumentException("魔王祭坛Boss配置不完整："+kind.id);
        result.bosses=List.copyOf(fixed.values());
        result.byId=Map.copyOf(fixed);
        return result;
    }
    public Boss find(String id){return byId.get(id);}
    public Boss boss(String id){var boss=find(id);if(boss==null)throw new IllegalArgumentException("魔王祭坛Boss定义不存在："+id);return boss;}
    private static Boss validate(String id,Boss boss,DefaultsSpec defaults){
        if(boss==null||boss.name()==null||boss.name().isBlank())throw new IllegalArgumentException("魔王祭坛Boss缺少名称："+id);
        if(!boss.token().equals("altar_token_"+id))throw new IllegalArgumentException("魔王祭坛信物ID必须为 altar_token_+BossID："+boss.token());
        if(boss.material()==null||boss.material().isBlank())throw new IllegalArgumentException("魔王祭坛Boss缺少专材："+id);
        if(boss.craftMana()<=0||boss.summonMana()<=0)throw new IllegalArgumentException("魔王祭坛魔能必须为正："+id);
        var arena=boss.arena();
        if(arena==null||arena.radius()<4||arena.radius()>64||arena.spawnAhead()<1||arena.clearance()==null
            ||arena.clearance().length()<1||arena.clearance().width()<1||arena.clearance().height()<1)
            throw new IllegalArgumentException("魔王祭坛战圈参数无效："+id);
        if(!Double.isFinite(boss.speed())||boss.speed()<=0)throw new IllegalArgumentException("魔王祭坛移速无效："+id);
        if(boss.moves()==null||boss.moves().isEmpty())throw new IllegalArgumentException("魔王祭坛Boss缺少招式："+id);
        var seen=new HashSet<String>();
        for(var move:boss.moves()){
            if(move.id()==null||move.id().isBlank()||!seen.add(move.id()))throw new IllegalArgumentException("魔王祭坛招式ID为空或重复："+id);
            validate(id,move);
        }
        if(boss.ending()==null||boss.ending().id()==null||boss.ending().id().isBlank())throw new IllegalArgumentException("魔王祭坛Boss缺少收尾方式："+id);
        if(boss.shell()!=null&&boss.shell().steps()!=null){
            var steps=boss.shell().steps();
            for(int step:steps)if(step<=0||step>=100)throw new IllegalArgumentException("魔王祭坛阶段阈值须在0与100之间："+id);
            for(int index=1;index<steps.length;index++)if(steps[index]>=steps[index-1])throw new IllegalArgumentException("魔王祭坛阶段阈值须降序："+id);
        }
        double health=boss.healthMultiplier()==0?defaults.healthMultiplier():boss.healthMultiplier();
        long countdown=boss.countdownSeconds()==0?defaults.countdownSeconds():boss.countdownSeconds();
        long limit=boss.limitSeconds()==0?defaults.limitSeconds():boss.limitSeconds();
        long grace=boss.noSurvivorSeconds()==0?defaults.noSurvivorSeconds():boss.noSurvivorSeconds();
        if(health<=0||!Double.isFinite(health)||countdown<=0||limit<=0||grace<=0)throw new IllegalArgumentException("魔王祭坛生命或时限无效："+id);
        return new Boss(boss.id(),boss.name(),boss.grade(),boss.level(),boss.token(),boss.material(),boss.craftMana(),boss.summonMana(),
            boss.arena(),boss.speed(),boss.intro(),List.copyOf(boss.moves()),boss.ending(),boss.shell(),boss.chip(),
            boss.chantPerSecond(),boss.chantDamageRegression(),boss.windSlots(),boss.windIntervalMs(),boss.replayMove(),
            countdown,limit,grace,health);
    }
    private static void validate(String id,Move move){
        if(move.animation()==null||move.animation().isBlank())throw new IllegalArgumentException("魔王祭坛招式缺少动作名："+move.id());
        if(move.cooldownMs()<0||move.durationMs()<0)throw new IllegalArgumentException("魔王祭坛招式时间轴无效："+move.id());
        if(move.contactMs()==null)throw new IllegalArgumentException("魔王祭坛招式缺少接触时间："+move.id());
        long previous=0;
        for(long contact:move.contactMs()){
            if(contact<previous||contact>move.durationMs())throw new IllegalArgumentException("魔王祭坛接触时间须递增且不晚于动作时长："+move.id());
            previous=contact;
        }
        if(move.hitLimit()<1&&move.damage()!=null&&move.damage().coefficient()>0)throw new IllegalArgumentException("魔王祭坛命中上限无效："+move.id());
        if(move.damage()!=null){
            if(move.damage().coefficient()<0)throw new IllegalArgumentException("魔王祭坛倍率无效："+move.id());
            if(move.damage().type()==null||!(move.damage().type().equals("physical")||move.damage().type().equals("magic")))
                throw new IllegalArgumentException("魔王祭坛伤害类型无效："+move.id());
            if(move.damage().element()==null)throw new IllegalArgumentException("魔王祭坛元素缺失："+move.id());
        }
        if(move.shape()!=null&&move.shape().type()==null)throw new IllegalArgumentException("魔王祭坛形状类型缺失："+move.id());
        if(move.linger()!=null&&(move.linger().ms()<=0||move.linger().tickMs()<=0||move.linger().perTickCoefficient()<0||move.linger().hitLimit()<1))
            throw new IllegalArgumentException("魔王祭坛持续攻击参数无效："+move.id());
    }
    public List<Boss> ordered(){return bosses;}
}
