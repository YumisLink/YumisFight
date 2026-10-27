package cn.yumis.fight.client;

import cn.yumis.fight.industry.*;
import com.google.gson.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.BlockPos;
import com.mojang.serialization.JsonOps;
import java.util.*;

public final class IndustrialScreen extends AbstractContainerScreen<IndustrialMenu> {
    public boolean lines;
    public IndustrialScreen(IndustrialMenu menu,Inventory inventory,Component title){super(menu,inventory,title,256,238);inventoryLabelY=141;}
    @Override protected void init(){
        super.init();var kind=menu.machine.kind();button("开关",8,25,32,0).active=kind!=DeviceKind.BEACON;button("水晶备用",43,25,51,1).active=kind.processing();button("升级",97,25,32,2).active=kind.graded&&menu.machine.tier()<5||kind.functional()&&menu.machine.tier()<3;
        var recipes=addRenderableWidget(Button.builder(Component.literal(kind==DeviceKind.CURRENCY?"概率":"配方"),b->minecraft.gui.pushScreenLayer(kind==DeviceKind.CURRENCY?new Currency(this):new Recipes(this))).bounds(leftPos+132,topPos+25,34,18).build(ThemedButton::new));recipes.active=kind.processing();
        var structure=addRenderableWidget(Button.builder(Component.literal("结构"),b->minecraft.gui.pushScreenLayer(new Structure(this))).bounds(leftPos+169,topPos+25,34,18).build(ThemedButton::new));structure.active=kind==DeviceKind.FORGE||kind==DeviceKind.BEACON;button("连线",206,25,34,3);
    }
    private Button button(String text,int x,int y,int w,int id){return addRenderableWidget(Button.builder(Component.literal(text),b->minecraft.gameMode.handleInventoryButtonClick(menu.containerId,id)).bounds(leftPos+x,topPos+y,w,18).build(ThemedButton::new));}
    private String text(String key,String fallback){return menu.info.has(key)?menu.info.get(key).getAsString():fallback;}
    private long number(String key){return menu.info.has(key)?menu.info.get(key).getAsLong():0;}
    @Override public void extractBackground(GuiGraphicsExtractor g,int mx,int my,float alpha){
        g.fill(0,0,width,height,UiTheme.OVERLAY);g.fill(leftPos-5,topPos-5,leftPos+261,topPos+242,UiTheme.PANEL);g.fill(leftPos-5,topPos-5,leftPos+261,topPos-3,UiTheme.ACCENT);
        for(var slot:menu.slots)if(slot.isActive()){int x=leftPos+slot.x,y=topPos+slot.y;g.fill(x-1,y-1,x+17,y+17,UiTheme.CARD);g.fill(x,y,x+16,y+16,UiTheme.BUTTON);}
        double progress=menu.info.has("progress")?menu.info.get("progress").getAsDouble():0;if(menu.machine.kind().processing()){g.fill(leftPos+87,topPos+57,leftPos+174,topPos+65,UiTheme.CARD);g.fill(leftPos+88,topPos+58,leftPos+88+(int)(85*Math.clamp(progress,0,1)),topPos+64,UiTheme.ACCENT);}
        if(menu.info.has("preview")){ItemStack preview=ItemStack.OPTIONAL_CODEC.parse(minecraft.player.registryAccess().createSerializationContext(JsonOps.INSTANCE),menu.info.get("preview")).result().orElse(ItemStack.EMPTY);g.item(preview,leftPos+16,topPos+93);if(mx>=leftPos+14&&mx<=leftPos+34&&my>=topPos+91&&my<=topPos+111)g.setComponentTooltipForNextFrame(UiFonts.font(),Screen.getTooltipFromItem(minecraft,preview),mx,my,preview);}
        g.text(UiFonts.font(),"?",leftPos+242,topPos+8,UiTheme.ACCENT,false);
        if(mx>=leftPos+238&&mx<=leftPos+254&&my>=topPos+4&&my<=topPos+22){
            List<Component> help=new ArrayList<>();if(menu.info.has("help"))for(var line:menu.info.getAsJsonArray("help"))help.add(Component.literal(line.getAsString()));else help.add(Component.literal("正在读取设备规则"));help.add(Component.literal("权杖：Shift右键空气切换供能/连网/传送模式。"));
            if(menu.machine.kind().generator()){help.add(Component.literal("额定产能："+number("power")+" 点/秒；满网继续消耗。"));help.add(Component.literal("燃料余时："+number("fuel")+" tick；缺少流体暂停。"));}
            if(menu.machine.kind()==DeviceKind.BEACON){help.add(Component.literal("覆盖半径 "+number("radius")+" 格，三维直线距离。"));help.add(Component.literal(text("member_change","")));}
            if(menu.machine.kind()==DeviceKind.TRANSFER)help.add(Component.literal("目标："+text("target","未绑定")));
            g.setComponentTooltipForNextFrame(UiFonts.font(),help,mx,my);
        }
    }
    @Override protected void extractLabels(GuiGraphicsExtractor g,int mx,int my){
        g.text(UiFonts.font(),text("name",title.getString()),8,8,0xffe5e8ef,false);
        var kind=menu.machine.kind();boolean connected=menu.info.has("connected")&&menu.info.get("connected").getAsBoolean();g.text(UiFonts.font(),kind==DeviceKind.BEACON?(number("network_capacity")>0?"● 基座有效":"○ 基座无效"):connected?"● 信标有效":"○ 未连接",88,46,connected||kind==DeviceKind.BEACON&&number("network_capacity")>0?0xff8fe3b2:0xff8a98aa,false);
        if(kind.processing()){g.text(UiFonts.font(),font.plainSubstrByWidth("已扣 "+number("paid"),92),88,72,0xffb8c9d9,false);g.text(UiFonts.font(),"余能 "+number("crystal"),88,103,0xffb8c9d9,false);g.text(UiFonts.font(),font.plainSubstrByWidth("共需 "+number("total_energy"),92),88,87,0xffb8c9d9,false);}
        else if(kind.generator()){g.text(UiFonts.font(),"产能 "+number("power")+" 点/秒",88,63,0xffb8c9d9,false);g.text(UiFonts.font(),"燃料余时 "+number("fuel")+" tick",88,81,0xffb8c9d9,false);}
        else if(kind==DeviceKind.BEACON){g.text(UiFonts.font(),"核心储能 "+number("energy"),16,64,0xffb8c9d9,false);g.text(UiFonts.font(),"覆盖半径 "+number("radius")+" 格",16,79,0xffb8c9d9,false);}
        else {g.text(UiFonts.font(),kind==DeviceKind.TRANSFER?"权杖绑定接收容器":"成熟作物自动入下方箱",16,65,0xffb8c9d9,false);}
        g.text(UiFonts.font(),font.plainSubstrByWidth(text("status","读取设备状态"),240),8,111,0xfff0d9a5,false);
        String line=menu.machine.kind().generator()||menu.machine.kind()==DeviceKind.MIXER?"流体 "+number("fluid_in")+" / 产液 "+number("fluid_out")+" mB":"网络 "+number("network_energy")+" / "+number("network_capacity");
        g.text(UiFonts.font(),line,8,124,0xffa9cbd9,false);g.text(UiFonts.font(),"玩家背包",47,141,0xffa9b4c5,false);
        if(menu.machine.kind()==DeviceKind.BEACON)g.text(UiFonts.font(),"发能 "+number("generated")+"  消耗 "+number("consumed")+"  溢出 "+number("overflow"),8,94,0xffa9cbd9,false);
    }
    private static final class Recipes extends Screen {
        private final IndustrialScreen parent;private int page;
        Recipes(IndustrialScreen p){super(Component.literal("选择加工配方"));parent=p;}
        @Override protected void init(){controls();}
        private void controls(){clearWidgets();JsonArray list=parent.menu.info.has("recipes")?parent.menu.info.getAsJsonArray("recipes"):new JsonArray();int x=(width-290)/2,y=(height-230)/2;for(int i=0;i<8&&page*8+i<list.size();i++){int index=page*8+i;JsonObject recipe=list.get(index).getAsJsonObject();boolean allowed=recipe.get("tier").getAsInt()<=parent.menu.machine.tier();String name=recipe.get("name").getAsString()+" · "+recipe.get("energy").getAsLong()+"点"+(allowed?"":" · 需要"+recipe.get("tier").getAsInt()+"级");Button b=Button.builder(Component.literal(name),button->{minecraft.gameMode.handleInventoryButtonClick(parent.menu.containerId,100+index);onClose();}).bounds(x,y+28+i*22,290,20).build(ThemedButton::new);b.active=allowed;b.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(recipe.get("ingredients").getAsString()+"\n基准时间 "+recipe.get("ticks").getAsInt()+" tick；总耗能 "+recipe.get("energy").getAsLong()+" 点；最低 "+recipe.get("tier").getAsInt()+" 级")));addRenderableWidget(b);}addRenderableWidget(Button.builder(Component.literal("上一页"),b->{page=Math.max(0,page-1);controls();}).bounds(x,y+210,80,20).build(ThemedButton::new));addRenderableWidget(Button.builder(Component.literal("自动"),b->{minecraft.gameMode.handleInventoryButtonClick(parent.menu.containerId,4);onClose();}).bounds(x+105,y+210,80,20).build(ThemedButton::new));addRenderableWidget(Button.builder(Component.literal("下一页"),b->{page=Math.min(Math.max(0,(list.size()-1)/8),page+1);controls();}).bounds(x+210,y+210,80,20).build(ThemedButton::new));}
        @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float a){g.fill(0,0,width,height,UiTheme.OVERLAY);int x=(width-300)/2,y=(height-240)/2;g.fill(x,y,x+300,y+240,UiTheme.PANEL);g.text(UiFonts.font(),"选择配方 · 当前批次完成后切换",x+10,y+10,0xffe5e8ef,false);super.extractRenderState(g,mx,my,a);}
        @Override public boolean isPauseScreen(){return false;}
        @Override public void onClose(){minecraft.gui.popScreenLayer();}
    }
    private static final class Structure extends Screen {
        private final IndustrialScreen parent;Structure(IndustrialScreen p){super(Component.literal("结构搭建"));parent=p;}
        @Override protected void init(){addRenderableWidget(Button.builder(Component.literal("返回"),b->onClose()).bounds((width+170)/2,(height-180)/2,50,20).build(ThemedButton::new));}
        @Override public void extractBackground(GuiGraphicsExtractor g,int mx,int my,float delta){
            int left=(width-300)/2,top=(height-210)/2;g.fill(0,0,width,height,UiTheme.OVERLAY);g.fill(left,top,left+300,top+210,UiTheme.PANEL);g.text(UiFonts.font(),"结构搭建 · "+parent.text("structure_material","读取材料"),left+12,top+15,0xffe5e8ef,false);
            var be=parent.menu.machine;boolean forge=be.kind()==DeviceKind.FORGE;Set<Long> missing=new HashSet<>();if(parent.menu.info.has("missing"))for(var p:parent.menu.info.getAsJsonArray("missing"))missing.add(p.getAsLong());BlockPos center=forge?be.getBlockPos().relative(be.getBlockState().getValue(MachineBlock.FACING).getOpposite()).above():be.getBlockPos().below();
            for(int layer=0;layer<(forge?3:1);layer++){int x0=left+20+layer*92,y0=top+60;g.text(UiFonts.font(),forge?new String[]{"底层","中层","顶层"}[layer]:"3×3基座",x0,y0-15,0xffd1dbe8,false);for(int z=-1;z<=1;z++)for(int x=-1;x<=1;x++){BlockPos pos=center.offset(x,forge?layer-1:0,z);boolean control=pos.equals(be.getBlockPos()),air=forge&&layer==1&&x==0&&z==0,bad=missing.contains(pos.asLong());int sx=x0+(x+1)*19,sy=y0+(z+1)*19;g.fill(sx,sy,sx+17,sy+17,control?0xffbd9d5c:air?UiTheme.CARD:bad?0xff9a524d:0xff3d7872);g.text(UiFonts.font(),control?"控":air?"空":bad?"×":"✓",sx+4,sy+5,0xfff0ede7,false);if(mx>=sx&&mx<sx+17&&my>=sy&&my<sy+17)g.setComponentTooltipForNextFrame(UiFonts.font(),List.of(Component.literal(pos.toShortString()),Component.literal(control?"控制器":air?"中心留空":bad?"此处缺少正确材料":"材料正确")),mx,my);}}
            g.text(UiFonts.font(),forge?"控制器位于底层正面中央，中心留空。":"核心位于基座上方中央。",left+12,top+145,0xffc0ccd9,false);g.text(UiFonts.font(),forge?"其余25处使用同级材料；下方可放输出容器。":"九块同级材料；允许地下使用。",left+12,top+163,0xffa9bacd,false);g.text(UiFonts.font(),"当前缺失 "+missing.size()+" 处",left+12,top+186,missing.isEmpty()?0xff8fe3b2:0xffffa297,false);
        }
        @Override public void onClose(){minecraft.gui.popScreenLayer();}
        @Override public boolean isPauseScreen(){return false;}
    }
    private static final class Currency extends Screen {
        Currency(IndustrialScreen p){super(Component.literal("工业通货概率"));}
        @Override protected void init(){addRenderableWidget(Button.builder(Component.literal("返回"),b->onClose()).bounds((width+180)/2,(height-230)/2,50,20).build(ThemedButton::new));}
        @Override public void extractBackground(GuiGraphicsExtractor g,int mx,int my,float delta){int x=(width-310)/2,y=(height-260)/2;g.fill(0,0,width,height,UiTheme.OVERLAY);g.fill(x,y,x+310,y+260,UiTheme.PANEL);g.text(UiFonts.font(),"每轮必出1枚 · 玩家掉宝不参与",x+12,y+15,0xffe5e8ef,false);var rules=IndustrialRules.CURRENT;double[] probabilities=cn.yumis.fight.rules.CurrencyRules.factoryProbabilities(rules.currencyRarityWeights,rules.currencyItemWeights);int row=0;for(int i=0;i<probabilities.length;i++)if(probabilities[i]>0){var currency=cn.yumis.fight.rules.CurrencyRules.ALL.get(i);ItemStack stack=cn.yumis.fight.game.GameItems.item(currency.id(),1);g.item(stack,x+14,y+38+row*20);g.text(UiFonts.font(),stack.getHoverName(),x+38,y+43+row*20,0xffd1dbe8,false);g.text(UiFonts.font(),String.format(java.util.Locale.ROOT,"%.2f%%",probabilities[i]*100),x+230,y+43+row*20,0xffd8e5bd,false);row++;}}
        @Override public void onClose(){minecraft.gui.popScreenLayer();}
        @Override public boolean isPauseScreen(){return false;}
    }
}
