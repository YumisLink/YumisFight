package cn.yumis.fight.modules.economy.client;
import cn.yumis.fight.modules.presentation.api.client.*;
import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import java.util.*;
/** Server-quoted real slots. Selection survives source switches and pages. */
public final class SaleScreen extends Screen {
    private final JsonObject state;private final Map<String,JsonObject> selected=new LinkedHashMap<>();
    private final List<JsonObject> rows=new ArrayList<>();private final Map<String,ItemStack> previews=new HashMap<>();
    private final List<Cell> cells=new ArrayList<>();private boolean bag;private int page,left,top,columns,perPage;private Button confirm;
    private record Cell(JsonObject row,Button button){}
    public SaleScreen(JsonObject state){super(Component.literal("出售装备"));this.state=state;state.getAsJsonArray("rows").forEach(row->rows.add(row.getAsJsonObject()));}
    private long total(){long total=0;for(var row:selected.values())total=Math.addExact(total,row.get("price").getAsLong());return total;}
    @Override protected void init(){
        cells.clear();int panel=Math.min(540,width-16);left=(width-panel)/2;top=16;columns=Math.max(3,(panel-16)/42);int available=Math.max(2,(height-155)/42);perPage=columns*available;
        addRenderableWidget(Button.builder(Component.literal("普通背包"),b->{bag=false;page=0;rebuildWidgets();}).bounds(left+8,top+25,94,20).build(ThemedButton::new));
        addRenderableWidget(Button.builder(Component.literal("次元背包"),b->{bag=true;page=0;rebuildWidgets();}).bounds(left+106,top+25,94,20).build(ThemedButton::new));
        addRenderableWidget(Button.builder(Component.literal("全部可售"),b->{for(JsonObject row:rows)if(row.get("allowed").getAsBoolean())selected.put(row.get("key").getAsString(),row);rebuildWidgets();}).bounds(left+204,top+25,92,20).build(ThemedButton::new));
        addRenderableWidget(Button.builder(Component.literal("清空"),b->{selected.clear();rebuildWidgets();}).bounds(left+300,top+25,60,20).build(ThemedButton::new));
        var visible=rows.stream().filter(row->row.get("bag").getAsBoolean()==bag).toList();int pages=Math.max(1,(visible.size()+perPage-1)/perPage);page=Math.clamp(page,0,pages-1);
        for(int i=page*perPage;i<Math.min(visible.size(),(page+1)*perPage);i++){
            JsonObject row=visible.get(i);int cell=i-page*perPage,x=left+8+(cell%columns)*42,y=top+54+(cell/columns)*42;String key=row.get("key").getAsString();
            Button button=Button.builder(Component.literal(""),b->{if(selected.containsKey(key))selected.remove(key);else selected.put(key,row);rebuildWidgets();}).bounds(x,y,38,38).build(ThemedButton::new);button.active=row.get("allowed").getAsBoolean();((ThemedButton)button).selected=selected.containsKey(key);addRenderableWidget(button);cells.add(new Cell(row,button));
            if(row.has("item")&&!previews.containsKey(key))previews.put(key,ItemStack.OPTIONAL_CODEC.parse(minecraft.player.registryAccess().createSerializationContext(JsonOps.INSTANCE),row.get("item")).getOrThrow());
        }
        int bottom=height-76;
        addRenderableWidget(Button.builder(Component.literal("上一页"),b->{page--;rebuildWidgets();}).bounds(left+8,bottom,76,20).build(ThemedButton::new)).active=page>0;
        addRenderableWidget(Button.builder(Component.literal((page+1)+" / "+pages+" · 下一页"),b->{page++;rebuildWidgets();}).bounds(left+88,bottom,120,20).build(ThemedButton::new)).active=page+1<pages;
        confirm=addRenderableWidget(Button.builder(Component.literal("确认出售 · "+total()+"金币"),b->confirm()).bounds(left+8,height-31,Math.max(110,panel-116),20).build(ThemedButton::new));confirm.active=!selected.isEmpty();
        addRenderableWidget(Button.builder(Component.literal("取消出售"),b->onClose()).bounds(left+panel-102,height-31,94,20).build(ThemedButton::new));
    }
    private void confirm(){
        if(selected.isEmpty())return;long amount=total();int count=selected.size();JsonArray keys=new JsonArray();selected.keySet().forEach(keys::add);
        minecraft.gui.setScreen(new ConfirmScreen(yes->{minecraft.gui.setScreen(this);if(yes){confirm.active=false;JsonObject request=ClientUi.current().operation("sell");request.addProperty("session",state.get("session").getAsString());request.addProperty("menu",state.get("menu").getAsInt());request.addProperty("token",state.get("token").getAsString());request.add("items",keys);ClientUi.current().send("sell",request);}},Component.literal("确认出售 "+count+" 件装备？"),Component.literal("合计 "+amount+" 金币；镶嵌宝石一并出售。")));
    }
    @Override public void extractBackground(GuiGraphicsExtractor g,int x,int y,float delta){g.fill(0,0,width,height,UiTheme.OVERLAY);UiTheme.panel(g,left,top,width-2*left,height-top-8);g.text(UiFonts.font(),title,left+12,top+10,UiTheme.GOLD,false);}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float delta){
        super.extractRenderState(g,x,y,delta);
        for(Cell cell:cells){JsonObject row=cell.row;int bx=cell.button.getX(),by=cell.button.getY();ItemStack preview=previews.get(row.get("key").getAsString());if(preview!=null)g.item(preview,bx+11,by+5);
            g.text(UiFonts.font(),Integer.toString(row.get("slot").getAsInt()+1),bx+4,by+25,cell.button.active?UiTheme.MUTED:UiTheme.ERROR,false);
            if(x>=bx&&x<bx+38&&y>=by&&y<by+38){List<Component> lines=new ArrayList<>();lines.add(Component.literal(row.get("name").getAsString()));if(row.has("price"))lines.add(Component.literal("售价："+row.get("price").getAsLong()+"金币"));if(!row.get("allowed").getAsBoolean())lines.add(Component.literal(row.get("reason").getAsString()).withColor(UiTheme.ERROR));if(row.has("lines"))row.getAsJsonArray("lines").forEach(line->lines.add(Component.literal(line.getAsString())));g.setComponentTooltipForNextFrame(UiFonts.font(),lines.stream().map(UiFonts::decorate).toList(),x,y,preview==null?ItemStack.EMPTY:preview);}
        }
        g.text(UiFonts.font(),"已选 "+selected.size()+" 件 · 合计 "+total()+" 金币",left+12,height-48,UiTheme.GOLD,false);
    }
    @Override public void tick(){if(minecraft.player==null||!ClientUi.current().ready()||!ClientUi.current().session().equals(state.get("session").getAsString()))minecraft.gui.setScreen(null);}
    @Override public void onClose(){minecraft.gui.setScreen(null);}
    @Override public boolean isPauseScreen(){return false;}
}
