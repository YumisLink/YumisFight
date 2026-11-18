package cn.yumis.fight.modules.arkguard.client;

import cn.yumis.fight.modules.presentation.api.client.ClientSettings;
import cn.yumis.fight.modules.presentation.api.client.ThemedButton;
import cn.yumis.fight.modules.presentation.api.client.ThemedScreen;
import cn.yumis.fight.modules.presentation.api.client.UiFonts;
import cn.yumis.fight.modules.presentation.api.client.UiTheme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

final class ArkDisplaySettings extends ThemedScreen {
    private static final int PANEL_HEIGHT=153;
    private int left,top,panelWidth;

    ArkDisplaySettings(){super(Component.literal("方舟显示设置"));}

    @Override protected void init(){
        panelWidth=Math.min(220,width-16);
        left=(width-panelWidth)/2;
        top=(height-PANEL_HEIGHT)/2;
        toggle("敌方血条",31,()->ClientSettings.arkEnemyHealth,value->ClientSettings.arkEnemyHealth=value);
        toggle("友方血条",55,()->ClientSettings.arkAllyHealth,value->ClientSettings.arkAllyHealth=value);
        toggle("敌方造成的伤害浮字",79,()->ClientSettings.arkEnemyDamage,value->ClientSettings.arkEnemyDamage=value);
        toggle("友方造成的伤害浮字",103,()->ClientSettings.arkAllyDamage,value->ClientSettings.arkAllyDamage=value);
        addRenderableWidget(Button.builder(Component.literal("关闭"),button->onClose())
            .bounds(left+12,top+127,panelWidth-24,19).build(ThemedButton::new));
    }

    private void toggle(String label,int offset,BooleanSupplier read,Consumer<Boolean> write){
        addRenderableWidget(Button.builder(label(label,read.getAsBoolean()),button->{
            boolean enabled=!read.getAsBoolean();
            write.accept(enabled);
            ClientSettings.save();
            button.setMessage(label(label,enabled));
        }).bounds(left+12,top+offset,panelWidth-24,20).build(ThemedButton::new));
    }

    private static Component label(String title,boolean enabled){
        return Component.literal(title+" · "+(enabled?"显示":"隐藏"));
    }

    @Override protected void drawBackground(GuiGraphicsExtractor graphics,int x,int y,float delta){
        graphics.fill(0,0,width,height,UiTheme.OVERLAY);
        UiTheme.panel(graphics,left,top,panelWidth,PANEL_HEIGHT);
    }

    @Override protected void drawContents(GuiGraphicsExtractor graphics,int x,int y,float delta){
        graphics.text(UiFonts.font(),title,left+12,top+12,UiTheme.GOLD,false);
        super.drawContents(graphics,x,y,delta);
    }

    @Override public boolean isPauseScreen(){return false;}
}
