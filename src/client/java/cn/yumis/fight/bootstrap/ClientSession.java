package cn.yumis.fight.bootstrap;
import cn.yumis.fight.modules.skills.api.client.CloudMovement;
import cn.yumis.fight.modules.skills.api.client.SkillController;
import cn.yumis.fight.verification.FeedbackProbe;
import cn.yumis.fight.modules.equipment.client.CurrencyUi;
import cn.yumis.fight.modules.presentation.api.client.UiFonts;
import cn.yumis.fight.modules.equipment.api.EquipmentStats;
import cn.yumis.fight.modules.inventory.api.MenuHooks;
import cn.yumis.fight.modules.equipment.api.EquipmentDefinitions;
import cn.yumis.fight.modules.attributes.api.AttributeSources;
import cn.yumis.fight.modules.attributes.api.Stat;
import cn.yumis.fight.modules.industry.client.IndustrialVisuals;
import cn.yumis.fight.modules.skills.client.Effects;
import cn.yumis.fight.modules.economy.api.GoldFeedback;


import cn.yumis.fight.network.Network;

import cn.yumis.fight.modules.character.api.Progress;
import cn.yumis.fight.modules.combat.api.Runtime;
import cn.yumis.fight.modules.attributes.api.Stats;
import cn.yumis.fight.modules.combat.api.Life;
import cn.yumis.fight.modules.dungeons.api.Target;
import cn.yumis.fight.modules.party.api.Team;
import cn.yumis.fight.modules.dungeons.api.Dungeon;
import static cn.yumis.fight.bootstrap.ApplicationJson.JSON;
import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import java.util.*;
/** Connection-scoped prediction. Receipts retire pending entries exactly once. */
public final class ClientSession implements cn.yumis.fight.modules.equipment.api.client.EquipmentClient,cn.yumis.fight.modules.skills.api.client.SkillsClient {
    public Progress progress(){return progress;}
    public Stats stats(){return stats;}
    public Life own(){return own;}
    public cn.yumis.fight.modules.combat.api.Runtime resources(){return resources;}
    public boolean ready(){return ready;}
    public String session(){return session;}
    public boolean encounterSync(){return dungeon==null?progress.sync:dungeon.sync;}
    public Map<UUID,Target> targets(){return targets;}

    public static final ClientSession INSTANCE=new ClientSession();
    public Progress progress=new Progress();
    public cn.yumis.fight.modules.combat.api.Runtime resources=new cn.yumis.fight.modules.combat.api.Runtime();
    public Stats stats=new Stats();
    public Life own=new Life();
    public String session="",dimension="";
    public Team party;
    public Dungeon dungeon;
    public JsonObject profile=new JsonObject(),professions=new JsonObject(),damageMeter=new JsonObject();
    public boolean ready;
    public final GoldFeedback gold=new GoldFeedback();
    private cn.yumis.fight.modules.attributes.api.AttributeSources syncedSources=new cn.yumis.fight.modules.attributes.api.AttributeSources();
    private long lastUpload,lastHello,lastUploadCheck;
    private final ProfileSync.Receiver viewSync=new ProfileSync.Receiver();
    private cn.yumis.fight.modules.combat.api.RuntimeCheckpoint checkpoint;
    private long checkpointGeneration=-1;
    private long lastResourceServerTime=Long.MIN_VALUE,lastClockArrival=Long.MIN_VALUE,lastDefinitionRetry;
    private int definitionRetries;
    private long runtimeSequence,pendingRuntimeSequence,pendingRuntimeAt;
    private long minimumRuntimeIssuedAt;
    private JsonObject pendingRuntime;
    private cn.yumis.fight.modules.combat.api.RuntimeCheckpoint pendingRuntimeState;
    private record ReportCheckpoint(cn.yumis.fight.modules.combat.api.RuntimeCheckpoint state,long generation,long sent){}
    private final Map<Long,ReportCheckpoint> reportedCheckpoints=new LinkedHashMap<>();
    public long serverOffset;
    public final Map<UUID,Target> targets=new HashMap<>();
    public final Map<String,JsonObject> ailments=new HashMap<>();
    private final Map<Integer,Long> contextRequests=new HashMap<>();
    public final cn.yumis.fight.modules.combat.api.client.CombatPrediction prediction=new cn.yumis.fight.modules.combat.api.client.CombatPrediction(new cn.yumis.fight.modules.combat.api.client.CombatPrediction.State(){
        public boolean ready(){return ready;} public String session(){return session;} public Life own(){return own;}
        public cn.yumis.fight.modules.combat.api.Runtime resources(){return resources;}
        public long serverOffset(){return serverOffset;} public long serverNow(){return ClientSession.this.serverNow();}
        public Life target(UUID id){var target=targets.get(id);return target==null?null:target.life;}
        public boolean training(UUID id){return cn.yumis.fight.modules.skills.api.client.TrainingCombat.isTraining(id);}
        public boolean consumesHealth(UUID id){var target=targets.get(id);return target==null||target.consumesHealth();}
        public net.minecraft.world.phys.Vec3 position(UUID id){var mc=Minecraft.getInstance();var target=targets.get(id);var entity=target==null||mc.level==null?null:mc.level.getEntity(target.entityId);return entity==null?mc.player.position().add(mc.player.getLookAngle().scale(2)).add(0,1,0):entity.position().add(0,entity.getBbHeight()*.8,0);}
        public void send(String action,JsonObject packet){ClientSession.this.send(action,packet);}
        public void message(String text){ClientSession.this.message(text);}
        public void reported(JsonObject packet){SkillController.INSTANCE.reported(packet);if(Boolean.getBoolean("ydnf.verify")&&packet.has("nativeRoot"))FeedbackProbe.lastReport=packet.deepCopy();}
    });
    private ClientSession(){cn.yumis.fight.modules.equipment.api.EquipmentEligibility.CLIENT_ALLOWANCE=p->profile.has("contractUntil")&&profile.get("contractUntil").getAsLong()>serverNow()?5:0;registerMessages();}
    public static long now(){
        return net.minecraft.util.Util.getMillis();
    }
    public long serverNow(){
        return now()+serverOffset;
    }
    public void send(String action,JsonObject value){
        if(value.has("operation")&&value.has("runtime")){
            long operation=value.get("operation").getAsLong();
            reportedCheckpoints.computeIfAbsent(operation,id->new ReportCheckpoint(cn.yumis.fight.modules.combat.api.RuntimeCheckpoint.capture(resources),own.generation,now()));
            while(reportedCheckpoints.size()>256)reportedCheckpoints.remove(reportedCheckpoints.keySet().iterator().next());
        }
        if(Minecraft.getInstance().getConnection()!=null){String wire=JSON.toJson(value);cn.yumis.fight.modules.presentation.api.ViewTraffic.record("send",action,wire);ClientPacketDistributor.sendToServer(new Network.Request(action,wire));}
    }
    public void send(String action){
        send(action,new JsonObject());
    }
    public JsonObject operation(String kind){
        JsonObject j=new JsonObject();
        j.addProperty("kind",kind);
        j.addProperty("rulesRevision",EquipmentDefinitions.current().revision);
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
        cn.yumis.fight.modules.combat.api.client.DamageAnalysis.clear();
        gold.clear();viewSync.clear();checkpoint=null;checkpointGeneration=-1;reportedCheckpoints.clear();pendingRuntime=null;pendingRuntimeState=null;runtimeSequence=0;minimumRuntimeIssuedAt=0;lastResourceServerTime=lastClockArrival=Long.MIN_VALUE;definitionRetries=0;
        cn.yumis.fight.modules.skills.api.client.TrainingCombat.clear();
        ready=false;
        damageMeter=new JsonObject();
        IndustrialVisuals.clear();
        session="";
        targets.clear();
        prediction.clear();
        ailments.clear();
        contextRequests.clear();
        resources=new cn.yumis.fight.modules.combat.api.Runtime();
        own=new Life();
        profile=new JsonObject();
        professions=new JsonObject();
        SkillController.INSTANCE.cancel(false);
        SkillController.INSTANCE.clearTransient();
        Effects.clear();
        CloudMovement.clear();
    }
    public void accept(String action,JsonObject j){
        cn.yumis.fight.modules.presentation.api.ViewTraffic.record("receive",action,j.toString());
        try{
            messages.dispatch(null,action,j);
            if(j.has("serverNow")&&(action.equals("careerStarted")||action.equals("skillStarted")||action.equals("basicStarted")||action.equals("ack")&&j.has("receipt")&&j.getAsJsonObject("receipt").get("accepted").getAsBoolean()))lastResourceServerTime=Math.max(lastResourceServerTime,j.get("serverNow").getAsLong());
        }catch(Exception error){
            message("消息处理失败："+error.getMessage());
            if((action.equals("profile")||action.equals("definitionPart"))&&definitionRetries<3&&now()-lastDefinitionRetry>=1000){lastDefinitionRetry=now();definitionRetries++;viewSync.retryDefinitions();var request=new JsonObject();request.addProperty("definitions",true);send("profile",request);}
        }
    }
    private final cn.yumis.fight.architecture.CommandRouter<Void,JsonObject> messages=new cn.yumis.fight.architecture.CommandRouter<>();
    private void registerMessages(){
        cn.yumis.fight.modules.industry.client.IndustryClientMessages.register(messages);
        cn.yumis.fight.modules.inventory.client.InventoryClientMessages.register(messages);
        cn.yumis.fight.modules.economy.client.EconomyClientMessages.register(messages);
        cn.yumis.fight.modules.skills.client.SkillsClientMessages.register(messages);
        messages.register("dungeons","damageMeter",(context,j)->{
damageMeter=j;
        });
        messages.register("presentation","profile",(context,j)->{if(!viewSync.personal(j,now(),this::applyProfile))send("profile");});
        messages.register("presentation","definitionPart",(context,j)->viewSync.definition(j,value->{
            // An integrated server already owns this shared static store. A delayed wire copy must never roll it back.
            if(Minecraft.getInstance().getSingleplayerServer()==null)ApplicationRules.receive(value);
        },this::applyProfile));
        messages.register("dungeons","context",(context,j)->{
Target t=JSON.fromJson(j,Target.class);
                Target old=targets.get(t.id);
                if(old==null||t.life.generation>old.life.generation||t.life.generation==old.life.generation&&t.life.revision>=old.life.revision){
                    targets.put(t.id,t);
                    if(old!=null&&old.life.basis!=t.life.basis)prediction.invalidate(t.id);
                }
        });
        messages.register("combat","health",(context,j)->{
Life l=JSON.fromJson(j,Life.class);
                if(l.target.equals(own.target)){
                    if(l.generation==own.generation&&l.revision>=own.revision)own=l;
                }
                else{
                    Target t=targets.get(l.target);
                    if(t!=null&&l.generation==t.life.generation&&l.revision>=t.life.revision)t.life=l;
                }
        });
        messages.register("combat","ack",(context,j)->{
prediction.ack(j);
SkillController.INSTANCE.ack(j);
var reported=reportedCheckpoints.remove(j.get("operation").getAsLong());
if(session.equals(j.get("session").getAsString())&&j.has("runtimeRecordedAt"))minimumRuntimeIssuedAt=Math.max(minimumRuntimeIssuedAt,j.get("runtimeRecordedAt").getAsLong());
if(reported!=null&&session.equals(j.get("session").getAsString())&&reported.generation==own.generation&&reported.sent>=lastUpload&&j.getAsJsonObject("receipt").get("accepted").getAsBoolean()&&j.has("runtimeApplied")&&j.get("runtimeApplied").getAsBoolean()){checkpoint=reported.state;checkpointGeneration=own.generation;lastUpload=reported.sent;if(reported.sent>=pendingRuntimeAt){pendingRuntime=null;pendingRuntimeState=null;}}
        });
        messages.register("combat","runtimeAck",(context,j)->{
            if(pendingRuntime==null||!session.equals(j.get("session").getAsString())||own.generation!=j.get("generation").getAsLong()||pendingRuntimeSequence!=j.get("checkpoint").getAsLong())return;
            minimumRuntimeIssuedAt=Math.max(minimumRuntimeIssuedAt,j.get("recordedAt").getAsLong());
            if(j.get("accepted").getAsBoolean()){checkpoint=pendingRuntimeState;checkpointGeneration=own.generation;}
            else if(j.get("epoch").getAsLong()!=resources.controlEpoch)send("profile");
            pendingRuntime=null;pendingRuntimeState=null;
        });
        messages.register("presentation","message",(context,j)->{
message(j.get("text").getAsString());
        });
        messages.register("economy","goldGain",(context,j)->{
if(ready&&session.equals(j.get("session").getAsString()))gold.income(j.get("amount").getAsLong(),j.get("balance").getAsLong(),j.get("revision").getAsLong(),now());
        });
        messages.register("professions","professions",(context,j)->{
professions=j;
                if(Minecraft.getInstance().gui.screen() instanceof FightScreen screen)screen.refresh();
        });
        messages.register("combat","healing",(context,j)->{
if(ready&&!own.dead&&(!j.has("generation")||j.get("generation").getAsLong()==own.generation)&&(!j.has("session")||j.get("session").getAsString().equals(session)))report(own,j.get("points").getAsDouble()*.05,true,stats.get(Stat.HEALTH),0x8ef5ad,false,null);
        });
        messages.register("combat","ailmentsClear",(context,j)->{
if(j.has("target")&&j.has("kind"))ailments.remove(j.get("target").getAsString()+"/"+j.get("owner").getAsString()+"/"+j.get("kind").getAsString());
        });
        messages.register("combat","ailments",(context,j)->{
if(j.has("target")&&j.has("kind")){
                    ailments.put(j.get("target").getAsString()+"/"+j.get("owner").getAsString()+"/"+j.get("kind").getAsString(),j);
                    SkillController.INSTANCE.ailment(j);
                }
        });
        ApplicationModules.validateCommands(messages);messages.seal();
    }
    private void applyProfile(JsonObject j){
                if(Minecraft.getInstance().getSingleplayerServer()!=null&&ServerApplication.ready()&&!ServerApplication.get().definitionKey().equals(j.get("definitionsKey").getAsString()))return;
                if(j.get("protocol").getAsInt()!=cn.yumis.fight.architecture.ProtocolVersion.CURRENT||j.get("rules").getAsInt()!=cn.yumis.fight.bootstrap.ApplicationRules.DATA_VERSION){
                    message("模组协议或规则版本不一致");
                    return;
                }
                String incoming=j.get("session").getAsString();
                if(!incoming.equals(session))gold.clear();
                gold.snapshot(j.get("gold").getAsLong(),j.get("goldRevision").getAsLong());
                Life life=JSON.fromJson(j.get("life"),Life.class);
                cn.yumis.fight.modules.combat.api.Runtime runtime=JSON.fromJson(j.get("resources"),cn.yumis.fight.modules.combat.api.Runtime.class);
                long serverNow=j.get("serverNow").getAsLong();
                long receivedAt=j.get("_receivedAt").getAsLong();
                boolean same=incoming.equals(session);
                if(same&&life.generation<own.generation)return;
                boolean newerLife=!same||life.generation>own.generation;
                boolean freshResources=newerLife||runtime.controlEpoch>=resources.controlEpoch&&serverNow>=lastResourceServerTime;
                boolean reset=newerLife||freshResources&&runtime.controlEpoch>resources.controlEpoch;
                if(receivedAt>=lastClockArrival){serverOffset=serverNow-receivedAt;lastClockArrival=receivedAt;}
                if(!incoming.equals(session))syncedSources=new cn.yumis.fight.modules.attributes.api.AttributeSources();
                if(reset){
                    SkillController.INSTANCE.cancel(false);
                    SkillController.INSTANCE.clearTransient();reportedCheckpoints.clear();
                    ailments.clear();
                    resources=runtime;
                    translate(resources,receivedAt-serverNow);
                    prediction.reset(!incoming.equals(session));
                }
                if(newerLife||life.revision>=own.revision)own=life;
                session=incoming;
                profile=j;
                progress=JSON.fromJson(j.get("progress"),Progress.class);
                if(freshResources)stats=JSON.fromJson(j.get("stats"),Stats.class);
                if(freshResources&&j.has("equipmentEffects"))resources.equipment=JSON.fromJson(j.get("equipmentEffects"),cn.yumis.fight.modules.equipment.api.EquipmentEffects.Snapshot.class);
                if(freshResources&&!reset){resources.equipmentBuffs=runtime.equipmentBuffs;for(var buff:resources.equipmentBuffs.values())buff.expires+=receivedAt-serverNow;}
                if(freshResources)lastResourceServerTime=Math.max(lastResourceServerTime,serverNow);
                resources.mp=Math.clamp(resources.mp,0,stats.get(Stat.MP));
                resources.shield=Math.clamp(resources.shield,0,stats.get(Stat.SHIELD));
                party=!j.has("party")||j.get("party").isJsonNull()?null:JSON.fromJson(j.get("party"),Team.class);
                dungeon=!j.has("dungeon")||j.get("dungeon").isJsonNull()?null:JSON.fromJson(j.get("dungeon"),Dungeon.class);
                if(j.has("equipmentGroups")&&Minecraft.getInstance().player!=null){
                    var groups=new ArrayList<String>();
                    j.getAsJsonArray("equipmentGroups").forEach(g->groups.add(g.getAsString()));
                    var extra=MenuHooks.extra(Minecraft.getInstance().player);
                    if(!extra.groups.equals(groups))extra.layout(groups);
                }
                if(j.has("attributeSources")&&Minecraft.getInstance().player!=null){
                    var sources=new ArrayList<cn.yumis.fight.modules.attributes.api.AttributeSources.Source>();
                    for(var source:j.getAsJsonArray("attributeSources"))sources.add(JSON.fromJson(source,cn.yumis.fight.modules.attributes.api.AttributeSources.Source.class));
                    syncedSources.replace(j.get("attributeRevision").getAsLong(),sources);
                }
                if(reset){checkpoint=cn.yumis.fight.modules.combat.api.RuntimeCheckpoint.capture(resources);checkpointGeneration=own.generation;pendingRuntime=null;pendingRuntimeState=null;}
                minimumRuntimeIssuedAt=Math.max(minimumRuntimeIssuedAt,runtime.recordedAt);
                ready=true;definitionRetries=0;analysisLifecycle();
                if(j.has("movement"))CloudMovement.receive(j.getAsJsonObject("movement"));
                CloudMovement.screen(Minecraft.getInstance().gui.screen()!=null);
                cn.yumis.fight.modules.inventory.client.InventoryClientMessages.applyEquipment(j.get("equipmentState").getAsString());
                if(Minecraft.getInstance().gui.screen() instanceof FightScreen screen)screen.refresh();
    }
    private void translate(cn.yumis.fight.modules.combat.api.Runtime r,long delta){
        r.combatUntil+=delta;
        r.shieldRecoveryAt+=delta;
        r.cooldowns.replaceAll((k,v)->v+delta);
        r.buffs.replaceAll((k,v)->v+delta);
        for(var buff:r.equipmentBuffs.values())buff.expires+=delta;
        r.absorptionUntil.replaceAll((k,v)->v+delta);
        r.recordedAt=now();
    }
    public void upload(){
        if(!ready)return;
        lastUploadCheck=now();
        var next=cn.yumis.fight.modules.combat.api.RuntimeCheckpoint.capture(resources);
        if(pendingRuntime!=null&&next.equals(pendingRuntimeState)){
            if(now()-pendingRuntimeAt>=500){send("runtime",pendingRuntime);pendingRuntimeAt=now();lastUpload=pendingRuntimeAt;}
            return;
        }
        if(pendingRuntime==null&&own.generation==checkpointGeneration&&next.equals(checkpoint))return;
        resources.recordedAt=now();
        JsonObject j=new JsonObject();
        j.addProperty("session",session);
        j.addProperty("generation",own.generation);
        j.addProperty("checkpoint",++runtimeSequence);
        j.addProperty("serverIssuedAt",Math.max(serverNow(),minimumRuntimeIssuedAt+1));
        j.add("state",JSON.toJsonTree(resources));
        send("runtime",j);
        pendingRuntime=j;pendingRuntimeState=next;pendingRuntimeSequence=runtimeSequence;pendingRuntimeAt=now();lastUpload=pendingRuntimeAt;
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
                j.addProperty("protocol",cn.yumis.fight.architecture.ProtocolVersion.CURRENT);
                j.addProperty("rules",cn.yumis.fight.bootstrap.ApplicationRules.DATA_VERSION);
                send("hello",j);
            }
            return;
        }
        stats=EquipmentStats.build(mc.player,progress,syncedSources);
        resources.equipment=EquipmentStats.cachedEffects(mc.player,progress,syncedSources);
        cn.yumis.fight.modules.combat.api.EquipmentBuffs.prune(resources,resources.equipment,now(),own.dead);
        EquipmentStats.applyMovement(mc.player,stats);
        resources.mp=Math.clamp(resources.mp,0,stats.get(Stat.MP));
        resources.shield=Math.clamp(resources.shield,0,stats.get(Stat.SHIELD));
        String dim=mc.level.dimension().identifier().toString();
        if(!dimension.equals(dim)){
            dimension=dim;
            targets.entrySet().removeIf(entry->!entry.getValue().dimension.equals(dim));
            contextRequests.clear();cn.yumis.fight.modules.skills.api.client.TrainingCombat.clear();
            prediction.clearPending();
            ailments.clear();
            SkillController.INSTANCE.cancel(false);
            SkillController.INSTANCE.clearTransient();reportedCheckpoints.clear();
            Effects.clear();
        }
        analysisLifecycle();
        prediction.tick();
        if(now()-lastUploadCheck>=500)upload();
    }
    public Target context(net.minecraft.world.entity.LivingEntity entity){
        Target training=cn.yumis.fight.modules.skills.api.client.TrainingCombat.context(entity);
        if(training!=null){targets.put(training.id,training);return training;}
        Target t=targets.get(entity.getUUID());
        if(t==null&&now()-contextRequests.getOrDefault(entity.getId(),0L)>250){
            contextRequests.put(entity.getId(),now());
            JsonObject j=new JsonObject();
            j.addProperty("entity",entity.getId());
            send("context",j);
        }
        return t;
    }
    private void analysisLifecycle(){
        var mc=Minecraft.getInstance();if(mc.level==null)return;
        boolean changed=cn.yumis.fight.modules.combat.api.client.DamageAnalysis.lifecycle(session,mc.level.dimension().identifier().toString(),own.instance,own.dead);
        if(changed&&mc.gui.screen() instanceof FightScreen screen&&screen.page>=0&&cn.yumis.fight.modules.presentation.api.PlayerPages.at(screen.page).id().equals("damage_analysis"))mc.gui.setScreen(null);
    }
    public double predicted(Life life){return prediction.predicted(life);}
    public Stats projectileStats(ItemStack weapon){
        return EquipmentStats.build(Minecraft.getInstance().player,progress,syncedSources,weapon);
    }
    public void report(Life life,double fraction,boolean heal,double maximum,int color,boolean critical,JsonObject extras){prediction.report(life,fraction,heal,maximum,color,critical,extras);}
    public JsonObject ailment(String target,String kind){
        if(cn.yumis.fight.modules.skills.api.client.TrainingCombat.isTraining(UUID.fromString(target)))return cn.yumis.fight.modules.skills.api.client.TrainingCombat.ailment(target,kind);
        return ailments.values().stream().filter(a->a.get("target").getAsString().equals(target)&&a.get("kind").getAsString().equals(kind)&&a.get("expires").getAsLong()>serverNow()).max(java.util.Comparator.comparingLong(a->a.get("expires").getAsLong())).orElse(null);
    }
    public void message(String s){
        var mc=Minecraft.getInstance();
        CurrencyUi.notice(s);
        if(mc.player!=null)mc.player.sendSystemMessage(UiFonts.decorate(Component.literal(s)));
    }
}
