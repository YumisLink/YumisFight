package cn.yumis.fight.modules.skills.client;
import cn.yumis.fight.modules.skills.api.CareerSkills;
import cn.yumis.fight.modules.skills.api.CareerRules;
import cn.yumis.fight.modules.skills.api.SummonerSkills;
import cn.yumis.fight.modules.skills.api.SummonKind;
import cn.yumis.fight.modules.skills.api.client.SkillsClient;
import cn.yumis.fight.modules.skills.api.client.SkillController;

import cn.yumis.fight.modules.presentation.api.client.UiFonts;
import cn.yumis.fight.modules.presentation.api.client.UiTheme;
import cn.yumis.fight.modules.attributes.api.Stat;

import cn.yumis.fight.modules.skills.api.SkillPreview;
import cn.yumis.fight.modules.skills.api.WeaponSkills;
import cn.yumis.fight.modules.skills.api.SkillDefinition;
import cn.yumis.fight.modules.skills.api.SkillCatalog;
import cn.yumis.fight.modules.skills.api.SkillLearning;

import cn.yumis.fight.modules.skills.api.SkillParameters;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import java.util.*;
import static cn.yumis.fight.modules.presentation.api.client.NumberText.number;

/** Rebuild while hovered so temporary buffs expire even when no profile packet arrives. */
public final class SkillTooltip {
    private SkillTooltip(){}
    private static Object hoveredScreen;
    private static String hoveredSkill="";
    private static long hoveredAt;
    private static int hoveredX,hoveredY,scroll,maximumScroll;
    private record Content(FormattedCharSequence title,List<FormattedCharSequence> body,int width,int rows,int step,int offset) implements TooltipComponent {}
    private record Viewport(Content content) implements ClientTooltipComponent {
        public int getWidth(Font font){return content.width();}
        public int getHeight(Font font){return (content.rows()+1)*content.step()+12;}
        public void extractText(GuiGraphicsExtractor graphics,Font font,int x,int y){
            graphics.text(font,content.title(),x,y,0xffffffff,false);
            for(int row=0;row<content.rows();row++){
                int index=content.offset()+row;
                if(index<content.body().size())graphics.text(font,content.body().get(index),x,y+(row+1)*content.step(),0xffffffff,false);
            }
            String guide="Shift＋滚轮查看 · "+(content.offset()+1)+"–"+Math.min(content.body().size(),content.offset()+content.rows())+"/"+content.body().size();
            graphics.text(font,UiFonts.decorate(Component.literal(font.plainSubstrByWidth(guide,content.width()))),x,y+(content.rows()+1)*content.step()+2,UiTheme.tooltipColor(UiTheme.ACCENT),false);
            int track=content.rows()*content.step(),thumb=Math.max(8,track*content.rows()/content.body().size());
            int top=y+content.step(),offset=(track-thumb)*content.offset()/Math.max(1,content.body().size()-content.rows());
            graphics.fill(x+content.width()-3,top,x+content.width()-1,top+track,0x80404c64);
            graphics.fill(x+content.width()-3,top+offset,x+content.width()-1,top+offset+thumb,0xff94bfe8);
        }
    }
    public static void register(IEventBus bus){
        bus.addListener((RegisterClientTooltipComponentFactoriesEvent event)->event.register(Content.class,Viewport::new));
        NeoForge.EVENT_BUS.addListener((ScreenEvent.MouseScrolled.Pre event)->{
            if(event.getScreen()!=hoveredScreen||maximumScroll<=0||!Minecraft.getInstance().hasShiftDown()||net.minecraft.util.Util.getMillis()-hoveredAt>250||Math.abs(event.getMouseX()-hoveredX)>3||Math.abs(event.getMouseY()-hoveredY)>3||event.getScrollDeltaY()==0)return;
            int steps=(int)Math.min(12,Math.ceil(Math.abs(event.getScrollDeltaY()))*3);
            scroll=Math.clamp(scroll-(event.getScrollDeltaY()>0?steps:-steps),0,maximumScroll);
            event.setCanceled(true);
        });
    }
    private static String amount(SkillPreview.Damage value){
        String low=cn.yumis.fight.modules.presentation.api.client.NumberText.damage(value.minimum()),high=cn.yumis.fight.modules.presentation.api.client.NumberText.damage(value.maximum());
        return low.equals(high)?low:low+"–"+high;
    }
    public static List<Component> lines(SkillDefinition skill){
        var state=SkillsClient.current();
        var stats=cn.yumis.fight.modules.skills.api.OracleRules.castStats(cn.yumis.fight.modules.skills.api.SunMoonRules.forCast(SkillController.INSTANCE.snapshot(),skill),skill);
        int rank=SkillLearning.effective(stats,state.progress(),skill);
        double castSpeed=Math.max(.01,stats.get(Stat.CAST_SPEED));
        var parameters=cn.yumis.fight.modules.skills.api.WeaponSkills.skill(SkillParameters.forCast(Minecraft.getInstance().player.getUUID(),skill,Math.max(1,rank),stats.parameters),state.progress(),stats);
        if(CareerSkills.contains(skill.id)){var view=CareerClient.INSTANCE.view;int activeField=view.field==null?0:view.field.rank;int fieldRank=skill.id.equals(CareerSkills.BLACHE)?cn.yumis.fight.modules.skills.api.SoulBenderRules.blacheEffectiveFieldRank(activeField,SkillLearning.effective(stats,state.progress(),SkillCatalog.require(CareerSkills.FIELD)),stats):activeField;parameters=CareerRules.resourceSnapshot(parameters,stats,Math.max(1,view.brands),view.blood,fieldRank,false);}
        var preview=SkillPreview.resolve(state.progress(),stats,skill,parameters,state.resources().equipment);
        List<Component> result=new ArrayList<>();
        String displayTitle=cn.yumis.fight.modules.skills.api.ElementalistSkills.contains(skill.id)?cn.yumis.fight.modules.skills.api.ElementalistRules.skillTitle(skill,parameters):WeaponSkills.skillTitle(skill,stats);
        if(displayTitle.equals(skill.title))displayTitle=cn.yumis.fight.modules.skills.api.DemonHunterRules.title(skill,stats);
        add(result,displayTitle,UiTheme.GOLD);
        boolean granted=skill.equipmentGranted||skill.cardGranted;
        add(result,rank>0?"有效等级 "+rank:skill.cardGranted?"缺少对应神话卡牌":skill.equipmentGranted?"缺少有效装备来源":"未学习 · 1级预览",UiTheme.ACCENT);
        if(!granted)add(result,(skill.automaticByJob?"职业自带1级 · ":"")+"SP学习："+state.progress().learned(skill.id)+"级",UiTheme.TEXT);
        add(result,skill.automaticByJob?"转职自动获得1级，继续升级每级消耗"+skill.sp+" SP":skill.cardGranted?"神话卡牌授予，无需SP；等级随角色等级自动成长":skill.equipmentGranted?"装备授予，无需SP；等级随有效来源装备变化":"学习等级 "+skill.unlock+(skill.advanced()?" · "+cn.yumis.fight.modules.character.api.JobCatalog.require(skill.requiredJob).title():"")+" · 每级 "+skill.sp+" SP",UiTheme.MUTED);
        if(skill.maximumLearnedRank<Integer.MAX_VALUE)add(result,"最多可学习"+skill.maximumLearnedRank+"级，装备仍可提高有效等级",UiTheme.MUTED);
        result.add(Component.empty());
        if(skill==SkillCatalog.MASTERY){
            double power=Math.pow(1.12,Math.max(1,rank)-1);
            boolean overload=cn.yumis.fight.modules.dungeons.api.TowerBattleCard.SM_MASTERY_OVERLOAD.held(stats);
            double shortWavePercent=20*power*WeaponSkills.shortSwordWaveDamageFactor(stats);
            double stunChance=WeaponSkills.bluntStunChance(stats)*100;
            add(result,"随当前武器生效（新增效果不随等级成长）：",UiTheme.TEXT);
            if(overload)add(result,"职业神话：攻击速度 +60%，以下全部精通强化生效。",UiTheme.GOLD);
            add(result,"光剑：普攻"+(overload?"4连击":"双击")+"。\n巨剑：90°横扫6.8格，"+(overload?"普攻攻速+100%（取消双倍间隔、蓄力0.4秒）":"间隔翻倍")+"，松键出刀100%～250%。",UiTheme.TEXT);
            add(result,"三段斩：太刀/光剑7段，其余5段；光剑猛龙+1段。",UiTheme.TEXT);
            add(result,"太刀/光剑幻影20刀，持续1.25秒；拔刀追加固定20%光斩。",UiTheme.TEXT);
            add(result,"太刀：10%造成流血；流血目标5%触发 "+number(2*power)+"倍伤害。"+(overload?"\n职业神话：普攻必定施加可无限叠加的重度流血（3秒内受到20%当前生命伤害，对首领仅1/12）。":""),UiTheme.TEXT);
            add(result,"光剑：20%造成感电；攻击感电目标追加光伤，随触发技能的基础伤害校准。",UiTheme.TEXT);
            add(result,"巨剑：技能伤害 +"+number(20*power)+"%。",UiTheme.TEXT);
            add(result,"短剑：追加剑气"+(overload?"扩大至幻影剑舞大小":"斩面随机，拔刀剑气宽高翻倍")+"。\n普攻、三段斩、拔刀斩与剑舞追加 "+number(shortWavePercent)+"%系数剑气。",UiTheme.TEXT);
            add(result,"钝器："+number(stunChance)+"%眩晕 "+number(.5*power)+"秒，精英减半，首领仅 1/12 效果。",UiTheme.TEXT);
            add(result,"巨剑蓄力：按住左键，基础"+(overload?"0.4":"0.8")+"秒线性升至250%；长按保持，梯云纵保留蓄力。",UiTheme.MUTED);
        }else{
            var costs=cn.yumis.fight.modules.skills.api.SkillCosts.resolve(skill,Math.max(1,rank),stats,state.resources().equipment,CareerRules.magic(skill,state.progress()));
            if(!skill.passive())add(result,"消耗："+(cn.yumis.fight.modules.skills.api.DemonHunterSkills.contains(skill.id)?"怒气 "+cn.yumis.fight.modules.skills.api.DemonHunterRules.cost(skill.id):costs.life()>0?"生命 "+number(costs.life()):"MP "+costs.mana())+" · 冷却 "+number(preview.cooldown())+"秒",UiTheme.TEXT);
            if(skill==SkillCatalog.OVERDRIVE){
                add(result,"持续30秒，基础物理攻击 +"+number(100*parameters.get("damage",.13))+"%。",UiTheme.TEXT);
                if(cn.yumis.fight.modules.dungeons.api.TowerBattleCard.SM_OVERDRIVE_LIGHT.held(stats)){
                    add(result,"职业神话：生效期间攻击命中额外附加 20% 光属性伤害。",UiTheme.GOLD);
                }
            }else if(skill.id.equals(CareerSkills.FIELD)){
                if(rank<8)add(result,"当前等级提供阵法增益，8级起获得持续伤害。",UiTheme.TEXT);
                if(rank>=8)add(result,"每秒冰伤："+amount(SkillPreview.dotDamage(state.progress(),stats,skill,parameters.get("iceDot",0),2,state.resources().equipment)),UiTheme.TEXT);
                if(rank>=15)add(result,"每秒暗伤："+amount(SkillPreview.dotDamage(state.progress(),stats,skill,parameters.get("darkDot",0),3,state.resources().equipment)),UiTheme.TEXT);
            }else if(skill.id.equals(cn.yumis.fight.modules.skills.api.MageSkills.HERDER)){
                var nextSummon=SummonerSkills.inherit(stats,SummonKind.HERDER,SkillLearning.effective(stats,state.progress(),SkillCatalog.require(SummonerSkills.MASTERY)));
                boolean cannon=cn.yumis.fight.modules.dungeons.api.TowerBattleCard.SUMMONER_HERDER_CANNON.held(stats);
                boolean squad=cn.yumis.fight.modules.dungeons.api.TowerBattleCard.SUMMONER_HERDER_SQUAD.held(stats);
                if(cannon||squad){
                    double speed=Math.max(.01,1+nextSummon.get(Stat.ATTACK_SPEED)/100);
                    double intervalMs=(cannon?cn.yumis.fight.modules.dungeons.api.TowerBattleCard.SUMMONER_HERDER_CANNON.value("interval")*1000d:1800d)/speed;
                    int shots=Math.max(1,(int)Math.floor((120000-Math.min(400d,intervalMs))/intervalMs)+1);
                    double shotShare=cn.yumis.fight.modules.skills.api.SummonMoves.herderShotShare(parameters,nextSummon,120000,speed,shots);
                    add(result,(cannon?"单发烈焰重炮：":"单发爆弹：")+amount(SkillPreview.damage(state.progress(),nextSummon,skill,parameters.get("stoneDamage",0)*shotShare,false,state.resources().equipment))+(squad?" × 3只":""),UiTheme.GOLD);
                }else{
                    add(result,"下次召唤敲击："+amount(SkillPreview.damage(state.progress(),nextSummon,skill,parameters.get("damage",0),false,state.resources().equipment)),UiTheme.TEXT);
                    add(result,"下次召唤单石："+amount(SkillPreview.damage(state.progress(),nextSummon,skill,parameters.get("stoneDamage",0),false,state.resources().equipment)),UiTheme.TEXT);
                }
                add(result,"120秒参考合计："+amount(preview.total()),UiTheme.ACCENT);
                var summon=MageClient.INSTANCE;if(summon.summonUntil>state.serverNow()&&summon.summonedMelee!=null){add(result,"在场赫尔德快照 · Lv."+summon.summonRank,UiTheme.GOLD);add(result,"敲击："+amount(summon.summonedMelee),UiTheme.TEXT);if(summon.summonRank>=5)add(result,"单石："+amount(summon.summonedStone),UiTheme.TEXT);if(summon.summonRank>=10)add(result,"三连："+amount(summon.summonedTriple),UiTheme.TEXT);}
            }else if(SummonerSkills.contains(skill.id)){
                if(!SummonerSkills.kinds(skill.id).isEmpty()){
                    add(result,(skill.id.equals(SummonerSkills.KASIJAS)?"20秒完整参考：":"120秒完整参考：")+amount(preview.total()),UiTheme.ACCENT);
                    
                }
                for(var entry:MageClient.INSTANCE.army.values())if(entry.get("skill").getAsString().equals(skill.id)&&entry.get("until").getAsLong()>state.serverNow())add(result,SummonKind.valueOf(entry.get("kind").getAsString()).title+" · Lv."+entry.get("rank").getAsInt()+" · 生命 "+number(entry.get("health").getAsDouble())+" / "+number(entry.get("maximum").getAsDouble()),UiTheme.TEXT);
            }else if(!skill.passive()){
                add(result,"面板伤害："+amount(preview.hit())+" × "+preview.slashes()+"段",UiTheme.TEXT);
                if(preview.finisher().maximum()>0){String label=cn.yumis.fight.modules.skills.api.SkillPreviewRules.finisherLabel(skill);add(result,(label==null?"终结伤害":label)+"："+amount(preview.finisher())+(label==null?" × 1段":""),UiTheme.TEXT);}
                if(skill==SkillCatalog.GHOST){
                    double fullCoeff=cn.yumis.fight.modules.skills.api.SoulBenderRules.ghostFullChargeCoefficient(parameters);
                    add(result,"满蓄力单段："+amount(SkillPreview.damage(state.progress(),stats,skill,fullCoeff,false,state.resources().equipment)),UiTheme.TEXT);
                    if(cn.yumis.fight.modules.dungeons.api.TowerBattleCard.SB_INSTANT_SOUL.held(stats))add(result,"职业神话 · 瞬发噬灵：无需蓄力即打出满蓄力与噬灵鬼斩",UiTheme.GOLD);
                    if(cn.yumis.fight.modules.dungeons.api.TowerBattleCard.SB_HELL_SLASH.held(stats)&&parameters.get("soulBonus",0)>0){
                        var hellHit=SkillPreview.damage(state.progress(),stats,skill,cn.yumis.fight.modules.skills.api.SoulBenderRules.hellSlashHitCoefficient(fullCoeff),false,state.resources().equipment);
                        add(result,"职业神话 · 鬼斩·炼狱："+amount(hellHit)+" × "+cn.yumis.fight.modules.skills.api.SoulBenderRules.hellSlashHits()+"段（10格穿透）",UiTheme.GOLD);
                    }
                }
                else if(skill.id.equals(CareerSkills.KALLA)&&cn.yumis.fight.modules.dungeons.api.TowerBattleCard.SB_KALLA_ETERNAL.held(stats)){
                    add(result,"职业神话 · 永驻冥炎：持续 "+number(parameters.get("duration",120))+" 秒（无终结三剑）",UiTheme.GOLD);
                }
                else {add(result,(skill.id.equals(CareerSkills.KALLA)?"终结三剑合计：":"主技能全段命中：")+amount(preview.total()),UiTheme.ACCENT);if(skill.id.equals(CareerSkills.CRESCENT)&&!cn.yumis.fight.modules.dungeons.api.TowerBattleCard.ASURA_CRESCENT_MAX.held(stats))add(result,"满蓄力总计（"+(int)Math.max(1,parameters.get("slashes",3))+"段）："+amount(preview.total().times((int)Math.max(1,parameters.get("slashes",3)))),UiTheme.TEXT);}
                if(skill.id.equals(CareerSkills.MOONLIGHT)&&cn.yumis.fight.modules.dungeons.api.TowerBattleCard.SB_MOON_WAVE.held(stats)){
                    var waveDmg=SkillPreview.damage(state.progress(),stats,skill,cn.yumis.fight.modules.skills.api.SoulBenderRules.moonlightWaveCoefficient(parameters,stats,1),false,state.resources().equipment);
                    add(result,"职业神话 · 圆月剑气（首段起，10格穿透）："+amount(waveDmg)+"+",UiTheme.GOLD);
                }
                if(skill.id.equals(CareerSkills.PHANTOM)&&cn.yumis.fight.modules.dungeons.api.TowerBattleCard.SB_PHANTOM_RIFT.held(stats)){
                    var riftDmg=SkillPreview.damage(state.progress(),stats,skill,cn.yumis.fight.modules.skills.api.SoulBenderRules.phantomRiftBurstCoefficient(parameters),false,state.resources().equipment);
                    add(result,"职业神话 · 次元断空爆破（半径3.5格）："+amount(riftDmg),UiTheme.GOLD);
                }
                if(skill.id.equals(CareerSkills.KALLA))add(result,"冥炎弹爆炸（半径"+number(parameters.get("blastRadius",2))+"格）："+amount(SkillPreview.damage(state.progress(),stats,skill,parameters.get("boltDamage",0),false,state.resources().equipment)),UiTheme.TEXT);
                if(skill==SkillCatalog.RISING&&cn.yumis.fight.modules.dungeons.api.TowerBattleCard.SM_RISING_DRAGON_SLAY.held(stats)){
                    var rush=SkillPreview.damage(state.progress(),stats,skill,WeaponSkills.risingDragonSlayRushCoeff(parameters.get("damage",0)),false,state.resources().equipment);
                    var upper=SkillPreview.damage(state.progress(),stats,skill,WeaponSkills.risingDragonSlayUpperCoeff(parameters.get("damage",0)),false,state.resources().equipment);
                    var slam=SkillPreview.damage(state.progress(),stats,skill,WeaponSkills.risingDragonSlaySlamCoeff(parameters.get("finisher",0)),false,state.resources().equipment);
                    add(result,"职业神话 · 斩龙三段："+amount(rush)+" × 3段 · 上挑 "+amount(upper)+" · 斩龙重击 "+amount(slam),UiTheme.GOLD);
                }
                if(skill==SkillCatalog.DRAW){
                    if(cn.yumis.fight.modules.dungeons.api.TowerBattleCard.SM_DRAW_BARRAGE.held(stats)){
                        int count=WeaponSkills.drawBarrageCount(stats);
                        var waveDmg=SkillPreview.damage(state.progress(),stats,skill,parameters.get("damage",0)*WeaponSkills.drawBarrageDamageRatio(),false,state.resources().equipment);
                        add(result,"职业神话 · 剑气连闪："+amount(waveDmg)+" × "+count+"道（不再造成周身弧形伤害）",UiTheme.GOLD);
                    }else{
                        var drawLight=cn.yumis.fight.modules.skills.api.SkillPreviewRules.supplement(skill,state.progress(),stats,parameters,state.resources().equipment);
                        if(drawLight.maximum()>0)add(result,"光剑／太刀二段光斩："+amount(drawLight)+" × 1段",UiTheme.TEXT);
                        if(cn.yumis.fight.modules.dungeons.api.TowerBattleCard.SM_DRAW_ARC_WAVE.held(stats)){
                            var arcDmg=SkillPreview.damage(state.progress(),stats,skill,parameters.get("damage",0)*WeaponSkills.drawArcWaveDamageRatio(),false,state.resources().equipment);
                            add(result,"职业神话 · 弧形扩大剑气（20格）："+amount(arcDmg)+" × 1段",UiTheme.GOLD);
                        }
                    }
                }
                if(skill==SkillCatalog.DRAGON&&cn.yumis.fight.modules.dungeons.api.TowerBattleCard.SM_DRAGON_WHIRLWIND.held(stats)){
                    var whirl=SkillPreview.damage(state.progress(),stats,skill,parameters.get("damage",0)*WeaponSkills.dragonWhirlwindDamageRatio(),false,state.resources().equipment);
                    add(result,"职业神话 · 追踪旋风单次命中："+amount(whirl),UiTheme.GOLD);
                }
                if(cn.yumis.fight.modules.skills.api.OracleSkills.ACTIONS.contains(skill.id)){
                    if("LIGHTSABER".equals(stats.weaponType)&&cn.yumis.fight.modules.dungeons.api.TowerBattleCard.ORACLE_LIGHTSABER_ECHO.held(stats)){
                        var echo=SkillPreview.damage(state.progress(),stats,skill,parameters.get("damage",0)*cn.yumis.fight.modules.dungeons.api.TowerBattleCard.ORACLE_LIGHTSABER_ECHO.ratio("damage"),false,state.resources().equipment);
                        add(result,"职业神话 · 光剑额外斩击："+amount(echo)+" × 1段",UiTheme.GOLD);
                    }
                    if("BLUNT".equals(stats.weaponType)&&cn.yumis.fight.modules.dungeons.api.TowerBattleCard.ORACLE_BLUNT_QUAKE.held(stats)){
                        var quake=SkillPreview.damage(state.progress(),stats,skill,parameters.get("damage",0)*cn.yumis.fight.modules.dungeons.api.TowerBattleCard.ORACLE_BLUNT_QUAKE.ratio("damage"),true,state.resources().equipment);
                        add(result,"职业神话 · 钝器震荡波（半径"+number(cn.yumis.fight.modules.dungeons.api.TowerBattleCard.ORACLE_BLUNT_QUAKE.value("radius"))+"格）："+amount(quake)+" · 眩晕3秒（首领0.25秒）",UiTheme.GOLD);
                    }
                }
                if(skill.id.equals(CareerSkills.HAND)&&cn.yumis.fight.modules.dungeons.api.TowerBattleCard.BERSERKER_HAND_CLAW.held(stats)){
                    add(result,"职业神话 · 血魔巨爪：命中生命低于50%目标伤害额外+50%并返还50%冷却",UiTheme.GOLD);
                }
                if(skill.id.equals(CareerSkills.BLADE)&&cn.yumis.fight.modules.dungeons.api.TowerBattleCard.BERSERKER_BLADE_ASSAULT.held(stats)){
                    int blasts=Math.max(1,cn.yumis.fight.modules.dungeons.api.TowerBattleCard.BERSERKER_BLADE_ASSAULT.count("blasts"));
                    double ratio=cn.yumis.fight.modules.dungeons.api.TowerBattleCard.BERSERKER_BLADE_ASSAULT.ratio("ratio");
                    var burst=SkillPreview.damage(state.progress(),stats,skill,parameters.get("finisher",0)*ratio,false,state.resources().equipment);
                    add(result,"职业神话 · 血刃突袭：连续血气爆炸 "+amount(burst)+" × "+blasts+"次",UiTheme.GOLD);
                }
                if(preview.shortSwordWave().maximum()>0)add(result,"每次短剑追加剑气："+amount(preview.shortSwordWave()),UiTheme.TEXT);
                
            }
            if(!skill.passive())result.add(Component.empty());
            add(result,cn.yumis.fight.modules.skills.client.SkillDescriptions.describe(skill,new cn.yumis.fight.modules.skills.client.SkillDescriptions.Context(castSpeed,parameters,preview)),UiTheme.MUTED);
            if(skill.id.equals(CareerSkills.ORB)||skill.id.equals(CareerSkills.AGNI))add(result,"当前刻印 "+CareerClient.INSTANCE.view.brands+" / "+CareerClient.INSTANCE.brandCap()+"；零印时按1印预览，无法施放。",UiTheme.ACCENT);
            var content=CareerSkills.content(skill.id);double effectiveHpCost=content==null?0:parameters.get("hpCost",content.hpCost());if(effectiveHpCost>0)add(result,"生命消耗：最大生命的"+number(effectiveHpCost*100)+"%；不足支付时不施放。",UiTheme.TEXT);
        }
        add(result,skill.passive()&&!skill.bindingProxy?"被动技能，无需绑定。":rank<=0?(skill.cardGranted?"缺少对应神话卡牌，保留原有绑定。":skill.equipmentGranted?"缺少有效装备来源，保留原有绑定。":"学习后点击图标选择快捷槽。"):"点击图标选择快捷槽。",UiTheme.ACCENT);
        return result;
    }
    private static void add(List<Component> lines,String text,int color){
        for(String line:text.split("\\R"))lines.add(UiFonts.decorate(Component.literal(line).withColor(UiTheme.tooltipColor(color))));
    }
    public static void render(GuiGraphicsExtractor graphics,SkillDefinition skill,int mouseX,int mouseY){
        var font=UiFonts.font();
        int width=Math.max(1,Math.min(360,graphics.guiWidth()-36));
        var wrapped=lines(skill).stream().flatMap(line->font.splitIgnoringLanguage(line,width).stream()).toList();
        long now=net.minecraft.util.Util.getMillis();Object screen=Minecraft.getInstance().gui.screen();
        if(screen!=hoveredScreen||!skill.id.equals(hoveredSkill)||now-hoveredAt>250)scroll=0;
        hoveredScreen=screen;hoveredSkill=skill.id;hoveredAt=now;hoveredX=mouseX;hoveredY=mouseY;
        int step=font.lineHeight+2,rows=Math.max(1,(graphics.guiHeight()-step-36)/step);
        maximumScroll=Math.max(0,wrapped.size()-1-rows);scroll=Math.clamp(scroll,0,maximumScroll);
        if(maximumScroll==0){graphics.setComponentTooltipForNextFrame(font,wrapped,mouseX,mouseY,ItemStack.EMPTY);return;}
        var title=net.minecraft.locale.Language.getInstance().getVisualOrder(wrapped.getFirst());
        var body=wrapped.stream().skip(1).map(line->net.minecraft.locale.Language.getInstance().getVisualOrder(line)).toList();
        int actualWidth=Math.max(body.stream().mapToInt(font::width).max().orElse(0),font.width(title))+10;
        Content content=new Content(title,body,actualWidth,rows,step,scroll);
        graphics.setTooltipForNextFrame(font,List.of(),Optional.of(content),ItemStack.EMPTY,mouseX,mouseY);
    }
}
