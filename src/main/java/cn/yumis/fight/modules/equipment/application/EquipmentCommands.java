package cn.yumis.fight.modules.equipment.application;

import cn.yumis.fight.modules.affixes.api.AffixDefinitions;
import cn.yumis.fight.modules.equipment.api.CraftingEssences;
import cn.yumis.fight.modules.equipment.api.EquipmentItems;
import cn.yumis.fight.modules.equipment.api.Gear;
import cn.yumis.fight.modules.inventory.api.InventoryOperations;
import cn.yumis.fight.modules.presentation.api.ModText;
import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Admin commands owned by the equipment module.
 * `/ydnf craft <player> <essenceId> [percent]` force-writes a crafting essence affix onto the
 * target's held gear, bypassing the normal artifact/slot/family qualification for testing; all
 * essences are available, including drop-only ones. The percent argument is the roll percentile
 * used by essences whose affix has a value range; fixed-value essences ignore it.
 */
public final class EquipmentCommands {
    /** Bootstrap-supplied ports; the command only needs the inventory transaction port and a profile refresh. */
    public record Admin(InventoryOperations inventory,Consumer<ServerPlayer> profile){}
    private EquipmentCommands(){}

    public static void commands(LiteralArgumentBuilder<CommandSourceStack> root,Supplier<Admin> admin){
        var percent=Commands.argument("percent",IntegerArgumentType.integer(0,100)).executes(c->craft(c,admin.get(),IntegerArgumentType.getInteger(c,"percent")));
        var essence=Commands.argument("essence",StringArgumentType.word())
            .suggests((context,builder)->{for(var d:CraftingEssences.all())builder.suggest(d.id());return builder.buildFuture();})
            .executes(c->craft(c,admin.get(),50))
            .then(percent);
        root.then(Commands.literal("craft")
            .requires(source->source.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER))
            .then(Commands.argument("player",EntityArgument.player()).then(essence)));
    }
    private static int craft(CommandContext<CommandSourceStack> context,Admin admin,int percent){
        try{
            ServerPlayer player=EntityArgument.getPlayer(context,"player");
            var essence=CraftingEssences.find(StringArgumentType.getString(context,"essence"));
            if(essence==null)throw new IllegalArgumentException("未知工艺，请用指令补全查看可用ID");
            int slot=player.getInventory().getSelectedSlot();
            if(EquipmentItems.gear(player.getInventory().getItem(slot))==null)throw new IllegalArgumentException("手持物品不是装备");
            JsonObject intent=new JsonObject();intent.addProperty("adminCraft",essence.id());intent.addProperty("percent",percent);
            admin.inventory().transact(player,"admin-craft/"+UUID.randomUUID(),intent,()->{
                ItemStack held=player.getInventory().getItem(slot);
                Gear gear=EquipmentItems.gear(held);
                if(gear==null)throw new IllegalArgumentException("手持物品不是装备");
                ItemStack updated=held.copy();
                EquipmentItems.write(updated,CraftingEssences.applyForced(gear,essence,percent/100d));
                player.getInventory().setItem(slot,updated);
            },Map.of());
            admin.profile().accept(player);
            var definition=AffixDefinitions.current().affixes.get(essence.affix());
            String note=definition!=null&&definition.minimum()==null?"（定值词条，分位不改变数值）":"（分位 "+percent+"%）";
            String name=essence.name(),target=player.getName().getString();
            context.getSource().sendSuccess(()->ModText.of("已向 "+target+" 的手持装备写入工艺："+name+note),true);
            return 1;
        }catch(Exception failure){context.getSource().sendFailure(ModText.of(failure.getMessage()));return 0;}
    }
}
