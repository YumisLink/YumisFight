package cn.yumis.fight.bootstrap;
import cn.yumis.fight.modules.skills.api.client.CloudMovement;
import cn.yumis.fight.modules.skills.api.client.SkillController;
import cn.yumis.fight.verification.FeedbackProbe;
import cn.yumis.fight.modules.equipment.client.CurrencyUi;
import cn.yumis.fight.modules.equipment.client.EquipmentActions;
import cn.yumis.fight.modules.presentation.api.client.UiFonts;
import cn.yumis.fight.modules.equipment.api.EquipmentStats;
import cn.yumis.fight.modules.inventory.api.MenuHooks;
import cn.yumis.fight.modules.equipment.api.EquipmentDefinitions;
import cn.yumis.fight.modules.combat.api.CombatEffect;
import cn.yumis.fight.modules.attributes.api.AttributeSources;
import cn.yumis.fight.modules.attributes.api.Stat;
import cn.yumis.fight.modules.industry.client.IndustrialVisuals;
import cn.yumis.fight.modules.skills.client.Effects;
import cn.yumis.fight.modules.economy.api.GoldFeedback;
import cn.yumis.fight.modules.economy.api.RewardSettings;
import cn.yumis.fight.modules.economy.client.AutoSellClient;
import cn.yumis.fight.modules.economy.client.ShopClient;
import cn.yumis.fight.modules.quests.client.TutorialClient;


import cn.yumis.fight.network.Network;

import cn.yumis.fight.modules.character.api.Progress;
import cn.yumis.fight.modules.combat.api.Runtime;
import cn.yumis.fight.modules.attributes.api.Stats;
import cn.yumis.fight.modules.combat.api.Life;
import cn.yumis.fight.modules.dungeons.api.Target;
import cn.yumis.fight.modules.party.api.Team;
import cn.yumis.fight.modules.dungeons.api.Dungeon;
import cn.yumis.fight.modules.dungeons.client.DungeonEntryTitle;
import cn.yumis.fight.modules.dungeons.client.TowerClient;
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
    public cn.yumis.fight.modules.combat.api.CombatSettings combatSettings(){return combatSettings;}
    public cn.yumis.fight.modules.combat.api.CombatSettings combatSettings=cn.yumis.fight.modules.combat.api.CombatSettings.DEFAULT;
    /** Server combat experience rate from the profile sync; feeds the HUD hint and never changes locally. */
    public double combatExperienceMultiplier=RewardSettings.DEFAULT.combatExperienceMultiplier();
    public Life own(){return own;}
    public cn.yumis.fight.modules.combat.api.Runtime resources(){return resources;}
    public void resources(cn.yumis.fight.modules.combat.api.Runtime value){resources=java.util.Objects.requireNonNull(value);}
    public boolean ready(){return ready;}
    public boolean combatAvailable(){return !TowerClient.blocksCombat();}
    public cn.yumis.fight.modules.dungeons.api.TowerBattleState towerBattle(){return TowerClient.battle();}
    public String towerSkill(cn.yumis.fight.modules.dungeons.api.TowerBattleCard card){return TowerClient.boundSkill(card);}
    public void saveTowerBattle(){TowerClient.saveBattle();}
    public String session(){return session;}
    public boolean encounterSync(){return dungeon==null?progress.sync:dungeon.syncFor(own.target);}
    public Map<UUID,Target> targets(){return targets;}
    public void skillHit(Target target,cn.yumis.fight.modules.skills.api.SkillDefinition skill,double fraction){cn.yumis.fight.modules.quests.client.TutorialClient.skillHit(target,skill,fraction);}

    public static final ClientSession INSTANCE=new ClientSession();
    public Progress progress=new Progress();
    public cn.yumis.fight.modules.combat.api.Runtime resources=new cn.yumis.fight.modules.combat.api.Runtime();
    public Stats stats=new Stats();
    public Life own=new Life();
    public String session="",dimension="";
    public Team party;
    public Dungeon dungeon;
    public JsonObject profile=new JsonObject(),professions=new JsonObject(),damageMeter=new JsonObject(),trainingMeter=new JsonObject();
    public boolean ready;
    public final GoldFeedback gold=new GoldFeedback();
    private cn.yumis.fight.modules.attributes.api.AttributeSources syncedSources=new cn.yumis.fight.modules.attributes.api.AttributeSources();
    private long lastUpload,lastHello,lastUploadCheck,lastStatsCheck;
    private boolean uploadDeferred;
    private String sentStats="";
    private String sentBagOption="";
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
    private final Map<UUID,Long> targetArrivedAt=new HashMap<>();
    public final cn.yumis.fight.modules.combat.client.TargetHudFocus targetHud=new cn.yumis.fight.modules.combat.client.TargetHudFocus();
    public final Map<String,JsonObject> ailments=new HashMap<>();
    private record ContextRequest(UUID target,long sent){}
    private final Map<Integer,ContextRequest> contextRequests=new HashMap<>();
    public final cn.yumis.fight.modules.combat.api.client.CombatPrediction prediction=new cn.yumis.fight.modules.combat.api.client.CombatPrediction(new cn.yumis.fight.modules.combat.api.client.CombatPrediction.State(){
        public boolean ready(){return ready;} public String session(){return session;} public Life own(){return own;}
        public cn.yumis.fight.modules.combat.api.Runtime resources(){return resources;}
        public cn.yumis.fight.modules.combat.api.CombatSettings combatSettings(){return combatSettings;}
        public Stats stats(){return ClientSession.this.stats();}
        public long serverOffset(){return serverOffset;} public long serverNow(){return ClientSession.this.serverNow();}
        public Life target(UUID id){var target=targets.get(id);return target==null?null:target.life;}
        public cn.yumis.fight.modules.combat.api.client.DamageAnalysis.Kind damageKind(UUID id){var target=targets.get(id);return target==null||!target.training()?cn.yumis.fight.modules.combat.api.client.DamageAnalysis.Kind.COMBAT:target.timedTraining?cn.yumis.fight.modules.combat.api.client.DamageAnalysis.Kind.TIMED_TRAINING:cn.yumis.fight.modules.combat.api.client.DamageAnalysis.Kind.TRAINING;}
        public boolean consumesHealth(UUID id){var target=targets.get(id);return target==null||target.consumesHealth();}
        public net.minecraft.world.phys.Vec3 position(UUID id){var mc=Minecraft.getInstance();var target=targets.get(id);var entity=target==null||mc.level==null?null:mc.level.getEntity(target.entityId);return entity==null?mc.player.position().add(mc.player.getLookAngle().scale(2)).add(0,1,0):entity.position().add(0,entity.getBbHeight()*.8,0);}
        public void send(String action,JsonObject packet){ClientSession.this.send(action,packet);}
        public void message(String text){ClientSession.this.message(text);}
        public void reported(JsonObject packet){trackTargetDamage(packet);SkillController.INSTANCE.reported(packet);if(Boolean.getBoolean("ydnf.verify")&&packet.has("nativeRoot"))FeedbackProbe.lastReport=packet.deepCopy();}
        public void confirmedDamage(double points,String instance,long generation){
            if(own.instance.equals(instance)&&own.generation==generation)TowerClient.recordFrost(instance,generation,points);
        }
    });
    private ClientSession(){cn.yumis.fight.modules.equipment.api.EquipmentEligibility.CLIENT_ALLOWANCE=p->profile.has("contractUntil")&&profile.get("contractUntil").getAsLong()>serverNow()?5:0;registerMessages();}
    public static long now(){
        return net.minecraft.util.Util.getMillis();
    }
    public long serverNow(){
        return now()+serverOffset;
    }
    public final cn.yumis.fight.architecture.CommandRouter<Void,JsonObject> preparations=new cn.yumis.fight.architecture.CommandRouter<>();
    private static final long PERSONAL_TIMEOUT_MS=5000;
    private static final int PERSONAL_TIMEOUT_LIMIT=3;
    private record PersonalRequest(String action,JsonObject value){}
    private final ArrayDeque<PersonalRequest> personalQueue=new ArrayDeque<>();
    private long personalSequence,personalSentAt;
    private JsonObject personalSent;
    private boolean personalAcknowledged,personalReceived;
    private int personalRetries,personalTimeouts;
    public void send(String action,JsonObject value){
        // Every profession result passes here exactly once: server relays are re-sent by ProfessionClient.event, rod variants originate here.
        if(action.equals("professionResult")&&value.has("event"))cn.yumis.fight.modules.quests.client.TutorialClient.professionObserved(value.getAsJsonObject("event"));
        if(preparations.owners().containsKey(action)){personalQueue.add(new PersonalRequest(action,value.deepCopy()));personalNext();return;}
        transmit(action,value);
    }
    private void personalNext(){
        if(!ready||personalSent!=null)return;
        while(!personalQueue.isEmpty()){
            var next=personalQueue.peek();var value=next.value().deepCopy();
            try{
                if(value.has("retryStorage")){value.remove("profileId");value.remove("profileRevision");}
                if(value.has("profileId")&&(!cn.yumis.fight.modules.character.api.client.CareerProfilesClient.active().equals(value.get("profileId").getAsString())||cn.yumis.fight.modules.character.api.client.CareerProfilesClient.revision()!=value.get("profileRevision").getAsLong()))throw new IllegalArgumentException("职业配置已变化，请重新操作");
                value.addProperty("session",session);value.addProperty("clientTransaction",++personalSequence);
                preparations.dispatch(null,next.action(),value);
                if(!value.has("localOnly"))transmit(next.action(),value);
            }catch(Exception failure){
                personalQueue.remove();
                personalRetries=0;
                finishPersonal(next.action(),value,false);
                message(Objects.toString(failure.getMessage(),"操作失败"));
                continue;
            }
            if(value.has("localOnly")){
                personalQueue.remove();
                personalRetries=0;
                finishPersonal(next.action(),value,true);
                continue;
            }
            personalSent=value;
            personalSentAt=now();
            personalAcknowledged=personalReceived=false;
            personalTimeouts=0;
            return;
        }
    }
    private void personalCommit(JsonObject value){
        if(personalSent==null||!session.equals(value.get("session").getAsString())
            ||personalSent.get("clientTransaction").getAsLong()!=value.get("sequence").getAsLong())return;
        personalAcknowledged=true;
        personalReceived|=value.get("received").getAsBoolean();
        personalRetire();
    }
    private void personalRetire(){
        if(personalSent==null||!personalAcknowledged||!profile.has("personalSequence")
            ||profile.get("personalSequence").getAsLong()<personalSent.get("clientTransaction").getAsLong())return;
        if(!personalReceived&&personalSent.has("retryStorage")&&personalRetries++<3){
            personalSent=null;
        }else completePersonal(personalReceived);
        personalNext();
    }
    private void checkPersonalTimeout(){
        if(!ready||personalSent==null||now()-personalSentAt<PERSONAL_TIMEOUT_MS)return;
        if(++personalTimeouts<PERSONAL_TIMEOUT_LIMIT){
            personalSentAt=now();
            try{
                if(!personalAcknowledged)transmit(personalQueue.element().action(),personalSent);
                send("profile");
            }catch(RuntimeException failure){
                com.mojang.logging.LogUtils.getLogger().warn("重试个人事务时发送失败",failure);
            }
            return;
        }
        boolean committed=personalAcknowledged&&personalReceived;
        completePersonal(committed);
        message(committed?"操作已提交，档案同步超时":"操作响应超时，已取消当前请求");
        personalNext();
    }
    private void completePersonal(boolean committed){
        var finished=personalQueue.remove();
        var value=personalSent;
        personalSent=null;
        personalSentAt=0;
        personalAcknowledged=personalReceived=false;
        personalRetries=personalTimeouts=0;
        finishPersonal(finished.action(),value,committed);
    }
    private void finishPersonal(String action,JsonObject value,boolean committed){
        try{
            switch(action){
                case "currency" -> EquipmentActions.currencyFinished(value,committed);
                case "enhance" -> EquipmentActions.enhanceFinished(value,committed);
                case "initializeEquipment" -> EquipmentActions.initializeFinished(value,committed);
                case "shopPurchase" -> ShopClient.finished(value,committed);
                case "sell" -> AutoSellClient.finished(value);
            }
            TutorialClient.committed(action,value,committed);
        }catch(RuntimeException failure){
            message(Objects.toString(failure.getMessage(),"操作失败"));
        }
    }
    private void transmit(String action,JsonObject value){
        if(ready&&!action.equals("playerStats"))cn.yumis.fight.modules.character.api.client.CareerProfilesClient.context(value);
        if(value.has("operation")&&value.has("runtime")){
            long operation=value.get("operation").getAsLong();
            if(!reportedCheckpoints.containsKey(operation)){resources.sequence=++runtimeSequence;value.getAsJsonObject("runtime").getAsJsonObject("state").addProperty("sequence",resources.sequence);}

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
        j.addProperty("kind",kind);j.addProperty("session",session);j.addProperty("generation",own.generation);
        j.addProperty("rulesRevision",EquipmentDefinitions.current().revision);
        j.addProperty("operation",UUID.randomUUID().toString());
        cn.yumis.fight.modules.character.api.client.CareerProfilesClient.context(j);
        return j;
    }
    public void progress(String kind,String key,Object value){
        upload();
        JsonObject j=operation(kind);
        j.add(key,JSON.toJsonTree(value));
        send("progress",j);
    }
    public void clear(){
        TowerClient.clear();
        DungeonEntryTitle.clear();
        cn.yumis.fight.modules.dungeons.client.ElainaBossClient.clear();
        cn.yumis.fight.modules.arkguard.client.ArkGuardClient.clear();
        personalQueue.clear();personalSequence=personalSentAt=0;personalSent=null;personalAcknowledged=personalReceived=false;personalRetries=personalTimeouts=0;
        lastUpload=lastUploadCheck=lastStatsCheck=0;uploadDeferred=false;
        sentStats="";EquipmentActions.clear();cn.yumis.fight.modules.character.client.CharacterClient.clear();cn.yumis.fight.modules.professions.client.ProfessionClient.clear();cn.yumis.fight.modules.economy.client.EconomyActions.clear();cn.yumis.fight.modules.dungeons.client.DungeonActions.clear();
        cn.yumis.fight.modules.economy.client.JarClient.clear();ShopClient.clear();AutoSellClient.clear();cn.yumis.fight.modules.npc.client.NpcClient.clear();
        cn.yumis.fight.modules.quests.client.TutorialClient.clear();
        cn.yumis.fight.modules.dungeons.client.WaystoneClient.clear();
        cn.yumis.fight.modules.character.api.client.CareerProfilesClient.clear();
        cn.yumis.fight.modules.party.client.PartyInvitations.clear();combatSettings=cn.yumis.fight.modules.combat.api.CombatSettings.DEFAULT;combatExperienceMultiplier=RewardSettings.DEFAULT.combatExperienceMultiplier();
        cn.yumis.fight.modules.combat.api.client.DamageAnalysis.clear();
        targetHud.clear();
        gold.clear();viewSync.clear();checkpoint=null;checkpointGeneration=-1;reportedCheckpoints.clear();pendingRuntime=null;pendingRuntimeState=null;runtimeSequence=0;minimumRuntimeIssuedAt=0;lastResourceServerTime=lastClockArrival=Long.MIN_VALUE;definitionRetries=0;
        cn.yumis.fight.modules.skills.api.client.TrainingCombat.clear();
        cn.yumis.fight.modules.talents.api.client.TalentCombat.clear();
        ready=false;
        damageMeter=new JsonObject();
        trainingMeter=new JsonObject();
        IndustrialVisuals.clear();
        session="";
        sentBagOption="";
        targets.clear();
        targetArrivedAt.clear();
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
            if(j.has("serverNow")&&(action.equals("ack")&&j.has("receipt")&&j.getAsJsonObject("receipt").get("accepted").getAsBoolean()))lastResourceServerTime=Math.max(lastResourceServerTime,j.get("serverNow").getAsLong());
        }catch(Exception error){
            message("消息处理失败："+error.getMessage());
            if((action.equals("profile")||action.equals("definitionPart"))&&definitionRetries<3&&now()-lastDefinitionRetry>=1000){lastDefinitionRetry=now();definitionRetries++;viewSync.retryDefinitions();var request=new JsonObject();request.addProperty("definitions",true);send("profile",request);}
        }
    }
    private final cn.yumis.fight.architecture.CommandRouter<Void,JsonObject> messages=new cn.yumis.fight.architecture.CommandRouter<>();
    private void registerMessages(){
        messages.register("inventory","personalCommit",(context,j)->personalCommit(j));
        messages.register("equipment","equipmentArrival",(context,j)->cn.yumis.fight.modules.equipment.client.EquipmentActions.tick());
        messages.register("equipment","equipmentBag",(context,j)->cn.yumis.fight.modules.equipment.client.BagSnapshotClient.receive(j));
        messages.register("equipment","equipmentCrafted",(context,j)->{if(ready&&session.equals(j.get("session").getAsString()))cn.yumis.fight.modules.quests.client.TutorialClient.crafted();});
        messages.register("npc","tutorialCrystalPickup",(context,j)->{
            if(ready&&session.equals(j.get("session").getAsString()))cn.yumis.fight.modules.quests.client.TutorialClient.crystalPickedUp();
        });
        messages.register("dungeons","waystoneReturn",(context,j)->cn.yumis.fight.modules.dungeons.client.DungeonActions.receiveReturn(j));
        messages.register("economy","killReward",(context,j)->{
            var fact=JSON.fromJson(j,cn.yumis.fight.modules.economy.api.RewardRules.Context.class);
            var player=Minecraft.getInstance().player;
            boolean cardKill=ready&&TowerClient.battle()!=null&&player!=null&&player.getUUID().equals(fact.killer())
                &&fact.target()!=null&&fact.target().rewardsEligible()&&fact.ritual()==null
                &&own.instance.equals(fact.instance())&&player.level().dimension().identifier().toString().equals(fact.dimension())
                &&!j.has(cn.yumis.fight.modules.economy.api.RewardRules.NO_BONUS_EFFECTS);
            boolean first=cn.yumis.fight.modules.economy.client.EconomyActions.reward(j,cardKill&&cn.yumis.fight.modules.dungeons.api.TowerBattleCard.COINS.held(stats));
            if(first&&cardKill)cn.yumis.fight.modules.skills.client.TowerSkillCombat.killed(fact.target(),
                new net.minecraft.world.phys.Vec3(fact.x(),fact.y(),fact.z()));
            cn.yumis.fight.modules.quests.client.TutorialClient.killObserved(j);
        });
        messages.register("economy","chestOpened",(context,j)->cn.yumis.fight.modules.quests.client.TutorialClient.chestOpened());
        messages.register("professions","professionApplied",(context,j)->cn.yumis.fight.modules.professions.client.ProfessionClient.applied(j));
        messages.register("professions","professionEvent",(context,j)->cn.yumis.fight.modules.professions.client.ProfessionClient.event(j));
        cn.yumis.fight.modules.industry.client.IndustryClientMessages.register(messages);
        cn.yumis.fight.modules.inventory.client.InventoryClientMessages.register(messages);
        cn.yumis.fight.modules.economy.client.EconomyClientMessages.register(messages);
        cn.yumis.fight.modules.skills.client.SkillsClientMessages.register(messages);
        cn.yumis.fight.modules.dungeons.client.DungeonClientMessages.register(messages,SkillController.INSTANCE::boss,SkillController.INSTANCE::cancelBoss);
        cn.yumis.fight.modules.arkguard.client.ArkGuardClient.register(messages);
        messages.register("dungeons","treasureWeapon",(context,j)->cn.yumis.fight.modules.dungeons.client.TreasureWeaponVisuals.receive(j));
        messages.register("dungeons","waystoneTable",(context,j)->cn.yumis.fight.modules.dungeons.client.WaystoneClient.receive(j));
        messages.register("dungeons","waystoneStarted",(context,j)->cn.yumis.fight.modules.dungeons.client.WaystoneClient.started());
        messages.register("dungeons","gblAttack",(context,j)->{cn.yumis.fight.modules.dungeons.client.GblVisuals.receive(j);cn.yumis.fight.modules.dungeons.client.EncounterClientContacts.receive(cn.yumis.fight.modules.dungeons.client.EncounterClientContacts.Family.GBL,j,SkillController.INSTANCE::boss);});
        cn.yumis.fight.modules.character.api.client.CareerControls.register(messages);
        messages.register("dungeons","damageMeter",(context,j)->{
damageMeter=j;
        });
        messages.register("dungeons","trainingMeter",(context,j)->{trainingMeter=j;});
        messages.register("presentation","profile",(context,j)->{if(!viewSync.personal(j,now(),this::applyProfile))send("profile");});
        messages.register("presentation","definitionPart",(context,j)->viewSync.definition(j,value->{
            // An integrated server already owns this shared static store. A delayed wire copy must never roll it back.
            if(Minecraft.getInstance().getSingleplayerServer()==null)ApplicationRules.receive(value);
        },this::applyProfile));
        messages.register("dungeons","context",(context,j)->{
Target t=JSON.fromJson(j,Target.class);
                ContextRequest request=contextRequests.get(t.entityId);
                if(request!=null&&request.target().equals(t.id))contextRequests.remove(t.entityId);
                Target old=targets.get(t.id);
                if(old==null||t.life.generation>old.life.generation||t.life.generation==old.life.generation&&t.life.revision>=old.life.revision){
                    targets.put(t.id,t);
                    targetArrivedAt.put(t.id,now());
                    if(old!=null&&old.life.generation!=t.life.generation)prediction.invalidate(t.id);
                    SkillController.INSTANCE.contextArrived(t.id);
                }
        });
        messages.register("combat","health",(context,j)->{
Life l=JSON.fromJson(j,Life.class);
                if(l.target.equals(own.target)){
                    if(l.generation==own.generation&&l.revision>=own.revision){l.elemental=prediction.elemental(l.elemental);own=l;own.dead=prediction.predicted(own)<=0;}
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
cn.yumis.fight.modules.professions.client.ProfessionClient.decorate(j);professions=j;
                if(Minecraft.getInstance().gui.screen() instanceof FightScreen screen)screen.refresh();
        });
        messages.register("combat","healing",(context,j)->{
if(ready&&!own.dead&&(!j.has("generation")||j.get("generation").getAsLong()==own.generation)&&(!j.has("session")||j.get("session").getAsString().equals(session)))report(own,j.get("points").getAsDouble()*.05,true,stats.get(Stat.HEALTH),0x8ef5ad,false,null);
        });
        messages.register("combat","ailmentsClear",(context,j)->{
            if(j.has("target")&&j.has("kind")){
                String key=j.get("target").getAsString()+"/"+j.get("owner").getAsString()+"/"+j.get("kind").getAsString();
                String incomingEffect=j.has("change")&&j.getAsJsonObject("change").has("effect")?j.getAsJsonObject("change").get("effect").getAsString():j.has("effect")?j.get("effect").getAsString():"";
                JsonObject local=ailments.get(key);
                if(local!=null&&!incomingEffect.isEmpty()){
                    String localEffect=local.has("change")&&local.getAsJsonObject("change").has("effect")?local.getAsJsonObject("change").get("effect").getAsString():local.has("effect")?local.get("effect").getAsString():"";
                    long localExpires=local.has("expires")?local.get("expires").getAsLong():0L;
                    if(!localEffect.isEmpty()&&!localEffect.equals(incomingEffect)&&localExpires>serverNow())return;
                }
                ailments.remove(key);
            }
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
                if(!j.has("careerProfiles")||!j.getAsJsonObject("careerProfiles").has("schema")||j.getAsJsonObject("careerProfiles").get("schema").getAsInt()!=1){message("职业配置版本不一致，请同时更新客户端和服务端");return;}
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
                if(same&&j.getAsJsonObject("careerProfiles").get("revision").getAsLong()<cn.yumis.fight.modules.character.api.client.CareerProfilesClient.revision())return;
                if(same&&life.generation<own.generation)return;
                boolean newerLife=!same||life.generation>own.generation;
                boolean changingProfile=same&&!cn.yumis.fight.modules.character.api.client.CareerProfilesClient.active().equals(j.getAsJsonObject("careerProfiles").get("activeId").getAsString());
                boolean inheritResources=changingProfile&&ready&&!newerLife;
                double mpRatio=1d,shieldRatio=1d;
                if(inheritResources){
                    var previous=SkillController.INSTANCE.snapshot();
                    mpRatio=Math.clamp(resources.mp/Math.max(1d,previous.get(Stat.MP)),0d,1d);
                    shieldRatio=Math.clamp(resources.shield/Math.max(1d,previous.get(Stat.SHIELD)),0d,1d);
                }
                var localResources=inheritResources?JSON.fromJson(JSON.toJson(resources),cn.yumis.fight.modules.combat.api.Runtime.class):null;
                boolean freshResources=newerLife||runtime.controlEpoch>=resources.controlEpoch&&serverNow>=lastResourceServerTime;
                boolean reset=newerLife||freshResources&&runtime.controlEpoch>resources.controlEpoch;
                if(receivedAt>=lastClockArrival){serverOffset=serverNow-receivedAt;lastClockArrival=receivedAt;}
                if(!incoming.equals(session))syncedSources=new cn.yumis.fight.modules.attributes.api.AttributeSources();
                if(reset){
                    SkillController.INSTANCE.cancel(false);
                    SkillController.INSTANCE.clearTransient();reportedCheckpoints.clear();
                    if(!changingProfile||newerLife||!life.dimension.equals(own.dimension))ailments.clear();
                    resources=inheritResources?localResources:runtime;
                    if(newerLife||!life.dimension.equals(own.dimension))resources.equipmentBuffs.remove("wall");
                    if(inheritResources){resources.controlEpoch=runtime.controlEpoch;resources.recordedAt=now();}else translate(resources,receivedAt-serverNow);
                    prediction.reset(!incoming.equals(session));
                }
                if(newerLife||life.revision>=own.revision)own=life;
                session=incoming;
                profile=j;
                cn.yumis.fight.modules.quests.client.TutorialClient.receive(j);
                cn.yumis.fight.modules.character.client.CharacterClient.receive(j);cn.yumis.fight.modules.professions.client.ProfessionClient.receive(j);
                professions=new JsonObject();professions.add("professions",j.get("professionState").deepCopy());professions.add("gold",j.get("gold"));cn.yumis.fight.modules.professions.client.ProfessionClient.decorate(professions);
                progress=JSON.fromJson(j.get("progress"),Progress.class);
                boolean selectedProfileChanged=cn.yumis.fight.modules.character.api.client.CareerProfilesClient.receive(j.getAsJsonObject("careerProfiles"));
                if(selectedProfileChanged){Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.EXPERIENCE_ORB_PICKUP,1.0f));cn.yumis.fight.modules.skills.client.CareerClient.INSTANCE.clear();cn.yumis.fight.modules.skills.client.DemonHunterClient.clear();}
                combatSettings=JSON.fromJson(j.get("combatSettings"),cn.yumis.fight.modules.combat.api.CombatSettings.class);
                combatExperienceMultiplier=j.get("combatExperienceMultiplier").getAsDouble();
                if(freshResources)lastResourceServerTime=Math.max(lastResourceServerTime,serverNow);
                party=!j.has("party")||j.get("party").isJsonNull()?null:JSON.fromJson(j.get("party"),Team.class);
                cn.yumis.fight.modules.party.client.PartyInvitations.receive(j.getAsJsonArray("partyInvitations"));
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
                cn.yumis.fight.modules.inventory.client.InventoryClientMessages.applyEquipment(j.get("equipmentState").getAsString());
                var equipped=EquipmentStats.build(Minecraft.getInstance().player,progress,cn.yumis.fight.modules.professions.client.AlchemyClient.attributes(syncedSources));
                TowerClient.sync(dungeon,j.get("towerCards"),j.get("towerBattle"),progress,own,resources,
                    ()->cn.yumis.fight.modules.skills.api.TowerSkillTraits.choices(progress,equipped));
                stats=TowerClient.apply(equipped,progress);
                resources.equipment=EquipmentStats.cachedEffects(Minecraft.getInstance().player,progress,cn.yumis.fight.modules.professions.client.AlchemyClient.attributes(syncedSources));
                if(inheritResources){
                    cn.yumis.fight.modules.skills.api.SkillReductions.resources(cn.yumis.fight.modules.skills.api.SkillCatalog.OVERDRIVE.id,resources);
                    cn.yumis.fight.modules.combat.api.EquipmentBuffs.prune(resources,resources.equipment,now(),false);
                    resources.mp=mpRatio*stats.get(Stat.MP);
                    resources.shield=shieldRatio*stats.get(Stat.SHIELD);
                }
                boolean initializeResources=newerLife&&(runtime.mp<0||runtime.shield<0);
                if(newerLife&&runtime.mp<0)resources.mp=stats.get(Stat.MP);if(newerLife&&runtime.shield<0)resources.shield=stats.get(Stat.SHIELD);
                resources.mp=Math.clamp(resources.mp,0,stats.get(Stat.MP));resources.shield=Math.clamp(resources.shield,0,stats.get(Stat.SHIELD));
                if(newerLife&&resources.training)resources.trainingBoundary=resources.mp;
                if(reset){checkpoint=initializeResources||inheritResources?null:cn.yumis.fight.modules.combat.api.RuntimeCheckpoint.capture(resources);checkpointGeneration=own.generation;pendingRuntime=null;pendingRuntimeState=null;}
                minimumRuntimeIssuedAt=Math.max(minimumRuntimeIssuedAt,runtime.recordedAt);runtimeSequence=Math.max(runtimeSequence,runtime.sequence);
                own.dead=prediction.predicted(own)<=0;ready=true;definitionRetries=0;analysisLifecycle();
                if(inheritResources&&!own.dead)uploadNow();

                CloudMovement.screen(Minecraft.getInstance().gui.screen()!=null);
                if(selectedProfileChanged&&!progress.selected())Minecraft.getInstance().gui.setScreen(new FightScreen("skills"));
                else if(Minecraft.getInstance().gui.screen() instanceof FightScreen screen)screen.refresh();
                if(initializeResources)uploadNow();personalRetire();personalNext();
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
    public void upload(){upload(false);}
    public void uploadNow(){upload(true);}
    private void upload(boolean force){
        if(!ready)return;
        long now=now();
        if(!force&&now-lastUpload<250){uploadDeferred=true;return;}
        uploadDeferred=false;
        lastUploadCheck=now;
        var next=cn.yumis.fight.modules.combat.api.RuntimeCheckpoint.capture(resources);
        if(pendingRuntime!=null&&next.equals(pendingRuntimeState)){
            if(now-pendingRuntimeAt>=500){send("runtime",pendingRuntime);pendingRuntimeAt=now;lastUpload=pendingRuntimeAt;}
            return;
        }
        if(pendingRuntime==null&&own.generation==checkpointGeneration&&next.equals(checkpoint))return;
        resources.recordedAt=now;
        JsonObject j=new JsonObject();
        j.addProperty("session",session);
        j.addProperty("generation",own.generation);
        resources.sequence=++runtimeSequence;j.addProperty("checkpoint",runtimeSequence);
        j.addProperty("serverIssuedAt",Math.max(serverNow(),minimumRuntimeIssuedAt+1));
        j.add("state",JSON.toJsonTree(resources));
        send("runtime",j);
        pendingRuntime=j;pendingRuntimeState=next;pendingRuntimeSequence=runtimeSequence;pendingRuntimeAt=now;lastUpload=pendingRuntimeAt;
    }
    public void tick(){
        var mc=Minecraft.getInstance();
        cn.yumis.fight.modules.equipment.client.FabricatorOpening.tick();
        cn.yumis.fight.modules.party.client.PartyInvitations.tick(ready&&mc.player!=null&&!own.dead&&mc.gui.screen()==null&&mc.player.containerMenu==mc.player.inventoryMenu&&now()>=resources.combatUntil&&!SkillController.INSTANCE.locked());
        if(mc.player==null||mc.level==null){
            if(ready)clear();
            return;
        }
        cn.yumis.fight.modules.dungeons.client.DungeonActions.updateSwimSpeed(mc.player,ready?dungeon:null);
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
        // 次元背包自动收纳偏好由本人客户端判定，登录后和每次变更时上报，服务端仅持久化并在拾取时采用。
        String bagOption=cn.yumis.fight.modules.presentation.api.client.ClientSettings.bagAutoStore+"/"+cn.yumis.fight.modules.presentation.api.client.ClientSettings.bagStoreOnlyWhenFull;
        if(!bagOption.equals(sentBagOption)){
            sentBagOption=bagOption;
            JsonObject option=new JsonObject();
            option.addProperty("autoStore",cn.yumis.fight.modules.presentation.api.client.ClientSettings.bagAutoStore);
            option.addProperty("onlyWhenFull",cn.yumis.fight.modules.presentation.api.client.ClientSettings.bagStoreOnlyWhenFull);
            send("bagOption",option);
        }
        // Client-created effects have no server removal packet when their timer expires.
        for(var effect:List.copyOf(mc.player.getActiveEffects())){
            if(effect.endsWithin(0))mc.player.removeEffect(effect.getEffect());
        }
        cn.yumis.fight.modules.equipment.client.EquipmentActions.tick();cn.yumis.fight.modules.professions.client.ProfessionClient.tick();
        stats=TowerClient.apply(EquipmentStats.build(mc.player,progress,cn.yumis.fight.modules.professions.client.AlchemyClient.attributes(syncedSources)),progress);
        resources.equipment=EquipmentStats.cachedEffects(mc.player,progress,cn.yumis.fight.modules.professions.client.AlchemyClient.attributes(syncedSources));
        if(now()-lastStatsCheck>=250){
            lastStatsCheck=now();
            var ownStats=JSON.toJsonTree(SkillController.INSTANCE.snapshot());
            String nextStats=ownStats.toString();
            if(!nextStats.equals(sentStats)){
                sentStats=nextStats;
                JsonObject state=new JsonObject();
                state.addProperty("session",session);
                state.add("stats",ownStats);
                send("playerStats",state);
            }
        }
        cn.yumis.fight.modules.combat.api.EquipmentBuffs.prune(resources,resources.equipment,now(),own.dead);
        EquipmentStats.applyMovement(mc.player,TowerClient.battle()!=null?SkillController.INSTANCE.snapshot():stats);
        resources.mp=Math.clamp(resources.mp,0,stats.get(Stat.MP));
        resources.shield=Math.clamp(resources.shield,0,stats.get(Stat.SHIELD));
        boolean abyss=profile.has("dungeonAbyss")&&profile.get("dungeonAbyss").getAsBoolean();
        if(DungeonEntryTitle.update(dungeon,own.instance,abyss)&&mc.gui.screen() instanceof FightScreen)mc.gui.setScreen(null);
        TowerClient.tick(progress,own,this::recoverTowerFloor,SkillController.INSTANCE::towerFire,SkillController.INSTANCE::towerFrost,
            ()->cn.yumis.fight.modules.skills.api.TowerSkillTraits.choices(progress,stats));
        String dim=mc.level.dimension().identifier().toString();
        if(!dimension.equals(dim)){
            dimension=dim;
            targets.entrySet().removeIf(entry->!entry.getValue().dimension.equals(dim));
            targetArrivedAt.keySet().retainAll(targets.keySet());
            contextRequests.clear();cn.yumis.fight.modules.skills.api.client.TrainingCombat.clear();
            prediction.clearPending();
            ailments.clear();
            SkillController.INSTANCE.cancel(false);
            SkillController.INSTANCE.clearTransient();reportedCheckpoints.clear();
            Effects.clear();
        }
        analysisLifecycle();
        updateTargetHud();
        Set<UUID> referenced=new HashSet<>(prediction.referencedTargets());
        referenced.addAll(SkillController.INSTANCE.referencedTargets());
        if(targetHud.target()!=null)referenced.add(targetHud.target());
        long currentNow=now();
        targets.entrySet().removeIf(entry->{
            if(referenced.contains(entry.getKey()))return false;
            Target target=entry.getValue();
            if(!target.dimension.equals(dim)){targetArrivedAt.remove(entry.getKey());return true;}
            var entity=mc.level.getEntity(target.entityId);
            if(entity==null){
                if(currentNow-targetArrivedAt.getOrDefault(entry.getKey(),0L)<2000)return false;
                targetArrivedAt.remove(entry.getKey());
                return true;
            }
            if(!entity.getUUID().equals(entry.getKey())){targetArrivedAt.remove(entry.getKey());return true;}
            return false;
        });
        contextRequests.entrySet().removeIf(entry->{var entity=mc.level.getEntity(entry.getKey());return entity==null||!entity.getUUID().equals(entry.getValue().target());});
        prediction.tick();
        if(uploadDeferred&&now()-lastUpload>=250||now()-lastUploadCheck>=500)upload();
        checkPersonalTimeout();
    }
    private void recoverTowerFloor() {
        var player = Minecraft.getInstance().player;
        CombatEffect.recoverNativeState(player);
        String owner = own.target.toString();
        ailments.values().removeIf(effect -> owner.equals(effect.get("target").getAsString()));
        resources.cooldowns.clear();
        CloudMovement.clear();
        var skills = SkillController.INSTANCE;
        skills.prepareFloorRecovery();
        var maximums = skills.snapshot();
        resources.mp = maximums.get(Stat.MP);
        resources.shield = maximums.get(Stat.SHIELD);
        prediction.recover(maximums.get(Stat.HEALTH));
    }
    private void trackTargetDamage(JsonObject packet){
        if(packet.get("heal").getAsBoolean()||packet.get("fraction").getAsDouble()<=0)return;
        UUID id=UUID.fromString(packet.get("target").getAsString());if(id.equals(own.target))return;
        Target target=targets.get(id);var mc=Minecraft.getInstance();
        if(target==null||mc.level==null||target.damageImmune||target.life.generation!=packet.get("generation").getAsLong())return;
        var entity=mc.level.getEntity(target.entityId);
        if(entity instanceof net.minecraft.world.entity.LivingEntity living&&id.equals(entity.getUUID())&&!(living instanceof net.minecraft.world.entity.player.Player)&&cn.yumis.fight.modules.npc.api.NpcInteractionComponent.query(living)==null)
            targetHud.damage(target.life,living.getName().getString(),living.position(),now());
        else if(targetHud.matches(target.life))targetHud.damage(target.life,null,null,now());
    }
    private void updateTargetHud(){
        if(targetHud.target()==null)return;
        var mc=Minecraft.getInstance();Target target=targets.get(targetHud.target());
        if(target==null||!target.dimension.equals(dimension)||!target.instance.equals(own.instance)){targetHud.clear();return;}
        var entity=mc.level.getEntity(target.entityId);boolean loaded=entity instanceof net.minecraft.world.entity.LivingEntity&&target.id.equals(entity.getUUID());
        targetHud.update(target.life,loaded?entity.getName().getString():null,loaded?entity.position():null,mc.player.position(),now(),target.boss());
    }
    public Target context(net.minecraft.world.entity.LivingEntity entity){
        Target t=targets.get(entity.getUUID());
        if(t!=null&&(t.entityId!=entity.getId()||!t.dimension.equals(entity.level().dimension().identifier().toString())))t=null;
        ContextRequest request=contextRequests.get(entity.getId());
        if(t==null&&(request==null||!request.target().equals(entity.getUUID())||now()-request.sent()>250)){
            contextRequests.put(entity.getId(),new ContextRequest(entity.getUUID(),now()));
            JsonObject j=new JsonObject();
            j.addProperty("entity",entity.getId());
            send("context",j);
        }
        return t;
    }
    private void analysisLifecycle(){
        var mc=Minecraft.getInstance();if(mc.level==null)return;
        boolean changed=cn.yumis.fight.modules.combat.api.client.DamageAnalysis.lifecycle(session,mc.level.dimension().identifier().toString(),own.instance,own.dead);
        if(changed)targetHud.clear();
        if(changed&&mc.gui.screen() instanceof FightScreen screen&&screen.page>=0&&cn.yumis.fight.modules.presentation.api.PlayerPages.at(screen.page).id().equals("damage_analysis"))mc.gui.setScreen(null);
    }
    public double predicted(Life life){
        double q=prediction.predicted(life);
        Target target=targets.get(life.target);
        return target==null||life.dead?q:Math.max(target.healthFloor,q);
    }
    public Stats weaponStats(ItemStack weapon){
        return TowerClient.apply(EquipmentStats.build(Minecraft.getInstance().player,progress,cn.yumis.fight.modules.professions.client.AlchemyClient.attributes(syncedSources),weapon),progress);
    }
    public void report(Life life,double fraction,boolean heal,double maximum,int color,boolean critical,JsonObject extras){
        boolean healthAdjustment=extras!=null&&extras.has("resourceCost")&&extras.get("resourceCost").getAsBoolean();
        if(heal&&!healthAdjustment&&life.target.equals(own.target))fraction*=cn.yumis.fight.modules.combat.api.CombatEffect.healingScale(Minecraft.getInstance().player);
        prediction.report(life,fraction,heal,maximum,color,critical,extras);
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
