package cn.yumis.fight.client;
import cn.yumis.fight.game.GameItems;
import cn.yumis.fight.rules.*;
import cn.yumis.fight.rules.Models.*;
import static cn.yumis.fight.rules.Rules.*;
import static cn.yumis.fight.rules.Models.JSON;
import com.google.gson.*;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import java.util.*;
/** Separate, scrollable pages. Text keeps Minecraft's native size at every GUI scale. */
public final class FightScreen extends Screen {
    private static final String[] NAMES={
        "角色属性","职业技能","转职任务","副职业","战斗队伍","地下城","设置","商城","次元背包"
    }
    ;
    private static final String[] ICONS={
        "character","skills","job","professions","party","dungeons","settings","shop","dimensional_bag"
    }
    ;
    public final int page;
    private final ClientState state=ClientState.INSTANCE;
    private record Line(String text,int x,int y,int color){
    }
    private final List<Line> lines=new ArrayList<>();
    private record StatHint(String name,int x,int y,int width,int height){}
    private final List<StatHint> statHints=new ArrayList<>();
    private record Card(int x,int y,int width,int height){
    }
    private record Icon(String path,int x,int y,int size){
    }
    private final List<Card> cards=new ArrayList<>();
    private final List<Icon> icons=new ArrayList<>();
    private final List<AbstractWidget> body=new ArrayList<>();
    private int sidebar,left,top,panelWidth,panelHeight,scroll,contentHeight,selectedDungeon;
    private Difficulty difficulty=Difficulty.NORMAL;
    private boolean sync=true;
    private EditBox input;
    private String inputValue="";
    private final Map<AbstractWidget,Skill> skillRows=new LinkedHashMap<>();
    private int nextY;
    private long themeRevision=UiTheme.revision;
    public FightScreen(){
        this(-1);
    }
    public FightScreen(int page){
        super(net.minecraft.client.Minecraft.getInstance(),UiFonts.font(),Component.literal(page<0?"地下城与战斗":NAMES[page]));
        this.page=page;
    }
    @Override protected void init(){
        UiTheme.check();
        boolean small=ClientSettings.panel.equals("small");
        panelWidth=page<0?Math.min(width-12,380):Math.min(width-12,small?760:ClientSettings.panel.equals("large")?1000:900);
        panelHeight=page<0?Math.min(height-12,294):Math.min(height-12,small?Math.round(height*.8f):ClientSettings.panel.equals("large")?660:600);
        if(page>=0&&small)panelWidth=Math.min(panelWidth,Math.round(width*.8f));
        left=(width-panelWidth)/2;
        top=(height-panelHeight)/2;
        sidebar=page>=0&&panelWidth>=480?112:0;
        build();
    }
    public void refresh(){
        if(font!=null&&!(input!=null&&input.isFocused()))build();
    }
    private void build(){
        try(var palette=UiTheme.character(page==0&&ClientSettings.characterDark)){buildContent();}
    }
    private void buildContent(){
        if(input!=null)inputValue=input.getValue();
        clearWidgets();
        body.clear();
        skillRows.clear();
        lines.clear();statHints.clear();
        cards.clear();
        icons.clear();
        nextY=0;
        buttonFixed(page<0?"关闭":"返回",left+panelWidth-50,top+7,42,()->{
            state.upload();if(page<0)onClose();else minecraft.gui.setScreen(new FightScreen());
        }
        );
        if(page==0)buttonFixed("暗色模式："+(ClientSettings.characterDark?"开":"关"),left+panelWidth-136,top+7,81,()->{ClientSettings.characterDark=!ClientSettings.characterDark;ClientSettings.save();build();});
        if(page>=0){
            String[] labels={
                "属性","技能","转职","副职","队伍","副本","设置","商城"
            }
            ;
            int cell=(panelWidth-16)/8;
            for(int index=0;index<8;index++){
                final int target=index;
                var tab=Button.builder(Component.literal(labels[index]),button->{
                    state.upload();minecraft.gui.setScreen(new FightScreen(target));if(target==3)state.send("professions");
                }
                ).bounds(left+8+index*cell,top+35,cell-3,23).build(ThemedButton::new);
                ((ThemedButton)tab).selected=page==index;
                addRenderableWidget(tab);
            }
        }
        if(!state.ready){
            line("正在读取服务器角色档案…");
            contentHeight=40;
            return;
        }
        switch(page){
            case -1->menu();
            case 0->character();
            case 1->skills();
            case 2->job();
            case 3->professions();
            case 4->party();
            case 5->dungeons();
            case 6->settings();
            case 7->{
                line("商城");
                line("暂时没有商品。");
            }
            default->{
            }
        }
        contentHeight=nextY+12;
        scroll=Math.clamp(scroll,0,Math.max(0,contentHeight-bodyHeight()));
        updateVisibility();
    }
    private int bodyTop(){
        return top+(page<0?36:68);
    }
    private int bodyHeight(){
        return panelHeight-(page<0?66:98);
    }
    private int contentLeft(){
        return left+sidebar+(sidebar>0?18:8);
    }
    private int contentWidth(){
        return panelWidth-sidebar-(sidebar>0?26:16);
    }
    private void menu(){
        int cell=(panelWidth-20)/4;
        for(int i=0;i<NAMES.length;i++){
            int index=i,row=i/4,col=i%4;
            AbstractWidget b=Button.builder(Component.empty(),x->{
                state.upload();if(index==8){
                    onClose();state.send("bag");
                }
                else{
                    minecraft.gui.setScreen(new FightScreen(index));if(index==3)state.send("professions");
                }
            }
            ).bounds(left+10+col*cell,bodyTop()+row*68-scroll,cell-5,60).build(ThemedButton::new);
            b.setTooltip(Tooltip.create(UiFonts.decorate(Component.literal(NAMES[i]))));
            body.add(addRenderableWidget(b));
        }
        nextY=3*68;
    }
    private void line(String text){
        line(text,UiTheme.TEXT);
    }
    private void line(String text,int color){
        for(var chars:font.split(Component.literal(text),contentWidth()-24)){
            lines.add(new Line(charsToString(chars),0,nextY,color));
            nextY+=12;
        }
    }
    private static String charsToString(net.minecraft.util.FormattedCharSequence seq){
        StringBuilder b=new StringBuilder();
        seq.accept((i,s,c)->{
            b.appendCodePoint(c);return true;
        }
        );
        return b.toString();
    }
    private void gap(){
        nextY+=8;
    }
    private void rowButton(String label,Runnable action){
        button(label,0,nextY,contentWidth()-24,action);
        nextY+=24;
    }
    private void button(String label,int x,int y,int w,Runnable action){
        var b=Button.builder(Component.literal(label),ignored->action.run()).bounds(contentLeft()+12+x,bodyTop()+y-scroll,w,20).build(ThemedButton::new);
        body.add(addRenderableWidget(b));
    }
    private void buttonFixed(String label,int x,int y,int w,Runnable action){
        addRenderableWidget(Button.builder(Component.literal(label),ignored->action.run()).bounds(x,y,w,20).build(ThemedButton::new));
    }
    private void simpleProgress(String kind){
        state.progress(kind,"unused",0);
    }
    private void character(){
        Progress p=state.progress;
        Stats s=state.stats;
        line((p.swordmaster?"剑魂":"鬼剑士")+"  Lv."+p.level+" / 80",UiTheme.GOLD);
        line("战斗经验 "+p.experience+" / "+(p.level==80?"MAX":nextExperience(p.level))+"    自由点 "+p.free());
        line("按住 Shift 点击 +1：最多加5点。",UiTheme.MUTED);
        line("怪物攻击职业防护 "+(p.swordmaster?"65%":"35%")+"    "+(ClientState.now()<state.resources.combatUntil?"战斗中":"已脱战"));
        gap();
        String[] ids={
            "strength","intelligence","spirit"
        }
        ;
        Stat[] values={
            Stat.STRENGTH,Stat.INTELLIGENCE,Stat.SPIRIT
        }
        ;
        for(int i=0;i<3;i++){
            String id=ids[i];
            line(statName(values[i])+"  "+AffixText.integer(s.get(values[i])));
            statHints.add(new StatHint(statName(values[i]),0,nextY-12,contentWidth()-90,12));
            button("+1",contentWidth()-80,nextY-14,52,()->addPoints("allocate","stat",id));
            nextY+=10;
        }
        rowButton("重置自由点",()->simpleProgress("resetAttributes"));
        int columns=contentWidth()>=440?4:contentWidth()>=260?2:1,cell=(contentWidth()-24)/columns,index=0,base=nextY;
        for(Stat stat:Stat.values())if(stat!=Stat.STRENGTH&&stat!=Stat.INTELLIGENCE&&stat!=Stat.SPIRIT){
            statCard(statName(stat),AffixText.integer(s.get(stat))+(stat==Stat.ATTACK_SPEED?"%":""),index++,columns,cell,base);
        }
        statCard("闪避率",AffixText.integer(CombatRules.evasionChance(s.get(Stat.EVASION))*100)+"%",index++,columns,cell,base);
        statCard("物理面板攻击",AffixText.integer(s.panel(false)),index++,columns,cell,base);
        statCard("魔法面板攻击",AffixText.integer(s.panel(true)),index++,columns,cell,base);
        nextY=base+((index+columns-1)/columns)*38;
        gap();
        rowButton("个人等级同步："+(p.sync?"开启":"关闭"),()->state.progress("sync","enabled",!p.sync));
        line("饰品按 G 打开 Curios 界面管理；无效等级或职业的装备不提供属性。",UiTheme.MUTED);
    }
    private void statCard(String name,String value,int index,int columns,int cell,int base){
        int x=(index%columns)*cell,y=base+(index/columns)*38;
        cards.add(new Card(x,y,cell-5,33));
        statHints.add(new StatHint(name,x,y,cell-5,33));
        lines.add(new Line(name,x+6,y+5,UiTheme.MUTED));
        lines.add(new Line(value,x+6,y+18,UiTheme.TEXT));
    }
    private void addPoints(String kind,String key,String value){
        state.upload();
        var request=state.operation(kind);
        request.addProperty(key,value);
        request.addProperty("count",minecraft.hasShiftDown()?5:1);
        state.send("progress",request);
    }
    private void skills(){
        line("剩余SP："+state.progress.sp+"    装备可提高有效等级",UiTheme.GOLD);
        line("悬浮查看实时技能介绍；点击已学主动技能选择快捷槽。",UiTheme.MUTED);
        line("按住 Shift 点击学习：最多升5级；点数不足时尽量加满。",UiTheme.MUTED);
        for(Skill skill:Skill.values()){
            int rank=state.progress.learned(skill),effective=state.stats.effective(state.progress,skill),limit=skill.limit(state.progress.level,state.progress.swordmaster);
            button(skill.title+" "+rank+"/"+limit+(effective!=rank?" ["+effective+"]":""),0,nextY,contentWidth()-142,()->{
                if(!skill.passive()&&state.progress.learned(skill)>0)minecraft.gui.pushScreenLayer(new SkillBindingScreen(skill));
            });
            ((ThemedButton)body.getLast()).textInset=24;
            skillRows.put(body.getLast(),skill);
            button("学习 "+skill.sp,contentWidth()-128,nextY,104,()->addPoints("learn","skill",skill.name()));
            skillRows.put(body.getLast(),skill);
            icons.add(new Icon("textures/gui/skills/"+skill.icon()+".png",4,nextY+3,14));
            nextY+=24;
        }
        gap();
        rowButton("洗点并返还技能SP",()->simpleProgress("resetSkills"));
    }
    private void job(){
        Progress p=state.progress;
        line(p.swordmaster?"已完成转职：剑魂":"鬼剑士 → 剑魂",UiTheme.GOLD);
        line("达到20级后接取任务，以实际终结击杀计数。");
        line("有效等级≥20敌人："+p.questKills+" / 50");
        line("其中精英："+p.questElites+" / 3");
        line("提交铁锭32、青金石16；转职保留已有进度。");
        gap();
        if(!p.swordmaster){
            rowButton(p.questAccepted?"任务进行中":"接取转职任务",()->simpleProgress("quest"));
            rowButton("提交材料并转职",()->simpleProgress("job"));
        }
    }
    private void professions(){
        if(!state.professions.has("professions")){
            line("正在读取副职业…");
            return;
        }
        JsonObject all=state.professions.getAsJsonObject("professions");
        String[] ids={
            "craftsman","enchanter","farmer","fisherman"
        }
        ,names={
            "工匠","附魔师","农夫","渔夫"
        }
        ;
        for(int i=0;i<4;i++){
            JsonObject p=all.getAsJsonObject(ids[i]);
            line(names[i]+"  Lv."+p.get("level").getAsInt()+" / 10",UiTheme.GOLD);
            line("累计经验 "+p.get("experience").getAsString()+"    职业币 "+p.get("coins").getAsString());
            line(i==0?"装备制作成功 +60经验与职业币":i==1?"自定义附魔接口保留，暂未开放":"每次有效"+(i==2?"收获成熟作物 +10":"钓获 +20")+"经验与职业币",UiTheme.MUTED);
            gap();
        }
        line("金币："+state.professions.get("gold").getAsString());
        line("TODO：矿工、药剂师、分解师。");
        rowButton("刷新",()->state.send("professions"));
    }
    private void party(){
        Team t=state.party;
        if(t==null)line("当前没有战斗队伍");
        else{
            line("战斗队伍 · 人数 "+t.members.size(),UiTheme.GOLD);
            for(UUID id:t.members)line((id.equals(t.leader)?"[队长] ":"[成员] ")+playerName(id));
            line("待批准申请 "+t.applications.size()+"    待接受邀请 "+t.invitations.size());
        }
        gap();
        line("目标玩家账号名：");
        input=new EditBox(font,left+16,bodyTop()+nextY-scroll,contentWidth()-24,20,Component.literal("玩家账号名"));
        input.setValue(inputValue);
        input.setMaxLength(32);
        input.setBordered(false);
        body.add(addRenderableWidget(input));
        nextY+=26;
        String[] actions={
            "create","invite","apply","accept","approve","transfer","kick","leave","disband"
        }
        ,labels={
            "创建队伍","邀请目标玩家","申请加入目标队伍","接受目标队长邀请","批准目标申请","移交队长","移除目标成员","离开队伍","解散队伍"
        }
        ;
        for(int i=0;i<actions.length;i++){
            String action=actions[i];
            rowButton(labels[i],()->{
                var j=state.operation(action);j.addProperty("name",input.getValue().trim());state.send("party",j);
            }
            );
        }
        line("队伍人数不限；创建地下城由队长选择设置，公开轮次可随时加入。",UiTheme.MUTED);
    }
    private String playerName(UUID id){
        var p=minecraft.getConnection()==null?null:minecraft.getConnection().getPlayerInfo(id);
        return p==null?id+" [离线]":p.getProfile().name()+" [在线]";
    }
    private void dungeons(){
        JsonArray defs=state.profile.getAsJsonArray("dungeonCatalog");
        if(defs==null)return;
        selectedDungeon=Math.clamp(selectedDungeon,0,defs.size()-1);
        if(state.dungeon!=null){
            line("当前："+state.dungeon.dungeon+"  波次 "+(state.dungeon.wave+1)+" / 7");
            line("累计参与 "+state.dungeon.roster.size()+"  "+state.dungeon.difficulty.title()+"  "+state.dungeon.status);
            rowButton("离开地下城",()->state.send("dungeon",state.operation("leave")));
            gap();
        }
        button("难度："+difficulty.title(),0,nextY,(contentWidth()-32)/2,()->{
            difficulty=Difficulty.values()[(difficulty.ordinal()+1)%5];build();
        }
        );
        button("同步："+(sync?"开启":"关闭"),(contentWidth()-32)/2+8,nextY,(contentWidth()-32)/2,()->{
            sync=!sync;build();
        }
        );
        nextY+=28;
        for(int i=0;i<defs.size();i++){
            JsonObject d=defs.get(i).getAsJsonObject();
            String id=d.get("id").getAsString();
            boolean available=d.get("available").getAsBoolean();
            rowButton("Lv."+d.get("level").getAsInt()+" "+d.get("name").getAsString()+(available?" · 创建／进入":" · 尚未开放"),()->{
                if(!available){
                    state.message("该副本为未来内容占位");return;
                }
                state.upload();var j=state.operation("join");j.addProperty("dungeon",id);j.addProperty("difficulty",difficulty.name());j.addProperty("sync",sync);state.send("dungeon",j);
            }
            );
        }
        line("已运行的公开轮次沿用原设置。材料按产区掉落，不随同步等级改变。",UiTheme.MUTED);
    }
    private void settings(){
        rowButton("面板大小："+ClientSettings.panel,()->{
            ClientSettings.panel=switch(ClientSettings.panel){
                case "small"->"medium";case "medium"->"large";default->"small";
            }
            ;ClientSettings.save();init();
        }
        );
        rowButton("本人伤害数字倍率："+ClientSettings.damageScale,()->{
            ClientSettings.damageScale=ClientSettings.damageScale==1?2:ClientSettings.damageScale==2?4:1;ClientSettings.save();build();
        }
        );
        rowButton("目标框位置："+ClientSettings.targetPosition,()->{
            ClientSettings.targetPosition=ClientSettings.targetPosition.equals("top_center")?"top_left":"top_center";ClientSettings.save();build();
        }
        );
        rowButton("目标框倍率："+ClientSettings.targetScale,()->{
            double value=ClientSettings.targetScale==1?1.5:ClientSettings.targetScale==1.5?2:1;if(180*value>width-16){
                state.message("当前窗口无法容纳该倍率");return;
            }
            ClientSettings.targetScale=value;ClientSettings.save();build();
        }
        );
        line("按键可在原版设置中修改。普通、Ctrl、Alt三层共24槽。");
        gap();
        line("界面字体："+UiFonts.currentName());

        line(UiFonts.PREVIEW);
        if(!UiFonts.status().isEmpty())line(UiFonts.status(),UiTheme.MUTED);
        line("I 返回菜单；Esc 一次关闭。聚焦输入框时保留文字输入。",UiTheme.MUTED);
    }
    private void updateVisibility(){
        for(AbstractWidget w:body){
            w.visible=w.getY()>=bodyTop()&&w.getY()+w.getHeight()<=bodyTop()+bodyHeight();
            w.active=w.visible;
        }
    }
    @Override public void extractBackground(GuiGraphicsExtractor g,int mx,int my,float a){
        try(var palette=UiTheme.character(page==0&&ClientSettings.characterDark)){drawBackground(g);}
    }
    private void drawBackground(GuiGraphicsExtractor g){
        g.fill(0,0,width,height,UiTheme.OVERLAY);
        UiTheme.panel(g,left,top,(left+panelWidth)-(left),(top+panelHeight)-(top));

    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float a){
        try(var palette=UiTheme.character(page==0&&ClientSettings.characterDark)){drawContents(g,mx,my,a);}
    }
    private void drawContents(GuiGraphicsExtractor g,int mx,int my,float a){
        UiTheme.card(g,left+6,top+6,(left+panelWidth-6)-(left+6),(top+30)-(top+6));
        g.text(font,title,left+14,top+13,UiTheme.TEXT,false);
        UiTheme.card(g,contentLeft(),bodyTop(),(contentLeft()+contentWidth())-(contentLeft()),(bodyTop()+bodyHeight())-(bodyTop()));
        UiTheme.card(g,left+6,top+panelHeight-28,(left+panelWidth-6)-(left+6),(top+panelHeight-6)-(top+panelHeight-28));
        g.text(font,page<0?"I / Esc 关闭":"I 返回菜单 · Esc 关闭 · 滚轮浏览",left+14,top+panelHeight-20,UiTheme.MUTED,false);
        if(sidebar>0){
            UiTheme.card(g,left+6,bodyTop(),(left+sidebar+8)-(left+6),(bodyTop()+bodyHeight())-(bodyTop()));
            int yy=bodyTop()+12;
            g.text(font,state.progress.swordmaster?"剑魂":"鬼剑士",left+14,yy,UiTheme.GOLD,false);
            g.text(font,"战斗等级 "+state.progress.level,left+14,yy+21,UiTheme.TEXT,false);
            g.text(font,"可用 SP  "+state.progress.sp,left+14,yy+44,UiTheme.GOLD,false);
            g.text(font,"自由点  "+state.progress.free(),left+14,yy+65,UiTheme.ACCENT,false);
            g.text(font,ClientState.now()<state.resources.combatUntil?"战斗中":"脱战",left+14,yy+94,UiTheme.MUTED,false);
        }
        g.enableScissor(left+5,bodyTop(),left+panelWidth-5,bodyTop()+bodyHeight());
        for(Card card:cards){
            int yy=bodyTop()+card.y-scroll;
            UiTheme.card(g,contentLeft()+12+card.x,yy,(contentLeft()+12+card.x+card.width)-(contentLeft()+12+card.x),(yy+card.height)-(yy));
        }
        super.extractRenderState(g,mx,my,a);
        for(Icon icon:icons){
            int yy=bodyTop()+icon.y-scroll;
            g.blit(ClientBootstrap.id(icon.path),contentLeft()+12+icon.x,yy,contentLeft()+12+icon.x+icon.size,yy+icon.size,0,1,0,1);
        }
        for(Line l:lines){
            int y=bodyTop()+l.y-scroll;
            if(y>=bodyTop()&&y+9<=bodyTop()+bodyHeight())g.text(font,page==0?AttributeTooltip.numbers(l.text):Component.literal(l.text),contentLeft()+12+l.x,y,0xff000000|l.color,false);
        }
        if(page<0&&state.ready){
            int cell=(panelWidth-20)/4;
            for(int i=0;i<NAMES.length;i++){
                int x=left+10+(i%4)*cell,y=bodyTop()+(i/4)*68-scroll;
                if(y<bodyTop()||y+60>bodyTop()+bodyHeight())continue;
                g.text(font,NAMES[i],x+(cell-5-font.width(NAMES[i]))/2,y+6,UiTheme.TEXT,false);
                g.blit(Identifier.fromNamespaceAndPath(GameItems.NS,"textures/gui/menu/"+ICONS[i]+".png"),x+(cell-37)/2,y+21,x+(cell+27)/2,y+53,0,1,0,1);
            }
        }
        g.disableScissor();
        if(page==0&&my>=bodyTop()&&my<bodyTop()+bodyHeight())for(var hint:statHints){int x=contentLeft()+12+hint.x,y=bodyTop()+hint.y-scroll;if(mx>=x&&mx<x+hint.width&&my>=y&&my<y+hint.height){g.setComponentTooltipForNextFrame(UiFonts.font(),AttributeTooltip.lines(hint.name),mx,my);break;}}
        for(var child:children())if(child instanceof AbstractWidget w&&!body.contains(w))w.extractRenderState(g,mx,my,a);
        if(minecraft.gui.screen()==this)for(var row:skillRows.entrySet()){
            var widget=row.getKey();
            if(widget.visible&&mx>=widget.getX()&&mx<widget.getX()+widget.getWidth()&&my>=widget.getY()&&my<widget.getY()+widget.getHeight()){
                SkillTooltip.render(g,row.getValue(),mx,my);
                break;
            }
        }
        if(contentHeight>bodyHeight()){
            int bar=Math.max(12,bodyHeight()*bodyHeight()/contentHeight),y=bodyTop()+(bodyHeight()-bar)*scroll/Math.max(1,contentHeight-bodyHeight());
            g.fill(left+panelWidth-6,y,left+panelWidth-3,y+bar,UiTheme.ACCENT);
        }
    }
    @Override public boolean mouseScrolled(double x,double y,double sx,double sy){
        int old=scroll;
        scroll=Math.clamp(scroll-(int)(sy*24),0,Math.max(0,contentHeight-bodyHeight()));
        if(old!=scroll){
            for(AbstractWidget w:body)w.setY(w.getY()+old-scroll);
            updateVisibility();
        }
        return true;
    }
    @Override public boolean keyPressed(KeyEvent e){
        if(e.isEscape()){
            onClose();
            return true;
        }
        if(!(getFocused() instanceof EditBox box&&box.isFocused())&&ClientBootstrap.MENU.matches(e)){
            state.upload();
            if(page<0)onClose();
            else minecraft.gui.setScreen(new FightScreen());
            return true;
        }
        return super.keyPressed(e);
    }
    @Override public void onClose(){
        state.upload();
        minecraft.gui.setScreen(null);
    }
    @Override public void tick(){UiTheme.check();if(themeRevision!=UiTheme.revision){themeRevision=UiTheme.revision;build();}}
    @Override public boolean isPauseScreen(){
        return false;
    }
    public static String number(double n){
        return n==Math.rint(n)?String.format(java.util.Locale.ROOT,"%.0f",n):String.format(java.util.Locale.ROOT,"%.1f",n);
    }
    public static String statName(Stat s){
        return new String[]{
            "生命","MP","力量","智力","精神","基础物攻","基础魔攻","物理防御","魔法防御","光强化","火强化","冰强化","暗强化","光抗性","火抗性","冰抗性","暗抗性","掉宝率","装备护盾","闪避值","攻击速度"
        }
        [s.ordinal()];
    }
}
