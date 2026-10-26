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
    private record Card(int x,int y,int width,int height){
    }
    private record Icon(String path,int x,int y,int size){
    }
    private final List<Card> cards=new ArrayList<>();
    private final List<Icon> icons=new ArrayList<>();
    private final List<AbstractWidget> body=new ArrayList<>();
    private int sidebar,left,top,panelWidth,panelHeight,scroll,contentHeight,bindSlot,selectedDungeon;
    private Difficulty difficulty=Difficulty.NORMAL;
    private boolean sync=true;
    private EditBox input;
    private String inputValue="";
    private Skill selected=Skill.GHOST;
    private int nextY;
    public FightScreen(){
        this(-1);
    }
    public FightScreen(int page){
        super(Component.literal(page<0?"地下城与战斗":NAMES[page]));
        this.page=page;
    }
    @Override protected void init(){
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
        if(input!=null)inputValue=input.getValue();
        clearWidgets();
        body.clear();
        lines.clear();
        cards.clear();
        icons.clear();
        nextY=0;
        buttonFixed(page<0?"关闭":"返回",left+panelWidth-50,top+7,42,()->{
            state.upload();if(page<0)onClose();else minecraft.gui.setScreen(new FightScreen());
        }
        );
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
            b.setTooltip(Tooltip.create(Component.literal(NAMES[i])));
            body.add(addRenderableWidget(b));
        }
        nextY=3*68;
    }
    private void line(String text){
        line(text,0xe4e8ee);
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
        line((p.swordmaster?"剑魂":"鬼剑士")+"  Lv."+p.level+" / 80",0xffd47d);
        line("战斗经验 "+p.experience+" / "+(p.level==80?"MAX":nextExperience(p.level))+"    自由点 "+p.free());
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
            line(statName(values[i])+"  "+number(s.get(values[i])));
            button("+1",contentWidth()-80,nextY-14,52,()->state.progress("allocate","stat",id));
            nextY+=10;
        }
        rowButton("重置自由点",()->simpleProgress("resetAttributes"));
        int columns=contentWidth()>=440?4:contentWidth()>=260?2:1,cell=(contentWidth()-24)/columns,index=0,base=nextY;
        for(Stat stat:Stat.values())if(stat!=Stat.STRENGTH&&stat!=Stat.INTELLIGENCE&&stat!=Stat.SPIRIT){
            statCard(statName(stat),number(s.get(stat)),index++,columns,cell,base);
        }
        statCard("物理面板攻击",number(s.panel(false)),index++,columns,cell,base);
        statCard("魔法面板攻击",number(s.panel(true)),index++,columns,cell,base);
        nextY=base+((index+columns-1)/columns)*38;
        gap();
        rowButton("个人等级同步："+(p.sync?"开启":"关闭"),()->state.progress("sync","enabled",!p.sync));
        line("装备通过 E 背包右侧扩展栏管理。无效等级或职业的装备不提供属性。",0xa5adbb);
    }
    private void statCard(String name,String value,int index,int columns,int cell,int base){
        int x=(index%columns)*cell,y=base+(index/columns)*38;
        cards.add(new Card(x,y,cell-5,33));
        lines.add(new Line(name,x+6,y+5,UiTheme.MUTED));
        lines.add(new Line(value,x+6,y+18,UiTheme.TEXT));
    }
    private void skills(){
        line("剩余SP："+state.progress.sp+"    硬上限保留，装备可提高有效等级",0xffd47d);
        for(Skill s:Skill.values()){
            int rank=state.progress.learned(s),effective=state.stats.effective(state.progress,s),limit=s.limit(state.progress.level,state.progress.swordmaster);
            button("   "+s.title+" "+rank+"/"+limit+(effective!=rank?" ["+effective+"]":""),0,nextY,contentWidth()-142,()->{
                selected=s;build();
            }
            );
            button("学习 "+s.sp,contentWidth()-128,nextY,104,()->state.progress("learn","skill",s.name()));
            icons.add(new Icon("textures/gui/skills/"+s.icon()+".png",4,nextY+3,14));
            nextY+=24;
        }
        gap();
        line(selected.title+" · Lv."+selected.unlock+"解锁"+(selected.advanced()?" · 剑魂":""));
        line("MP "+selected.mp(Math.max(1,state.stats.effective(state.progress,selected)))+"    冷却 "+selected.cooldown+"秒    系数 "+number(selected.coefficient(Math.max(1,state.stats.effective(state.progress,selected)))*100)+"%");
        line(description(selected),0xaec6df);
        gap();
        String[] layers={
            "普通","Ctrl","Shift","Alt"
        }
        ;
        line("快捷槽："+layers[bindSlot/8]+" "+ClientBootstrap.SKILLS[bindSlot%8].getTranslatedKeyMessage().getString()+"  ["+(state.progress.slots[bindSlot].isEmpty()?"空":Skill.valueOf(state.progress.slots[bindSlot]).title)+"]");
        button("上一槽",0,nextY,70,()->{
            bindSlot=(bindSlot+31)%32;build();
        }
        );
        button("下一槽",76,nextY,70,()->{
            bindSlot=(bindSlot+1)%32;build();
        }
        );
        nextY+=24;
        rowButton("绑定选中技能："+selected.title,()->bind(selected.name()));
        rowButton("清空此槽",()->bind(""));
        rowButton("洗点并返还技能SP",()->simpleProgress("resetSkills"));
    }
    private void bind(String skill){
        var j=state.operation("bind");
        j.addProperty("slot",bindSlot);
        j.addProperty("skill",skill);
        state.send("progress",j);
    }
    private static String description(Skill s){
        return switch(s){
            case GHOST->"按住蓄力1秒达到双倍伤害，松开释放；满蓄后最多持有2秒。";
            case TRIPLE->"每段冲刺2.5格；1秒内再次按原槽续段，最多三段。";
            case WAVE->"地面波前进10格，逐目标命中一次，遇实墙停止。";
            case MASTERY->"主手切换太刀出血、光剑感电、巨剑技能增伤、短剑波与钝器眩晕。";
            case OVERDRIVE->"30秒内提高基础物理攻击。";
            case RISING->"冲撞4格后上挑；动作与结束后0.5秒提供位移防护。";
            case DRAW->"蓄势0.4秒，对前方180°、半径5格攻击，实墙遮挡。";
            case DRAGON->"最多四次4格冲刺；0.8秒内按原槽续段。";
            case DANCE->"10刀斩击及一次穿透终结剑气，终结时解除动作锁。";
            case STORM->"8秒剑阵：24柄不同外观剑、24次斩击及终结，全程免伤。";
        }
        ;
    }
    private void job(){
        Progress p=state.progress;
        line(p.swordmaster?"已完成转职：剑魂":"鬼剑士 → 剑魂",0xffd47d);
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
            line(names[i]+"  Lv."+p.get("level").getAsInt()+" / 10",0xffd47d);
            line("累计经验 "+p.get("experience").getAsString()+"    职业币 "+p.get("coins").getAsString());
            line(i==0?"装备制作成功 +60经验与职业币":i==1?"自定义附魔接口保留，暂未开放":"每次有效"+(i==2?"收获成熟作物 +10":"钓获 +20")+"经验与职业币",0xa5adbb);
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
            line("战斗队伍 · 人数 "+t.members.size(),0xffd47d);
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
        line("队伍人数不限；创建地下城由队长选择设置，公开轮次可随时加入。",0xa5adbb);
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
        line("已运行的公开轮次沿用原设置。材料按产区掉落，不随同步等级改变。",0xa5adbb);
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
        line("按键可在原版设置中修改。普通、Ctrl、Shift、Alt四层共32槽。");
        line("I 返回菜单；Esc 一次关闭。聚焦输入框时保留文字输入。",0xa5adbb);
    }
    private void updateVisibility(){
        for(AbstractWidget w:body){
            w.visible=w.getY()>=bodyTop()&&w.getY()+w.getHeight()<=bodyTop()+bodyHeight();
            w.active=w.visible;
        }
    }
    @Override public void extractBackground(GuiGraphicsExtractor g,int mx,int my,float a){
        g.fill(0,0,width,height,UiTheme.OVERLAY);
        g.fill(left,top,left+panelWidth,top+panelHeight,UiTheme.PANEL);
        g.fill(left,top,left+panelWidth,top+1,UiTheme.ACCENT);
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float a){
        g.fill(left+6,top+6,left+panelWidth-6,top+30,UiTheme.CARD);
        g.text(font,title,left+14,top+13,UiTheme.TEXT);
        g.fill(contentLeft(),bodyTop(),contentLeft()+contentWidth(),bodyTop()+bodyHeight(),UiTheme.CARD);
        g.fill(left+6,top+panelHeight-28,left+panelWidth-6,top+panelHeight-6,UiTheme.CARD);
        g.text(font,page<0?"I / Esc 关闭":"I 返回菜单 · Esc 关闭 · 滚轮浏览",left+14,top+panelHeight-20,UiTheme.MUTED,false);
        if(sidebar>0){
            g.fill(left+6,bodyTop(),left+sidebar+8,bodyTop()+bodyHeight(),UiTheme.CARD);
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
            g.fill(contentLeft()+12+card.x,yy,contentLeft()+12+card.x+card.width,yy+card.height,UiTheme.CARD);
        }
        super.extractRenderState(g,mx,my,a);
        for(Icon icon:icons){
            int yy=bodyTop()+icon.y-scroll;
            g.blit(ClientBootstrap.id(icon.path),contentLeft()+12+icon.x,yy,contentLeft()+12+icon.x+icon.size,yy+icon.size,0,1,0,1);
        }
        for(Line l:lines){
            int y=bodyTop()+l.y-scroll;
            if(y>=bodyTop()&&y+9<=bodyTop()+bodyHeight())g.text(font,l.text,contentLeft()+12+l.x,y,0xff000000|l.color);
        }
        if(page<0&&state.ready){
            int cell=(panelWidth-20)/4;
            for(int i=0;i<NAMES.length;i++){
                int x=left+10+(i%4)*cell,y=bodyTop()+(i/4)*68-scroll;
                if(y<bodyTop()||y+60>bodyTop()+bodyHeight())continue;
                g.text(font,NAMES[i],x+(cell-5-font.width(NAMES[i]))/2,y+6,0xfff0e5cf);
                g.blit(Identifier.fromNamespaceAndPath(GameItems.NS,"textures/gui/menu/"+ICONS[i]+".png"),x+(cell-37)/2,y+21,x+(cell+27)/2,y+53,0,1,0,1);
            }
        }
        g.disableScissor();
        for(var child:children())if(child instanceof AbstractWidget w&&!body.contains(w))w.extractRenderState(g,mx,my,a);
        if(contentHeight>bodyHeight()){
            int bar=Math.max(12,bodyHeight()*bodyHeight()/contentHeight),y=bodyTop()+(bodyHeight()-bar)*scroll/Math.max(1,contentHeight-bodyHeight());
            g.fill(left+panelWidth-6,y,left+panelWidth-3,y+bar,0xffa6b4c8);
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
    @Override public boolean isPauseScreen(){
        return false;
    }
    public static String number(double n){
        return n==Math.rint(n)?String.format(java.util.Locale.ROOT,"%.0f",n):String.format(java.util.Locale.ROOT,"%.1f",n);
    }
    public static String statName(Stat s){
        return new String[]{
            "生命","MP","力量","智力","精神","基础物攻","基础魔攻","物理防御","魔法防御","光强化","火强化","冰强化","暗强化","光抗性","火抗性","冰抗性","暗抗性","掉宝率","装备护盾"
        }
        [s.ordinal()];
    }
}
