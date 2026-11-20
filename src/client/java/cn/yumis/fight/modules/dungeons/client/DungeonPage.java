package cn.yumis.fight.modules.dungeons.client;
import cn.yumis.fight.modules.dungeons.api.*;
import cn.yumis.fight.modules.dungeons.content.CastlePalace;
import cn.yumis.fight.modules.dungeons.content.GblChurch;
import cn.yumis.fight.modules.equipment.api.MaterialCatalog;
import cn.yumis.fight.modules.presentation.api.client.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
public final class DungeonPage {
    public record Join(String dungeon,Difficulty difficulty,boolean sync){}
    private static final List<String> ART=List.of("goblin_camp","dragon_tower","castle_palace","gbl_church","behemoth_forbidden","dark_city","skasa","moonlight_tavern","hamelin","pursuit","sea_train","time_rift","mysterious_awareness","grandi","central_park");
    private static final List<PlayerPageSurface.Navigation> TABS=List.of(
        new PlayerPageSurface.Navigation("story","主线副本"),
        new PlayerPageSurface.Navigation("practice","练功房"));
    private DungeonPage(){}
    public static void render(PlayerPageSurface page,Dungeon current,int level,boolean preferredSync,Consumer<Join> join,Runnable leave,Consumer<String> message){
        if(current!=null&&current.practice!=null){
            page.rowButton("离开练功房",leave);
            return;
        }
        String tab=page.remember("dungeon.tab","story").equals("practice")?"practice":"story";
        page.navigation(TABS,tab,id->{
            page.rememberValue("dungeon.tab",id);
            page.rememberValue("dungeon.materials",false);
            page.rebuild();
        });
        if(tab.equals("practice")){
            if(current!=null)page.rowButton("离开地下城",leave);
            renderPractice(page,level,room->{
                if(current!=null){message.accept("请先退出当前副本");return;}
                join.accept(new Join(room.id(),Difficulty.NORMAL,true));
            });
            return;
        }
        if(tab.equals("story"))WaystoneClient.enrollment(page);
        if(current!=null&&current.waystone!=null){var run=current.waystone;page.line("地狱 +"+run.stone.tier+" · "+DungeonCatalog.current().dungeon(current.dungeon).name()+" · Lv."+run.monsterLevel);page.line(objective(current,run.settled?"已结束":"挑战中"));for(var m:run.stone.modifiers)page.line(Waystone.modifierText(m));for(String text:run.stone.rewardText())page.line(text);page.rowButton("离开地下城",leave);page.gap();}
        if(current!=null&&current.waystone==null){if(current.treasure!=null){var t=current.treasure;page.line(t.region==0?"哥布林王国 · 野外守宝战斗":"藏宝秘境 · 第"+t.region+" / 5区 · "+(t.theme.equals("palace")?"黄金王宫":t.theme.equals("tower")?"龙人之塔":"格兰之森"));page.line("Lv."+(t.baseLevel+Math.max(0,t.region-1)*2)+" · 地狱 · "+(t.cleared?"已清场":"第"+(current.wave+1)+"波"));}else{page.line("当前："+DungeonCatalog.current().dungeon(current.dungeon).name()+" · "+current.difficulty.title());page.line(objective(current,current.status));}page.rowButton("离开地下城",leave);page.gap();}
        if(current!=null&&current.dungeon.equals(GblChurch.ID)&&Minecraft.getInstance().player!=null)
            page.line("区域："+GblChurch.area(Minecraft.getInstance().player.getZ()));
        if(current!=null&&current.dungeon.equals("behemoth_forbidden"))page.line("区域："+cn.yumis.fight.modules.dungeons.content.BehemothForbidden.AREAS[cn.yumis.fight.modules.dungeons.content.BehemothForbidden.area(current.wave)]);
        if(current!=null&&current.dungeon.equals("skasa"))page.line("区域："+cn.yumis.fight.modules.dungeons.content.SkasaLair.AREAS[cn.yumis.fight.modules.dungeons.content.SkasaLair.area(current.wave)]);
        int cell=(page.contentWidth()-24)/2,base=page.cursor(),pictureHeight=Math.max(42,(cell-13)/2),height=pictureHeight+43,index=0;
        for(var definition:DungeonCatalog.current().dungeons){
            int x=(index%2)*cell,y=base+(index/2)*(height+6),w=cell-5;index++;
            page.card(x,y,w,height);var button=page.button("",x,y,w,()->choose(definition,current,preferredSync,join,message));button.setHeight(height);
            int tile=ART.indexOf(definition.id());if(tile<0)tile=15;
            page.image("textures/gui/dungeons/atlas.png",x+4,y+4,w-8,pictureHeight,(tile%4)/4f,(tile%4+1)/4f,(tile/4)/4f,(tile/4+1)/4f);
            page.text(UiFonts.font().plainSubstrByWidth(definition.name(),w-12),x+6,y+pictureHeight+10,definition.available()?UiTheme.TEXT:UiTheme.MUTED);
            page.text("Lv."+definition.level()+(definition.available()?"":" · 尚未开放"),x+6,y+pictureHeight+25,UiTheme.MUTED);
        }
        page.cursor(base+((index+1)/2)*(height+6));
    }

    private static void choose(DungeonCatalog.DungeonDef definition,Dungeon current,boolean preferredSync,Consumer<Join> join,Consumer<String> message){
        if(!definition.available()){message.accept("该副本尚未开放");return;}
        if(current!=null){message.accept("请先退出当前副本");return;}
        Minecraft.getInstance().gui.pushScreenLayer(new Choice(definition,preferredSync,join));
    }

    private static void renderPractice(PlayerPageSurface page,int level,Consumer<PracticeRoom> join){
        if(page.remember("dungeon.materials",false)){
            page.rowButton("返回练功房列表",()->showMaterials(page,false));
            var rooms=PracticeRoom.availableMaterials(level);
            if(rooms.isEmpty()){
                page.line("达到主线副本的入场等级后可选择对应材料房。",UiTheme.MUTED);
                return;
            }
            var materials=MaterialCatalog.current().materials;
            for(var room:rooms){
                page.rowButton(room.name(),()->join.accept(room));
                page.line(room.materials().stream().map(id->{
                    var material=materials.get(id);
                    return material==null?id:material.name();
                }).collect(Collectors.joining("、")),UiTheme.MUTED);
            }
            return;
        }
        page.rowButton(PracticeRoom.MOB.name(),()->join.accept(PracticeRoom.MOB));
        page.line("随机刷新各副本小怪，等级跟随本人，血量3倍，掉落与副本内击杀一致，经验降为四分之一。",UiTheme.MUTED);
        page.rowButton(PracticeRoom.GOLD.name(),()->join.accept(PracticeRoom.GOLD));
        page.line("每波结算金币，按当时在场人数分成。",UiTheme.MUTED);
        page.rowButton(PracticeRoom.CURRENCY.name(),()->join.accept(PracticeRoom.CURRENCY));
        page.line("掉落自然通货，可在场内拾取。",UiTheme.MUTED);
        page.rowButton("材料练功房",()->showMaterials(page,true));
        page.line("选择主线材料，击败怪物即可拾取。",UiTheme.MUTED);
    }

    private static void showMaterials(PlayerPageSurface page,boolean show){
        page.rememberValue("dungeon.materials",show);
        page.rebuild();
    }
    private static boolean openCamp(Dungeon dungeon){return dungeon.treasure==null&&dungeon.dungeon.equals("goblin_camp");}
    private static String campObjective(Dungeon dungeon){return dungeon.status.equals("COMPLETED")?"凯诺已击败 · 可继续清理营区或离开":"开放密林 · 寻找并击败落雷凯诺";}
    private static String objective(Dungeon dungeon,String status){
        if(openCamp(dungeon))return campObjective(dungeon);
        if(dungeon.dungeon.equals(GblChurch.ID))return dungeon.status.equals("COMPLETED")?"阿索尔已击败":"探索五殿 · 击败阿索尔";
        if(AwakeningTower.find(dungeon.dungeon)!=null)return dungeon.status.equals("COMPLETED")?"觉醒塔已通关":"进入战场 · 击败鲁卡斯";
        if(CastlePalace.handles(dungeon))return dungeon.status.equals("COMPLETED")?"城主宫殿已通关"
            :"清理宫殿 · 已到达 "+dungeon.palaceRooms.size()+" / "+CastlePalace.ROOM_COUNT+" 个区域";
        return "波次 "+(dungeon.wave+1)+" / 7 · "+status;
    }
    private static final class Choice extends ThemedScreen {
        private final DungeonCatalog.DungeonDef definition;private final Consumer<Join> join;private boolean sync;
        Choice(DungeonCatalog.DungeonDef definition,boolean sync,Consumer<Join> join){super(Component.literal(definition.name()),ThemeDomain.MENU);this.definition=definition;this.sync=sync;this.join=join;}
        @Override protected void init(){int x=(width-250)/2,y=(height-220)/2;
            addRenderableWidget(Button.builder(Component.literal("本人同步："+(sync?"开启":"关闭")),b->{sync=!sync;rebuildWidgets();}).bounds(x+10,y+35,230,22).build(ThemedButton::new));
            int[] colors={UiTheme.TEXT,0xff55aaff,0xffb787ff,0xffff9a35,0xffff4545};
            for(var difficulty:Difficulty.values()){var button=addRenderableWidget(Button.builder(Component.literal(difficulty.title()),b->{minecraft.gui.popScreenLayer();join.accept(new Join(definition.id(),difficulty,sync));}).bounds(x+10,y+64+difficulty.ordinal()*25,230,22).build(ThemedButton::new));((ThemedButton)button).textColor=colors[difficulty.ordinal()];}
            addRenderableWidget(Button.builder(Component.literal("返回"),b->onClose()).bounds(x+193,y+7,47,20).build(ThemedButton::new));
        }
        @Override protected void drawBackground(GuiGraphicsExtractor g,int mx,int my,float a){g.fill(0,0,width,height,UiTheme.OVERLAY);UiTheme.panel(g,(width-250)/2,(height-220)/2,250,220);}
        @Override protected void drawContents(GuiGraphicsExtractor g,int mx,int my,float a){g.text(UiFonts.font(),definition.name(),(width-250)/2+12,(height-220)/2+14,UiTheme.TEXT,false);super.drawContents(g,mx,my,a);}
        @Override public void onClose(){minecraft.gui.popScreenLayer();}
        @Override public boolean isPauseScreen(){return false;}
    }
}
