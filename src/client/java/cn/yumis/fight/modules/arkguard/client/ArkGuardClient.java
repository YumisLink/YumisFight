package cn.yumis.fight.modules.arkguard.client;

import cn.yumis.fight.architecture.CommandRouter;
import cn.yumis.fight.modules.arkguard.api.*;
import cn.yumis.fight.modules.npc.api.NpcInteractionComponent;
import cn.yumis.fight.modules.presentation.api.client.*;
import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.client.event.InputEvent;
import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/** Owner-side shopping and placement decisions, plus private scene information. */
public final class ArkGuardClient {
    private static final Gson JSON=new Gson();
    private static final Random RANDOM=new Random();
    private static final int BLUE=0xFF80D8FF,GRAY=0xFF969EA8;
    private static final Pattern NUMBERS=Pattern.compile("\\d+(?:\\.\\d+)?%?");
    private static final Pattern VALUES=Pattern.compile("\\{([A-Za-z0-9]+)(?::(percent|stars|starsPercent))?}");
    private static boolean inside,waiting,participating,personalReady,personalConfirmed,returnAvailable;
    private static String round="",phase="LOBBY",stage="",queriedSession="",lastResult="";
    private static ArkRules rules;
    private static ArkRoster roster;
    private static JsonObject war=new JsonObject();
    private static Map<String,Integer> layers=Map.of(),counts=Map.of();
    private static int seat=-1,selection;
    private static Object world;
    private static long pendingAt,nextQuery;
    private static PlayerPageSurface menu;
    private ArkGuardClient(){}

    public static void register(CommandRouter<Void,JsonObject> messages){
        NpcInteractionComponent.unregisterClient("ark_war");
        NpcInteractionComponent.registerClient("ark_war",(entity,action)->{
            if(!NpcInteractionComponent.id(entity).equals("ark_war_guide"))return false;
            request(action.value());return true;
        });
        messages.register("arkguard","arkGuard",(context,value)->{
            var ui=ClientUi.current();if(!ui.session().equals(value.get("session").getAsString()))return;
            if(value.has("effects")){ArkVisuals.receive(value);return;}
            boolean entered=waiting&&value.get("inside").getAsBoolean();
            inside=value.get("inside").getAsBoolean();waiting=value.get("waiting").getAsBoolean();
            participating=value.get("participating").getAsBoolean();
            returnAvailable=value.get("returnAvailable").getAsBoolean();
            lastResult=value.has("lastResult")?value.get("lastResult").getAsString():"";
            if(value.has("war")){
                var next=value.getAsJsonObject("war");String nextRound=next.get("round").getAsString(),nextPhase=next.get("phase").getAsString();
                String nextStage=next.has("stage")?next.get("stage").getAsString():"";
                boolean changed=!round.equals(nextRound)||!stage.equals(nextStage)||!phase.equals(nextPhase);
                if(!round.equals(nextRound)){roster=null;rules=null;selection=0;}
                if(next.has("rules"))rules=ArkRules.parse(next.get("rules").toString());
                round=nextRound;phase=nextPhase;stage=nextStage;war=next.deepCopy();ArkVisuals.state(next);
                seat=integer("seat",-1);personalReady=flag("personalReady");personalConfirmed=flag("personalConfirmed");
                if(next.has("roster")){
                    var received=JSON.fromJson(next.get("roster"),ArkRoster.class);
                    if(changed||roster==null||received.wave!=roster.wave||received.revision>=roster.revision||pendingAt==0){
                        roster=received;pendingAt=0;
                    }
                }else if(!participating){roster=null;pendingAt=0;}
                layers=numbers(next,"layers");
                counts=editing()&&roster!=null&&rules!=null?ArkTraits.counts(roster,rules):numbers(next,"counts");
            }
            var mc=Minecraft.getInstance();
            if(entered)mc.gui.setScreen(null);
            else if(menu!=null&&mc.gui.screen()==menu.screen())menu.rebuild();
        });
    }
    public static void clear(){
        ArkVisuals.clear();
        inside=waiting=participating=personalReady=personalConfirmed=returnAvailable=false;
        round=stage=queriedSession=lastResult="";phase="LOBBY";seat=-1;selection=0;rules=null;roster=null;
        war=new JsonObject();layers=counts=Map.of();world=null;pendingAt=nextQuery=0;menu=null;
    }
    public static void tick(){
        var mc=Minecraft.getInstance();var ui=ClientUi.current();if(!ui.ready()||mc.player==null)return;
        long now=ClientUi.now();boolean moved=world!=mc.level;
        if(moved){world=mc.level;nextQuery=0;}
        if(!queriedSession.equals(ui.session())||now>=nextQuery||pendingAt>0&&now-pendingAt>3000){
            queriedSession=ui.session();nextQuery=now+15000;
            if(pendingAt>0&&now-pendingAt>3000)pendingAt=0;
            ui.send("arkGuard",ui.operation("status"));
        }
        inside=ArkWar.contains(mc.player.level(),mc.player.blockPosition());
        ArkVisuals.tick();
    }
    private static Map<String,Integer> numbers(JsonObject object,String key){
        var result=new LinkedHashMap<String,Integer>();
        if(object.has(key))for(var entry:object.getAsJsonObject(key).entrySet())result.put(entry.getKey(),entry.getValue().getAsInt());
        return result;
    }
    private static boolean flag(String key){return war.has(key)&&war.get(key).getAsBoolean();}
    private static int integer(String key,int fallback){return war.has(key)?war.get(key).getAsInt():fallback;}
    private static long deadline(){return war.has("deadline")?war.get("deadline").getAsLong():0;}
    private static boolean editing(){return phase.equals("PREP");}
    private static boolean usable(){
        var mc=Minecraft.getInstance();return inside&&mc.player!=null&&mc.level!=null&&mc.gui.screen()==null&&ClientUi.current().ready();
    }
    private static boolean nearCrystal(){
        var mc=Minecraft.getInstance();return mc.player!=null&&inside&&mc.player.position().distanceToSqr(Vec3.atCenterOf(ArkWar.CORE))<18*18;
    }
    private static ArkWar.Plot plot(){return seat>=0&&seat<4?ArkWar.PLOTS.get(seat):null;}
    private static boolean own(ArkRoleEntity entity){var mc=Minecraft.getInstance();return mc.player!=null&&entity.ownerId().equals(mc.player.getStringUUID());}
    private static void request(String kind){
        var ui=ClientUi.current();
        if(kind.equals("confirm_start")){
            if(!nearCrystal()||!participating||!phase.equals("LOBBY")||rules==null){ui.message("请在水晶旁报名后确认开始。");return;}
            if(roster==null||!roster.round.equals(round))roster=ArkRoster.create(Minecraft.getInstance().player.getStringUUID(),round,rules,RANDOM);
            var value=ui.operation(kind);value.addProperty("round",round);value.add("roster",JSON.toJsonTree(roster));ui.send("arkGuard",value);return;
        }
        if((kind.equals("join")||kind.equals("withdraw")||kind.equals("cancel_start"))&&!nearCrystal()){ui.message("请到水晶旁报名或确认。");return;}
        var value=ui.operation(kind);value.addProperty("round",round);ui.upload();ui.send("arkGuard",value);
        if(kind.equals("leave"))Minecraft.getInstance().gui.setScreen(null);
    }
    private static void change(boolean shopping,Consumer<ArkRoster> operation){
        if(roster==null||rules==null||plot()==null||!editing()&&(!shopping||!phase.equals("BATTLE"))){ClientUi.current().message("战斗期间可以购买角色，布阵、抱起和出售请等准备阶段。");return;}
        try{
            var updated=roster.copy();operation.accept(updated);updated.revision++;updated.plan(rules,RANDOM);
            roster=updated;if(editing()){counts=ArkTraits.counts(roster,rules);personalReady=false;}pendingAt=ClientUi.now();
            var value=ClientUi.current().operation("owner_facts");value.addProperty("round",round);value.addProperty("stage",stage);value.add("roster",JSON.toJsonTree(roster));
            ClientUi.current().send("arkGuard",value);
        }catch(IllegalArgumentException failure){ClientUi.current().message(failure.getMessage());}
    }
    private static void ready(){
        if(!editing()||roster==null)return;personalReady=!personalReady;
        var value=ClientUi.current().operation("ready");value.addProperty("round",round);value.addProperty("stage",stage);value.addProperty("value",personalReady);ClientUi.current().send("arkGuard",value);
    }
    private static ArkRoleEntity pointedRole(){
        var hit=Minecraft.getInstance().hitResult;
        return hit instanceof EntityHitResult e&&e.getEntity() instanceof ArkRoleEntity role&&!role.roleId().equals("core")?role:null;
    }
    private static int[] pointedFrame(){
        if(!(Minecraft.getInstance().hitResult instanceof EntityHitResult e)||!(e.getEntity() instanceof ItemFrame frame))return null;
        for(var p:ArkWar.PLOTS){int slot=p.frame(frame.position());if(slot>=0)return new int[]{p.index(),slot};}return null;
    }
    private static ArkWar.Plot pointedSign(){
        var hit=Minecraft.getInstance().hitResult;if(!(hit instanceof BlockHitResult block))return null;
        var pos=block.getBlockPos();return ArkWar.PLOTS.stream().filter(plot->plot.readySign().equals(pos)||plot.refreshSign().equals(pos)||plot.upgradeSign().equals(pos)).findFirst().orElse(null);
    }
    public static void scroll(InputEvent.MouseScrollingEvent event){
        if(!usable())return;var role=pointedRole();boolean carrying=roster!=null&&!roster.carrying.isBlank();
        if(!carrying&&(role==null||!own(role)))return;
        event.setCanceled(true);if(event.getAccumulatedScrollY()!=0)selection=Math.floorMod(selection-Integer.signum(event.getAccumulatedScrollY()),2);
    }
    public static void interact(InputEvent.InteractionKeyMappingTriggered event){
        if(!usable())return;var mc=Minecraft.getInstance();var frame=pointedFrame();var role=pointedRole();
        boolean carrying=roster!=null&&!roster.carrying.isBlank();
        BlockPos block=mc.hitResult instanceof BlockHitResult b?b.getBlockPos():null;
        ArkWar.Plot sign=pointedSign();
        if(frame==null&&role==null&&!carrying&&sign==null)return;
        event.setCanceled(true);event.setSwingHand(false);
        if(!event.isUseItem()||event.getHand()!=InteractionHand.MAIN_HAND)return;
        if(frame!=null){
            if(frame[0]!=seat){ClientUi.current().message("这是其他参战者的商店。");return;}
            if(roster==null)return;
            if(frame[1]>=roster.shop.size()){ClientUi.current().message("提升商店等级后解锁这个购买位。");return;}
            change(true,r->r.buy(frame[1],plot().benches(mc.level).size(),rules));
        }else if(sign!=null){
            if(sign.index()!=seat){ClientUi.current().message("请使用自己商店旁的告示牌。");return;}
            if(sign.refreshSign().equals(block))change(true,r->r.refresh(rules,RANDOM,true));
            else if(sign.upgradeSign().equals(block))change(true,r->r.upgrade(rules,RANDOM));
            else ready();
        }else if(carrying){
            if(selection==1){String token=roster.carrying;change(false,r->r.sell(token,rules));selection=0;return;}
            if(block==null||plot()==null)return;
            int bench=plot().bench(mc.level,block);
            if(bench<0&&!plot().deployment(mc.level,block)){ClientUi.current().message("请放在自己的草地、泥土路径或磨制安山岩台阶备战位上。");return;}
            change(false,r->r.place(block.getX(),block.getY(),block.getZ(),bench,rules));selection=0;
        }else if(role!=null&&own(role)){
            String token=role.cardId();if(selection==1)change(false,r->r.sell(token,rules));else change(false,r->r.pick(token));selection=0;
        }
    }

    public static boolean coinHud(GuiGraphicsExtractor g){
        if(!inside)return false;
        String text=roster==null?"方舟币 · 观战":"方舟币 "+roster.coins;
        var font=UiFonts.font();int w=font.width(text)+12,x=g.guiWidth()-w-6,y=g.guiHeight()-23;if(x<g.guiWidth()/2+95)y-=35;
        UiTheme.hud(g,x,y,w,17);g.text(font,text,x+6,y+4,UiTheme.HUD_GOLD,true);return true;
    }
    public static void render(GuiGraphicsExtractor g){
        ArkVisuals.hud(g);
        var mc=Minecraft.getInstance();if(!usable()||mc.gui.hud.isHidden()||rules==null)return;
        var detail=details();
        if(!detail.isEmpty())panel(g,Math.max(8,g.guiWidth()-Math.min(315,g.guiWidth()/2)-8),8,Math.min(315,g.guiWidth()/2),detail);
    }
    private static List<Component> details(){
        var rows=new ArrayList<Component>();var frame=pointedFrame();var role=pointedRole();var sign=pointedSign();
        if(frame!=null&&frame[0]==seat&&roster!=null){
            int slot=frame[1];
            if(slot<roster.shop.size()){String id=roster.shop.get(slot);if(!id.isBlank())unit(rows,rules.unit(id),1,null);}
            else rows.add(Component.literal("提升商店等级后解锁这个购买位").withColor(GRAY));
        }else if(sign!=null&&sign.index()==seat&&roster!=null){
            var block=((BlockHitResult)Minecraft.getInstance().hitResult).getBlockPos();
            if(sign.refreshSign().equals(block)){rows.add(numeric("刷新商店 · "+rules.refreshCost+"方舟币"));rows.add(Component.literal("每波准备阶段自动免费刷新一次").withColor(UiTheme.HUD_MUTED));}
            else if(sign.upgradeSign().equals(block)){
                rows.add(numeric("商店 "+roster.level+"级 · "+rules.populations[roster.level-1]+"人口"));
                rows.add(numeric(roster.level>=7?"已满级":"升级费用 "+roster.upgradePrice(rules)+"方舟币 · 逐波减少1币"));
                for(int i=0;i<7;i++)rows.add(Component.literal((i+1)+"级 "+Arrays.toString(rules.shopProbabilities[i])+" · "+rules.shopSlots(i+1)+"购买位 · "+rules.populations[i]+"人口").withColor(i==roster.level-1?BLUE:GRAY));
            }else rows.add(numeric("上场 "+roster.population()+" · 备战位 "+plot().benches(Minecraft.getInstance().level).size()));
            if(!editing())rows.add(Component.literal("战斗中可购买，布阵与上场合成下波生效").withColor(UiTheme.HUD_MUTED));
        }else if(role!=null){
            var definition=rules.units.stream().filter(u->u.id().equals(role.roleId())).findFirst().orElse(null);
            if(definition!=null)unit(rows,definition,role.star(),own(role)?role.cardId():null);
        }else{
            String trait=pointedTrait();
            if(trait!=null&&roster!=null){
                rows.add(Component.literal(rules.trait(trait).name()+" · "+layers.getOrDefault(trait,0)+"层").withColor(ArkTraits.active(trait,counts,rules)?BLUE:GRAY));
                for(String line:ArkTraits.details(trait,counts,layers,rules))rows.add(numeric(line));
                List<String> deployed=roster.cards.stream().filter(c->c.deployed&&!c.held).map(c->c.unit).distinct().toList();
                rows.add(Component.literal("已上场："+String.join("、",rules.units.stream().filter(u->u.has(trait)&&deployed.contains(u.id())).map(ArkRules.Unit::name).toList())).withColor(BLUE));
                rows.add(Component.literal("未上场："+String.join("、",rules.units.stream().filter(u->u.has(trait)&&!deployed.contains(u.id())).map(ArkRules.Unit::name).toList())).withColor(GRAY));
            }
        }
        if(roster!=null&&!roster.carrying.isBlank()&&editing()){
            if(!rows.isEmpty())rows.add(Component.literal(" "));
            var card=roster.card(roster.carrying);rows.add(Component.literal("抱起："+rules.unit(card.unit).name()+" "+"★".repeat(card.star)).withColor(UiTheme.HUD_GOLD));
            rows.add(Component.literal((selection==0?"› ":"  ")+"放置到阵地／备战位").withColor(selection==0?BLUE:GRAY));
            rows.add(Component.literal((selection==1?"› ":"  ")+"出售 · "+sale(card)+"方舟币").withColor(selection==1?BLUE:GRAY));
            rows.add(Component.literal("滚轮选择 · 右键确认").withColor(UiTheme.HUD_MUTED));
        }else if(role!=null&&own(role)&&editing()){
            rows.add(Component.literal((selection==0?"› 抱起":"  抱起")+" · "+(selection==1?"› 出售":"  出售")).withColor(BLUE));
            rows.add(Component.literal("滚轮选择 · 右键确认").withColor(UiTheme.HUD_MUTED));
        }
        return rows;
    }
    private static int sale(ArkRoster.Card card){var u=rules.unit(card.unit);return u.parameters().containsKey("sale")?(int)u.p("sale"):card.copies();}
    private static String pointedTrait(){
        var mc=Minecraft.getInstance();var p=plot();if(p==null||roster==null||mc.player==null)return null;
        Vec3 origin=mc.player.getEyePosition(),look=mc.player.getLookAngle(),board=p.traitOrigin();
        if(Math.abs(look.x)<.001)return null;double t=(board.x-origin.x)/look.x;if(t<0||t>8)return null;
        Vec3 point=origin.add(look.scale(t));if(Math.abs(point.z-board.z)>1.3)return null;
        if(mc.hitResult instanceof BlockHitResult hit&&hit.getLocation().distanceTo(origin)+.16<t)return null;
        int row=(int)Math.round((board.y-point.y)/.12)-1;var ids=ArkTraits.present(roster,rules);
        return row>=0&&row<ids.size()&&Math.abs(point.y-(board.y-.12*(row+1)))<.065?ids.get(row):null;
    }
    private static void unit(List<Component> rows,ArkRules.Unit u,int star,String token){
        rows.add(Component.literal(u.name()+" "+"★".repeat(star)+" · "+u.role()).withColor(UiTheme.HUD_GOLD));
        rows.add(Component.literal("技能类型："+u.skillTrigger()).withColor(BLUE));
        rows.add(numeric("价格 "+u.cost()+"方舟币 · "+String.join("／",u.traits().stream().map(id->rules.trait(id).name()).toList())));
        JsonObject live=token!=null&&war.has("units")&&war.getAsJsonObject("units").has(token)?war.getAsJsonObject("units").getAsJsonObject(token):null;
        rows.add(numeric("生命 "+(live==null?ArkRules.number(u.health()*u.starScale(star)):ArkRules.number(live.get("health").getAsDouble())+" / "+ArkRules.number(live.get("maximum").getAsDouble()))));
        rows.add(numeric("攻击 "+ArkRules.number(live==null?u.attack()*u.starScale(star):live.get("attack").getAsDouble())+" · "+(u.magic()?"魔法":"物理")));
        rows.add(numeric("物防 "+ArkRules.number(live==null?u.physicalDefense():live.get("physicalDefense").getAsDouble())+" · 魔防 "+ArkRules.number(live==null?u.magicalDefense():live.get("magicalDefense").getAsDouble())));
        rows.add(numeric("攻速 "+ArkRules.number(live==null?u.attackRate():live.get("attackRate").getAsDouble())+"次/秒 · 射程 "+ArkRules.number(live==null?u.range():live.get("range").getAsDouble())+"格"));
        for(String line:u.description())rows.add(description(u,line,star));
        if(live!=null&&!editing())rows.add(numeric((u.p("cooldown")>0?"自动技能冷却 "+ArkRules.number(live.get("cooldown").getAsDouble()/1000)+"秒 · ":"")+"情绪 "+live.get("emotion").getAsInt()+"层"));
    }
    private static Component description(ArkRules.Unit unit,String template,int star){
        var line=Component.empty();var matcher=VALUES.matcher(template);int end=0;
        while(matcher.find()){
            line.append(numeric(template.substring(end,matcher.start())));
            String key=matcher.group(1),style=matcher.group(2);boolean percent=style!=null&&style.contains("Percent")||"percent".equals(style);
            if(unit.stars().containsKey(key)){
                var values=unit.stars().get(key);
                for(int i=0;i<values.size();i++){if(i>0)line.append(Component.literal("／").withColor(GRAY));line.append(Component.literal(ArkRules.number(values.get(i)*(percent?100:1))+(percent?"%":"")).withColor(i==star-1?BLUE:GRAY));}
            }else line.append(Component.literal(ArkRules.number(unit.p(key)*(percent?100:1))+(percent?"%":"")).withColor(BLUE));
            end=matcher.end();
        }
        line.append(numeric(template.substring(end)));return line;
    }
    private static Component numeric(String text){
        var result=Component.empty();var matcher=NUMBERS.matcher(text);int end=0;
        while(matcher.find()){result.append(Component.literal(text.substring(end,matcher.start())).withColor(UiTheme.HUD_TEXT));result.append(Component.literal(matcher.group()).withColor(BLUE));end=matcher.end();}
        result.append(Component.literal(text.substring(end)).withColor(UiTheme.HUD_TEXT));return result;
    }
    private static void panel(GuiGraphicsExtractor g,int x,int y,int width,List<Component> rows){
        var font=UiFonts.font();int w=Math.max(90,Math.min(width,g.guiWidth()-x-8));
        var lines=new ArrayList<net.minecraft.util.FormattedCharSequence>();for(var row:rows)lines.addAll(font.split(UiFonts.decorate(row),w-14));
        int max=Math.max(1,(g.guiHeight()-y-22)/12);if(lines.size()>max)lines.subList(max,lines.size()).clear();
        UiTheme.hud(g,x,y,w,lines.size()*12+12);for(int i=0;i<lines.size();i++)g.text(font,lines.get(i),x+7,y+6+i*12,UiTheme.HUD_TEXT,false);
    }

    public static void render(PlayerPageSurface page,boolean canEnter){
        menu=page;var ui=ClientUi.current();if(ui.ready()&&!ui.session().equals(queriedSession)){queriedSession=ui.session();ui.send("arkGuard",ui.operation("status"));}
        page.line("其他玩法",UiTheme.GOLD);page.gap();int top=page.cursor();page.card(0,top,page.contentWidth()-5,146);
        page.text("方舟战争",12,top+12,UiTheme.GOLD);text(page,"四路迎敌 · 共同守护方舟",top+33,UiTheme.TEXT);
        text(page,inside?(phase.equals("LOBBY")?"水晶集合 · 报名 "+integer("registered",0)+"/4 · 开始确认 "+integer("confirmed",0)+"/"+integer("registered",0):"第"+integer("wave",1)+"波 · "+(participating?"已参战":"观战")):waiting?"正在准备方舟地图……":"进入水晶集合点，向附近引导员报名。",top+53,UiTheme.MUTED);
        int width=Math.min(156,Math.max(1,(page.contentWidth()-36)/2));
        if(inside){
            if(phase.equals("LOBBY")){
                var join=page.button(participating?"取消报名":"报名参加",12,top+77,width,()->{if(canEnter)request(participating?"withdraw":"join");else ui.message("请先结束当前战斗或等待复活。");});join.active=nearCrystal();
                var confirm=page.button(personalConfirmed?"取消开始确认":"确认开始本局",24+width,top+77,width,()->request(personalConfirmed?"cancel_start":"confirm_start"));confirm.active=participating&&nearCrystal();
            }else{
                var ready=page.button(personalReady?"取消准备":"准备下一场",12,top+77,width,ArkGuardClient::ready);ready.active=participating&&editing();
            }
            page.button("离开方舟战争",12,top+107,width,()->request("leave"));
        }else{
            var button=page.button(waiting?"取消等候":"进入方舟战争",12,top+77,width,()->{
                if(!canEnter){ui.message("请先结束当前战斗或等待复活。");return;}
                if(waiting){waiting=false;request("leave");}else{waiting=true;request("enter");}page.rebuild();
            });button.active=ui.ready();
            if(returnAvailable)page.button("返回进入前的位置",24+width,top+77,width,()->request("leave"));
        }
        page.cursor(top+154);
    }
    private static void text(PlayerPageSurface page,String value,int y,int color){page.text(UiFonts.font().plainSubstrByWidth(value,Math.max(1,page.contentWidth()-24)),12,y,color);}
}
