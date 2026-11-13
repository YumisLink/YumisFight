package cn.yumis.fight.modules.economy.api.client;

import cn.yumis.fight.modules.economy.api.ItemRarity;
import cn.yumis.fight.modules.equipment.api.Rarity;
import cn.yumis.fight.modules.equipment.api.client.MythicDisplay;
import cn.yumis.fight.platform.ItemData;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/** One frame palette for inventory slots, display stock and purchase previews. */
public final class RarityFrames {
    private static final Identifier BACKGROUND=Identifier.fromNamespaceAndPath(ItemData.NS,"textures/gui/rarity/background.png");
    private RarityFrames(){}
    public static void draw(GuiGraphicsExtractor g,ItemStack stack,int x,int y,boolean background){
        if(!cn.yumis.fight.modules.presentation.api.client.ClientSettings.itemQualityFrames)return;
        var rarity=ItemRarity.rarity(stack);int color=0xff000000|rarity.color();
        if(background)g.blit(RenderPipelines.GUI_TEXTURED,BACKGROUND,x,y,0f,0f,16,16,16,16,color);
        if(rarity==Rarity.MYTHIC)MythicDisplay.frame(g,x,y,16,16);
        else{g.fill(x,y,x+16,y+1,color);g.fill(x,y+15,x+16,y+16,color);g.fill(x,y+1,x+1,y+15,color);g.fill(x+15,y+1,x+16,y+15,color);}
    }
}
