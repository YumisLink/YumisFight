package cn.yumis.fight.modules.economy.client;

import cn.yumis.fight.modules.presentation.api.client.*;
import cn.yumis.fight.modules.affixes.api.client.AffixFavorites;
import cn.yumis.fight.modules.economy.api.AutoSellRule;
import cn.yumis.fight.modules.equipment.api.Rarity;
import cn.yumis.fight.modules.equipment.api.client.MythicDisplay;
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
    private record Row(String key,boolean bag,int slot,boolean allowed,String name,String reason,long price,String dismantle,Rarity rarity,JsonElement identity,ItemStack preview,List<Component> details){}
    private record Cell(Row row,int x,int y){}
    private record InventoryValue(Object item,int count,Object components){}
    private record ConfigCell(int index,int top,int chipX,int chipW,int cmpX,int minusX,int plusX,int deleteX,int cardWidth){}
    private final Map<String,Row> rows=new LinkedHashMap<>();
    private final Set<String> selected=new LinkedHashSet<>();
    private List<Cell> cells=List.of();
    private List<FormattedCharSequence> detailLines=List.of();
    private List<InventoryValue> observedInventory=List.of();
    private JsonObject quote;
    private Region region;
    private boolean draggingScroll;private double scrollGrab;
    private String focused="",detailKey="";
    private boolean bag=SaleClient.bag(),requested,waiting,confirming,dismantling,configuring,layoutDirty=true;
    private long favoriteRevision=AffixFavorites.revision();
    private int scroll,maxScroll,cell,gridLeft,gridTop,gridBottom,detailLeft,detailWidth,detailScroll;
    private List<AutoSellRule> configRules=List.of();
    private final List<ConfigCell> configCells=new ArrayList<>();
    private int configTop,configBottom,configScroll,configMaxScroll;
    private long configRevision=-1;
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
        if(favoriteRevision!=AffixFavorites.revision()&&quote!=null){favoriteRevision=AffixFavorites.revision();accept(quote);}
        if(configuring&&configRevision!=AutoSellSettings.revision()){configRules=AutoSellSettings.rules();configRevision=AutoSellSettings.revision();layoutDirty=true;}
        var player=Minecraft.getInstance().player;
        if(player!=null){
            if(observedHand>=0&&observedHand!=player.getInventory().getSelectedSlot()){dirtyAt=now;confirming=false;}
            observedHand=player.getInventory().getSelectedSlot();
            List<InventoryValue> next=new ArrayList<>(36);
            for(int slot=0;slot<36;slot++){var stack=player.getInventory().getItem(slot);next.add(new InventoryValue(stack.getItem(),stack.getCount(),stack.getComponentsPatch()));}
            if(!observedInventory.isEmpty()&&!observedInventory.equals(next)){dirtyAt=now;confirming=false;}
            observedInventory=List.copyOf(next);
        }
        if(dirtyAt>=0&&!waiting&&!configuring&&now-dirtyAt>=250&&now-lastRequest>=250)request();
    }
    private void request(){waiting=true;lastRequest=ClientUi.now();dirtyAt=-1;SaleClient.request();}
    private void accept(JsonObject data){
        Map<String,Row> next=new LinkedHashMap<>();var mc=Minecraft.getInstance();if(mc.player==null)return;
        for(var value:data.getAsJsonArray("rows")){
            var row=value.getAsJsonObject();String key=row.get("key").getAsString();
            ItemStack preview=row.has("source")?ItemStack.OPTIONAL_CODEC.parse(mc.player.registryAccess().createSerializationContext(JsonOps.INSTANCE),row.get("source")).result().orElse(ItemStack.EMPTY):ItemStack.EMPTY;
            Rarity rarity=row.has("rarity")?Rarity.valueOf(row.get("rarity").getAsString()):Rarity.COMMON;
            List<Component> lines=new ArrayList<>();Component name=Component.literal(row.get("name").getAsString());lines.add(rarity==Rarity.MYTHIC?MythicDisplay.mark(name):rarity==Rarity.PRIMORDIAL?cn.yumis.fight.modules.equipment.api.client.PrimordialDisplay.mark(name):name);
            long price=row.has("price")?row.get("price").getAsLong():0;
            String dismantle=row.has("dismantle")?row.get("dismantle").getAsString():"";
            boolean allowed=row.get(dismantling?"dismantleAllowed":"allowed").getAsBoolean();String reason=row.get(dismantling?"dismantleReason":"reason").getAsString();
            if(preview.getCount()>1)lines.add(Component.literal("数量："+preview.getCount()));
            if(dismantling&&row.has("dismantle"))lines.add(Component.literal("分解："+dismantle));else if(!dismantling&&row.has("price"))lines.add(Component.literal((preview.getCount()>1?"整组售价：":"售价：")+price+"金币"));
            if(dismantling&&row.has("gems")&&row.get("gems").getAsInt()>0)lines.add(Component.literal("镶嵌宝石将一并销毁").withColor(0xff8a58));
            if(!allowed)lines.add(Component.literal(reason));
            if(row.has("lines"))for(var valueLine:row.getAsJsonArray("lines")){var line=valueLine.getAsJsonObject();lines.add(AffixFavorites.decorate(line.get("id").getAsString(),Component.literal(line.get("text").getAsString())));}
            JsonElement identity=row.has("source")?row.get("source"):JsonNull.INSTANCE;
            next.put(key,new Row(key,row.get("bag").getAsBoolean(),row.get("slot").getAsInt(),allowed,row.get("name").getAsString(),reason,price,dismantle,rarity,identity,preview,List.copyOf(lines)));
        }
        selected.removeIf(key->{var old=rows.get(key);var current=next.get(key);return old==null||current==null||!current.allowed||!old.identity.equals(current.identity)||old.price!=current.price;});
        rows.clear();rows.putAll(next);quote=data;waiting=false;confirming=false;layoutDirty=true;detailKey="";sum();
    }
    private void sum(){total=0;for(String key:selected)total=Math.addExact(total,rows.get(key).price);}
    private void select(String key){var row=rows.get(key);focused=key;detailScroll=0;detailKey="";if(row.allowed){if(!selected.remove(key))selected.add(key);sum();}}
    private boolean valid(){return quote!=null&&!waiting&&dirtyAt<0&&ClientUi.current().ready()&&quote.get("session").getAsString().equals(ClientUi.current().session());}
    private void sell(){
        if(!valid()||selected.isEmpty())return;
        String action=dismantling?"dismantle":"sell";var request=ClientUi.current().operation(action);request.addProperty("session",quote.get("session").getAsString());request.addProperty("menu",quote.get("menu").getAsInt());request.addProperty("token",quote.get("token").getAsString());
        JsonArray keys=new JsonArray();selected.forEach(keys::add);request.add("items",keys);waiting=true;lastRequest=ClientUi.now();confirming=false;ClientUi.current().send(action,request);
    }
    private void layout(){
        if(region==null||!layoutDirty)return;layoutDirty=false;
        detailWidth=region.width()>=390?Math.min(260,region.width()/2):0;
        int gridWidth=region.width()-detailWidth-(detailWidth>0?12:8);
        cell=Math.max(18,Math.min(38,(gridWidth-12)/9));gridLeft=region.x()+6;gridTop=region.y()+54;
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
        if(configuring){
            configTop=region.y()+54;
            configBottom=region.y()+region.height()-(region.height()<160?52:76);
            configMaxScroll=Math.max(0,configRules.size()-Math.max(1,(configBottom-configTop-38)/48));
            configScroll=Math.clamp(configScroll,0,configMaxScroll);
            configCells.clear();
            int cardWidth=region.width()-12,chipW=Math.max(20,(cardWidth-4-12)/7);
            int visible=Math.max(1,(configBottom-configTop-38)/48);
            for(int offset=0;offset<visible&&configScroll+offset<configRules.size();offset++){
                int index=configScroll+offset,top=configTop+38+offset*48;
                int cmpX=region.x()+10,minusX=cmpX+78,plusX=minusX+50;
                configCells.add(new ConfigCell(index,top,region.x()+8,chipW,cmpX,minusX,plusX,region.x()+cardWidth-44,cardWidth));
            }
        }
    }
    private static boolean inside(double x,double y,int left,int top,int width,int height){return x>=left&&x<left+width&&y>=top&&y<top+height;}
    private void text(GuiGraphicsExtractor g,String value,int x,int y,int color,int width){var font=UiFonts.font();g.text(font,font.plainSubstrByWidth(value,Math.max(0,width)),x,y,color,false);}
    private void button(GuiGraphicsExtractor g,String label,int x,int y,int width,boolean active,boolean chosen,int mx,int my){UiTheme.button(g,x,y,width,20,active,inside(mx,my,x,y,width,20),chosen);text(g,label,x+5,y+6,active?UiTheme.TEXT:UiTheme.MUTED,width-10);}
    private boolean mode(double x,double y){
        int toolWidth=region.width()>=330?76:Math.max(30,(region.width()-24)/4);
        int left=region.x()+6,top=region.y()+4;
        if(!inside(x,y,left,top+24,toolWidth,20)&&!inside(x,y,left+toolWidth+4,top+24,toolWidth,20)&&!inside(x,y,left+(toolWidth+4)*2,top+24,toolWidth,20))return false;
        if(inside(x,y,left+(toolWidth+4)*2,top+24,toolWidth,20)){
            if(!configuring){configuring=true;configRules=AutoSellSettings.rules();configRevision=AutoSellSettings.revision();configScroll=0;confirming=false;layoutDirty=true;}
            return true;
        }
        if(configuring){configuring=false;dirtyAt=ClientUi.now();confirming=false;layoutDirty=true;}
        boolean dismantle=inside(x,y,left+toolWidth+4,top+24,toolWidth,20);
        if(dismantling!=dismantle){dismantling=dismantle;confirming=false;detailKey="";if(quote!=null)accept(quote);}
        return true;
    }
    private void renderConfig(GuiGraphicsExtractor g,Region area,int mx,int my){
        int left=region.x()+6;
        boolean on=AutoSellSettings.enabled();
        button(g,on?"自动出售：已开启":"自动出售：已关闭",left,configTop+14,132,true,on,mx,my);
        button(g,"新增规则",left+region.width()-70,configTop+14,58,configRules.size()<AutoSellRule.RULE_LIMIT,false,mx,my);
        for(var cell:configCells){
            var rule=cell.index<configRules.size()?configRules.get(cell.index):null;
            if(rule==null)continue;
            UiTheme.card(g,left,cell.top,cell.cardWidth,44);
            var rarities=Rarity.values();
            for(int r=0;r<rarities.length;r++){
                var rarity=rarities[r];int cx=cell.chipX+r*(cell.chipW+2);
                boolean chosen=rule.rarities().contains(rarity);
                UiTheme.button(g,cx,cell.top+4,cell.chipW,16,true,inside(mx,my,cx,cell.top+4,cell.chipW,16),chosen);
                text(g,rarity.title(),cx+3,cell.top+9,chosen?rarity.color():UiTheme.MUTED,cell.chipW-6);
            }
            boolean bounded=rule.comparison()!=AutoSellRule.Comparison.ANY;
            String label=switch(rule.comparison()){case GREATER->"等级 > "+rule.level();case LESS->"等级 < "+rule.level();case ANY->"等级不限";};
            button(g,label,cell.cmpX,cell.top+24,72,true,false,mx,my);
            button(g,"-",cell.minusX,cell.top+24,20,bounded,false,mx,my);
            button(g,"+",cell.plusX,cell.top+24,20,bounded,false,mx,my);
            String value=String.valueOf(rule.level());
            g.text(UiFonts.font(),value,(cell.minusX+20+cell.plusX-UiFonts.font().width(value))/2,cell.top+30,bounded?UiTheme.TEXT:UiTheme.MUTED,false);
            button(g,"删除",cell.deleteX,cell.top+24,40,true,false,mx,my);
        }
        if(configMaxScroll>0){
            int h=configBottom-configTop,thumb=Math.max(10,h/(configMaxScroll+1)),offset=(h-thumb)*configScroll/configMaxScroll;
            UiTheme.scrollbar(g,left+region.width()-12,configTop,h,thumb,offset);
        }
    }
    private String configSummary(){
        String error=AutoSellSettings.error();
        if(!error.isEmpty())return error;
        int count=configRules.size();
        if(!AutoSellSettings.enabled())return count==0?"自动出售未开启，未配置规则":"自动出售未开启，已保留 "+count+" 条规则";
        if(count==0)return "自动出售已开启，未配置规则";
        boolean missing=configRules.stream().anyMatch(rule->rule.rarities().isEmpty());
        return "自动出售已开启 · "+count+" 条规则"+(missing?"，有规则未选稀有度不会生效":"");
    }
    private int configSummaryColor(){
        boolean missing=AutoSellSettings.enabled()&&configRules.stream().anyMatch(rule->rule.rarities().isEmpty());
        return !AutoSellSettings.error().isEmpty()||missing?UiTheme.ERROR:UiTheme.GOLD;
    }
    private void configClick(double x,double y){
        int left=region.x()+6;
        if(inside(x,y,left,configTop+14,132,20)){commitConfig(!AutoSellSettings.enabled(),configRules);return;}
        if(inside(x,y,left+region.width()-70,configTop+14,58,20)){
            if(configRules.size()<AutoSellRule.RULE_LIMIT){
                var next=new ArrayList<>(configRules);
                next.add(new AutoSellRule(EnumSet.of(Rarity.COMMON),AutoSellRule.Comparison.ANY,AutoSellRule.LEVEL_MIN));
                commitConfig(AutoSellSettings.enabled(),next);
            }
            return;
        }
        if(configMaxScroll>0&&inside(x,y,left+region.width()-14,configTop,12,configBottom-configTop)){
            int next=Math.clamp(configScroll+(y<(configTop+configBottom)/2d?1:-1),0,configMaxScroll);
            if(next!=configScroll){configScroll=next;layoutDirty=true;}
            return;
        }
        for(var cell:configCells){
            var rule=cell.index<configRules.size()?configRules.get(cell.index):null;
            if(rule==null)continue;
            var rarities=Rarity.values();
            for(int r=0;r<rarities.length;r++){
                int cx=cell.chipX+r*(cell.chipW+2);
                if(inside(x,y,cx,cell.top+4,cell.chipW,16)){commitRule(cell.index,rule.withRarity(rarities[r],!rule.rarities().contains(rarities[r])));return;}
            }
            if(inside(x,y,cell.cmpX,cell.top+24,72,20)){commitRule(cell.index,rule.withComparison(AutoSellRule.cycle(rule.comparison())));return;}
            if(inside(x,y,cell.minusX,cell.top+24,20,20)){if(rule.comparison()!=AutoSellRule.Comparison.ANY)commitRule(cell.index,rule.withLevel(rule.level()-1));return;}
            if(inside(x,y,cell.plusX,cell.top+24,20,20)){if(rule.comparison()!=AutoSellRule.Comparison.ANY)commitRule(cell.index,rule.withLevel(rule.level()+1));return;}
            if(inside(x,y,cell.deleteX,cell.top+24,40,20)){
                var next=new ArrayList<>(configRules);next.remove(cell.index);
                commitConfig(AutoSellSettings.enabled(),next);return;
            }
        }
    }
    private void commitRule(int index,AutoSellRule rule){
        var next=new ArrayList<>(configRules);
        next.set(index,rule);
        commitConfig(AutoSellSettings.enabled(),next);
    }
    private void commitConfig(boolean active,List<AutoSellRule> next){
        AutoSellSettings.commit(active,next);
        configRules=AutoSellSettings.rules();
        configRevision=AutoSellSettings.revision();
        layoutDirty=true;
    }
    @Override public void render(GuiGraphicsExtractor g,Region area,int mx,int my,float delta){
        if(!area.equals(region)){region=area;layoutDirty=true;}layout();
        g.enableScissor(area.x(),area.y(),area.x()+area.width(),area.y()+area.height());
        int x=area.x()+6,y=area.y()+4;
        int toolWidth=area.width()>=330?76:Math.max(30,(area.width()-24)/4);
        if(configuring){
            text(g,"新获得的装备命中任一规则即自动出售，普通与次元背包都生效。",x,y+5,UiTheme.MUTED,area.width()-12);
            button(g,"出售",x,y+24,toolWidth,true,false,mx,my);
            button(g,"分解",x+toolWidth+4,y+24,toolWidth,true,false,mx,my);
            button(g,"自动出售",x+(toolWidth+4)*2,y+24,toolWidth,true,true,mx,my);
            renderConfig(g,area,mx,my);
            int bottom=area.y()+area.height();boolean compact=area.height()<160;int footer=compact?48:70;
            UiTheme.card(g,area.x()+2,bottom-footer,area.width()-4,footer-2);
            text(g,configSummary(),area.x()+8,bottom-(compact?43:61),configSummaryColor(),area.width()-16);
            if(!compact)text(g,"规则保存在本机，不会上传服务器。",area.x()+8,bottom-46,UiTheme.MUTED,area.width()-16);
            g.disableScissor();
            return;
        }
        button(g,area.width()>=330?"普通背包":"背包",x,y,toolWidth,!waiting,!bag,mx,my);button(g,area.width()>=330?"次元背包":"次元",x+toolWidth+4,y,toolWidth,!waiting,bag,mx,my);
        button(g,area.width()>=330?"全部可选":"全选",x+(toolWidth+4)*2,y,toolWidth,!waiting,false,mx,my);button(g,"清空",x+(toolWidth+4)*3,y,area.width()>=330?44:toolWidth,!waiting,false,mx,my);
        button(g,"出售",x,y+24,toolWidth,!waiting,!dismantling,mx,my);button(g,"分解",x+toolWidth+4,y+24,toolWidth,!waiting,dismantling,mx,my);
        button(g,"自动出售",x+(toolWidth+4)*2,y+24,toolWidth,!waiting,false,mx,my);
        Row hovered=null;
        for(Cell slot:cells){
            var row=slot.row;boolean chosen=selected.contains(row.key);UiTheme.button(g,slot.x,slot.y,cell-2,cell-2,row.allowed,inside(mx,my,slot.x,slot.y,cell-2,cell-2),chosen);
            if(!row.preview.isEmpty()){int iconX=slot.x+(cell-18)/2,iconY=slot.y+(cell-18)/2;g.item(row.preview,iconX,iconY);if(row.rarity==Rarity.MYTHIC)MythicDisplay.frame(g,iconX,iconY,16,16);if(row.preview.getCount()>1){String count=Integer.toString(row.preview.getCount());g.text(UiFonts.font(),count,iconX+17-UiFonts.font().width(count),iconY+9,UiTheme.TEXT,true);}}
            if(chosen){g.fill(slot.x,slot.y,slot.x+cell-2,slot.y+1,UiTheme.SUCCESS);g.fill(slot.x,slot.y,slot.x+1,slot.y+cell-2,UiTheme.SUCCESS);}
            if(inside(mx,my,slot.x,slot.y,cell-2,cell-2))hovered=row;
        }
        if(maxScroll>0){int h=Math.max(20,gridBottom-gridTop),thumb=Math.max(10,h/(maxScroll+1)),offset=(h-thumb)*scroll/maxScroll;UiTheme.scrollbar(g,gridLeft+cell*9,gridTop,h,thumb,offset);}
        if(hovered!=null&&!hovered.key.equals(focused)){focused=hovered.key;detailScroll=0;}
        Row detail=hovered!=null?hovered:rows.get(focused);
        if(detailWidth>0){
            int w=Math.max(70,area.x()+area.width()-detailLeft-5),h=Math.max(1,gridBottom-gridTop);UiTheme.card(g,detailLeft,gridTop,w,h);
            if(detail==null)text(g,"悬浮或点击物品查看详情",detailLeft+8,gridTop+10,UiTheme.MUTED,w-16);
            else{
                String cache=detail.key+":"+w+":"+revision;if(!cache.equals(detailKey)){List<FormattedCharSequence> lines=new ArrayList<>();for(Component line:detail.details)lines.addAll(UiFonts.font().split(line,w-16));detailLines=List.copyOf(lines);detailKey=cache;}
                int limit=Math.max(1,(h-16)/12);detailScroll=Math.clamp(detailScroll,0,Math.max(0,detailLines.size()-limit));
                g.enableScissor(detailLeft+4,gridTop+4,detailLeft+w-4,gridBottom-4);
                for(int i=detailScroll;i<Math.min(detailLines.size(),detailScroll+limit);i++)MythicDisplay.text(g,UiFonts.font(),detailLines.get(i),detailLeft+8,gridTop+8+(i-detailScroll)*12,i==0?UiTheme.GOLD:UiTheme.TEXT,false);
                g.disableScissor();g.enableScissor(area.x(),area.y(),area.x()+area.width(),area.y()+area.height());
            }
        }else if(detail!=null&&area.height()>=180){Component title=Component.literal(UiFonts.font().plainSubstrByWidth(detail.name,area.width()-12));MythicDisplay.text(g,UiFonts.font(),(detail.rarity==Rarity.MYTHIC?MythicDisplay.mark(title):title).getVisualOrderText(),gridLeft,gridBottom+5,UiTheme.GOLD,false);text(g,detail.allowed?(dismantling?detail.dismantle:"售价 "+detail.price+"金币"):detail.reason,gridLeft,gridBottom+18,detail.allowed?UiTheme.TEXT:UiTheme.ERROR,area.width()-12);}
        int bottom=area.y()+area.height();boolean compact=area.height()<160;int footer=compact?48:70;UiTheme.card(g,area.x()+2,bottom-footer,area.width()-4,footer-2);
        text(g,waiting?"正在读取报价…":dirtyAt>=0?"背包已变化，正在更新报价…":compact&&confirming?(dismantling?"宝石一并销毁 · 分解":"物品及内容一并出售 · ")+selected.size()+"格":dismantling?"已选 "+selected.size()+" 格 · 产物公开落地":"已选 "+selected.size()+" 格 · 合计 "+total+"金币",x,bottom-(compact?43:61),confirming?UiTheme.ERROR:UiTheme.GOLD,area.width()-12);
        if(!compact)text(g,confirming?(dismantling?"宝石一并销毁。再次确认后不可撤回。":"整组物品及其中内容一并出售。再次确认后不可撤回。"):dismantling?"当前主手与穿戴装备不可分解。":"按整组出售；穿戴装备请先放入背包。",x,bottom-46,confirming?UiTheme.ERROR:UiTheme.MUTED,area.width()-12);
        int confirmWidth=Math.min(144,Math.max(84,(area.width()-22)/2));
        button(g,confirming?(dismantling?"确定分解":"确定出售"):(dismantling?"确认分解":"确认出售"),x,bottom-26,confirmWidth,valid()&&!selected.isEmpty(),confirming,mx,my);
        button(g,confirming?"取消确认":"清空选择",x+confirmWidth+6,bottom-26,Math.min(88,Math.max(64,area.width()-confirmWidth-24)),!waiting,false,mx,my);
        if(waiting&&ClientUi.now()-lastRequest>=10000)button(g,"刷新报价",area.x()+area.width()-88,area.y()+4,82,true,false,mx,my);
        g.disableScissor();
        if(hovered!=null&&detailWidth==0&&area.height()<180)g.setComponentTooltipForNextFrame(UiFonts.font(),hovered.details,mx,my,hovered.preview);
    }
    @Override public boolean mouseClicked(MouseButtonEvent event,boolean twice){
        if(region==null||!inside(event.x(),event.y(),region.x(),region.y(),region.width(),region.height()))return false;
        if(event.button()!=0)return true;double x=event.x(),y=event.y();int left=region.x()+6,top=region.y()+4,bottom=region.y()+region.height();
        if(configuring){
            if(mode(x,y))return true;
            configClick(x,y);
            return true;
        }
        if(waiting){if(ClientUi.now()-lastRequest>=10000&&inside(x,y,region.x()+region.width()-88,top,82,20))request();return true;}
        if(maxScroll>0&&inside(x,y,gridLeft+cell*9-2,gridTop,14,gridBottom-gridTop)){int h=Math.max(20,gridBottom-gridTop),thumb=Math.max(10,h/(maxScroll+1)),at=gridTop+(h-thumb)*scroll/maxScroll;scrollGrab=y>=at&&y<at+thumb?y-at:thumb/2d;draggingScroll=true;drag(y);return true;}
        int toolWidth=region.width()>=330?76:Math.max(30,(region.width()-24)/4);
        if(mode(x,y))return true;
        if(inside(x,y,left,top,toolWidth,20)||inside(x,y,left+toolWidth+4,top,toolWidth,20)){bag=x>=left+toolWidth+4;SaleClient.bag(bag);scroll=0;layoutDirty=true;confirming=false;return true;}
        if(inside(x,y,left+(toolWidth+4)*2,top,toolWidth,20)){for(var row:rows.values())if(row.allowed)selected.add(row.key);sum();confirming=false;return true;}
        if(inside(x,y,left+(toolWidth+4)*3,top,region.width()>=330?44:toolWidth,20)){selected.clear();sum();confirming=false;return true;}
        int confirmWidth=Math.min(144,Math.max(84,(region.width()-22)/2));
        if(inside(x,y,left,bottom-26,confirmWidth,20)){if(valid()&&!selected.isEmpty()){if(confirming)sell();else confirming=true;}return true;}
        if(inside(x,y,left+confirmWidth+6,bottom-26,Math.min(88,Math.max(64,region.width()-confirmWidth-24)),20)){if(confirming)confirming=false;else{selected.clear();sum();}return true;}
        for(Cell slot:cells)if(inside(x,y,slot.x,slot.y,cell-2,cell-2)){select(slot.row.key);confirming=false;return true;}
        return true;
    }
    private void drag(double y){int h=Math.max(20,gridBottom-gridTop),thumb=Math.max(10,h/(maxScroll+1));scroll=Math.clamp((int)Math.round((y-gridTop-scrollGrab)*maxScroll/Math.max(1,h-thumb)),0,maxScroll);layoutDirty=true;}
    @Override public boolean mouseDragged(MouseButtonEvent event,double dx,double dy){if(!draggingScroll)return false;drag(event.y());return true;}
    @Override public boolean mouseReleased(MouseButtonEvent event){boolean old=draggingScroll;draggingScroll=false;return old;}
    @Override public boolean mouseScrolled(double x,double y,double sx,double sy){
        if(region==null||!inside(x,y,region.x(),region.y(),region.width(),region.height()))return false;
        if(configuring){
            int next=Math.clamp(configScroll-(int)Math.signum(sy),0,configMaxScroll);
            if(next!=configScroll){configScroll=next;layoutDirty=true;}
            return true;
        }
        if(detailWidth>0&&x>=detailLeft)detailScroll=Math.max(0,detailScroll-(int)Math.signum(sy));else{int next=Math.clamp(scroll-(int)Math.signum(sy),0,maxScroll);if(next!=scroll){scroll=next;layoutDirty=true;}}return true;
    }
}
