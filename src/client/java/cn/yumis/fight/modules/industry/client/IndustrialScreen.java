package cn.yumis.fight.modules.industry.client;
import cn.yumis.fight.modules.presentation.api.client.UiFonts;
import cn.yumis.fight.platform.ItemRegistry;
import cn.yumis.fight.modules.industry.platform.MachineBlock;
import cn.yumis.fight.modules.industry.api.IndustrialRules;
import cn.yumis.fight.modules.industry.platform.IndustrialMenu;
import cn.yumis.fight.modules.economy.api.CurrencyRules;
import cn.yumis.fight.modules.industry.api.DeviceType;

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
    private final IndustrialProgress progress=new IndustrialProgress();
    @Override protected void containerTick(){super.containerTick();progress.tick(menu.info);}

    public IndustrialScreen(IndustrialMenu menu,Inventory inventory,Component title){super(menu,inventory,title,256,238);inventoryLabelY=141;}
    @Override protected void init(){
        super.init();var kind=menu.machine.device();int x=8;
        if(!kind.has(DeviceType.Capability.STORAGE_NETWORK)){button("开关",x,25,32,0);x+=35;}
        if(kind.selectedRecipe()||kind.randomCurrency()){final int at=x;addRenderableWidget(Button.builder(Component.literal(kind.randomCurrency()?"概率":"配方"),b->minecraft.gui.pushScreenLayer(kind.randomCurrency()?new Currency(this):new Recipes(this))).bounds(leftPos+at,topPos+25,34,18).build(IndustrialUi.IndustrialButton::new));x+=37;}
        if(kind.needsStructure()){final int at=x;addRenderableWidget(Button.builder(Component.literal("结构"),b->minecraft.gui.pushScreenLayer(new Structure(this))).bounds(leftPos+at,topPos+25,34,18).build(IndustrialUi.IndustrialButton::new));x+=37;}
        button("连线",x,25,34,3);
    }
    private Button button(String text,int x,int y,int w,int id){return addRenderableWidget(Button.builder(Component.literal(text),b->minecraft.gameMode.handleInventoryButtonClick(menu.containerId,id)).bounds(leftPos+x,topPos+y,w,18).build(IndustrialUi.IndustrialButton::new));}
    private String text(String key,String fallback){return menu.info.has(key)?menu.info.get(key).getAsString():fallback;}
    private long number(String key){return menu.info.has(key)?menu.info.get(key).getAsLong():0;}
    @Override public void extractBackground(GuiGraphicsExtractor g,int mx,int my,float alpha){
        g.fill(0,0,width,height,IndustrialUi.OVERLAY);IndustrialUi.panel(g,leftPos,topPos,imageWidth,imageHeight);
        for(var slot:menu.slots)if(slot.isActive()){int x=leftPos+slot.x,y=topPos+slot.y;IndustrialUi.slot(g,x,y);}
        double progress=menu.machine.device().selectedRecipe()?this.progress.value(alpha):menu.info.has("progress")?menu.info.get("progress").getAsDouble():0;if(menu.machine.device().processing()){IndustrialUi.card(g,leftPos+87,topPos+57,(leftPos+174)-(leftPos+87),(topPos+65)-(topPos+57));g.fill(leftPos+88,topPos+58,leftPos+88+(int)(85*Math.clamp(progress,0,1)),topPos+64,IndustrialUi.ACCENT);}
        if(menu.info.has("preview")){ItemStack preview=ItemStack.OPTIONAL_CODEC.parse(minecraft.player.registryAccess().createSerializationContext(JsonOps.INSTANCE),menu.info.get("preview")).result().orElse(ItemStack.EMPTY);g.item(preview,leftPos+16,topPos+93);if(mx>=leftPos+14&&mx<=leftPos+34&&my>=topPos+91&&my<=topPos+111)g.setComponentTooltipForNextFrame(UiFonts.font(),Screen.getTooltipFromItem(minecraft,preview),mx,my,preview);}
        if(menu.machine.device().has(DeviceType.Capability.FLUID_PROCESSING)){fluidGauge(g,"fluid_in",leftPos+96,topPos+85,28,23,mx,my);fluidGauge(g,"fluid_out",leftPos+139,topPos+85,28,23,mx,my);}
        else if(menu.machine.device().has(DeviceType.Capability.FLUID_FUEL))fluidGauge(g,"fluid_in",leftPos+16,topPos+73,28,35,mx,my);
        g.text(UiFonts.font(),"?",leftPos+242,topPos+8,IndustrialUi.ACCENT,false);
        if(mx>=leftPos+238&&mx<=leftPos+254&&my>=topPos+4&&my<=topPos+22){
            List<Component> help=new ArrayList<>();if(menu.info.has("help"))for(var line:menu.info.getAsJsonArray("help"))help.add(Component.literal(line.getAsString()));else help.add(Component.literal("正在读取设备规则"));help.add(Component.literal("权杖：Shift右键空气切换供能/连网/传送模式。"));
            if(menu.machine.device().generator()){help.add(Component.literal("额定产能："+number("power")+" 点/秒；满网继续消耗。"));help.add(Component.literal("燃料余时："+seconds(number("fuel"))+"；缺少流体暂停。"));}
            if(menu.machine.device().has(DeviceType.Capability.STORAGE_NETWORK)){help.add(Component.literal("覆盖半径 "+number("radius")+" 格，三维直线距离。"));help.add(Component.literal(text("member_change","")));}
            if(menu.machine.device().has(DeviceType.Capability.LOGISTICS))help.add(Component.literal("目标："+text("target","未绑定")));
            g.setComponentTooltipForNextFrame(UiFonts.font(),help,mx,my);
        }
    }
    @Override protected void extractLabels(GuiGraphicsExtractor g,int mx,int my){
        g.text(UiFonts.font(),text("name",title.getString()),8,8,IndustrialUi.TEXT,false);
        var kind=menu.machine.device();boolean connected=menu.info.has("connected")&&menu.info.get("connected").getAsBoolean();g.text(UiFonts.font(),kind.has(DeviceType.Capability.STORAGE_NETWORK)?(number("network_capacity")>0?"● 基座有效":"○ 基座无效"):connected?"● 信标有效":"○ 未连接",88,46,connected||kind.has(DeviceType.Capability.STORAGE_NETWORK)&&number("network_capacity")>0?IndustrialUi.SUCCESS:IndustrialUi.MUTED,false);
        if(kind.processing()){double shown=kind.selectedRecipe()?progress.value(0):menu.info.has("progress")?menu.info.get("progress").getAsDouble():0;g.text(UiFonts.font(),String.format(Locale.ROOT,"%.1f%%",shown*100),112,72,IndustrialUi.TEXT,false);}
        else if(kind.generator()){g.text(UiFonts.font(),"产能 "+number("power")+" 点/秒",88,63,IndustrialUi.MUTED,false);g.text(UiFonts.font(),"燃料余时 "+seconds(number("fuel")),88,81,IndustrialUi.MUTED,false);}
        else if(kind.has(DeviceType.Capability.STORAGE_NETWORK)){g.text(UiFonts.font(),"核心储能 "+number("energy"),16,64,IndustrialUi.MUTED,false);g.text(UiFonts.font(),"覆盖半径 "+number("radius")+" 格",16,79,IndustrialUi.MUTED,false);}
        else {g.text(UiFonts.font(),kind.has(DeviceType.Capability.LOGISTICS)?"权杖绑定接收容器":"成熟作物自动入下方箱",16,65,IndustrialUi.MUTED,false);}
        g.text(UiFonts.font(),font.plainSubstrByWidth(text("status","读取设备状态"),240),8,111,IndustrialUi.GOLD,false);
        String line=kind.processing()?number("paid")+" / "+number("total_energy"):"网络 "+number("network_energy")+" / "+number("network_capacity");
        g.text(UiFonts.font(),line,8,124,IndustrialUi.MUTED,false);g.text(UiFonts.font(),"玩家背包",47,141,IndustrialUi.MUTED,false);
        if(menu.machine.device().has(DeviceType.Capability.STORAGE_NETWORK))g.text(UiFonts.font(),"发能 "+number("generated")+"  消耗 "+number("consumed")+"  溢出 "+number("overflow"),8,94,IndustrialUi.MUTED,false);
    }
    private static String seconds(long ticks){return String.format(Locale.ROOT,"%.1f 秒",ticks/20.0);}
    private void fluidGauge(GuiGraphicsExtractor g,String key,int x,int y,int w,int h,int mx,int my){
        IndustrialUi.card(g,x,y,w,h);long capacity=number("fluid_capacity"),amount=number(key);
        String id=text(key+"_id","");Component fluidTitle=Component.literal("空");
        if(amount>0&&capacity>0&&!id.isEmpty()){
            var fluid=net.minecraft.core.registries.BuiltInRegistries.FLUID.getValue(net.minecraft.resources.Identifier.parse(id));
            var stack=new net.neoforged.neoforge.fluids.FluidStack(fluid,(int)Math.min(Integer.MAX_VALUE,amount));
            fluidTitle=stack.getHoverName();
            var model=minecraft.getModelManager().getFluidStateModelSet().get(fluid.defaultFluidState());
            var sprite=model.stillMaterial().sprite();int tint=model.fluidTintSource()==null?-1:model.fluidTintSource().colorAsStack(stack);
            int fill=(int)Math.ceil((h-4)*Math.clamp((double)amount/capacity,0,1));
            g.enableScissor(x+2,y+h-2-fill,x+w-2,y+h-2);
            for(int row=y+2;row<y+h-2;row+=16)for(int col=x+2;col<x+w-2;col+=16)g.blitSprite(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,sprite,col,row,16,16,tint);
            g.disableScissor();
        }
        g.fill(x+3,y+3,x+4,y+h-3,0x66ffffff);for(int mark=y+7;mark<y+h-3;mark+=6)g.fill(x+w-7,mark,x+w-2,mark+1,0x99505050);
        if(mx>=x&&mx<x+w&&my>=y&&my<y+h)g.setComponentTooltipForNextFrame(UiFonts.font(),List.of(fluidTitle,Component.literal(amount+" / "+capacity+" mB")),mx,my);
    }
    private static final class Recipes extends Screen {
        private final IndustrialScreen parent;private int page;
        Recipes(IndustrialScreen p){super(Component.literal("选择加工配方"));parent=p;}
        @Override protected void init(){controls();}
        private void controls(){clearWidgets();JsonArray list=parent.menu.info.has("recipes")?parent.menu.info.getAsJsonArray("recipes"):new JsonArray();int x=(width-290)/2,y=(height-230)/2;for(int i=0;i<8&&page*8+i<list.size();i++){int index=page*8+i;JsonObject recipe=list.get(index).getAsJsonObject();boolean allowed=recipe.get("tier").getAsInt()<=parent.menu.machine.tier();String name=recipe.get("name").getAsString()+" · "+recipe.get("energy").getAsLong()+"点"+(allowed?"":" · 需要"+recipe.get("tier").getAsInt()+"级");Button b=Button.builder(Component.literal(name),button->{minecraft.gameMode.handleInventoryButtonClick(parent.menu.containerId,100+index);onClose();}).bounds(x,y+28+i*22,290,20).build(IndustrialUi.IndustrialButton::new);b.active=allowed;b.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(recipe.get("ingredients").getAsString()+"\n基准时间 "+recipe.get("ticks").getAsInt()+" tick；总耗能 "+recipe.get("energy").getAsLong()+" 点；最低 "+recipe.get("tier").getAsInt()+" 级")));addRenderableWidget(b);}addRenderableWidget(Button.builder(Component.literal("上一页"),b->{page=Math.max(0,page-1);controls();}).bounds(x,y+210,80,20).build(IndustrialUi.IndustrialButton::new));addRenderableWidget(Button.builder(Component.literal("取消选择"),b->{minecraft.gameMode.handleInventoryButtonClick(parent.menu.containerId,4);onClose();}).bounds(x+105,y+210,80,20).build(IndustrialUi.IndustrialButton::new));addRenderableWidget(Button.builder(Component.literal("下一页"),b->{page=Math.min(Math.max(0,(list.size()-1)/8),page+1);controls();}).bounds(x+210,y+210,80,20).build(IndustrialUi.IndustrialButton::new));}
        @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float a){g.fill(0,0,width,height,IndustrialUi.OVERLAY);int x=(width-300)/2,y=(height-240)/2;IndustrialUi.panel(g,x,y,(x+300)-(x),(y+240)-(y));g.text(UiFonts.font(),"选择配方 · 当前批次完成后切换",x+10,y+10,IndustrialUi.TEXT,false);super.extractRenderState(g,mx,my,a);}
        @Override public boolean isPauseScreen(){return false;}
        @Override public void onClose(){minecraft.gui.popScreenLayer();}
    }
    private static final class Structure extends Screen {
        private final IndustrialScreen parent;Structure(IndustrialScreen p){super(Component.literal("结构搭建"));parent=p;}
        @Override protected void init(){addRenderableWidget(Button.builder(Component.literal("返回"),b->onClose()).bounds((width+170)/2,(height-180)/2,50,20).build(IndustrialUi.IndustrialButton::new));}
        @Override public void extractBackground(GuiGraphicsExtractor g,int mx,int my,float delta){
            int left=(width-300)/2,top=(height-210)/2;g.fill(0,0,width,height,IndustrialUi.OVERLAY);IndustrialUi.panel(g,left,top,(left+300)-(left),(top+210)-(top));g.text(UiFonts.font(),"结构搭建 · "+parent.text("structure_material","读取材料"),left+12,top+15,IndustrialUi.TEXT,false);
            var be=parent.menu.machine;boolean forge=be.device()==DeviceType.FORGE;Set<Long> missing=new HashSet<>();if(parent.menu.info.has("missing"))for(var p:parent.menu.info.getAsJsonArray("missing"))missing.add(p.getAsLong());BlockPos center=forge?be.getBlockPos().relative(be.getBlockState().getValue(MachineBlock.FACING).getOpposite()).above():be.getBlockPos().below();
            for(int layer=0;layer<(forge?3:1);layer++){int x0=left+20+layer*92,y0=top+60;g.text(UiFonts.font(),forge?new String[]{"底层","中层","顶层"}[layer]:"3×3基座",x0,y0-15,IndustrialUi.TEXT,false);for(int z=-1;z<=1;z++)for(int x=-1;x<=1;x++){BlockPos pos=center.offset(x,forge?layer-1:0,z);boolean control=pos.equals(be.getBlockPos()),air=forge&&layer==1&&x==0&&z==0,bad=missing.contains(pos.asLong());int sx=x0+(x+1)*19,sy=y0+(z+1)*19;g.fill(sx,sy,sx+17,sy+17,control?IndustrialUi.GOLD:air?IndustrialUi.CARD:bad?IndustrialUi.ERROR:IndustrialUi.SUCCESS);g.text(UiFonts.font(),control?"控":air?"空":bad?"×":"✓",sx+4,sy+5,IndustrialUi.TEXT,false);if(mx>=sx&&mx<sx+17&&my>=sy&&my<sy+17)g.setComponentTooltipForNextFrame(UiFonts.font(),List.of(Component.literal(pos.toShortString()),Component.literal(control?"控制器":air?"中心留空":bad?"此处缺少正确材料":"材料正确")),mx,my);}}
            g.text(UiFonts.font(),forge?"控制器位于底层正面中央，中心留空。":"核心位于基座上方中央。",left+12,top+145,IndustrialUi.TEXT,false);g.text(UiFonts.font(),forge?"其余25处使用同级材料；下方可放输出容器。":"九块同级材料；允许地下使用。",left+12,top+163,IndustrialUi.MUTED,false);g.text(UiFonts.font(),"当前缺失 "+missing.size()+" 处",left+12,top+186,missing.isEmpty()?IndustrialUi.SUCCESS:IndustrialUi.ERROR,false);
        }
        @Override public void onClose(){minecraft.gui.popScreenLayer();}
        @Override public boolean isPauseScreen(){return false;}
    }
    private static final class Currency extends Screen {
        Currency(IndustrialScreen p){super(Component.literal("工业通货概率"));}
        @Override protected void init(){addRenderableWidget(Button.builder(Component.literal("返回"),b->onClose()).bounds((width+180)/2,(height-230)/2,50,20).build(IndustrialUi.IndustrialButton::new));}
        @Override public void extractBackground(GuiGraphicsExtractor g,int mx,int my,float delta){int x=(width-310)/2,y=(height-260)/2;g.fill(0,0,width,height,IndustrialUi.OVERLAY);IndustrialUi.panel(g,x,y,(x+310)-(x),(y+260)-(y));g.text(UiFonts.font(),"每轮必出1枚 · 玩家掉宝不参与",x+12,y+15,IndustrialUi.TEXT,false);var rules=IndustrialRules.current();double[] probabilities=cn.yumis.fight.modules.economy.api.CurrencyRules.factoryProbabilities(rules.currencyRarityWeights,rules.currencyItemWeights);int row=0;for(int i=0;i<probabilities.length;i++)if(probabilities[i]>0){var currency=cn.yumis.fight.modules.economy.api.CurrencyRules.ALL.get(i);ItemStack stack=ItemRegistry.item(currency.id(),1);g.item(stack,x+14,y+38+row*20);g.text(UiFonts.font(),stack.getHoverName(),x+38,y+43+row*20,IndustrialUi.TEXT,false);g.text(UiFonts.font(),String.format(java.util.Locale.ROOT,"%.2f%%",probabilities[i]*100),x+230,y+43+row*20,IndustrialUi.TEXT,false);row++;}}
        @Override public void onClose(){minecraft.gui.popScreenLayer();}
        @Override public boolean isPauseScreen(){return false;}
    }
}
