package cn.yumis.fight.client;
import cn.yumis.fight.FightMod;
import cn.yumis.fight.game.*;
import cn.yumis.fight.network.Network;
import cn.yumis.fight.rules.*;
import cn.yumis.fight.rules.Models.*;
import static cn.yumis.fight.rules.Models.JSON;
import static cn.yumis.fight.rules.Rules.*;
import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import java.util.*;
/** Connection-scoped prediction. Receipts retire pending entries exactly once. */
public final class ClientState {
    public static final ClientState INSTANCE=new ClientState();
    public Progress progress=new Progress();
    public Models.Runtime resources=new Models.Runtime();
    public Stats stats=new Stats();
    public Life own=new Life();
    public String session="",dimension="";
    public Team party;
    public Dungeon dungeon;
    public JsonObject profile=new JsonObject(),professions=new JsonObject(),damageMeter=new JsonObject();
    public boolean ready;
    private cn.yumis.fight.api.AttributeSources syncedSources=new cn.yumis.fight.api.AttributeSources();
    private long nextOperation=1,lastUpload,lastHello;
    private long confirmedCombatUntil,confirmedShieldRecoveryAt;
    public long serverOffset;
    public final Map<UUID,Target> targets=new HashMap<>();
    public final Map<Long,Pending> pending=new LinkedHashMap<>();
    public final Map<String,JsonObject> ailments=new HashMap<>();
    public final ArrayList<DamageNumber> numbers=new ArrayList<>();
    private final Map<Integer,Long> contextRequests=new HashMap<>();
    public record DamageNumber(double amount,long expires,int color,net.minecraft.world.phys.Vec3 position,String dimension,int lane){
    }
    public static class Pending {
        public JsonObject report;
        public String action;
        public long sent;
        public final long occurredAt;
        public double maximum;
        public int color;
        public net.minecraft.world.phys.Vec3 point;
        Pending(JsonObject r,String a,double max,int color){
            report=r;
            action=a;
            maximum=max;
            this.color=color;
            sent=now();
            occurredAt=sent;
            var mc=Minecraft.getInstance();
            var target=ClientState.INSTANCE.targets.get(UUID.fromString(r.get("target").getAsString()));
            var entity=target==null||mc.level==null?null:mc.level.getEntity(target.entityId);
            point=entity==null?mc.player.position().add(mc.player.getLookAngle().scale(2)).add(0,1,0):entity.position().add(0,entity.getBbHeight()*.8,0);
        }
    }
    private ClientState(){
    }
    public static long now(){
        return net.minecraft.util.Util.getMillis();
    }
    public long serverNow(){
        return now()+serverOffset;
    }
    public void send(String action,JsonObject value){
        if(Minecraft.getInstance().getConnection()!=null)ClientPacketDistributor.sendToServer(new Network.Request(action,JSON.toJson(value)));
    }
    public void send(String action){
        send(action,new JsonObject());
    }
    public JsonObject operation(String kind){
        JsonObject j=new JsonObject();
        j.addProperty("kind",kind);
        j.addProperty("operation",UUID.randomUUID().toString());
        return j;
    }
    public void progress(String kind,String key,Object value){
        upload();
        JsonObject j=operation(kind);
        j.add(key,JSON.toJsonTree(value));
        send("progress",j);
    }
    public void clear(){
        ready=false;
        damageMeter=new JsonObject();
        IndustrialVisuals.clear();
        session="";
        targets.clear();
        pending.clear();
        confirmedCombatUntil=confirmedShieldRecoveryAt=0;
        ailments.clear();
        contextRequests.clear();
        numbers.clear();
        resources=new Models.Runtime();
        own=new Life();
        profile=new JsonObject();
        professions=new JsonObject();
        ClientCombat.INSTANCE.cancel(false);
        Effects.clear();
        CloudMovement.clear();
    }
    public void accept(String action,JsonObject j){
        switch(action){
            case "industrial_lines"->IndustrialVisuals.receive(j);
            case "industrial_ui"->{if(Minecraft.getInstance().player!=null&&Minecraft.getInstance().player.containerMenu instanceof cn.yumis.fight.industry.IndustrialMenu menu&&menu.containerId==j.get("menu").getAsInt())menu.info=j;}
            case "storage_ui"->{if(Minecraft.getInstance().player!=null&&Minecraft.getInstance().player.containerMenu instanceof cn.yumis.fight.industry.DimensionalMenu menu)menu.preview=j;}
            case "damageMeter"->damageMeter=j;
            case "profile"->{
                if(j.get("protocol").getAsInt()!=PROTOCOL||j.get("rules").getAsInt()!=VERSION){
                    message("模组协议或规则版本不一致");
                    return;
                }
                String incoming=j.get("session").getAsString();
                Life life=JSON.fromJson(j.get("life"),Life.class);
                Models.Runtime runtime=JSON.fromJson(j.get("resources"),Models.Runtime.class);
                boolean reset=!incoming.equals(session)||own.generation!=life.generation||runtime.controlEpoch!=resources.controlEpoch;
                serverOffset=j.get("serverNow").getAsLong()-now();
                long serverNow=j.get("serverNow").getAsLong();
                if(!incoming.equals(session))syncedSources=new cn.yumis.fight.api.AttributeSources();
                if(reset){
                    ClientCombat.INSTANCE.cancel(false);
                    pending.clear();
                    ailments.clear();
                    resources=runtime;
                    translate(resources,now()-serverNow);
                    confirmedCombatUntil=resources.combatUntil;
                    confirmedShieldRecoveryAt=resources.shieldRecoveryAt;
                    if(!incoming.equals(session))nextOperation=1;
                }
                own=life;
                session=incoming;
                profile=j;
                progress=JSON.fromJson(j.get("progress"),Progress.class);
                stats=JSON.fromJson(j.get("stats"),Stats.class);
                resources.mp=Math.clamp(resources.mp,0,stats.get(Stat.MP));
                resources.shield=Math.clamp(resources.shield,0,stats.get(Stat.SHIELD));
                party=!j.has("party")||j.get("party").isJsonNull()?null:JSON.fromJson(j.get("party"),Team.class);
                dungeon=!j.has("dungeon")||j.get("dungeon").isJsonNull()?null:JSON.fromJson(j.get("dungeon"),Dungeon.class);
                if(j.has("catalog"))Catalog.CURRENT=Catalog.parse(j.get("catalog").toString());
                if(j.has("industrialRules"))cn.yumis.fight.industry.IndustrialRules.CURRENT=cn.yumis.fight.industry.IndustrialRules.parse(j.get("industrialRules").toString());
                if(j.has("equipmentDefinitions"))Definitions.CURRENT=Definitions.parse(j.get("equipmentDefinitions").toString());
                if(j.has("equipmentLayout"))EquipmentLayout.CURRENT=JSON.fromJson(j.get("equipmentLayout"),EquipmentLayout.class);
                if(j.has("equipmentGroups")&&Minecraft.getInstance().player!=null){
                    var groups=new ArrayList<String>();
                    j.getAsJsonArray("equipmentGroups").forEach(g->groups.add(g.getAsString()));
                    var extra=MenuHooks.extra(Minecraft.getInstance().player);
                    if(!extra.groups.equals(groups))extra.layout(groups);
                }
                if(j.has("attributeSources")&&Minecraft.getInstance().player!=null){
                    var sources=new ArrayList<cn.yumis.fight.api.AttributeSources.Source>();
                    for(var source:j.getAsJsonArray("attributeSources"))sources.add(JSON.fromJson(source,cn.yumis.fight.api.AttributeSources.Source.class));
                    syncedSources.replace(j.get("attributeRevision").getAsLong(),sources);
                }
                ready=true;
                if(j.has("movement"))CloudMovement.receive(j.getAsJsonObject("movement"));
                CloudMovement.screen(Minecraft.getInstance().gui.screen()!=null);
                applyEquipment(j.get("equipmentState").getAsString());
                if(Minecraft.getInstance().gui.screen() instanceof FightScreen screen)screen.refresh();
            }
            case "context"->{
                Target t=JSON.fromJson(j,Target.class);
                Target old=targets.get(t.id);
                if(old==null||t.life.generation>old.life.generation||t.life.generation==old.life.generation&&t.life.revision>=old.life.revision){
                    targets.put(t.id,t);
                    if(old!=null&&old.life.basis!=t.life.basis)pending.entrySet().removeIf(e->e.getValue().report.get("target").getAsString().equals(t.id.toString()));
                }
            }
            case "health"->{
                Life l=JSON.fromJson(j,Life.class);
                if(l.target.equals(own.target)){
                    if(l.generation==own.generation&&l.revision>=own.revision)own=l;
                }
                else{
                    Target t=targets.get(l.target);
                    if(t!=null&&l.generation==t.life.generation&&l.revision>=t.life.revision)t.life=l;
                }
            }
            case "ack"->ack(j);
            case "message"->message(j.get("text").getAsString());
            case "professions"->{
                professions=j;
                if(Minecraft.getInstance().gui.screen() instanceof FightScreen screen)screen.refresh();
            }
            case "incoming"->ClientCombat.INSTANCE.incoming(j);
            case "movement"->CloudMovement.receive(j);
            case "healing"->{
                if(ready&&!own.dead&&(!j.has("generation")||j.get("generation").getAsLong()==own.generation)&&(!j.has("session")||j.get("session").getAsString().equals(session)))report(own,j.get("points").getAsDouble()*.05,true,stats.get(Stat.HEALTH),0x8ef5ad,null);
            }
            case "projectile"->ClientCombat.INSTANCE.projectile(j);
            case "bossAction"->ClientCombat.INSTANCE.boss(j);
            case "cancelBossAction"->ClientCombat.INSTANCE.cancelBoss(j.get("id").getAsString());
            case "visual"->Effects.receive(j);
            case "ailmentsClear"->{
                if(j.has("target")&&j.has("kind"))ailments.remove(j.get("target").getAsString()+"/"+j.get("owner").getAsString()+"/"+j.get("kind").getAsString());
            }
            case "ailments"->{
                if(j.has("target")&&j.has("kind")){
                    ailments.put(j.get("target").getAsString()+"/"+j.get("owner").getAsString()+"/"+j.get("kind").getAsString(),j);
                    ClientCombat.INSTANCE.ailment(j);
                }
            }
            default->{
            }
        }
    }
    private void applyEquipment(String raw){
        try{
            if(raw.isEmpty()||Minecraft.getInstance().player==null)return;
            JsonObject j=JsonParser.parseString(raw).getAsJsonObject();
            var p=Minecraft.getInstance().player;
            var context=p.registryAccess().createSerializationContext(JsonOps.INSTANCE);
            var extra=MenuHooks.extra(p);
            if(j.has("bagTier")){extra.bagTier=j.get("bagTier").getAsInt();extra.bagSchema=j.get("bagSchema").getAsInt();extra.bagPage=j.get("bagPage").getAsInt();}
            if(j.has("bag")){JsonArray bag=j.getAsJsonArray("bag");extra.bag.clearContent();for(int i=0;i<Math.min(extra.bagLimit(),bag.size());i++)extra.bag.setItem(i,ItemStack.OPTIONAL_CODEC.parse(context,bag.get(i)).getOrThrow());}
        }
        catch(RuntimeException e){
            message("装备状态解码失败："+e.getMessage());
        }
    }
    private void translate(Models.Runtime r,long delta){
        r.combatUntil+=delta;
        r.shieldRecoveryAt+=delta;
        r.cooldowns.replaceAll((k,v)->v+delta);
        r.buffs.replaceAll((k,v)->v+delta);
        r.absorptionUntil.replaceAll((k,v)->v+delta);
        r.recordedAt=now();
    }
    public void upload(){
        if(!ready)return;
        resources.recordedAt=now();
        JsonObject j=new JsonObject();
        j.addProperty("session",session);
        j.addProperty("generation",own.generation);
        j.addProperty("serverIssuedAt",serverNow());
        j.add("state",JSON.toJsonTree(resources));
        send("runtime",j);
        lastUpload=now();
    }
    public void tick(){
        var mc=Minecraft.getInstance();
        if(mc.player==null||mc.level==null){
            if(ready)clear();
            return;
        }
        if(!ready){
            if(now()-lastHello>1000){
                lastHello=now();
                JsonObject j=new JsonObject();
                j.addProperty("protocol",PROTOCOL);
                j.addProperty("rules",VERSION);
                send("hello",j);
            }
            return;
        }
        stats=EquipmentStats.build(mc.player,progress,syncedSources);
        resources.mp=Math.clamp(resources.mp,0,stats.get(Stat.MP));
        resources.shield=Math.clamp(resources.shield,0,stats.get(Stat.SHIELD));
        String dim=mc.level.dimension().identifier().toString();
        if(!dimension.equals(dim)){
            dimension=dim;
            targets.entrySet().removeIf(entry->!entry.getValue().dimension.equals(dim));
            contextRequests.clear();
            pending.clear();
            ailments.clear();
            ClientCombat.INSTANCE.cancel(false);
            Effects.clear();
        }
        for(Pending p:pending.values())if(now()-p.sent>=1000){
            p.sent=now();
            send(p.action,p.report);
        }
        if(now()-lastUpload>=500)upload();
        numbers.removeIf(n->n.expires<now());
    }
    public Target context(net.minecraft.world.entity.LivingEntity entity){
        Target t=targets.get(entity.getUUID());
        if(t==null&&now()-contextRequests.getOrDefault(entity.getId(),0L)>250){
            contextRequests.put(entity.getId(),now());
            JsonObject j=new JsonObject();
            j.addProperty("entity",entity.getId());
            send("context",j);
        }
        return t;
    }
    public double predicted(Life life){
        double q=life.q;
        for(Pending p:pending.values())if(p.report.get("target").getAsString().equals(life.target.toString())&&p.report.get("generation").getAsLong()==life.generation&&p.report.get("basis").getAsLong()==life.basis)q=Math.clamp(q+(p.action.equals("heal")?1:-1)*p.report.get("fraction").getAsDouble(),0,1);
        return q;
    }
    public Stats projectileStats(ItemStack weapon){
        return EquipmentStats.build(Minecraft.getInstance().player,progress,syncedSources,weapon);
    }
    public void report(Life life,double fraction,boolean heal,double maximum,int color,JsonObject extras){
        boolean outcomeOnly=!heal&&fraction==0&&extras!=null&&extras.has("outcome")&&(extras.has("nativeRoot")||extras.has("feedbackEntity"));
        if(!ready||life.dead||fraction<0||fraction==0&&!outcomeOnly||!Double.isFinite(fraction))return;
        JsonObject j=new JsonObject();
        j.addProperty("session",session);
        j.addProperty("operation",nextOperation++);
        j.addProperty("sourceGeneration",own.generation);
        j.addProperty("dimension",life.dimension);
        j.addProperty("target",life.target.toString());
        j.addProperty("generation",life.generation);
        j.addProperty("basis",life.basis);
        j.addProperty("instance",life.instance);
        j.addProperty("fraction",fraction);
        j.addProperty("heal",heal);
        j.addProperty("occurredAt",serverNow());
        if(extras!=null)extras.entrySet().forEach(e->j.add(e.getKey(),e.getValue()));
        String action=heal?"heal":outcomeOnly?"nativeOutcome":"damage";
        pending.put(j.get("operation").getAsLong(),new Pending(j,action,maximum,color));
        refreshDamageClocks();
        JsonObject runtime=new JsonObject();
        runtime.addProperty("session",session);
        runtime.addProperty("generation",own.generation);
        resources.recordedAt=now();
        runtime.addProperty("serverIssuedAt",serverNow());
        runtime.add("state",JSON.toJsonTree(resources));
        j.add("runtime",runtime);
        if(Boolean.getBoolean("ydnf.verify")&&j.has("nativeRoot"))FeedbackProbe.lastReport=j.deepCopy();
        send(action,j);
    }
    private void refreshDamageClocks(){
        resources.combatUntil=confirmedCombatUntil;
        resources.shieldRecoveryAt=confirmedShieldRecoveryAt;
        for(Pending p:pending.values())if(!p.action.equals("heal")){
            boolean hit=!p.report.has("outcome")||p.report.get("outcome").getAsString().equals("HIT");
            if(!hit)continue;
            boolean taken=p.report.get("target").getAsString().equals(own.target.toString());
            double damage=p.report.has("damageTaken")?p.report.get("damageTaken").getAsDouble():p.report.get("fraction").getAsDouble();
            if(damage>0){if(taken)CombatTiming.taken(resources,p.occurredAt);else CombatTiming.dealt(resources,p.occurredAt);}
        }
    }
    private void ack(JsonObject j){
        if(!ready||!session.equals(j.get("session").getAsString()))return;
        Pending p=pending.remove(j.get("operation").getAsLong());
        if(p==null)return;
        Receipt r=JSON.fromJson(j.get("receipt"),Receipt.class);
        if(!r.accepted()){
            refreshDamageClocks();
            if(!r.reason().isEmpty())message(r.reason());
            return;
        }
        if(j.has("combatUntil"))confirmedCombatUntil=Math.max(confirmedCombatUntil,j.get("combatUntil").getAsLong()-serverOffset);
        if(j.has("shieldRecoveryAt"))confirmedShieldRecoveryAt=Math.max(confirmedShieldRecoveryAt,j.get("shieldRecoveryAt").getAsLong()-serverOffset);
        refreshDamageClocks();
        UUID id=UUID.fromString(j.get("target").getAsString());
        Life l=id.equals(own.target)?own:targets.containsKey(id)?targets.get(id).life:null;
        if(l!=null&&j.get("generation").getAsLong()==l.generation&&r.revision()>=l.revision){
            l.q=r.q();
            l.revision=r.revision();
            l.dead=l.q<=0;
        }
        if(r.actual()>0&&p.action.equals("damage")&&!id.equals(own.target)){
            if(numbers.size()>=96)numbers.removeFirst();
            numbers.add(new DamageNumber(r.actual()*p.maximum,now()+1100,p.color,p.point,p.report.get("dimension").getAsString(),(int)(j.get("operation").getAsLong()%3)-1));
        }
    }
    public JsonObject ailment(String target,String kind){
        return ailments.values().stream().filter(a->a.get("target").getAsString().equals(target)&&a.get("kind").getAsString().equals(kind)&&a.get("expires").getAsLong()>serverNow()).max(java.util.Comparator.comparingLong(a->a.get("expires").getAsLong())).orElse(null);
    }
    public void message(String s){
        var mc=Minecraft.getInstance();
        CurrencyUi.notice(s);
        if(mc.player!=null)mc.player.sendSystemMessage(UiFonts.decorate(Component.literal(s)));
    }
}
