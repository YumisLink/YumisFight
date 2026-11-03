package cn.yumis.fight.modules.skills.api.client;

import cn.yumis.fight.modules.combat.api.client.DamageAnalysis;
import cn.yumis.fight.modules.dungeons.api.Target;
import cn.yumis.fight.modules.presentation.api.client.ClientUi;
import cn.yumis.fight.modules.presentation.api.client.NumberText;
import cn.yumis.fight.modules.presentation.api.client.UiFonts;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import java.util.*;

/** Ordinary sandbag summaries observe confirmed receipts; combat and ailments use the normal server path. */
public final class TrainingCombat {
    private static final Map<String,Attempt> ATTEMPTS=new LinkedHashMap<>();
    private static final Set<String> CLOSED=new LinkedHashSet<>();
    private static final Map<Long,Pending> PENDING=new LinkedHashMap<>();
    private static final Map<String,Effect> EFFECTS=new HashMap<>();
    private static final int[] COLORS={0xffffff,0xffed8d,0xff8a58,0x83dfff,0xc783ff};
    private static final String[] ELEMENTS={"无属性","光","火","冰","暗"};
    private record Hit(String root,UUID target,long generation,double[] values,double maximum,double damage,JsonObject ailment){}
    private record Pending(String session,long sourceGeneration,long sent,Hit hit){}
    private record Effect(String root,UUID target,long expires){}
    private static Hit reporting;
    private static final class Attempt {
        final String title;final double[] values=new double[5];final Map<String,Long> holds=new HashMap<>();
        boolean actionDone,emitted;long changed=ClientUi.now();
        Attempt(String title){this.title=title;}
    }
    private TrainingCombat(){}
    /** A failed summary never changes a real hit, its receipt, or its gameplay effects. */
    public static void failed(String root,String title,RuntimeException failure){
        if(CLOSED.contains(root))return;
        ATTEMPTS.remove(root);PENDING.values().removeIf(p->p.hit.root.equals(root));EFFECTS.values().removeIf(e->e.root.equals(root));seal(root);
        System.getLogger("yumis_dungeon_and_fight").log(System.Logger.Level.WARNING,"Training summary aborted: "+title+" ("+root+")",failure);
        var mc=Minecraft.getInstance();if(mc!=null&&mc.player!=null)mc.player.sendSystemMessage(UiFonts.decorate(Component.literal("[沙袋] "+title+"演算出现异常，已结束本次统计").withColor(0xff8a58)));
    }
    public static void begin(String root,String title){if(root!=null&&!root.isEmpty()&&!CLOSED.contains(root))ATTEMPTS.computeIfAbsent(root,k->new Attempt(title));}
    public static void hold(String root,String token,long until){Attempt a=ATTEMPTS.get(root);if(a!=null)a.holds.put(token,until);}
    /** Live skill execution and pending receipts own completion; a hold's time does not discard its tail. */
    public static void touch(String root){Attempt a=ATTEMPTS.get(root);if(a!=null)a.changed=ClientUi.now();}
    public static void release(String root,String token){Attempt a=ATTEMPTS.get(root);if(a!=null&&a.holds.remove(token)!=null)a.changed=ClientUi.now();}
    public static void actionFinished(String root){Attempt a=ATTEMPTS.get(root);if(a==null||a.actionDone)return;a.actionDone=true;a.changed=ClientUi.now();initial(a);}
    private static void initial(Attempt a){if(a.actionDone&&!a.emitted&&a.holds.keySet().stream().noneMatch(k->k.startsWith("receipt/")))emit(a,false);}
    public static void report(String root,String title,Target target,double[] values,double damage,int color,boolean critical,JsonObject extras){
        begin(root,title);Hit previous=reporting;double maximum=target.health(target.level(SkillsClient.current().progress().level,SkillsClient.current().encounterSync()));
        reporting=ATTEMPTS.containsKey(root)?new Hit(root,target.id,target.life.generation,values.clone(),maximum,damage,extras.has("ailment")?extras.getAsJsonObject("ailment").deepCopy():null):null;
        try{
            var mc=Minecraft.getInstance();var entity=mc.level==null?null:mc.level.getEntity(target.entityId);
            if(damage>0&&entity instanceof net.minecraft.world.entity.LivingEntity living){living.hurtTime=10;living.hurtDuration=10;}
            SkillsClient.current().report(target.life,damage/maximum,false,maximum,color,critical,extras);
        }
        finally{reporting=previous;}
    }
    /** Called synchronously by the existing report dispatcher; summary details stay on this client. */
    public static void reported(JsonObject packet){
        if(reporting==null||packet.get("heal").getAsBoolean()||!reporting.target.toString().equals(packet.get("target").getAsString()))return;
        long operation=packet.get("operation").getAsLong();PENDING.put(operation,new Pending(packet.get("session").getAsString(),packet.get("sourceGeneration").getAsLong(),ClientUi.now(),reporting));
        hold(reporting.root,"receipt/"+operation,ClientUi.now()+120000);
    }
    public static void ack(JsonObject packet){
        long operation=packet.get("operation").getAsLong();Pending p=PENDING.get(operation);
        if(p==null||!p.session.equals(packet.get("session").getAsString()))return;
        PENDING.remove(operation);Hit hit=p.hit;release(hit.root,"receipt/"+operation);
        try{
        var state=SkillsClient.current();var receipt=packet.getAsJsonObject("receipt");
        if(!state.session().equals(p.session)||state.own().generation!=p.sourceGeneration||!receipt.get("accepted").getAsBoolean()||receipt.get("actual").getAsDouble()<=0||!packet.has("target")||!hit.target.toString().equals(packet.get("target").getAsString())||packet.get("generation").getAsLong()!=hit.generation)return;
        Attempt a=ATTEMPTS.get(hit.root);if(a==null||hit.damage<=0)return;
        double scale=receipt.get("actual").getAsDouble()*hit.maximum/hit.damage;
        for(int i=0;i<5;i++){double value=hit.values[i]*scale;if(!Double.isFinite(value)||value<0||!Double.isFinite(a.values[i]+value))throw new IllegalArgumentException("训练伤害分量无效");a.values[i]+=value;}
        if(!Double.isFinite(Arrays.stream(a.values).sum()))throw new IllegalArgumentException("训练伤害合计无效");
        a.changed=ClientUi.now();
        if(hit.ailment!=null&&hit.ailment.get("kind").getAsString().equals("BLEED")){
            String id=hit.ailment.get("effect").getAsString();
            for(var it=EFFECTS.entrySet().iterator();it.hasNext();){var entry=it.next();if(entry.getValue().target.equals(hit.target)&&!entry.getKey().equals(id)){release(entry.getValue().root,"bleed/"+hit.target);it.remove();}}
            long expires=ClientUi.now()+Math.max(0,packet.get("serverNow").getAsLong()+hit.ailment.get("durationTicks").getAsLong()*50-state.serverNow());
            EFFECTS.put(id,new Effect(hit.root,hit.target,expires));hold(hit.root,"bleed/"+hit.target,expires);
        }
        initial(a);
        }catch(RuntimeException failure){var a=ATTEMPTS.get(hit.root);failed(hit.root,a==null?"伤害汇总":a.title,failure);}
    }
    public static void bleed(Target target,JsonObject ailment,JsonObject extras){
        var change=ailment.getAsJsonObject("change");String id=change.get("effect").getAsString();Effect effect=EFFECTS.get(id);
        String root=effect==null?"bleed/"+id:effect.root;double damage=change.get("fraction").getAsDouble()*target.health(1);double[] values=new double[5];values[0]=damage;
        report(root,DamageAnalysis.source(extras).title(),target,values,damage,0xe05360,false,extras);
        if(effect==null)actionFinished(root);
    }
    public static void tick(){
        long now=ClientUi.now();var mc=Minecraft.getInstance();if(mc.level==null){clear();return;}
        for(var it=EFFECTS.entrySet().iterator();it.hasNext();){var e=it.next().getValue();if(e.expires<now){release(e.root,"bleed/"+e.target);it.remove();}else touch(e.root);}
        for(var it=PENDING.entrySet().iterator();it.hasNext();){var entry=it.next();if(now-entry.getValue().sent>120000){release(entry.getValue().hit.root,"receipt/"+entry.getKey());it.remove();}else touch(entry.getValue().hit.root);}
        for(var it=ATTEMPTS.entrySet().iterator();it.hasNext();){var entry=it.next();Attempt a=entry.getValue();if(a.actionDone&&a.holds.isEmpty()&&now-a.changed>=100){emit(a,a.emitted);seal(entry.getKey());it.remove();}else if(now-a.changed>120000){emit(a,a.emitted);seal(entry.getKey());it.remove();}}
        while(ATTEMPTS.size()>256){String root=ATTEMPTS.keySet().iterator().next();Attempt a=ATTEMPTS.remove(root);emit(a,a.emitted);seal(root);}
    }
    private static void emit(Attempt attempt,boolean tail){
        double total=Arrays.stream(attempt.values).sum();if(total<=0)return;var mc=Minecraft.getInstance();if(mc.player==null)return;
        MutableComponent message=Component.literal("[沙袋] "+attempt.title+(tail?" · 后续结算":"")+"：").withColor(0xb4f2ff);
        boolean first=true;for(int i=0;i<5;i++)if(attempt.values[i]>0){if(!first)message.append(Component.literal("；").withColor(0xaaaaaa));message.append(Component.literal(ELEMENTS[i]+" "+NumberText.damage(attempt.values[i])).withColor(COLORS[i]));first=false;}
        message.append(Component.literal("；总伤害 "+NumberText.damage(total)).withColor(0xffffff));mc.player.sendSystemMessage(UiFonts.decorate(message));Arrays.fill(attempt.values,0);attempt.emitted=true;
    }
    private static void seal(String root){CLOSED.add(root);while(CLOSED.size()>4096)CLOSED.remove(CLOSED.iterator().next());}
    public static void clear(){ATTEMPTS.clear();CLOSED.clear();PENDING.clear();EFFECTS.clear();reporting=null;}
}
