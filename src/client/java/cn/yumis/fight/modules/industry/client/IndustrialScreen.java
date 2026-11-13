package cn.yumis.fight.modules.industry.client;
import cn.yumis.fight.modules.presentation.api.client.*;
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
import net.minecraft.client.input.MouseButtonEvent;
import com.mojang.serialization.JsonOps;
import java.util.*;

public final class IndustrialScreen extends ThemedContainerScreen<IndustrialMenu> {
    public boolean lines;
    private Button linePicker;
    private final IndustrialProgress progress=new IndustrialProgress();
    private final IndustrialFluidDisplay fluidDisplay=new IndustrialFluidDisplay();
    private record FluidGauge(String key,int tank,int x,int y,int width,int height){
        boolean contains(double mx,double my){return mx>=x&&mx<x+width&&my>=y&&my<y+height;}
    }
    private static final List<FluidGauge> REACTOR_GAUGES=List.of(new FluidGauge("fluid_in",0,88,68,23,70),new FluidGauge("fluid_in2",1,116,68,23,70),new FluidGauge("fluid_out",2,144,68,23,70));
    private static final List<FluidGauge> PROCESS_GAUGES=List.of(new FluidGauge("fluid_in",0,96,68,28,70),new FluidGauge("fluid_out",1,139,68,28,70));
    private static final List<FluidGauge> INFUSION_GAUGES=List.of(new FluidGauge("fluid_in",0,116,68,28,70));
    private static final List<FluidGauge> MINER_GAUGES=List.of(new FluidGauge("fluid_in",0,116,68,28,70));
    private static final List<FluidGauge> PUMP_GAUGES=List.of(new FluidGauge("fluid_out",1,116,68,28,70));
    private static final List<FluidGauge> STORAGE_GAUGES=List.of(new FluidGauge("fluid_out",0,16,62,32,70));
    private static final List<FluidGauge> FUEL_GAUGES=List.of(new FluidGauge("fluid_in",0,16,62,28,70));
    private static final List<FluidGauge> IGNITION_GAUGES=List.of(new FluidGauge("fluid_in",0,139,68,28,70));
    private int pendingDrainTank=-1;
    private long pendingDrainClick;
    private List<FluidGauge> fluidGauges(){
        var kind=menu.machine.device();
        if(kind.has(DeviceType.Capability.INFUSION))return INFUSION_GAUGES;
        if(kind.has(DeviceType.Capability.IGNITION))return IGNITION_GAUGES;
        if(kind.has(DeviceType.Capability.DUAL_FLUID))return REACTOR_GAUGES;
        if(kind.has(DeviceType.Capability.FLUID_STORAGE))return STORAGE_GAUGES;
        if(kind.has(DeviceType.Capability.PUMP))return PUMP_GAUGES;
        if(kind.has(DeviceType.Capability.MINING))return MINER_GAUGES;
        if(kind.has(DeviceType.Capability.FLUID_FUEL))return FUEL_GAUGES;
        return kind.has(DeviceType.Capability.FLUID_PROCESSING)?PROCESS_GAUGES:List.of();
    }
    @Override protected void containerTick(){super.containerTick();progress.tick(menu.info);if(linePicker!=null){linePicker.setMessage(Component.literal("产线 "+(menu.viewedLine+1)+"/"+menu.machine.lineCount()));linePicker.active=menu.machine.lineCount()>1;}}

    public IndustrialScreen(IndustrialMenu menu,Inventory inventory,Component title){super(menu,inventory,title,312,238,ThemeDomain.MACHINE);inventoryLabelY=141;}
    @Override protected void init(){
        pendingDrainTank=-1;progress.tick(menu.info);super.init();var kind=menu.machine.device();int x=8;
        if(kind.processing()||kind.generator()||kind.functional()||kind.currencyUpgrader()){button("开关",x,25,32,0);x+=35;}
        if(kind.selectedRecipe()||kind.randomCurrency()){final int at=x;addRenderableWidget(Button.builder(Component.literal(kind.randomCurrency()?"概率":"配方"),b->minecraft.gui.pushScreenLayer(kind.randomCurrency()?new Currency(this):new Recipes(this))).bounds(leftPos+at,topPos+25,34,18).build(IndustrialUi.IndustrialButton::new));x+=37;}
        if(kind.needsStructure()){final int at=x;addRenderableWidget(Button.builder(Component.literal("结构"),b->minecraft.gui.pushScreenLayer(new Structure(this))).bounds(leftPos+at,topPos+25,34,18).build(IndustrialUi.IndustrialButton::new));x+=37;}
        button("连线",x,25,34,3);
        if(kind.has(DeviceType.Capability.LOGISTICS)||kind.has(DeviceType.Capability.FLUID_LOGISTICS))addRenderableWidget(Button.builder(Component.literal("配对"),b->minecraft.gui.pushScreenLayer(new Routes(this))).bounds(leftPos+x+37,topPos+25,38,18).build(IndustrialUi.IndustrialButton::new));
        addRenderableWidget(Button.builder(Component.literal(menu.showPlugins?"◀":"▶"),b->{menu.showPlugins=!menu.showPlugins;minecraft.gameMode.handleInventoryButtonClick(menu.containerId,6);b.setMessage(Component.literal(menu.showPlugins?"◀":"▶"));}).bounds(leftPos+255,topPos+49,15,21).build(IndustrialUi.IndustrialButton::new));
        linePicker=null;
        if(kind.has(DeviceType.Capability.PARALLEL))linePicker=addRenderableWidget(Button.builder(Component.literal("产线 "+(menu.viewedLine+1)+"/"+menu.machine.lineCount()),b->minecraft.gui.pushScreenLayer(new ProductionLines(this))).bounds(leftPos+176,topPos+25,72,18).build(IndustrialUi.IndustrialButton::new));
    }
    @Override public boolean mouseClicked(MouseButtonEvent event,boolean doubleClick){
        if(event.button()==0)for(var gauge:fluidGauges())if(gauge.contains(event.x()-leftPos,event.y()-topPos)){
            if(event.hasControlDown()){
                long now=System.nanoTime();
                if(pendingDrainTank==gauge.tank()&&now-pendingDrainClick<=500_000_000L){
                    pendingDrainTank=-1;minecraft.gameMode.handleInventoryButtonClick(menu.containerId,70+gauge.tank());
                }else{pendingDrainTank=gauge.tank();pendingDrainClick=now;}
            }else pendingDrainTank=-1;
            return true;
        }
        pendingDrainTank=-1;return super.mouseClicked(event,doubleClick);
    }
    private Button button(String text,int x,int y,int w,int id){return addRenderableWidget(Button.builder(Component.literal(text),b->minecraft.gameMode.handleInventoryButtonClick(menu.containerId,id)).bounds(leftPos+x,topPos+y,w,18).build(IndustrialUi.IndustrialButton::new));}
    private String text(String key,String fallback){return menu.info.has(key)?menu.info.get(key).getAsString():fallback;}
    private long number(String key){return Set.of("fluid_in","fluid_in2","fluid_out").contains(key)?fluidDisplay.amount(menu.info,key):menu.info.has(key)?menu.info.get(key).getAsLong():0;}
    @Override protected void drawBackground(GuiGraphicsExtractor g,int mx,int my,float alpha){
        g.fill(0,0,width,height,UiTheme.OVERLAY);IndustrialUi.panel(g,leftPos,topPos,256,imageHeight);if(menu.showPlugins){UiTheme.panel(g,leftPos+272,topPos+33,40,104);g.text(UiFonts.font(),"插件",leftPos+279,topPos+39,UiTheme.TEXT,false);}
        for(var slot:menu.slots)if(slot.isActive()){int x=leftPos+slot.x,y=topPos+slot.y;IndustrialUi.slot(g,x,y);}
        double progress=(menu.machine.device().currencyUpgrader()||menu.machine.device().has(DeviceType.Capability.IGNITION))&&menu.info.has("progress")?menu.info.get("progress").getAsDouble():this.progress.value(alpha);if(menu.machine.device().processing()||menu.machine.device().currencyUpgrader()||menu.machine.device().has(DeviceType.Capability.IGNITION)){IndustrialUi.card(g,leftPos+87,topPos+57,(leftPos+174)-(leftPos+87),(topPos+65)-(topPos+57));g.fill(leftPos+88,topPos+58,leftPos+88+(int)(85*Math.clamp(progress,0,1)),topPos+64,UiTheme.ACCENT);}
        if(menu.info.has("preview")){ItemStack preview=ItemStack.OPTIONAL_CODEC.parse(minecraft.player.registryAccess().createSerializationContext(JsonOps.INSTANCE),menu.info.get("preview")).result().orElse(ItemStack.EMPTY);g.item(preview,leftPos+16,topPos+93);if(mx>=leftPos+14&&mx<=leftPos+34&&my>=topPos+91&&my<=topPos+111)g.setComponentTooltipForNextFrame(UiFonts.font(),Screen.getTooltipFromItem(minecraft,preview),mx,my,preview);}
        for(var gauge:fluidGauges())fluidGauge(g,gauge.key(),leftPos+gauge.x(),topPos+gauge.y(),gauge.width(),gauge.height(),mx,my);
        g.text(UiFonts.font(),"?",leftPos+242,topPos+8,UiTheme.ACCENT,false);
        if(mx>=leftPos+238&&mx<=leftPos+254&&my>=topPos+4&&my<=topPos+22){
            List<Component> help=new ArrayList<>();if(menu.info.has("help"))for(var line:menu.info.getAsJsonArray("help"))help.add(Component.literal(line.getAsString()));else help.add(Component.literal("正在读取设备规则"));help.add(Component.literal("权杖：Shift右键空气切换移动／联网，右键空气取消选择。"));help.add(Component.literal("插件：速度 ×"+text("speed_multiplier","1")+"，单批耗能 ×"+number("energy_multiplier")+"，存储 ×"+number("capacity_multiplier")));
            if(menu.machine.device().generator()){help.add(Component.literal("额定产能："+number("power")+" 点/秒；满网继续发电。"));if(menu.machine.device().has(DeviceType.Capability.FUEL))help.add(Component.literal("燃料余时："+seconds(number("fuel"))+"；缺少流体暂停。"));}
            if(menu.machine.device().has(DeviceType.Capability.STORAGE_NETWORK)){help.add(Component.literal("覆盖半径 "+number("radius")+" 格，三维直线距离。"));help.add(Component.literal(text("member_change","")));}
            if(menu.machine.device().has(DeviceType.Capability.LOGISTICS))help.add(Component.literal("目标："+text("target","未绑定")));
            g.setComponentTooltipForNextFrame(UiFonts.font(),help,mx,my);
        }
    }
    @Override protected void drawContents(GuiGraphicsExtractor g,int mx,int my,float alpha){super.drawContents(g,mx,my,alpha);g.nextStratum();
        if(menu.info.has("slot_amounts")){var amounts=menu.info.getAsJsonArray("slot_amounts");for(int i=0;i<menu.machine.getContainerSize()&&i<amounts.size();i++){var slot=menu.slots.get(i);long quantity=amounts.get(i).getAsLong();if(slot.isActive()&&quantity>1){String label=quantity<10000?Long.toString(quantity):quantity<1000000?String.format(Locale.ROOT,"%.1fk",quantity/1000d):quantity<1000000000?String.format(Locale.ROOT,"%.1fm",quantity/1000000d):String.format(Locale.ROOT,"%.1fb",quantity/1000000000d);g.pose().pushMatrix();g.pose().translate(leftPos+slot.x,topPos+slot.y+11);g.pose().scale(.65f,.65f);g.text(UiFonts.font(),label,0,0,UiTheme.GOLD,true);g.pose().popMatrix();if(mx>=leftPos+slot.x&&mx<leftPos+slot.x+16&&my>=topPos+slot.y&&my<topPos+slot.y+16)quantityTooltip(g,slot.getItem(),quantity,mx,my);}}}
    }
    private void quantityTooltip(GuiGraphicsExtractor graphics,ItemStack stack,long quantity,int mx,int my){var lines=new ArrayList<>(Screen.getTooltipFromItem(minecraft,stack));lines.add(Component.literal("储量 "+quantity));graphics.setComponentTooltipForNextFrame(UiFonts.font(),lines,mx,my,stack);}
    @Override protected void extractLabels(GuiGraphicsExtractor g,int mx,int my){
        g.text(UiFonts.font(),text("name",title.getString()),8,8,UiTheme.TEXT,false);
        var kind=menu.machine.device();var gauges=fluidGauges();boolean centralGauges=!gauges.isEmpty()&&gauges.getFirst().x()>=88;
        boolean connected=menu.info.has("connected")&&menu.info.get("connected").getAsBoolean();g.text(UiFonts.font(),kind.has(DeviceType.Capability.STORAGE_NETWORK)?(number("network_capacity")>0?"● 信标就绪":"○ 信标未就绪"):connected?"● 信标有效":"○ 未连接",88,46,connected||kind.has(DeviceType.Capability.STORAGE_NETWORK)&&number("network_capacity")>0?UiTheme.SUCCESS:UiTheme.MUTED,false);
        if(kind.processing()||kind.currencyUpgrader()){}
        else if(kind.has(DeviceType.Capability.GENERATION_OUTPUT)){int labelX=kind.has(DeviceType.Capability.IGNITION)?16:56;g.text(UiFonts.font(),"产能 "+number("power")+"/秒",labelX,80,UiTheme.MUTED,false);g.text(UiFonts.font(),"燃料 "+seconds(number("fuel")),labelX,96,UiTheme.MUTED,false);}
        else if(kind.generator()){g.text(UiFonts.font(),"产能 "+number("power")+" 点/秒",88,63,UiTheme.MUTED,false);g.text(UiFonts.font(),"燃料余时 "+seconds(number("fuel")),88,81,UiTheme.MUTED,false);}
        else if(kind.has(DeviceType.Capability.STORAGE_NETWORK)){g.text(UiFonts.font(),"核心储能 "+number("energy"),16,64,UiTheme.MUTED,false);g.text(UiFonts.font(),"覆盖半径 "+number("radius")+" 格",16,79,UiTheme.MUTED,false);}
        else if(kind.has(DeviceType.Capability.ENERGY_STORAGE)){g.text(UiFonts.font(),"本机 "+number("energy")+" / "+number("storage_capacity"),16,68,UiTheme.TEXT,false);g.text(UiFonts.font(),"接网增加容量；断网保留余额",16,87,UiTheme.MUTED,false);}
        else if(kind.has(DeviceType.Capability.FLUID_STORAGE)){g.text(UiFonts.font(),"一罐一种液体 · "+number("fluid_capacity")+" mB",56,66,UiTheme.TEXT,false);g.text(UiFonts.font(),"原地升级保留；拆除清空",56,84,UiTheme.MUTED,false);}
        else if(kind.has(DeviceType.Capability.PUMP)){g.text(UiFonts.font(),"相邻水源",16,65,UiTheme.MUTED,false);g.text(UiFonts.font(),"六面供液",16,81,UiTheme.MUTED,false);}
        else {g.text(UiFonts.font(),kind.has(DeviceType.Capability.LOGISTICS)?"权杖绑定接收容器":kind.has(DeviceType.Capability.FLUID_LOGISTICS)?"权杖绑定源出口和目标入口":"成熟作物自动入下方箱",16,65,UiTheme.MUTED,false);}
        int statusX=!gauges.isEmpty()&&!centralGauges?56:8,statusWidth=centralGauges?76:248-statusX;
        g.text(UiFonts.font(),font.plainSubstrByWidth(text("status","读取设备状态"),statusWidth),statusX,111,UiTheme.GOLD,false);
        if(!kind.processing()){
            String line=kind.currencyUpgrader()?"缓存 "+number("energy")+" / "+number("upgrade_capacity"):"网络 "+number("network_energy")+" / "+number("network_capacity");
            if(!centralGauges)g.text(UiFonts.font(),font.plainSubstrByWidth(line,statusWidth),statusX,124,UiTheme.MUTED,false);
        }
        if(mx>=leftPos+statusX&&mx<leftPos+statusX+statusWidth&&my>=topPos+111&&my<topPos+122)g.setComponentTooltipForNextFrame(UiFonts.font(),List.of(Component.literal(text("status","读取设备状态"))),mx,my);
        g.text(UiFonts.font(),"玩家背包",47,141,UiTheme.MUTED,false);
        if(menu.machine.device().has(DeviceType.Capability.STORAGE_NETWORK))g.text(UiFonts.font(),"发能 "+number("generated")+"  消耗 "+number("consumed")+"  溢出 "+number("overflow"),8,94,UiTheme.MUTED,false);
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
        if(mx>=x&&mx<x+w&&my>=y&&my<y+h){var lines=new ArrayList<Component>();lines.add(fluidTitle);int tier=cn.yumis.fight.modules.industry.api.IndustrialTechnology.fluidTier(id);if(tier>0)lines.add(Component.literal("材料等级 · "+cn.yumis.fight.modules.industry.api.IndustrialTechnology.label(tier)));lines.add(Component.literal(amount+" / "+capacity+" mB"));lines.add(Component.literal("批次预留 "+number(key+"_reserved")+" mB"));lines.add(Component.literal("Ctrl双击排出未预留液体。"));g.setComponentTooltipForNextFrame(UiFonts.font(),lines,mx,my);}
    }
    private static final class ProductionLines extends ThemedScreen {
        private final IndustrialScreen parent;
        private final List<Button> buttons=new ArrayList<>();
        ProductionLines(IndustrialScreen parent){super(Component.literal("并行产线"),ThemeDomain.MACHINE);this.parent=parent;}
        @Override protected void init(){
            buttons.clear();
            int x=(width-328)/2+8,y=(height-260)/2+36;
            for(int index=0;index<parent.menu.machine.lineCount();index++){
                final int selected=index;
                Button button=Button.builder(Component.empty(),b->{parent.pendingDrainTank=-1;minecraft.gameMode.handleInventoryButtonClick(parent.menu.containerId,20+selected);onClose();}).bounds(x+(index%4)*78,y+(index/4)*44,74,36).build(IndustrialUi.IndustrialButton::new);
                buttons.add(addRenderableWidget(button));
            }
            addRenderableWidget(Button.builder(Component.literal("返回"),b->onClose()).bounds(x+116,y+190,80,20).build(IndustrialUi.IndustrialButton::new));
            refresh();
        }
        private void refresh(){
            JsonArray states=parent.menu.info.has("production_lines")?parent.menu.info.getAsJsonArray("production_lines"):new JsonArray();
            for(int index=0;index<buttons.size();index++){
                JsonObject state=index<states.size()?states.get(index).getAsJsonObject():new JsonObject();
                double progress=state.has("progress")?state.get("progress").getAsDouble():0;
                String status=state.has("status")?state.get("status").getAsString():"等待读取";
                Button button=buttons.get(index);button.setMessage(Component.literal("产线 "+(index+1)));
                button.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(status+"\n点击查看本线原料、产物和配方")));
            }
        }
        @Override public void tick(){super.tick();if(buttons.size()!=parent.menu.machine.lineCount())rebuildWidgets();else refresh();}
        @Override protected void drawBackground(GuiGraphicsExtractor graphics,int mouseX,int mouseY,float alpha){
            int left=(width-328)/2,top=(height-260)/2;graphics.fill(0,0,width,height,UiTheme.OVERLAY);IndustrialUi.panel(graphics,left,top,328,260);
            graphics.text(UiFonts.font(),"并行产线 · 各线共享供能，输入与输出独立",left+10,top+12,UiTheme.TEXT,false);
        }
        @Override protected void drawContents(GuiGraphicsExtractor g,int mx,int my,float alpha){super.drawContents(g,mx,my,alpha);var states=parent.menu.info.has("production_lines")?parent.menu.info.getAsJsonArray("production_lines"):new JsonArray();for(int i=0;i<buttons.size()&&i<states.size();i++){var button=buttons.get(i);double value=states.get(i).getAsJsonObject().get("progress").getAsDouble();UiTheme.card(g,button.getX()+5,button.getY()+button.getHeight()-8,button.getWidth()-10,4);g.fill(button.getX()+6,button.getY()+button.getHeight()-7,button.getX()+6+(int)((button.getWidth()-12)*Math.clamp(value,0,1)),button.getY()+button.getHeight()-5,UiTheme.ACCENT);}}
        @Override public boolean isPauseScreen(){return false;}
        @Override public void onClose(){minecraft.gui.popScreenLayer();}
    }
    private static final class Recipes extends ThemedScreen {
        private final IndustrialScreen parent;private int page;
        Recipes(IndustrialScreen parent){super(Component.literal(parent.menu.machine.device().has(DeviceType.Capability.MINING)?"选择开采矿物":"选择加工工艺"),ThemeDomain.MACHINE);this.parent=parent;}
        @Override protected void init(){controls();}
        private void controls(){
            clearWidgets();var list=parent.menu.info.has("recipes")?parent.menu.info.getAsJsonArray("recipes"):new JsonArray();int columns=Math.max(1,Math.min(5,(width-32)/70)),rows=Math.max(1,Math.min(3,(height-90)/62)),perPage=columns*rows,w=columns*70,x=(width-w)/2,y=(height-(rows*62+62))/2;
            page=Math.clamp(page,0,Math.max(0,(list.size()-1)/perPage));
            for(int i=0;i<perPage&&page*perPage+i<list.size();i++){int index=page*perPage+i;var recipe=list.get(index).getAsJsonObject();var icon=recipe.has("icon")?ItemStack.OPTIONAL_CODEC.parse(minecraft.player.registryAccess().createSerializationContext(JsonOps.INSTANCE),recipe.get("icon")).result().orElse(ItemStack.EMPTY):ItemStack.EMPTY;
                var tile=Button.builder(Component.literal(recipe.has("mineral_name")?recipe.get("mineral_name").getAsString():recipe.get("name").getAsString()),b->{minecraft.gameMode.handleInventoryButtonClick(parent.menu.containerId,100+index);onClose();}).bounds(x+(i%columns)*70,y+25+(i/columns)*62,66,58).build(builder->new GridItemButton(builder,icon));
                tile.active=recipe.get("tier").getAsInt()<=parent.menu.machine.processingTier();tile.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(recipe.get("name").getAsString()+"\n"+recipe.get("ingredients").getAsString()+"\n基准时间 "+String.format(Locale.ROOT,"%.2f 秒",recipe.get("seconds").getAsDouble())+" · 单批耗能 "+recipe.get("energy").getAsLong()+"\n最低工艺 T"+recipe.get("tier").getAsInt()+" · 能耗 T"+recipe.get("energy_tier").getAsInt())));addRenderableWidget(tile);
            }
            String reset=parent.menu.machine.device().has(DeviceType.Capability.MINING)?"停止开采":parent.menu.machine.device().has(DeviceType.Capability.REQUIRE_RECIPE)?"取消工艺":"自动匹配";
            int footer=y+rows*62+30;addRenderableWidget(Button.builder(Component.literal("上一页"),b->{page--;controls();}).bounds(x,footer,66,20).build(IndustrialUi.IndustrialButton::new));addRenderableWidget(Button.builder(Component.literal(reset),b->{minecraft.gameMode.handleInventoryButtonClick(parent.menu.containerId,4);onClose();}).bounds(x+(w-70)/2,footer,70,20).build(IndustrialUi.IndustrialButton::new));addRenderableWidget(Button.builder(Component.literal("下一页"),b->{page++;controls();}).bounds(x+w-66,footer,66,20).build(IndustrialUi.IndustrialButton::new));
        }
        @Override protected void drawBackground(GuiGraphicsExtractor g,int mx,int my,float delta){g.fill(0,0,width,height,UiTheme.OVERLAY);UiTheme.panel(g,10,8,width-20,height-16);g.text(UiFonts.font(),(parent.menu.machine.device().has(DeviceType.Capability.MINING)?"选择矿物":"选择工艺")+" · 当前批次完成后切换",22,15,UiTheme.TEXT,false);}
        @Override public boolean isPauseScreen(){return false;}@Override public void onClose(){minecraft.gui.popScreenLayer();}
    }
    private static final class Routes extends ThemedScreen {
        private final IndustrialScreen parent;private int scroll;
        Routes(IndustrialScreen parent){super(Component.literal("水晶容器配对"),ThemeDomain.MACHINE);this.parent=parent;}
        private void action(String route,String action){var request=new JsonObject();request.addProperty("menu",parent.menu.containerId);request.addProperty("route",route);request.addProperty("action",action);ClientUi.current().send("industrialRoute",request);}
        @Override protected void init(){controls();}
        private void controls(){clearWidgets();int w=Math.min(390,width-24),left=(width-w)/2,top=32,visible=Math.max(1,(height-70)/58);var list=parent.menu.info.has("routes")?parent.menu.info.getAsJsonArray("routes"):new JsonArray();scroll=Math.clamp(scroll,0,Math.max(0,list.size()-visible));
            for(int row=0;row<visible&&row+scroll<list.size();row++){int index=row+scroll;var route=list.get(index).getAsJsonObject();int y=top+row*58;
                for(int endpoint=0;endpoint<2;endpoint++){String key=endpoint==0?"source":"target";int buttonId=1100+index*2+endpoint;String label=(endpoint==0?"来源 ":"目标 ")+route.get(key+"_name").getAsString()+" "+BlockPos.of(route.get(key).getAsLong()).toShortString();var button=Button.builder(Component.literal(label),b->action(route.get("id").getAsString(),key)).bounds(left,y+endpoint*23,w-42,21).build(IndustrialUi.IndustrialButton::new);button.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(label+"\n"+route.get("status").getAsString()+"\n点击坐标显示连接光束")));addRenderableWidget(button);}
                addRenderableWidget(Button.builder(Component.literal("删除"),b->{action(route.get("id").getAsString(),"delete");}).bounds(left+w-38,y+10,38,22).build(IndustrialUi.IndustrialButton::new));
            }
            addRenderableWidget(Button.builder(Component.literal("返回"),b->onClose()).bounds(left,height-29,55,20).build(IndustrialUi.IndustrialButton::new));
        }
        @Override public void tick(){super.tick();controls();}
        @Override public boolean mouseScrolled(double x,double y,double sx,double sy){scroll=Math.max(0,scroll-(int)Math.signum(sy));controls();return true;}
        @Override protected void drawBackground(GuiGraphicsExtractor g,int mx,int my,float delta){g.fill(0,0,width,height,UiTheme.OVERLAY);UiTheme.panel(g,8,5,width-16,height-10);g.text(UiFonts.font(),"移动"+(parent.menu.machine.device().has(DeviceType.Capability.LOGISTICS)?"物体":"液体")+" · "+parent.menu.info.getAsJsonArray("routes").size()+" / "+parent.number("route_limit")+" 对",20,14,UiTheme.TEXT,false);}
        @Override public boolean isPauseScreen(){return false;}@Override public void onClose(){minecraft.gui.popScreenLayer();}
    }
    private static final class Structure extends ThemedScreen {
        private final IndustrialScreen parent;
        private int layer;
        Structure(IndustrialScreen parent){super(Component.literal("结构搭建"),ThemeDomain.MACHINE);this.parent=parent;}
        private JsonObject definition(){return parent.menu.info.getAsJsonObject("structure");}
        @Override protected void init(){
            int left=(width-300)/2,top=(height-240)/2;
            addRenderableWidget(Button.builder(Component.literal("上一层"),b->layer=Math.max(0,layer-1)).bounds(left+12,top+205,66,20).build(IndustrialUi.IndustrialButton::new));
            addRenderableWidget(Button.builder(Component.literal("下一层"),b->layer=Math.min(definition().get("size").getAsInt()-1,layer+1)).bounds(left+82,top+205,66,20).build(IndustrialUi.IndustrialButton::new));
            addRenderableWidget(Button.builder(Component.literal("返回"),b->onClose()).bounds(left+232,top+205,55,20).build(IndustrialUi.IndustrialButton::new));
        }
        @Override protected void drawBackground(GuiGraphicsExtractor g,int mx,int my,float delta){
            int left=(width-300)/2,top=(height-240)/2;
            g.fill(0,0,width,height,UiTheme.OVERLAY);IndustrialUi.panel(g,left,top,300,240);
            if(!parent.menu.info.has("structure"))return;
            var data=definition();int size=data.get("size").getAsInt();layer=Math.clamp(layer,0,size-1);boolean automatic=data.has("automatic")&&data.get("automatic").getAsBoolean();
            g.text(UiFonts.font(),"结构 · "+size+"×"+size+"×"+size+" · 第"+(layer+1)+"层",left+12,top+12,UiTheme.TEXT,false);
            g.text(UiFonts.font(),parent.text("structure_material","机外壳"),left+12,top+28,UiTheme.TEXT,false);
            Set<Long> missing=new HashSet<>();if(parent.menu.info.has("missing"))for(var value:parent.menu.info.getAsJsonArray("missing"))missing.add(value.getAsLong());
            int cell=22,x0=left+(300-size*cell)/2,y0=top+48;
            for(var value:data.getAsJsonArray("cells")){
                var item=value.getAsJsonObject();if(item.get("y").getAsInt()!=layer)continue;
                int x=x0+item.get("x").getAsInt()*cell,y=y0+item.get("z").getAsInt()*cell;
                long position=item.get("position").getAsLong();boolean control=item.get("controller").getAsBoolean(),air=item.get("air").getAsBoolean(),bad=missing.contains(position);
                g.fill(x,y,x+20,y+20,control?UiTheme.GOLD:bad?UiTheme.ERROR:air?UiTheme.CARD:UiTheme.SUCCESS);
                g.text(UiFonts.font(),control?"控":bad?"×":air?"空":"✓",x+6,y+6,UiTheme.TEXT,false);
                if(mx>=x&&mx<x+20&&my>=y&&my<y+20)g.setComponentTooltipForNextFrame(UiFonts.font(),List.of(Component.literal(BlockPos.of(position).toShortString()),Component.literal(control?"控制器":automatic?(bad?"整机部件缺失":air?"悬浮留空区域":"整机部件完整"):air?"内部留空":bad?"缺少正确机壳":"机壳正确")),mx,my);
            }
            g.text(UiFonts.font(),automatic?"底层中央放置一台，自动组成整机。":"控制器在底层正面中央，内部留空。",left+12,top+170,UiTheme.TEXT,false);
            g.text(UiFonts.font(),(automatic?"整机自动成型":"机壳 "+data.get("shell_count").getAsInt())+" · 缺失 "+missing.size()+" 处",left+12,top+187,missing.isEmpty()?UiTheme.SUCCESS:UiTheme.ERROR,false);
        }
        @Override public void onClose(){minecraft.gui.popScreenLayer();}
        @Override public boolean isPauseScreen(){return false;}
    }
    private static final class Currency extends ThemedScreen {
        private final IndustrialScreen parent;
        Currency(IndustrialScreen p){super(Component.literal("工业通货概率"),ThemeDomain.MACHINE);parent=p;}
        @Override protected void init(){addRenderableWidget(Button.builder(Component.literal("返回"),b->onClose()).bounds((width+180)/2,(height-230)/2,50,20).build(IndustrialUi.IndustrialButton::new));}
        @Override protected void drawBackground(GuiGraphicsExtractor g,int mx,int my,float delta){int x=(width-310)/2,y=(height-260)/2;g.fill(0,0,width,height,UiTheme.OVERLAY);IndustrialUi.panel(g,x,y,(x+310)-(x),(y+260)-(y));g.text(UiFonts.font(),"每轮必出1枚 · 玩家掉宝不参与",x+12,y+15,UiTheme.TEXT,false);double[] probabilities=new double[CurrencyRules.ALL.size()];JsonArray values=parent.menu.info.getAsJsonArray("currency_probabilities");for(int i=0;i<probabilities.length;i++)probabilities[i]=values.get(i).getAsDouble();int row=0;for(int i=0;i<probabilities.length;i++)if(probabilities[i]>0){var currency=cn.yumis.fight.modules.economy.api.CurrencyRules.ALL.get(i);ItemStack stack=ItemRegistry.item(currency.id(),1);g.item(stack,x+14,y+38+row*20);g.text(UiFonts.font(),stack.getHoverName(),x+38,y+43+row*20,UiTheme.TEXT,false);g.text(UiFonts.font(),String.format(java.util.Locale.ROOT,"%.2f%%",probabilities[i]*100),x+230,y+43+row*20,UiTheme.TEXT,false);row++;}}
        @Override public void onClose(){minecraft.gui.popScreenLayer();}
        @Override public boolean isPauseScreen(){return false;}
    }
}
