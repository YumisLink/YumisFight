package cn.yumis.fight.modules.skills.api.client;

import cn.yumis.fight.modules.combat.api.Life;
import cn.yumis.fight.modules.combat.api.client.DamageNumber;
import cn.yumis.fight.modules.combat.api.client.DamageAnalysis;
import cn.yumis.fight.modules.dungeons.api.Target;
import cn.yumis.fight.modules.dungeons.api.TrainingSandbag;
import cn.yumis.fight.modules.presentation.api.client.ClientUi;
import cn.yumis.fight.modules.presentation.api.client.NumberText;
import cn.yumis.fight.modules.presentation.api.client.UiFonts;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.LivingEntity;
import java.util.*;

/** Disposable practice state. Never mutates Runtime, player life, a server target, or a pending report. */
public final class TrainingCombat {
    private static final Map<UUID,Target> TARGETS=new HashMap<>();
    private static final Map<String,Attempt> ATTEMPTS=new LinkedHashMap<>();
    private static final Set<String> CLOSED=new LinkedHashSet<>();
    private static final Map<String,Ailment> AILMENTS=new HashMap<>();
    private static final List<DamageNumber> NUMBERS=new ArrayList<>();
    private static final List<DamageNumber> NUMBER_VIEW=Collections.unmodifiableList(NUMBERS);
    private static final int[] COLORS={0xffffff,0xffed8d,0xff8a58,0x83dfff,0xc783ff};
    private static final String[] ELEMENTS={"无属性","光","火","冰","暗"};
    private record Ailment(String root,UUID target,String kind,JsonObject state,long expires,long next,int index,double amount,DamageAnalysis.Source source){}
    private static final class Attempt {
        final String title;final double[] values=new double[5];final Map<String,Long> holds=new HashMap<>();
        boolean actionDone;long changed=ClientUi.now();
        Attempt(String title){this.title=title;}
    }
    private TrainingCombat(){}
    public static Target context(LivingEntity entity){
        if(!(entity instanceof TrainingSandbag bag))return null;
        if(bag.timed()){TARGETS.remove(entity.getUUID());return null;}
        Target target=TARGETS.computeIfAbsent(entity.getUUID(),id->{var t=new Target();t.id=id;t.kind="TRAINING";t.noRewards=true;t.originalLevel=1;t.life.target=id;return t;});
        target.entityId=entity.getId();target.dimension=entity.level().dimension().identifier().toString();target.life.dimension=target.dimension;
        return target;
    }
    public static boolean isTraining(UUID target){return TARGETS.containsKey(target);}
    public static void forget(UUID target){TARGETS.remove(target);}
    public static boolean isTraining(Life life){return life!=null&&isTraining(life.target);}
    public static boolean accepts(String root){return !CLOSED.contains(root);}
    /** A broken practice attempt is discarded as a unit; real combat and its reports keep running. */
    public static void failed(String root,String title,RuntimeException failure){
        if(CLOSED.contains(root))return;
        ATTEMPTS.remove(root);AILMENTS.values().removeIf(a->a.root.equals(root));seal(root);
        System.getLogger("yumis_dungeon_and_fight").log(System.Logger.Level.WARNING,"Training calculation aborted: "+title+" ("+root+")",failure);
        var mc=Minecraft.getInstance();if(mc!=null&&mc.player!=null)mc.player.sendSystemMessage(UiFonts.decorate(Component.literal("[沙袋] "+title+"演算出现异常，已结束本次统计").withColor(0xff8a58)));
    }
    public static void begin(String root,String title){if(root!=null&&!root.isEmpty()&&!CLOSED.contains(root))ATTEMPTS.computeIfAbsent(root,k->new Attempt(title));}
    public static void hold(String root,String token,long until){Attempt a=ATTEMPTS.get(root);if(a!=null)a.holds.put(token,until);}
    /** Live simulation owns completion; the supplied hold time is diagnostic, never permission to drop a tail. */
    public static void touch(String root){Attempt a=ATTEMPTS.get(root);if(a!=null)a.changed=ClientUi.now();}
    public static void release(String root,String token){Attempt a=ATTEMPTS.get(root);if(a!=null&&a.holds.remove(token)!=null)a.changed=ClientUi.now();}
    public static void actionFinished(String root){Attempt a=ATTEMPTS.get(root);if(a==null||a.actionDone)return;a.actionDone=true;a.changed=ClientUi.now();emit(a,false);}
    public static void hit(String root,String title,Target target,double[] values,boolean critical,JsonObject extras){
        begin(root,title);Attempt attempt=ATTEMPTS.get(root);if(attempt==null)return;
        double total=0;int strongest=0;for(int i=0;i<5;i++){double v=values[i];if(!Double.isFinite(v)||v<0||!Double.isFinite(attempt.values[i]+v))throw new IllegalArgumentException("训练伤害分量无效");total+=v;if(v>values[strongest])strongest=i;}
        if(!Double.isFinite(total)||!Double.isFinite(Arrays.stream(attempt.values).sum()+total))throw new IllegalArgumentException("训练伤害合计无效");
        for(int i=0;i<5;i++)attempt.values[i]+=values[i];
        DamageAnalysis.training(DamageAnalysis.source(extras),total,SkillsClient.current().combatSettings());
        attempt.changed=ClientUi.now();
        var mc=Minecraft.getInstance();var entity=mc.level==null?null:mc.level.getEntity(target.entityId);
        if(total>0&&entity instanceof LivingEntity living){if(NUMBERS.size()>=96)NUMBERS.removeFirst();NUMBERS.add(new DamageNumber(total,ClientUi.now()+DamageNumber.LIFETIME_MILLIS,COLORS[strongest],entity.position().add(0,entity.getBbHeight()*.8,0),target.dimension,target.id,critical));living.hurtTime=10;living.hurtDuration=10;}
        if(extras!=null&&extras.has("ailment"))ailment(root,target,extras.getAsJsonObject("ailment"),DamageAnalysis.source(extras));
    }
    private static void ailment(String root,Target target,JsonObject change,DamageAnalysis.Source source){
        String kind=change.get("kind").getAsString(),key=target.id+"/"+kind;long now=ClientUi.now(),expires=now+change.get("durationTicks").getAsLong()*50;
        JsonObject local=new JsonObject();local.addProperty("expires",SkillsClient.current().serverNow()+expires-now);local.add("change",change.deepCopy());
        Ailment old=AILMENTS.get(key);if(old!=null&&!old.root.equals(root))release(old.root,key);double amount=change.has("fraction")?change.get("fraction").getAsDouble()*target.health(1):0;
        AILMENTS.put(key,new Ailment(root,target.id,kind,local,expires,old!=null&&kind.equals("BLEED")?old.next:now+1000,0,amount,source));
        if(kind.equals("BLEED"))hold(root,key,expires+50);
    }
    public static JsonObject ailment(String target,String kind){Ailment a=AILMENTS.get(target+"/"+kind);return a!=null&&a.expires>ClientUi.now()?a.state:null;}
    public static List<DamageNumber> numbers(){return NUMBER_VIEW;}
    public static void tick(){
        long now=ClientUi.now();var mc=Minecraft.getInstance();if(mc.level==null){clear();return;}
        TARGETS.entrySet().removeIf(e->{var entity=mc.level.getEntity(e.getValue().entityId);return entity==null||!entity.getUUID().equals(e.getKey())||!entity.isAlive();});
        for(var entry:new ArrayList<>(AILMENTS.entrySet())){
            Ailment a=entry.getValue();Target target=TARGETS.get(a.target);
            if(target==null||a.expires<now){release(a.root,entry.getKey());AILMENTS.remove(entry.getKey());continue;}
            touch(a.root);
            if(a.kind.equals("BLEED")&&now>=a.next&&a.index<10){double[] values=new double[5];values[0]=a.amount;JsonObject extras=new JsonObject();DamageAnalysis.attach(extras,a.source);try{hit(a.root,"流血",target,values,false,extras);if(accepts(a.root))AILMENTS.put(entry.getKey(),new Ailment(a.root,a.target,a.kind,a.state,a.expires,a.next+1000,a.index+1,a.amount,a.source));}catch(RuntimeException failure){failed(a.root,"流血",failure);}}
        }
        NUMBERS.removeIf(n->n.expires()<now);
        for(var it=ATTEMPTS.entrySet().iterator();it.hasNext();){var entry=it.next();Attempt a=entry.getValue();if(a.actionDone&&a.holds.isEmpty()&&now-a.changed>=100){emit(a,true);seal(entry.getKey());it.remove();}else if(now-a.changed>120000){emit(a,a.actionDone);seal(entry.getKey());it.remove();}}
        while(ATTEMPTS.size()>256){String root=ATTEMPTS.keySet().iterator().next();Attempt a=ATTEMPTS.remove(root);emit(a,a.actionDone);seal(root);}
    }
    private static void emit(Attempt attempt,boolean tail){
        double total=Arrays.stream(attempt.values).sum();if(total<=0)return;var mc=Minecraft.getInstance();if(mc.player==null)return;
        MutableComponent message=Component.literal("[沙袋] "+attempt.title+(tail?" · 后续结算":"")+"：").withColor(0xb4f2ff);
        boolean first=true;for(int i=0;i<5;i++)if(attempt.values[i]>0){if(!first)message.append(Component.literal("；").withColor(0xaaaaaa));message.append(Component.literal(ELEMENTS[i]+" "+NumberText.damage(attempt.values[i])).withColor(COLORS[i]));first=false;}
        message.append(Component.literal("；总伤害 "+NumberText.damage(total)).withColor(0xffffff));mc.player.sendSystemMessage(UiFonts.decorate(message));Arrays.fill(attempt.values,0);
    }
    private static void seal(String root){CLOSED.add(root);while(CLOSED.size()>4096)CLOSED.remove(CLOSED.iterator().next());}
    public static void clear(){TARGETS.clear();ATTEMPTS.clear();CLOSED.clear();AILMENTS.clear();NUMBERS.clear();}
}
