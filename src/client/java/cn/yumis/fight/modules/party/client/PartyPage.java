package cn.yumis.fight.modules.party.client;
import cn.yumis.fight.modules.party.api.Team;
import cn.yumis.fight.modules.presentation.api.client.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.network.chat.Component;
import java.util.*;
import java.util.function.*;
public final class PartyPage {
    private PartyPage(){}
    public static void render(PlayerPageSurface page,Team team,UUID self,Function<UUID,String> playerName,BiConsumer<String,String> command){
        if(team==null)page.line("当前没有战斗队伍");else{page.line("战斗队伍 · 人数 "+team.members.size(),UiTheme.GOLD);for(UUID id:team.members)page.line((id.equals(team.leader)?"[队长] ":"[成员] ")+playerName.apply(id));}
        page.gap();List<String> actions=new ArrayList<>(),labels=new ArrayList<>();
        if(team==null){actions.addAll(List.of("create","invite"));labels.addAll(List.of("创建队伍","邀请玩家"));}
        else if(team.leader.equals(self)){actions.add("invite");labels.add("邀请玩家");if(team.members.size()>1){actions.addAll(List.of("transfer","kick"));labels.addAll(List.of("移交队长","移除队员"));}actions.add("disband");labels.add("解散队伍");}
        else{actions.add("leave");labels.add("离开队伍");}
        int columns=Minecraft.getInstance().getWindow().getGuiScale()==2||page.contentWidth()>=400?4:2,cell=(page.contentWidth()-24)/columns,base=page.cursor();
        for(int i=0;i<actions.size();i++){String action=actions.get(i);page.button(labels.get(i),(i%columns)*cell,base+(i/columns)*26,cell-5,()->{
            if(action.equals("invite"))Minecraft.getInstance().gui.pushScreenLayer(new Target(team,self,playerName,command,action));
            else if(action.equals("kick")||action.equals("transfer"))Minecraft.getInstance().gui.pushScreenLayer(new Target(team,self,playerName,command,action));
            else command.accept(action,"");
        });}
        page.cursor(base+((actions.size()+columns-1)/columns)*26);page.line("通过邀请加入队伍。公开副本按地图与难度匹配，同步由本人选择。",UiTheme.MUTED);
    }
    private static final class Target extends ThemedScreen{
        private final Team team;private final UUID self;private final Function<UUID,String> names;private final BiConsumer<String,String> command;private final String kind;private int offset;
        Target(Team team,UUID self,Function<UUID,String> names,BiConsumer<String,String> command,String kind){super(Component.literal(kind.equals("invite")?"邀请玩家":kind.equals("kick")?"移除队员":"移交队长"),ThemeDomain.MENU);this.team=team;this.self=self;this.names=names;this.command=command;this.kind=kind;}
        @Override protected void init(){int x=(width-250)/2,y=(height-220)/2;addRenderableWidget(Button.builder(Component.literal("返回"),b->onClose()).bounds(x+190,y+8,50,20).build(ThemedButton::new));
            if(kind.equals("invite")){var input=addRenderableWidget(new EditBox(UiFonts.font(),x+12,y+48,226,20,Component.literal("玩家账号名")));input.setMaxLength(32);input.setHint(Component.literal("玩家账号名"));addRenderableWidget(Button.builder(Component.literal("邀请"),b->{String name=input.getValue().trim();if(name.isEmpty())return;onClose();command.accept(kind,name);}).bounds(x+12,y+80,226,22).build(ThemedButton::new));}
            else{var members=team.members.stream().filter(id->!id.equals(self)).toList();int rows=5;offset=Math.clamp(offset,0,Math.max(0,members.size()-rows));int index=0;for(UUID id:members.subList(offset,Math.min(members.size(),offset+rows))){int at=index++;addRenderableWidget(Button.builder(Component.literal(names.apply(id)),b->{onClose();command.accept(kind,id.toString());}).bounds(x+12,y+40+at*25,226,22).build(ThemedButton::new));}if(members.size()>rows){var previous=addRenderableWidget(Button.builder(Component.literal("上一页"),b->{offset-=rows;rebuildWidgets();}).bounds(x+12,y+184,108,22).build(ThemedButton::new));previous.active=offset>0;var next=addRenderableWidget(Button.builder(Component.literal("下一页"),b->{offset+=rows;rebuildWidgets();}).bounds(x+130,y+184,108,22).build(ThemedButton::new));next.active=offset+rows<members.size();}}
        }
        @Override protected void drawBackground(GuiGraphicsExtractor g,int mx,int my,float a){g.fill(0,0,width,height,UiTheme.OVERLAY);UiTheme.panel(g,(width-250)/2,(height-220)/2,250,220);}
        @Override protected void drawContents(GuiGraphicsExtractor g,int mx,int my,float a){g.text(UiFonts.font(),title,(width-250)/2+12,(height-220)/2+14,UiTheme.TEXT,false);super.drawContents(g,mx,my,a);}
        @Override public void onClose(){minecraft.gui.popScreenLayer();}
        @Override public boolean isPauseScreen(){return false;}
    }
}
