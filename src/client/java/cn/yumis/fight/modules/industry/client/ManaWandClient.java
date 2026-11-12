package cn.yumis.fight.modules.industry.client;

import cn.yumis.fight.modules.industry.platform.*;
import cn.yumis.fight.modules.presentation.api.client.*;
import net.minecraft.client.*;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

public final class ManaWandClient {
    private ManaWandClient(){}
    public static ItemStack held(){var player=Minecraft.getInstance().player;if(player==null)return ItemStack.EMPTY;return player.getMainHandItem().getItem() instanceof ManaWand?player.getMainHandItem():player.getOffhandItem().getItem() instanceof ManaWand?player.getOffhandItem():ItemStack.EMPTY;}
    public static void register(IEventBus bus,java.util.function.Function<ItemStack,Integer> displayColor){
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.player.ItemTooltipEvent event)->{
            var stack=event.getItemStack();var id=net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();int tier=cn.yumis.fight.modules.industry.api.IndustrialTechnology.itemTier(id);if(tier==0)return;
            String local=id.substring(id.indexOf(':')+1);boolean device=id.startsWith(IndustrialContent.NS+":")&&cn.yumis.fight.modules.industry.api.IndustrialTechnology.isDevice(local);
            Integer color=displayColor.apply(stack);if(color==null)color=cn.yumis.fight.modules.industry.api.IndustrialTechnology.color(tier);
            event.getToolTip().add(net.minecraft.network.chat.Component.literal((device?"科技等级 · ":"材料等级 · ")+cn.yumis.fight.modules.industry.api.IndustrialTechnology.label(tier)).withColor(color));
            if(stack.getItem() instanceof net.minecraft.world.item.BlockItem blockItem&&blockItem.getBlock() instanceof MachineBlock machine&&machine.initial.device()==cn.yumis.fight.modules.industry.api.DeviceType.STAR){
                event.getToolTip().add(net.minecraft.network.chat.Component.literal("放置后自动补全 · 占地5×5 · 总高5格 · 放置点为底座中央"));
                event.getToolTip().add(net.minecraft.network.chat.Component.literal(ArtificialStarPartBlock.hazardDescription()).withColor(0xff9b51));
            }
            if(device)event.getToolTip().add(net.minecraft.network.chat.Component.literal("工艺能力上限 · "+cn.yumis.fight.modules.industry.api.IndustrialTechnology.label(cn.yumis.fight.modules.industry.api.IndustrialTechnology.processing(local))));
            if(id.startsWith(IndustrialContent.NS+":")&&java.util.Set.of("raw_iridium","iridium_ore","crushed_iridium","iridium_concentrate","iridium_ingot","high_purity_iridium_ingot").contains(local))event.getToolTip().add(net.minecraft.network.chat.Component.literal("原矿开采 · T6 神话能耗"));
            if(local.endsWith("_ore")&&tier>3)event.getToolTip().add(net.minecraft.network.chat.Component.literal("原矿由对应等级矿机取得；玩家手挖不产出。"));
        });
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.player.ItemTooltipEvent event)->{var definition=MachineBlockEntity.pluginDefinition(event.getItemStack());if(definition!=null){event.getToolTip().add(net.minecraft.network.chat.Component.literal("背包堆叠64 · 机内每格最多 "+definition.installedStackLimit()+" · 逐件乘算"));event.getToolTip().add(net.minecraft.network.chat.Component.literal(definition.effect()==cn.yumis.fight.modules.industry.api.MachinePlugins.Effect.SPEED?"每件速度×1.5、单批耗能×2；发电机暂不适用":"每件输入／输出物品槽与液槽容量×2；超额储量未取出时不能拆卸"));}});
        bus.addListener((RegisterGuiLayersEvent event)->event.registerAboveAll(IndustrialContent.id("mana_wand"),(graphics,delta)->{
        var mc=Minecraft.getInstance();var stack=held();if(stack.isEmpty()||mc.gui.screen()!=null||mc.gui.hud.isHidden())return;var lines=ManaWand.information(stack);var font=UiFonts.font();int width=lines.stream().mapToInt(font::width).max().orElse(0)+16;float scale=Math.min(1f,(graphics.guiWidth()-16)/(float)width);
        graphics.pose().pushMatrix();graphics.pose().translate((graphics.guiWidth()-width*scale)/2,Math.max(8,graphics.guiHeight()-166));graphics.pose().scale(scale,scale);UiTheme.hud(graphics,0,0,width,lines.size()*12+8);for(int i=0;i<lines.size();i++)graphics.text(font,lines.get(i),8,5+i*12,UiTheme.HUD_TEXT,true);graphics.pose().popMatrix();
    }));}
}
