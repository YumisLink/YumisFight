package cn.yumis.fight.modules.arkguard.application;

import cn.yumis.fight.modules.arkguard.api.*;
import cn.yumis.fight.modules.npc.api.*;
import cn.yumis.fight.modules.presentation.api.ModText;
import cn.yumis.fight.platform.ItemData;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.phys.Vec3;
import java.util.*;

final class ArkScene {
    private static final int BLUE=0x80D8FF,GRAY=0x8E969E;
    private final ServerLevel level;
    private final List<List<ArkDisplays.Label>> traits=new ArrayList<>();
    private final List<List<ArkDisplays.Label>> information=new ArrayList<>();
    private final Map<ArkWar.FieldBoard,List<ArkDisplays.Label>> fieldTraits=new HashMap<>();
    private final Map<ArkWar.FieldBoard,List<ArkDisplays.Label>> fieldInformation=new HashMap<>();
    private final List<Entity> towers=new ArrayList<>();
    private final Map<BlockPos,String> signs=new HashMap<>();
    private boolean guideAnchored;
    ArkScene(ServerLevel level){
        this.level=level;for(int i=0;i<4;i++){traits.add(new ArrayList<>());information.add(new ArrayList<>());}
        for(int side:new int[]{-1,1})for(int y=0;y<=3;y++)towers.add(ArkDisplays.block(level,Vec3.atLowerCornerOf(ArkWar.ORIGIN.offset(side*12,y,84)),y==3?"minecraft:amethyst_block":y==2?"minecraft:sea_lantern":"minecraft:polished_deepslate",1));
    }
    void update(ArkBattle battle,Set<UUID> present,long now){
        if(towers.stream().anyMatch(Entity::isRemoved)){towers.forEach(Entity::discard);towers.clear();for(int side:new int[]{-1,1})for(int y=0;y<=3;y++)towers.add(ArkDisplays.block(level,Vec3.atLowerCornerOf(ArkWar.ORIGIN.offset(side*12,y,84)),y==3?"minecraft:amethyst_block":y==2?"minecraft:sea_lantern":"minecraft:polished_deepslate",1));}
        anchorGuide();
        for(Entity entity:level.getAllEntities())if(entity instanceof NpcEntity npc&&NpcInteractionComponent.id(npc).equals("ark_war_attendant"))npc.discard();
        for(var plot:ArkWar.PLOTS){
            var seat=battle==null?null:battle.seats.stream().filter(s->s.index==plot.index()).findFirst().orElse(null);
            for(Entity entity:level.getAllEntities())if(entity instanceof ItemFrame frame){
                int slot=plot.frame(frame.position());if(slot<0)continue;
                var item=offer(battle,seat,slot);if(!ItemStack.isSameItemSameComponents(frame.getItem(),item))frame.setItem(item,false);
                frame.setInvulnerable(true);frame.setInvisible(seat==null||slot>=seat.roster.shop.size());frame.setGlowingTag(seat!=null&&slot<seat.roster.shop.size()&&!seat.roster.shop.get(slot).isBlank()&&seat.roster.owns(seat.roster.shop.get(slot)));
            }
            var rowTexts=new ArrayList<Component>();
            rowTexts.add(Component.literal(seat==null?"空闲阵地":seat.name+" · 羁绊").withColor(0xD9E5EB));
            if(seat!=null){
                var counts=battle.editing()?ArkTraits.counts(seat.roster,battle.rules):seat.counts;
                for(String id:ArkTraits.displayOrder(counts.keySet(),counts,seat.layers)){
                    var meter=ArkTraits.meter(id,counts,seat.layers,battle.rules);
                    rowTexts.add(Component.literal(meter.label()).withColor(meter.tier()>0?BLUE:GRAY));
                }
            }
            var boards=plot.fieldBoards().stream().filter(this::backing).toList();
            display(traits.get(plot.index()),plot.traitOrigin(),-plot.facing(),rowTexts,.12,.45f,200);
            for(var board:plot.fieldBoards()){
                var labels=fieldTraits.computeIfAbsent(board,key->new ArrayList<>());
                if(boards.contains(board))display(labels,board.traitOrigin(),board.facing().toYRot(),rowTexts,.12,.45f,200);
                else labels.forEach(ArkDisplays.Label::clear);
            }
            information(plot,battle,seat,present,now,boards);backing(plot);sign(plot,battle,seat,present,now,boards);
            settingsPillar(plot);
        }
        if(availableSign(ArkWar.CORE_HOME_SIGN))sign(ArkWar.CORE_HOME_SIGN,Direction.WEST,"传送自己家\n右键前往\n个人商店／阵地\n仅参战者");
    }
    static List<Component> informationText(ArkBattle battle,ArkBattle.Seat seat,Set<UUID> present,long now){
        var text=new ArrayList<Component>();
        if(battle==null){
            text.add(Component.literal("方舟战争 · 空闲阵地").withColor(0xE8D39B));
            text.add(Component.literal("请到水晶旁报名").withColor(BLUE));
            text.add(Component.literal("全员确认后进入准备").withColor(GRAY));
        }else{
            text.add(Component.literal("第"+battle.wave+"／20波 · "+(battle.editing()?"准备":"战斗")).withColor(0xE8D39B));
            text.add(Component.literal("核心 "+ArkRules.number(battle.core.target.health(1)*battle.core.target.life.q)+"／"+ArkRules.number(battle.core.target.health(1))).withColor(BLUE));
            text.add(Component.literal(battle.deadline==0?(battle.editing()?"准备不限时":"Boss战不限时"):"剩余 "+Math.max(0,(battle.deadline-now+999)/1000)+"秒").withColor(BLUE));
            text.add(Component.literal(battle.editing()?"已准备 "+battle.readyCount(present)+"／"+battle.presentCount(present):"场内 "+battle.actors.stream().filter(a->a.enemy&&a.alive()).count()+" · 待出场 "+battle.seats.stream().mapToInt(s->s.queue.size()-s.spawned).sum()).withColor(0xD9E5EB));
            if(seat!=null){
                text.add(Component.literal(seat.name+" · 商店"+seat.roster.level+"级").withColor(0xD9E5EB));
                text.add(Component.literal((battle.editing()?"人口 ":"下波人口 ")+seat.roster.population()+"／"+battle.rules.populations[seat.roster.level-1]+" · 方舟币 "+seat.roster.coins).withColor(BLUE));
            }
            if(battle.editing()||battle.wave<20){
                int wave=battle.editing()?battle.wave:battle.wave+1;boolean dragon=battle.dragon[wave],boss=wave%5==0;var e=battle.rules.enemies;
                text.add(Component.literal((battle.editing()?"本波":"下波")+"预告 · "+(dragon?"龙人军团":"哥布林军团")).withColor(0xE8D39B));
                text.add(Component.literal(boss?(dragon?"鲁卡斯":"落雷凯诺")+"×1 · 护卫×"+(dragon?e.dragonGuards:e.goblinGuards):"每线 "+e.count(wave,dragon)+"名 · 每秒1名").withColor(0xD9E5EB));
                text.add(Component.literal((boss?"Boss":"普通")+"生命 "+ArkRules.number(boss?e.bossHealth(wave):e.health(wave,dragon))).withColor(BLUE));
                text.add(Component.literal((boss?"Boss":"普通")+"攻击 "+ArkRules.number(boss?e.bossAttack(wave):e.attack(wave,dragon))).withColor(BLUE));
                text.add(Component.literal("物防 "+ArkRules.number(e.physicalDefense(wave,dragon))+" · 魔防 "+ArkRules.number(e.magicalDefense(wave))).withColor(BLUE));
                double[] resist=e.resistances(dragon);text.add(Component.literal("光／火／冰／暗抗").withColor(GRAY));
                text.add(Component.literal(ArkRules.number(resist[0])+"／"+ArkRules.number(resist[1])+"／"+ArkRules.number(resist[2])+"／"+ArkRules.number(resist[3])).withColor(BLUE));
                if(!boss){
                    text.add(Component.literal(dragon?"龙人／翼龙／蓝龙人／蓝翼龙":"哥布林／投石／队长").withColor(GRAY));
                    text.add(Component.literal(dragon?"生命倍率 1／0.8／1.2／0.9":"生命倍率 1／0.8／1.6").withColor(BLUE));
                    text.add(Component.literal(dragon?"攻击倍率 1／1.2／1.1／1.3":"攻击倍率 1／1.2／1.4").withColor(BLUE));
                }
            }
        }
        return text;
    }
    private void information(ArkWar.Plot plot,ArkBattle battle,ArkBattle.Seat seat,Set<UUID> present,long now,List<ArkWar.FieldBoard> boards){
        var text=informationText(battle,seat,present,now);
        information(information.get(plot.index()),plot.informationOrigin(),-plot.facing(),text);
        for(var board:plot.fieldBoards()){
            var labels=fieldInformation.computeIfAbsent(board,unused->new ArrayList<>());
            if(boards.contains(board))information(labels,board.informationOrigin(),board.facing().toYRot(),text);
            else labels.forEach(ArkDisplays.Label::clear);
        }
    }
    private void information(List<ArkDisplays.Label> labels,Vec3 origin,float yaw,List<Component> text){
        display(labels,origin,yaw,text,.13,.42f,210);
    }
    private void display(List<ArkDisplays.Label> labels,Vec3 origin,float yaw,List<Component> text,double spacing,float scale,int width){
        while(labels.size()<text.size())labels.add(new ArkDisplays.Label());
        for(int i=0;i<labels.size();i++)if(i<text.size())labels.get(i).set(level,origin.add(0,-spacing*i,0),yaw,text.get(i),scale,width);else labels.get(i).clear();
    }
    private ItemStack offer(ArkBattle battle,ArkBattle.Seat seat,int slot){
        if(seat==null||slot>=seat.roster.shop.size())return ItemStack.EMPTY;
        String id=seat.roster.shop.get(slot);if(id.isBlank())return ItemStack.EMPTY;var unit=battle.rules.unit(id);
        var stack=new ItemStack(Items.PAPER);stack.set(DataComponents.ITEM_MODEL,Identifier.fromNamespaceAndPath(ItemData.NS,"ark/card/"+id));
        stack.set(DataComponents.CUSTOM_NAME,ModText.of(unit.name()+" ★").withColor(unit.nameColor())
            .append(ModText.of(" · "+unit.cost()+"方舟币").withColor(BLUE)));
        stack.set(DataComponents.LORE,new ItemLore(unit.description().stream().map(unit::describe).map(ModText::of).map(c->(Component)c).toList()));return stack;
    }
    private void backing(ArkWar.Plot plot){
        int x=plot.side()<0?-5:5;
        for(int offset:new int[]{-8,6})for(int z=plot.centerZ()+offset;z<=plot.centerZ()+offset+1;z++)for(int y=0;y<3;y++){var pos=ArkWar.ORIGIN.offset(x,y,z);var state=level.getBlockState(pos);if(state.isAir()||state.canBeReplaced())level.setBlock(pos,Blocks.STONE_BRICKS.defaultBlockState(),3);}
    }
    private boolean backing(ArkWar.FieldBoard board){
        for(int x=-2;x<=2;x++)for(int y=0;y<3;y++){
            var state=level.getBlockState(board.backing(x,y));
            if(y==0){if(!state.is(Blocks.STONE_BRICK_SLAB)&&!state.is(Blocks.STONE_BRICKS))return false;}
            else if(!state.isAir()&&!state.canBeReplaced()&&!state.is(Blocks.STONE_BRICKS))return false;
        }
        for(var pos:List.of(board.readySign(),board.coreSign()))if(!availableSign(pos))return false;
        for(int x=-2;x<=2;x++)for(int y=0;y<3;y++){
            var pos=board.backing(x,y);if(!level.getBlockState(pos).is(Blocks.STONE_BRICKS))level.setBlock(pos,Blocks.STONE_BRICKS.defaultBlockState(),3);
        }
        return true;
    }
    private boolean availableSign(BlockPos pos){var state=level.getBlockState(pos);return state.isAir()||state.canBeReplaced()||state.is(Blocks.OAK_WALL_SIGN);}
    private void settingsPillar(ArkWar.Plot plot){
        var base=plot.settingsPillar();
        for(int y=0;y<3;y++){
            var block=y==1?Blocks.CHISELED_STONE_BRICKS:Blocks.STONE_BRICKS;
            var pos=base.above(y);
            var current=level.getBlockState(pos);
            if(!current.is(block)&&!current.isAir()&&!current.canBeReplaced())return;
        }
        for(int y=0;y<3;y++){
            var block=y==1?Blocks.CHISELED_STONE_BRICKS:Blocks.STONE_BRICKS;
            var pos=base.above(y);
            if(!level.getBlockState(pos).is(block))level.setBlock(pos,block.defaultBlockState(),3);
        }
        if(availableSign(plot.settingsSign()))sign(plot.settingsSign(),plot.side(),"显示设置\n右键调整\n血条与伤害浮字\n仅本机");
    }
    private void sign(ArkWar.Plot plot,ArkBattle battle,ArkBattle.Seat seat,Set<UUID> present,long now,List<ArkWar.FieldBoard> boards){
        String value=readySignText(battle,present,now);
        sign(plot.readySign(),plot.side(),value);
        for(var board:boards){
            sign(board.readySign(),board.facing(),value);
            sign(board.coreSign(),board.facing(),"传送核心\n右键前往\n水晶集合点\n ");
            var pos=board.readySign().west();
            if(level.getBlockEntity(pos) instanceof SignBlockEntity old&&old.isWaxed()
                &&old.getBlockState().is(Blocks.OAK_WALL_SIGN)
                &&old.getBlockState().getValue(WallSignBlock.FACING)==board.facing()&&isReadySignText(old.getFrontText())){
                level.removeBlock(pos,false);
                signs.remove(pos);
            }
        }
        if(seat==null){
            sign(plot.refreshSign(),plot.side(),"刷新商店\n阵地尚未分配\n \n ");
            sign(plot.upgradeSign(),plot.side(),"提升商店\n阵地尚未分配\n \n ");
        }else{
            sign(plot.refreshSign(),plot.side(),"刷新商店\n消耗 "+battle.rules.refreshCost+"方舟币\n当前 "+seat.roster.level+"级 · "+seat.roster.shop.size()+"栏\n右键刷新");
            boolean maximum=seat.roster.level>=battle.rules.maximumLevel;
            sign(plot.upgradeSign(),plot.side(),"提升商店\n"+(maximum?"已满级 · "+seat.roster.level+"级":seat.roster.level+" → "+(seat.roster.level+1)+"级")+"\n"+(maximum?"当前 "+seat.roster.shop.size()+"个购买位":"消耗 "+seat.roster.upgradePrice(battle.rules)+"方舟币")+"\n"+(maximum?" ":"右键升本"));
        }
    }
    static boolean isReadySignText(SignText text){
        String title=text.getMessage(0,false).getString();
        return switch(text.getMessage(3,false).getString()){
            case "本阵地尚未分配"->title.equals("方舟战争");
            case "右键准备／取消"->title.matches("第\\d+波准备");
            case "等待本波结束"->title.matches("第\\d+波战斗");
            default->false;
        };
    }
    static String readySignText(ArkBattle battle,Set<UUID> present,long now){
        if(battle==null)return "方舟战争\n水晶旁报名\n全员确认开始\n本阵地尚未分配";
        if(battle.editing())return "第"+battle.wave+"波准备\n"+(battle.deadline==0?"准备不限时":"剩余 "+Math.max(0,(battle.deadline-now+999)/1000)+"秒")+"\n已准备 "+battle.readyCount(present)+"/"+battle.presentCount(present)+"\n右键准备／取消";
        return "第"+battle.wave+"波战斗\n"+(battle.boss()?"Boss战不限时":"剩余 "+Math.max(0,(battle.deadline-now+999)/1000)+"秒")+"\n可购买，禁止布阵\n等待本波结束";
    }
    private void sign(BlockPos pos,int side,String value){
        sign(pos,side<0?Direction.WEST:Direction.EAST,value);
    }
    private void sign(BlockPos pos,Direction facing,String value){
        var support=pos.relative(facing.getOpposite());
        var backing=level.getBlockState(support);if(backing.isAir()||backing.canBeReplaced())level.setBlock(support,Blocks.STONE_BRICKS.defaultBlockState(),3);
        var state=level.getBlockState(pos);
        if(!state.is(Blocks.OAK_WALL_SIGN)){
            level.setBlock(pos,Blocks.OAK_WALL_SIGN.defaultBlockState().setValue(WallSignBlock.FACING,facing),3);signs.remove(pos);
        }
        if(value.equals(signs.get(pos)))return;
        if(level.getBlockEntity(pos) instanceof SignBlockEntity sign){
            var text=new SignText();String[] lines=value.split("\n");for(int i=0;i<4;i++)text=text.setMessage(i,ModText.of(lines[i]).withColor(i==0?0xE8D39B:BLUE));
            sign.setText(text,true);sign.setText(text,false);sign.setWaxed(true);sign.setChanged();level.sendBlockUpdated(pos,level.getBlockState(pos),level.getBlockState(pos),3);signs.put(pos,value);
        }
    }
    private void anchorGuide(){
        if(guideAnchored)return;
        for(Entity raw:level.getAllEntities())if(raw instanceof NpcEntity npc&&!(npc instanceof ArkRoleEntity)&&NpcInteractionComponent.id(npc).equals("ark_war_guide")){
            npc.relocate(new Vec3(ArkWar.ORIGIN.getX()-3.5,ArkWar.ORIGIN.getY()+3,ArkWar.ORIGIN.getZ()+85.5),0);guideAnchored=true;return;
        }
    }
    void close(){
        traits.forEach(list->list.forEach(ArkDisplays.Label::clear));
        information.forEach(list->list.forEach(ArkDisplays.Label::clear));
        fieldTraits.values().forEach(list->list.forEach(ArkDisplays.Label::clear));
        fieldInformation.values().forEach(list->list.forEach(ArkDisplays.Label::clear));
        fieldTraits.clear();fieldInformation.clear();towers.forEach(Entity::discard);towers.clear();signs.clear();
    }
}
