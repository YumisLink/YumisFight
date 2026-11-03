package cn.yumis.fight.modules.presentation.client;

import cn.yumis.fight.modules.presentation.api.client.ClientUi;
import cn.yumis.fight.modules.presentation.api.client.MenuInput;
import cn.yumis.fight.platform.ItemData;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Opens the I-key menu from an icon beside the inventory's recipe book. */
public final class InventoryMenuButton {
    private static final WidgetSprites CHARACTER_SPRITES=new WidgetSprites(
        Identifier.fromNamespaceAndPath(ItemData.NS,"character_panel/button"),
        Identifier.fromNamespaceAndPath(ItemData.NS,"character_panel/button_highlighted"));
    private InventoryMenuButton(){}

    public static void register(){
        NeoForge.EVENT_BUS.addListener((ScreenEvent.Init.Post event)->{
            if(!(event.getScreen() instanceof InventoryScreen screen))return;
            event.addListener(new CharacterButton(screen));
        });
        NeoForge.EVENT_BUS.addListener((ScreenEvent.Render.Pre event)->{
            if(!(event.getScreen() instanceof InventoryScreen screen))return;
            for(var child:screen.children())if(child instanceof CharacterButton button)
                button.setPosition(screen.getLeftPos()+128,screen.height/2-22);
        });
    }

    private static final class CharacterButton extends ImageButton {
        CharacterButton(InventoryScreen screen){
            super(screen.getLeftPos()+128,screen.height/2-22,20,18,CHARACTER_SPRITES,button->{
                ClientUi.current().upload();
                MenuInput.open();
            },Component.literal("角色面板"));
            setTooltip(Tooltip.create(getMessage()));
        }
    }
}
