package cn.yumis.fight.modules.professions.client;

import cn.yumis.fight.architecture.CommandRouter;
import cn.yumis.fight.modules.professions.api.*;
import cn.yumis.fight.modules.professions.platform.*;
import cn.yumis.fight.modules.inventory.api.InventoryEdits;
import cn.yumis.fight.modules.presentation.api.client.ClientUi;
import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.*;
import net.minecraft.world.item.ItemStack;
import java.math.BigInteger;
import java.util.*;
import java.util.function.*;

/** Owner-side profession rewards, consumables and rod movement. */
public final class ProfessionClient {
    private static final Gson JSON=new Gson();private static final Random RANDOM=new Random();
    private static Supplier<JsonObject> view;private static Consumer<Double> healing;private static BooleanSupplier alive;
    private static final Set<String> effects=new LinkedHashSet<>();private static final Set<String> applied=new HashSet<>();
    private static final Identifier SWIM=Identifier.fromNamespaceAndPath("yumis_dungeon_and_fight","styx_swim");
    private static String session="",lastEffects="";private static long nextSave,effectsSequence;private static int lastTick=-1;private static boolean floating;
    private ProfessionClient(){}
    public static Profession get(String id){
        var result=new Profession();var state=view.get();if(!state.has("professions")||!state.getAsJsonObject("professions").has(id))return result;var row=state.getAsJsonObject("professions").getAsJsonObject(id);result.experience=new BigInteger(row.get("experience").getAsString());result.coins=new BigInteger(row.get("coins").getAsString());result.title=row.get("title").getAsString();return result;
    }
    public static void install(CommandRouter<Void,JsonObject> router,Supplier<JsonObject> snapshot,Consumer<Double> heal,BooleanSupplier living){
        view=snapshot;healing=heal;alive=living;router.register("professions","professionResult",(ignored,j)->prepare(j));router.register("professions","professionUse",(ignored,j)->consume(j));
        GraphiteRod.HANDLER=(player,rod)->fish("lava",player,rod);FarerRod.HANDLER=(player,rod)->fish("void",player,rod);
        ProfessionConsumable.CLIENT_USE=(player,hand,effect,duration,amplifier)->{var j=ClientUi.current().operation("professionUse");j.addProperty("hand",hand.name());j.addProperty("effect",BuiltInRegistries.MOB_EFFECT.getKey(effect.value()).toString());j.addProperty("duration",duration);j.addProperty("amplifier",amplifier);ClientUi.current().send("professionUse",j);};
    }
    private static JsonObject raw(String kind){var j=new JsonObject();j.addProperty("kind",kind);j.addProperty("event",UUID.randomUUID().toString());return j;}
    private static boolean fish(String kind,net.minecraft.world.entity.player.Player player,ItemStack rod){if(!ClientUi.current().ready()||!alive.getAsBoolean())return false;var value=raw(kind);int slot=rod==player.getOffhandItem()?40:player.getInventory().getSelectedSlot();value.addProperty("rodSlot",slot);value.add("rodSource",InventoryEdits.encode(player,rod));event(value);return true;}
    public static void event(JsonObject event){var player=Minecraft.getInstance().player;if(player==null)return;if(Set.of("fishing","lava","void").contains(event.get("kind").getAsString())){if(!event.has("hands")){var hands=new JsonArray();hands.add(InventoryEdits.encode(player,player.getMainHandItem()));hands.add(InventoryEdits.encode(player,player.getOffhandItem()));event.add("hands",hands);}event.add("fishingContext",JSON.toJsonTree(ProfessionRewards.fishingContext(player)));}var j=ClientUi.current().operation("professionResult");j.add("event",event.deepCopy());if(!Set.of("lava","void").contains(event.get("kind").getAsString()))j.addProperty("retryStorage",true);ClientUi.current().send("professionResult",j);}
    private static void prepare(JsonObject j){
        var player=Minecraft.getInstance().player;
        if(j.has("professions")){for(var entry:j.getAsJsonObject("professions").entrySet()){var row=entry.getValue().getAsJsonObject();var before=JSON.fromJson(row.get("before"),Profession.class);var after=JSON.fromJson(row.get("after"),Profession.class);var current=get(entry.getKey());var value=get(entry.getKey());value.experience=value.experience.add(after.experience.subtract(before.experience));value.coins=value.coins.add(after.coins.subtract(before.coins));value.grantTitle(entry.getKey());row.add("before",JSON.toJsonTree(current));row.add("after",JSON.toJsonTree(value));}return;}
        var event=j.getAsJsonObject("event");var result=ProfessionRewards.decide(player,event,ProfessionClient::get,RANDOM);if(result.professions().isEmpty()&&result.drops().isEmpty()){j.addProperty("localOnly",true);return;}var changes=new JsonObject();
        for(var entry:result.professions().entrySet()){var row=new JsonObject();row.add("before",JSON.toJsonTree(get(entry.getKey())));row.add("after",JSON.toJsonTree(entry.getValue()));changes.add(entry.getKey(),row);}j.add("professions",changes);var drops=new JsonArray();for(var item:result.drops())drops.add(InventoryEdits.encode(player,item));j.add("drops",drops);if(event.has("dropAt"))j.add("dropAt",event.get("dropAt").deepCopy());
        String kind=event.get("kind").getAsString();if(kind.equals("lava")||kind.equals("void")){int slot=event.get("rodSlot").getAsInt();var source=player.getInventory().getItem(slot);if(!(source.getItem() instanceof net.minecraft.world.item.FishingRodItem)||!InventoryEdits.encode(player,source).equals(event.get("rodSource")))throw new IllegalArgumentException("垂钓鱼竿已变化");var after=source.copy();int damage=after.getDamageValue()+1;if(damage>=after.getMaxDamage())after.shrink(1);else after.setDamageValue(damage);var edits=new JsonArray();edits.add(InventoryEdits.change(player,"inventory",slot,source,after));j.add("inventoryChanges",edits);}
    }
    private static void consume(JsonObject j){
        var player=Minecraft.getInstance().player;var hand=net.minecraft.world.InteractionHand.valueOf(j.get("hand").getAsString());int slot=hand==net.minecraft.world.InteractionHand.MAIN_HAND?player.getInventory().getSelectedSlot():40;var source=player.getItemInHand(hand);
        if(!(source.getItem() instanceof ProfessionConsumable))throw new IllegalArgumentException("食物或药剂已变化");var after=source.copy();after.shrink(1);var edits=new JsonArray();edits.add(InventoryEdits.change(player,"inventory",slot,source,after));j.add("inventoryChanges",edits);
        int duration=j.get("duration").getAsInt();duration+=(int)Math.round(duration*ProfessionBonuses.potionDuration(get("alchemist").level()));j.addProperty("duration",duration);
        var holder=BuiltInRegistries.MOB_EFFECT.get(Identifier.parse(j.get("effect").getAsString())).orElseThrow();var previous=player.getEffect(holder);var chosen=new MobEffectInstance(holder,duration,j.get("amplifier").getAsInt());if(previous!=null){var merged=new MobEffectInstance(previous);merged.update(chosen);chosen=merged;}
        if(holder.value()==MobEffects.SATURATION.value()){var food=new net.minecraft.world.food.FoodData();food.setFoodLevel(player.getFoodData().getFoodLevel());food.setSaturation(player.getFoodData().getSaturationLevel());food.eat(j.get("amplifier").getAsInt()+1,1);j.addProperty("foodLevel",food.getFoodLevel());j.addProperty("foodSaturation",food.getSaturationLevel());}
        j.add("effectState",encode(chosen));var rows=snapshot();rows.asList().removeIf(row->MobEffectInstance.CODEC.parse(player.registryAccess().createSerializationContext(JsonOps.INSTANCE),row).getOrThrow().getEffect().equals(holder));rows.add(encode(chosen));j.add("effects",rows);j.addProperty("effectsSequence",effectsSequence+1);
    }
    private static JsonElement encode(MobEffectInstance effect){return MobEffectInstance.CODEC.encodeStart(Minecraft.getInstance().player.registryAccess().createSerializationContext(JsonOps.INSTANCE),effect).getOrThrow();}
    private static JsonArray snapshot(){var rows=new JsonArray();var player=Minecraft.getInstance().player;for(String id:effects){var holder=BuiltInRegistries.MOB_EFFECT.get(Identifier.parse(id));if(holder.isPresent()){var effect=player.getEffect(holder.get());if(effect!=null)rows.add(encode(effect));}}return rows;}
    public static void decorate(JsonObject state){if(!state.has("professions"))return;for(var entry:state.getAsJsonObject("professions").entrySet()){var row=entry.getValue().getAsJsonObject();row.addProperty("level",ProfessionRules.professionLevel(new BigInteger(row.get("experience").getAsString())));}}
    public static void receive(JsonObject profile){
        String next=profile.get("session").getAsString();if(next.equals(session))return;clear();session=next;
        if(profile.has("professionEffects")){var stored=profile.getAsJsonObject("professionEffects");effectsSequence=stored.get("revision").getAsLong();for(var raw:stored.getAsJsonArray("effects"))apply(raw);}
    }
    public static void applied(JsonObject j){if(!applied.add(j.get("operation").getAsString())||!j.has("effectState"))return;effectsSequence=j.get("effectsSequence").getAsLong();if(j.has("foodLevel")){Minecraft.getInstance().player.getFoodData().setFoodLevel(j.get("foodLevel").getAsInt());Minecraft.getInstance().player.getFoodData().setSaturation(j.get("foodSaturation").getAsFloat());}apply(j.get("effectState"));nextSave=0;}
    private static void apply(JsonElement data){var player=Minecraft.getInstance().player;if(player==null)return;var effect=MobEffectInstance.CODEC.parse(player.registryAccess().createSerializationContext(JsonOps.INSTANCE),data).getOrThrow();effects.add(BuiltInRegistries.MOB_EFFECT.getKey(effect.getEffect().value()).toString());player.addEffect(effect);}
    public static void clear(){GraphiteRod.clearCooldown();effects.clear();applied.clear();session="";lastEffects="";effectsSequence=0;nextSave=0;lastTick=-1;floating=false;}
    public static void tick(){
        var player=Minecraft.getInstance().player;if(player==null||!ClientUi.current().ready())return;
        if(player.tickCount!=lastTick){lastTick=player.tickCount;for(var iterator=effects.iterator();iterator.hasNext();){String id=iterator.next();var holder=BuiltInRegistries.MOB_EFFECT.get(Identifier.parse(id));var effect=holder.isEmpty()?null:player.getEffect(holder.get());if(effect==null){iterator.remove();continue;}if(!alive.getAsBoolean()){player.removeEffect(holder.get());iterator.remove();nextSave=0;continue;}int left=effect.getDuration();if(holder.get().value()==MobEffects.REGENERATION.value()&&left>0&&left%Math.max(1,50>>effect.getAmplifier())==0)healing.accept(1d);}}
        if(ClientUi.now()>=nextSave){nextSave=ClientUi.now()+1000;var rows=snapshot();String value=rows.toString();if(!lastEffects.equals(value)){lastEffects=value;var j=ClientUi.current().operation("professionEffects");j.addProperty("effectsSequence",effectsSequence);j.add("effects",rows);ClientUi.current().send("professionEffects",j);}}
        String main=BuiltInRegistries.ITEM.getKey(player.getMainHandItem().getItem()).getPath(),off=BuiltInRegistries.ITEM.getKey(player.getOffhandItem().getItem()).getPath();boolean farer=main.equals(ProfessionContent.FARER_ROD_ID)||off.equals(ProfessionContent.FARER_ROD_ID),styx=main.equals(ProfessionContent.STYX_ROD_ID)||off.equals(ProfessionContent.STYX_ROD_ID),tidal=main.equals(ProfessionContent.TIDAL_ROD_ID)||off.equals(ProfessionContent.TIDAL_ROD_ID);double speed=0;
        if((farer||styx||tidal)&&player.isInWater()){player.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING,60,farer?4:styx?2:1));speed=farer?.40:styx?.30:0;if(tidal&&!styx&&!farer)player.addEffect(new MobEffectInstance(MobEffects.SPEED,40,0));var feet=player.blockPosition();boolean surface=player.level().getBlockState(feet).is(net.minecraft.world.level.block.Blocks.WATER)&&!player.level().getBlockState(feet.above()).is(net.minecraft.world.level.block.Blocks.WATER);if(surface&&!player.isShiftKeyDown()){player.setNoGravity(true);var motion=player.getDeltaMovement();player.setDeltaMovement(motion.x,Math.max(motion.y,0),motion.z);floating=true;}else if(floating){player.setNoGravity(false);floating=false;}}
        else if(floating){player.setNoGravity(false);floating=false;}
        var attribute=player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);if(attribute!=null){if(speed>0)attribute.addOrUpdateTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(SWIM,speed,net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));else attribute.removeModifier(SWIM);}
    }
}
