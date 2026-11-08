package cn.yumis.fight.modules.dungeons.client;

import cn.yumis.fight.architecture.CommandRouter;
import cn.yumis.fight.modules.dungeons.api.*;
import cn.yumis.fight.modules.equipment.api.Rarity;
import cn.yumis.fight.modules.character.api.Progress;
import cn.yumis.fight.modules.attributes.api.*;
import cn.yumis.fight.modules.inventory.api.InventoryEdits;
import cn.yumis.fight.modules.presentation.api.client.ClientUi;
import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import java.util.*;
import java.util.function.*;

/** Personal dungeon eligibility and the owner's returned waystone are decided locally. */
public final class DungeonActions {
    private static final Gson JSON=new Gson();private static final Random RANDOM=new Random();private static final Set<String> offered=new HashSet<>();
    private static Supplier<Progress> progress;private static Supplier<Stats> stats;private static BooleanSupplier alive,trainingAllowed;private static IntFunction<ItemStack> chest;private static DoubleFunction<Rarity> rarityRoll;
    private DungeonActions(){}
    public static void install(CommandRouter<Void,JsonObject> router,Supplier<Progress> p,Supplier<Stats> attributes,BooleanSupplier living,IntFunction<ItemStack> chests,DoubleFunction<Rarity> rarity,BooleanSupplier training){
        progress=p;stats=attributes;alive=living;chest=chests;rarityRoll=rarity;trainingAllowed=training;
        router.register("dungeons","dungeon",(ignored,j)->{if(j.get("kind").getAsString().equals("leave"))return;eligible(j.get("dungeon").getAsString());});
        router.register("dungeons","waystoneTable",(ignored,j)->{
            String action=j.get("action").getAsString();if(!Set.of("insert","join","ready","start").contains(action))return;Waystone stone=action.equals("insert")?Waystone.read(Minecraft.getInstance().player.getMainHandItem()):WaystoneClient.stone(j.get("table").getAsString());if(stone==null)throw new IllegalArgumentException("请先放入引路石");eligible(stone.dungeon);
        });
        router.register("dungeons","waystoneReturnResult",(ignored,j)->returned(j));
        router.register("dungeons","trainingStart",(ignored,j)->{if(!trainingAllowed.getAsBoolean())throw new IllegalArgumentException("请在副本外且存活时开始训练");if(Minecraft.getInstance().level.dimension().identifier().toString().equals("yumis_dungeon_and_fight:dungeon"))throw new IllegalArgumentException("请在副本外训练");});
    }
    private static void eligible(String id){var rule=DungeonCatalog.current().dungeon(id);if(!rule.available())throw new IllegalArgumentException("该副本尚未开放");if(progress.get().level<rule.level())throw new IllegalArgumentException("角色等级不足");if(!alive.getAsBoolean())throw new IllegalArgumentException("死亡状态不能参加挑战");}
    private static void returned(JsonObject j){
        if(j.has("items"))return;
        var context=j.getAsJsonObject("context");var run=JSON.fromJson(context.get("run"),WaystoneRun.class);var items=new JsonArray();
        if(context.get("returnStone").getAsBoolean()){
            int tier=run.rules.nextTier(run.stone.tier,run.elapsed(run.finishedAt),run.result.equals("COMPLETED"));double luck=run.stone.effectiveDropRate(stats.get().get(Stat.DROP_RATE));var rarity=rarityRoll.apply(luck);if(rarity.ordinal()>Rarity.LEGENDARY.ordinal())rarity=Rarity.LEGENDARY;
            items.add(InventoryEdits.encode(Minecraft.getInstance().player,Waystone.generate(tier,progress.get().level,rarity,RANDOM).item()));j.addProperty("resultingTier",tier);
        }
        if(context.get("chest").getAsBoolean())items.add(InventoryEdits.encode(Minecraft.getInstance().player,chest.apply(run.stone.tier)));j.add("items",items);
    }
    public static void receiveReturn(JsonObject context){if(!offered.add(context.get("id").getAsString()))return;var j=ClientUi.current().operation("waystoneReturnResult");j.add("context",context.deepCopy());j.addProperty("retryStorage",true);ClientUi.current().send("waystoneReturnResult",j);}
    public static void clear(){offered.clear();}
}
