package cn.yumis.fight.modules.dungeons.client;

import cn.yumis.fight.architecture.CommandRouter;
import cn.yumis.fight.modules.dungeons.api.*;
import cn.yumis.fight.modules.dungeons.content.CastlePalace;
import cn.yumis.fight.modules.equipment.api.Rarity;
import cn.yumis.fight.modules.character.api.Progress;
import cn.yumis.fight.modules.attributes.api.*;
import cn.yumis.fight.modules.inventory.api.InventoryEdits;
import cn.yumis.fight.modules.presentation.api.client.ClientUi;
import cn.yumis.fight.platform.ItemData;
import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForgeMod;
import java.util.*;
import java.util.function.*;

/** Personal dungeon eligibility and the owner's returned waystone are decided locally. */
public final class DungeonActions {
    private static final Gson JSON=new Gson();private static final Random RANDOM=new Random();private static final Set<String> offered=new HashSet<>();
    private static final Identifier DUNGEON_DIMENSION = Identifier.fromNamespaceAndPath(ItemData.NS, "dungeon");
    private static final AttributeModifier PALACE_SWIM_SPEED = new AttributeModifier(
        Identifier.fromNamespaceAndPath(ItemData.NS, "palace_swim_speed"), 1.0,
        AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    private static Supplier<Progress> progress;private static Supplier<Stats> stats;private static BooleanSupplier alive,trainingAllowed;private static IntFunction<ItemStack> chest;private static DoubleFunction<Rarity> rarityRoll;
    private DungeonActions(){}
    public static void install(CommandRouter<Void,JsonObject> router,Supplier<Progress> p,Supplier<Stats> attributes,BooleanSupplier living,IntFunction<ItemStack> chests,DoubleFunction<Rarity> rarity,BooleanSupplier training){
        progress=p;stats=attributes;alive=living;chest=chests;rarityRoll=rarity;trainingAllowed=training;
        router.register("dungeons","dungeon",(ignored,j)->{
            String kind=j.get("kind").getAsString();
            if(kind.equals("leave"))return;
            if(kind.equals("joinTower")){
                if(!alive.getAsBoolean())throw new IllegalArgumentException("死亡状态不能参加挑战");
                return;
            }
            String id=j.get("dungeon").getAsString();
            if(PracticeRoom.isRoomId(id)){
                var room=PracticeRoom.require(id);
                if(progress.get().level<room.level())throw new IllegalArgumentException("角色等级不足");
                if(!alive.getAsBoolean())throw new IllegalArgumentException("死亡状态不能参加挑战");
            }else eligible(id);
            if(j.has("abyssChallenge")&&j.get("abyssChallenge").getAsBoolean())prepareAbyssPayment(j);
        });
        router.register("dungeons","waystoneTable",(ignored,j)->{
            String action=j.get("action").getAsString();if(!Set.of("insert","join","ready","start").contains(action))return;
            Waystone stone;
            if(action.equals("insert")){
                if(j.has("openAfterInsert")&&WaystoneClient.stone(j.get("table").getAsString())!=null){j.addProperty("localOnly",true);WaystoneClient.openFilledTableLater(j.get("table").getAsString());return;}
                var hand=j.has("hand")?InteractionHand.valueOf(j.get("hand").getAsString()):InteractionHand.MAIN_HAND;
                var held=Minecraft.getInstance().player.getItemInHand(hand);
                if(j.has("stoneData")&&!j.get("stoneData").getAsString().equals(ItemData.string(held,Waystone.DATA)))throw new IllegalArgumentException("手中的引路石已变化，请重新操作");
                stone=Waystone.read(held);
            }
            else stone=WaystoneClient.stone(j.get("table").getAsString());
            if(stone==null)throw new IllegalArgumentException("请先放入引路石");eligible(stone.dungeon);
        });
        router.register("dungeons","waystoneReturnResult",(ignored,j)->returned(j));
        router.register("dungeons","trainingStart",(ignored,j)->{if(!trainingAllowed.getAsBoolean())throw new IllegalArgumentException("请在副本外且存活时开始训练");if(Minecraft.getInstance().level.dimension().identifier().equals(DUNGEON_DIMENSION))throw new IllegalArgumentException("请在副本外训练");});
    }

    static boolean hasAbyssInvitation(){
        return abyssInvitationSlot(Minecraft.getInstance().player)>=0;
    }
    /** Shared with the page assembly: true while the local player stands inside the dungeon dimension. */
    public static boolean inDungeonDimension(){
        var player=Minecraft.getInstance().player;
        return player!=null&&player.level().dimension().identifier().equals(DUNGEON_DIMENSION);
    }

    private static int abyssInvitationSlot(LocalPlayer player){
        if(player==null)return -1;
        for(int slot=0;slot<36;slot++){
            if(AbyssParty.invitation(player.getInventory().getItem(slot)))return slot;
        }
        return -1;
    }

    private static void prepareAbyssPayment(JsonObject request){
        var player=Minecraft.getInstance().player;
        int slot=abyssInvitationSlot(player);
        if(slot<0)throw new IllegalArgumentException("背包中需要 1 张深渊派对邀请函");
        var stack=player.getInventory().getItem(slot);
        var changes=new JsonArray();
        changes.add(InventoryEdits.change(player,"inventory",slot,stack,stack.copyWithCount(stack.getCount()-1)));
        request.add("inventoryChanges",changes);
    }

    public static void updateSwimSpeed(LocalPlayer player, Dungeon dungeon) {
        var speed = player.getAttribute(NeoForgeMod.SWIM_SPEED);
        if (speed == null) return;
        if (CastlePalace.handles(dungeon) && player.level().dimension().identifier().equals(DUNGEON_DIMENSION)) {
            if (!speed.hasModifier(PALACE_SWIM_SPEED.id())) speed.addTransientModifier(PALACE_SWIM_SPEED);
        } else {
            speed.removeModifier(PALACE_SWIM_SPEED.id());
        }
    }
    static void enterTower(JsonObject stele){
        var ui=ClientUi.current();
        if(!ui.ready())return;
        var request=ui.operation("joinTower");
        request.add("dungeon",stele.get("dungeon"));
        request.add("stele",stele.get("pos"));
        request.addProperty("sync",progress.get().dungeonSync);
        ui.upload();
        ui.send("dungeon",request);
    }
    private static void eligible(String id){
        if(WrithingCastle.ID.equals(id))throw new IllegalArgumentException(WrithingCastle.SPECIAL_ENTRY_MESSAGE);
        var rule=DungeonCatalog.current().dungeon(id);if(!rule.available())throw new IllegalArgumentException("该副本尚未开放");if(progress.get().level<rule.level())throw new IllegalArgumentException("角色等级不足");if(!alive.getAsBoolean())throw new IllegalArgumentException("死亡状态不能参加挑战");
    }
    private static void returned(JsonObject j){
        if(j.has("id"))return;
        var context=j.getAsJsonObject("context");
        var run=JSON.fromJson(context.get("run"),WaystoneRun.class);
        var player=Minecraft.getInstance().player;
        if(context.get("returnStone").getAsBoolean()){
            int tier=run.rules.nextTier(run.stone.tier,run.elapsed(run.finishedAt),run.result.equals("COMPLETED"));double luck=run.stone.effectiveDropRate(stats.get().get(Stat.DROP_RATE));var rarity=rarityRoll.apply(luck);if(rarity.ordinal()>Rarity.LEGENDARY.ordinal())rarity=Rarity.LEGENDARY;
            j.add("stone",InventoryEdits.encode(player,Waystone.generate(tier,progress.get().level,rarity,RANDOM).item()));
            j.addProperty("resultingTier",tier);
        }
        if(context.get("chest").getAsBoolean())j.add("chest",InventoryEdits.encode(player,chest.apply(run.stone.tier)));
        j.add("id",context.get("id"));
        j.remove("context");
    }
    public static void receiveReturn(JsonObject context){if(!offered.add(context.get("id").getAsString()))return;var j=ClientUi.current().operation("waystoneReturnResult");j.add("context",context.deepCopy());j.addProperty("retryStorage",true);ClientUi.current().send("waystoneReturnResult",j);}
    public static void clear(){offered.clear();}
}
