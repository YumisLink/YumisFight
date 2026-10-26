package cn.yumis.fight.client;
import cn.yumis.fight.rules.Models.Gear;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
public final class GemScreen extends Screen {
    private final net.minecraft.client.gui.Font font=UiFonts.font();
    private final Screen parent;
    private final JsonObject request;
    private final Gear gear;
    public GemScreen(Screen parent,JsonObject request,Gear gear){
        super(Component.literal("选择宝石孔"));
        this.parent=parent;
        this.request=request;
        this.gear=gear;
    }
    @Override protected void init(){
        for(int i=0;i<gear.sockets;i++){
            final int index=i;
            String existing=i<gear.gems.size()?gear.gems.get(i).name+"（覆盖销毁）":"空孔";
            addRenderableWidget(Button.builder(Component.literal("孔 "+(i+1)+" · "+existing),b->{
                if(!valid()){minecraft.gui.setScreen(null);return;}request.addProperty("socket",index);ClientState.INSTANCE.send("currency",request);minecraft.gui.popScreenLayer();
            }
            ).bounds(width/2-120,height/2-16+i*25,240,20).build(ThemedButton::new));
        }
        addRenderableWidget(Button.builder(Component.literal("返回装备"),b->onClose()).bounds(width/2-50,height/2+50,100,20).build(ThemedButton::new));
    }
    @Override public void extractBackground(GuiGraphicsExtractor g,int x,int y,float a){
        g.fill(0,0,width,height,UiTheme.OVERLAY);
        g.text(font,UiFonts.decorate(title),width/2-font.width(title)/2,height/2-42,0xffffd28a,false);
    }
    @Override public void onClose(){
        minecraft.gui.popScreenLayer();
    }
    private boolean valid(){return minecraft.player!=null&&minecraft.player.containerMenu.containerId==request.get("menu").getAsInt()&&parent instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>;}
    @Override public void tick(){if(!valid())minecraft.gui.setScreen(null);}
    @Override public boolean keyPressed(KeyEvent e){
        if(e.isEscape()){
            onClose();
            return true;
        }
        if(ClientBootstrap.MENU.matches(e)){
            onClose();
            return true;
        }
        return super.keyPressed(e);
    }
    @Override public boolean isPauseScreen(){
        return false;
    }
}
