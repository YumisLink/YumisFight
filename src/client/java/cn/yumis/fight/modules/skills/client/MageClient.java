package cn.yumis.fight.modules.skills.client;
import cn.yumis.fight.modules.skills.api.*;
import cn.yumis.fight.modules.skills.api.client.*;
import cn.yumis.fight.modules.attributes.api.*;
import cn.yumis.fight.modules.combat.api.CombatRules;
import cn.yumis.fight.modules.equipment.api.EquipmentEffects;
import cn.yumis.fight.modules.presentation.api.client.ClientUi;
import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import java.util.*;

/** Only the owner consumes action tokens; all attacks use the existing shared hit calculator. */
public final class MageClient {
    public static final MageClient INSTANCE=new MageClient();
    private static final Gson JSON=new Gson();
    public long summonUntil;
    public int summonRank;
    public SkillPreview.Damage summonedMelee,summonedStone,summonedTriple;
    private String session="";private long generation=-1;
    private final List<JsonObject> pending=new ArrayList<>();private final Set<String> received=new HashSet<>();
    private final Set<String> roots=new HashSet<>();private final Random random=new Random();
    private MageClient(){}
    private boolean valid(JsonObject j){var state=SkillsClient.current();if(!session.equals(state.session())||generation!=state.own().generation){clear();session=state.session();generation=state.own().generation;}return j.get("session").getAsString().equals(state.session())&&j.get("generation").getAsLong()==state.own().generation&&!state.own().dead;}
    public void clear(){for(String root:roots){TrainingCombat.release(root,"mage");TrainingCombat.actionFinished(root);}for(var j:pending)TrainingCombat.release(j.get("source").getAsString(),j.get("id").getAsString());pending.clear();received.clear();roots.clear();summonUntil=0;summonRank=0;summonedMelee=summonedStone=summonedTriple=null;}
    public void state(JsonObject j){if(!valid(j))return;summonUntil=j.get("until").getAsLong();if(j.has("damage")){summonRank=j.get("rank").getAsInt();var damage=j.getAsJsonObject("damage");summonedMelee=JSON.fromJson(damage.get("melee"),SkillPreview.Damage.class);summonedStone=JSON.fromJson(damage.get("stone"),SkillPreview.Damage.class);summonedTriple=JSON.fromJson(damage.get("triple"),SkillPreview.Damage.class);}if(summonUntil==0){summonRank=0;summonedMelee=summonedStone=summonedTriple=null;String source=j.get("source").getAsString();TrainingCombat.release(source,"mage");TrainingCombat.actionFinished(source);roots.remove(source);}}
    public void attack(JsonObject j){
        if(!valid(j)||!received.add(j.get("id").getAsString()))return;if(received.size()>4096)received.clear();pending.add(j);
        String source=j.get("source").getAsString();if(roots.add(source))TrainingCombat.begin(source,SkillCatalog.require(j.get("skill").getAsString()).title);
        long now=ClientUi.now();TrainingCombat.hold(source,"mage",now+Math.max(0,j.get("sourceExpires").getAsLong()-SkillsClient.current().serverNow()));TrainingCombat.hold(source,j.get("id").getAsString(),now+6000);
    }
    public void end(JsonObject j){String source=j.get("source").getAsString();TrainingCombat.release(source,"mage");TrainingCombat.actionFinished(source);roots.remove(source);}
    public static boolean decorate(SkillCast source,JsonObject extra){if(source==null||source.mageAttack.isBlank())return false;extra.addProperty("mageAttack",source.mageAttack);extra.addProperty("mageSource",source.id);return true;}
    public void incoming(JsonObject j){
        if(!valid(j))return;Stats stats=JSON.fromJson(j.get("stats"),Stats.class);
        double damage=j.has("environmentFraction")?stats.get(Stat.HEALTH)*j.get("environmentFraction").getAsDouble():HerderRules.incoming(stats,j.get("points").getAsDouble(),j.get("magic").getAsBoolean(),j.get("element").getAsInt(),j.get("attackerLevel").getAsInt(),j.get("ownerLevel").getAsInt(),j.get("protection").getAsDouble());
        boolean dodge=j.get("direct").getAsBoolean()&&CombatRules.evades(stats.get(Stat.EVASION),random::nextDouble);var request=ClientUi.current().operation("herderReceive");request.add("root",j.get("root"));request.add("source",j.get("source"));request.addProperty("damage",dodge?0:damage);request.addProperty("dodge",dodge);ClientUi.current().send("herderReceive",request);
    }
    public void tick(SkillBehaviors.Context context){
        var state=SkillsClient.current();var mc=Minecraft.getInstance();if(!session.equals(state.session())||generation!=state.own().generation){clear();session=state.session();generation=state.own().generation;}
        if(state.own().dead){clear();return;}if(mc.level==null)return;
        for(var j:new ArrayList<>(pending)){
            String source=j.get("source").getAsString(),id=j.get("id").getAsString();
            if(!valid(j)||state.serverNow()>j.get("expires").getAsLong()){pending.remove(j);TrainingCombat.release(source,id);continue;}
            var entity=mc.level.getEntity(j.get("entity").getAsInt());if(!(entity instanceof LivingEntity target)||!target.getStringUUID().equals(j.get("target").getAsString()))continue;
            var targetState=state.context(target);if(targetState==null)continue;if(targetState.life.generation!=j.get("targetGeneration").getAsLong()){pending.remove(j);TrainingCombat.release(source,id);continue;}
            var cast=new SkillCast();cast.id=source;cast.mageAttack=id;cast.skill=SkillCatalog.require(j.get("skill").getAsString());cast.stats=JSON.fromJson(j.get("stats"),Stats.class);cast.equipment=JSON.fromJson(j.get("effects"),EquipmentEffects.Snapshot.class);var values=new LinkedHashMap<String,Double>();j.getAsJsonObject("parameters").entrySet().forEach(e->values.put(e.getKey(),e.getValue().getAsDouble()));cast.parameters=new SkillParameters.Snapshot(cast.skill,j.get("rank").getAsInt(),values);cast.origin=cast.center=target.position();cast.direction=mc.player.getLookAngle();
            if(context.hit(cast,target,j.get("coefficient").getAsDouble())){pending.remove(j);TrainingCombat.release(source,id);}
        }
    }
}
