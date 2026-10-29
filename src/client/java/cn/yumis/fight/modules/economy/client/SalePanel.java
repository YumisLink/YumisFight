package cn.yumis.fight.modules.economy.client;

import cn.yumis.fight.modules.presentation.api.client.*;
import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** Nine real inventory columns, a detail pane, and fixed batch totals inside the I menu. */
public final class SalePanel implements ClientPagePanel {
    private record Row(String key,boolean bag,int slot,boolean allowed,String name,String reason,long price,String identity,ItemStack preview,List<Component> details){}
    private record Cell(Row row,int x,int y){}
    private record InventoryValue(Object item,int count,Object components){}
    private final Map<String,Row> rows=new LinkedHashMap<>();
    private final Set<String> selected=new LinkedHashSet<>();
    private List<Cell> cells=List.of();
    private List<FormattedCharSequence> detailLines=List.of();
    private List<InventoryValue> observedInventory=List.of();
    private JsonObject quote;
    private Region region;
    private String focused="",detailKey="";
    private boolean bag=SaleClient.bag(),requested,waiting,confirming,layoutDirty=true;
    private int scroll,maxScroll,cell,gridLeft,gridTop,gridBottom,detailLeft,detailWidth,detailScroll;
    private long revision=SaleClient.revision(),total,lastRequest,dirtyAt=-1;
    private int observedHand=-1;

    @Override public void tick(){
        if(!ClientUi.current().ready())return;
        long now=ClientUi.now();
        if(!requested){requested=true;request();}
        if(revision!=SaleClient.revision()){
            var data=SaleClient.snapshot();
            if(data!=null){accept(data);revision=SaleClient.revision();}
        }
        var player=Minecraft.getInstance().player;
        if(player!=null){
            if(observedHand>=0&&observedHand!=player.getInventory().getSelectedSlot()){dirtyAt=now;confirming=false;}
            observedHand=player.getInventory().getSelectedSlot();
            List<InventoryValue> next=new ArrayList<>(36);
            for(int slot=0;slot<36;slot++){var stack=player.getInventory().getItem(slot);next.add(new InventoryValue(stack.getItem(),stack.getCount(),stack.getComponentsPatch()));}
            if(!observedInventory.isEmpty()&&!observedInventory.equals(next)){dirtyAt=now;confirming=false;}
            observedInventory=List.copyOf(next);
        }
        if(dirtyAt>=0&&!waiting&&now-dirtyAt>=250&&now-lastRequest>=250)request();
    }
    private void request(){waiting=true;lastRequest=ClientUi.now();dirtyAt=-1;SaleClient.request();}
    private void accept(JsonObject data){
        Map<String,Row> next=new LinkedHashMap<>();var mc=Minecraft.getInstance();if(mc.player==null)return;
        for(var value:data.getAsJsonArray("rows")){
            var row=value.getAsJsonObject();String key=row.get("key").getAsString();
            ItemStack preview=row.has("item")?ItemStack.OPTIONAL_CODEC.parse(mc.player.registryAccess().createSerializationContext(JsonOps.INSTANCE),row.get("item")).result().orElse(ItemStack.EMPTY):ItemStack.EMPTY;
            List<Component> lines=new ArrayList<>();lines.add(Component.literal(row.get("name").getAsString()));
            long price=row.has("price")?row.get("price").getAsLong():0;
            if(row.has("price"))lines.add(Component.literal("售价："+price+"金币"));
            if(!row.get("allowed").getAsBoolean())lines.add(Component.literal(row.get("reason").getAsString()));
            if(row.has("lines"))for(var line:row.getAsJsonArray("lines"))lines.add(Component.literal(line.getAsString()));
            String identity=row.has("equipment")?row.get("equipment").getAsString()+":"+row.get("revision").getAsInt():"";
            next.put(key,new Row(key,row.get("bag").getAsBoolean(),row.get("slot").getAsInt(),row.get("allowed").getAsBoolean(),row.get("name").getAsString(),row.get("reason").getAsString(),price,identity,preview,List.copyOf(lines)));
        }
        selected.removeIf(key->{var old=rows.get(key);var current=next.get(key);return old==null||current==null||!current.allowed||!old.identity.equals(current.identity)||old.price!=current.price;});
        rows.clear();rows.putAll(next);quote=data;waiting=false;confirming=false;layoutDirty=true;detailKey="";sum();
    }
    private void sum(){total=0;for(String key:selected)total=Math.addExact(total,rows.get(key).price);}
    private void select(String key){var row=rows.get(key);focused=key;detailScroll=0;detailKey="";if(row.allowed){if(!selected.remove(key))selected.add(key);sum();}}
    private boolean valid(){return quote!=null&&!waiting&&dirtyAt<0&&ClientUi.current().ready()&&quote.get("session").getAsString().equals(ClientUi.current().session());}
    private void sell(){
        if(!valid()||selected.isEmpty())return;
        var request=ClientUi.current().operation("sell");request.addProperty("session",quote.get("session").getAsString());request.addProperty("menu",quote.get("menu").getAsInt());request.addProperty("token",quote.get("token").getAsString());
        JsonArray keys=new JsonArray();selected.forEach(keys::add);request.add("items",keys);waiting=true;lastRequest=ClientUi.now();confirming=false;ClientUi.current().send("sell",request);
    }
    private void layout(){
        if(region==null||!layoutDirty)return;layoutDirty=false;
        detailWidth=region.width()>=390?Math.min(260,region.width()/2):0;
        int gridWidth=region.width()-detailWidth-(detailWidth>0?12:8);
        cell=Math.max(18,Math.min(38,(gridWidth-12)/9));gridLeft=region.x()+6;gridTop=region.y()+30;
        gridBottom=region.y()+region.height()-(region.height()<160?52:76);detailLeft=gridLeft+cell*9+12;
        if(detailWidth==0&&region.height()>=180)gridBottom-=32;
        int available=Math.max(1,(gridBottom-gridTop)/cell),count=(int)rows.values().stream().filter(row->row.bag==bag).count();
        maxScroll=Math.max(0,(count+8)/9-available);scroll=Math.clamp(scroll,0,maxScroll);
        List<Cell> next=new ArrayList<>();
        for(var row:rows.values())if(row.bag==bag){
            int index=bag?row.slot:row.slot<9?27+row.slot:row.slot-9;
            int y=gridTop+(index/9-scroll)*cell;
            if(y>=gridTop&&y+cell<=gridBottom)next.add(new Cell(row,gridLeft+(index%9)*cell,y));
        }
        cells=List.copyOf(next);detailKey="";
    }
    private static boolean inside(double x,double y,int left,int top,int width,int height){return x>=left&&x<left+width&&y>=top&&y<top+height;}
    private void text(GuiGraphicsExtractor g,String value,int x,int y,int color,int width){var font=UiFonts.font();g.text(font,font.plainSubstrByWidth(value,Math.max(0,width)),x,y,color,false);}
    private void button(GuiGraphicsExtractor g,String label,int x,int y,int width,boolean active,boolean chosen,int mx,int my){UiTheme.button(g,x,y,width,20,active,inside(mx,my,x,y,width,20),chosen);text(g,label,x+5,y+6,active?UiTheme.TEXT:UiTheme.MUTED,width-10);}
    @Override public void render(GuiGraphicsExtractor g,Region area,int mx,int my,float delta){
        if(!area.equals(region)){region=area;layoutDirty=true;}layout();
        g.enableScissor(area.x(),area.y(),area.x()+area.width(),area.y()+area.height());
        int x=area.x()+6,y=area.y()+4;
        int toolWidth=area.width()>=330?76:Math.max(30,(area.width()-24)/4);
        button(g,area.width()>=330?"普通背包":"背包",x,y,toolWidth,!waiting,!bag,mx,my);button(g,area.width()>=330?"次元背包":"次元",x+toolWidth+4,y,toolWidth,!waiting,bag,mx,my);
        button(g,area.width()>=330?"全部可售":"全选",x+(toolWidth+4)*2,y,toolWidth,!waiting,false,mx,my);button(g,"清空",x+(toolWidth+4)*3,y,area.width()>=330?44:toolWidth,!waiting,false,mx,my);
        Row hovered=null;
        for(Cell slot:cells){
            var row=slot.row;boolean chosen=selected.contains(row.key);UiTheme.button(g,slot.x,slot.y,cell-2,cell-2,row.allowed,inside(mx,my,slot.x,slot.y,cell-2,cell-2),chosen);
            if(!row.preview.isEmpty()){int iconX=slot.x+(cell-18)/2,iconY=slot.y+(cell-18)/2;g.item(row.preview,iconX,iconY);}
            if(chosen){g.fill(slot.x,slot.y,slot.x+cell-2,slot.y+1,UiTheme.SUCCESS);g.fill(slot.x,slot.y,slot.x+1,slot.y+cell-2,UiTheme.SUCCESS);}
            if(inside(mx,my,slot.x,slot.y,cell-2,cell-2))hovered=row;
        }
        if(maxScroll>0){int h=Math.max(20,gridBottom-gridTop),thumb=Math.max(10,h/(maxScroll+1)),offset=(h-thumb)*scroll/maxScroll;UiTheme.scrollbar(g,gridLeft+cell*9,gridTop,h,thumb,offset);}
        if(hovered!=null&&!hovered.key.equals(focused)){focused=hovered.key;detailScroll=0;}
        Row detail=hovered!=null?hovered:rows.get(focused);
        if(detailWidth>0){
            int w=Math.max(70,area.x()+area.width()-detailLeft-5),h=Math.max(1,gridBottom-gridTop);UiTheme.card(g,detailLeft,gridTop,w,h);
            if(detail==null)text(g,"悬浮或点击装备查看详情",detailLeft+8,gridTop+10,UiTheme.MUTED,w-16);
            else{
                String cache=detail.key+":"+w+":"+revision;if(!cache.equals(detailKey)){List<FormattedCharSequence> lines=new ArrayList<>();for(Component line:detail.details)lines.addAll(UiFonts.font().split(line,w-16));detailLines=List.copyOf(lines);detailKey=cache;}
                int limit=Math.max(1,(h-16)/12);detailScroll=Math.clamp(detailScroll,0,Math.max(0,detailLines.size()-limit));
                g.enableScissor(detailLeft+4,gridTop+4,detailLeft+w-4,gridBottom-4);
                for(int i=detailScroll;i<Math.min(detailLines.size(),detailScroll+limit);i++)g.text(UiFonts.font(),detailLines.get(i),detailLeft+8,gridTop+8+(i-detailScroll)*12,i==0?UiTheme.GOLD:UiTheme.TEXT,false);
                g.disableScissor();g.enableScissor(area.x(),area.y(),area.x()+area.width(),area.y()+area.height());
            }
        }else if(detail!=null&&area.height()>=180){text(g,detail.name,gridLeft,gridBottom+5,UiTheme.GOLD,area.width()-12);text(g,detail.allowed?"售价 "+detail.price+"金币":detail.reason,gridLeft,gridBottom+18,detail.allowed?UiTheme.TEXT:UiTheme.ERROR,area.width()-12);}
        int bottom=area.y()+area.height();boolean compact=area.height()<160;int footer=compact?48:70;UiTheme.card(g,area.x()+2,bottom-footer,area.width()-4,footer-2);
        text(g,waiting?"正在读取报价…":dirtyAt>=0?"背包已变化，正在更新报价…":compact&&confirming?"出售 "+selected.size()+" 件 · "+total+"金币（含宝石）":"已选 "+selected.size()+" 件 · 合计 "+total+"金币",x,bottom-(compact?43:61),confirming?UiTheme.ERROR:UiTheme.GOLD,area.width()-12);
        if(!compact)text(g,confirming?"宝石一并出售。再次确认后不可撤回。":"当前主手、穿戴装备与待领取物品不可出售。",x,bottom-46,confirming?UiTheme.ERROR:UiTheme.MUTED,area.width()-12);
        int confirmWidth=Math.min(144,Math.max(84,(area.width()-22)/2));
        button(g,confirming?"确定出售":"确认出售",x,bottom-26,confirmWidth,valid()&&!selected.isEmpty(),confirming,mx,my);
        button(g,confirming?"取消确认":"清空选择",x+confirmWidth+6,bottom-26,Math.min(88,Math.max(64,area.width()-confirmWidth-24)),!waiting,false,mx,my);
        if(waiting&&ClientUi.now()-lastRequest>=10000)button(g,"刷新报价",area.x()+area.width()-88,area.y()+4,82,true,false,mx,my);
        g.disableScissor();
        if(hovered!=null&&detailWidth==0&&area.height()<180)g.setComponentTooltipForNextFrame(UiFonts.font(),hovered.details,mx,my,hovered.preview);
    }
    @Override public boolean mouseClicked(MouseButtonEvent event,boolean twice){
        if(region==null||!inside(event.x(),event.y(),region.x(),region.y(),region.width(),region.height()))return false;
        if(event.button()!=0)return true;double x=event.x(),y=event.y();int left=region.x()+6,top=region.y()+4,bottom=region.y()+region.height();
        if(waiting){if(ClientUi.now()-lastRequest>=10000&&inside(x,y,region.x()+region.width()-88,top,82,20))request();return true;}
        int toolWidth=region.width()>=330?76:Math.max(30,(region.width()-24)/4);
        if(inside(x,y,left,top,toolWidth,20)||inside(x,y,left+toolWidth+4,top,toolWidth,20)){bag=x>=left+toolWidth+4;SaleClient.bag(bag);scroll=0;layoutDirty=true;confirming=false;return true;}
        if(inside(x,y,left+(toolWidth+4)*2,top,toolWidth,20)){for(var row:rows.values())if(row.allowed)selected.add(row.key);sum();confirming=false;return true;}
        if(inside(x,y,left+(toolWidth+4)*3,top,region.width()>=330?44:toolWidth,20)){selected.clear();sum();confirming=false;return true;}
        int confirmWidth=Math.min(144,Math.max(84,(region.width()-22)/2));
        if(inside(x,y,left,bottom-26,confirmWidth,20)){if(valid()&&!selected.isEmpty()){if(confirming)sell();else confirming=true;}return true;}
        if(inside(x,y,left+confirmWidth+6,bottom-26,Math.min(88,Math.max(64,region.width()-confirmWidth-24)),20)){if(confirming)confirming=false;else{selected.clear();sum();}return true;}
        for(Cell slot:cells)if(inside(x,y,slot.x,slot.y,cell-2,cell-2)){select(slot.row.key);confirming=false;return true;}
        return true;
    }
    @Override public boolean mouseScrolled(double x,double y,double sx,double sy){
        if(region==null||!inside(x,y,region.x(),region.y(),region.width(),region.height()))return false;
        if(detailWidth>0&&x>=detailLeft)detailScroll=Math.max(0,detailScroll-(int)Math.signum(sy));else{int next=Math.clamp(scroll-(int)Math.signum(sy),0,maxScroll);if(next!=scroll){scroll=next;layoutDirty=true;}}return true;
    }
}
