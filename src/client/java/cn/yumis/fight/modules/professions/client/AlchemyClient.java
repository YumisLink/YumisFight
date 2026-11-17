package cn.yumis.fight.modules.professions.client;

import cn.yumis.fight.modules.attributes.api.AttributeSources;
import cn.yumis.fight.modules.attributes.api.Stat;
import cn.yumis.fight.modules.attributes.api.Stats;
import cn.yumis.fight.modules.professions.api.AlchemyPotions;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.function.DoubleConsumer;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/** Owner-authored alchemy effects, committed with the consumed item through professions. */
public final class AlchemyClient {
    private static JsonObject state=empty();
    private static AttributeSources sources=new AttributeSources(),combined=new AttributeSources(),world;
    private static long worldRevision=Long.MIN_VALUE,localRevision=Long.MIN_VALUE;
    private static Supplier<Stats> stats;
    private static DoubleConsumer healing,mana;
    private AlchemyClient(){}
    public static void install(Supplier<Stats> current,DoubleConsumer healFraction,DoubleConsumer restoreMana){stats=current;healing=healFraction;mana=restoreMana;}
    private static JsonObject empty(){var value=new JsonObject();value.add("timers",new JsonObject());value.add("cooldowns",new JsonObject());value.addProperty("active","");return value;}
    public static JsonObject snapshot(){return state.deepCopy();}
    public static void clear(){state=empty();sources=new AttributeSources();combined=new AttributeSources();world=null;worldRevision=Long.MIN_VALUE;localRevision=Long.MIN_VALUE;}
    public static void prepare(JsonObject request,String potion){
        long now=System.currentTimeMillis();var next=state.deepCopy();prune(next,now);
        var cooldowns=next.getAsJsonObject("cooldowns");
        if(cooldowns.has(potion)&&cooldowns.get(potion).getAsLong()>now)throw new IllegalArgumentException("药剂冷却中");
        cooldowns.addProperty(potion,now+AlchemyPotions.COOLDOWN_TICKS*50L);
        request.addProperty("potion",potion);
        if(AlchemyPotions.isRestore(potion)){
            boolean magic=AlchemyPotions.isMana(potion);double maximum=stats.get().get(magic?Stat.MP:Stat.HEALTH);
            double amount=AlchemyPotions.isPercentRestore(potion)?maximum*AlchemyPotions.percentRestore(potion):AlchemyPotions.restoreAmount(potion);
            request.addProperty(magic?"mana":"healing",magic?amount:amount/Math.max(1,maximum));
        }else if(AlchemyPotions.isBuff(potion)){
            var timer=new JsonObject();timer.addProperty("potion",potion);timer.addProperty("expires",now+AlchemyPotions.BUFF_MILLIS);
            next.getAsJsonObject("timers").add(AlchemyPotions.category(potion),timer);next.addProperty("active",potion);
        }else throw new IllegalArgumentException("无效炼金药剂");
        request.add("alchemy",next);
    }
    public static void applied(JsonObject request){
        restore(request.getAsJsonObject("alchemy"));
        if(request.has("healing"))healing.accept(request.get("healing").getAsDouble());
        if(request.has("mana"))mana.accept(request.get("mana").getAsDouble());
    }
    public static void restore(JsonObject value){
        state=value.deepCopy();prune(state,System.currentTimeMillis());
        AlchemyPotions.clearBuffs(sources);String active=state.get("active").getAsString();
        if(!active.isEmpty())AlchemyPotions.applyBuff(sources,active);
        var player=Minecraft.getInstance().player;if(player==null)return;
        long now=System.currentTimeMillis();
        for(var entry:state.getAsJsonObject("cooldowns").entrySet()){
            int remaining=(int)Math.max(0,(entry.getValue().getAsLong()-now+49)/50);
            if(remaining>0){var item=BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("yumis_dungeon_and_fight",entry.getKey()));player.getCooldowns().addCooldown(new ItemStack(item),remaining);}
        }
    }
    private static boolean prune(JsonObject value,long now){
        var timers=value.getAsJsonObject("timers");boolean expired=timers.entrySet().removeIf(entry->entry.getValue().getAsJsonObject().get("expires").getAsLong()<=now);
        if(expired)value.addProperty("active","");
        value.getAsJsonObject("cooldowns").entrySet().removeIf(entry->entry.getValue().getAsLong()<=now);
        return expired;
    }
    public static void tick(){if(prune(state,System.currentTimeMillis()))AlchemyPotions.clearBuffs(sources);}
    static JsonObject activeTimer(){
        String active=state.get("active").getAsString();if(active.isEmpty()||!AlchemyPotions.isBuff(active))return null;
        var timer=state.getAsJsonObject("timers").getAsJsonObject(AlchemyPotions.category(active));
        return timer!=null&&active.equals(timer.get("potion").getAsString())?timer:null;
    }
    public static AttributeSources attributes(AttributeSources external){
        if(world!=external||worldRevision!=external.revision()||localRevision!=sources.revision()){
            var next=new ArrayList<>(external.snapshot());next.addAll(sources.snapshot());combined.replace(combined.revision()+1,next);
            world=external;worldRevision=external.revision();localRevision=sources.revision();
        }
        return combined;
    }
}
