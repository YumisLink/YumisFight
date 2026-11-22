package cn.yumis.fight.bootstrap;
import cn.yumis.fight.modules.presentation.api.client.PlayerPageRenderers;
import cn.yumis.fight.modules.character.client.CharacterPage;
import cn.yumis.fight.modules.presentation.api.client.UiFonts;
import cn.yumis.fight.modules.presentation.api.client.UiTheme;

import cn.yumis.fight.modules.presentation.api.client.*;
import cn.yumis.fight.modules.character.client.*;
import cn.yumis.fight.modules.character.api.JobCatalog;
import cn.yumis.fight.modules.attributes.api.Stat;
import cn.yumis.fight.modules.attributes.api.client.AttributeLabels;
import cn.yumis.fight.modules.affixes.api.AffixText;
import cn.yumis.fight.modules.combat.api.CombatRules;
import cn.yumis.fight.modules.skills.client.SkillsPage;
import cn.yumis.fight.modules.party.client.PartyPage;
import cn.yumis.fight.modules.dungeons.client.DungeonPage;
import cn.yumis.fight.modules.professions.client.ProfessionPage;
import cn.yumis.fight.modules.economy.client.ShopPage;
import cn.yumis.fight.modules.presentation.client.SettingsPage;
import cn.yumis.fight.modules.npc.api.PlayGuide;
import cn.yumis.fight.modules.professions.api.ProfessionCatalog;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import java.util.*;

/** Client-only assembly. Each registered page receives only its data and commands. */
public final class PlayerPageAssembly {
    private PlayerPageAssembly(){}
    public static void register(){
        var state=ClientSession.INSTANCE;
        PlayerPageRenderers.register("tutorial",cn.yumis.fight.modules.quests.client.QuestsPage::render);
        PlayerPageRenderers.register("guide",page->cn.yumis.fight.modules.npc.client.GuidePage.render(page,guideEntries(),cn.yumis.fight.modules.presentation.api.client.MenuInput::open));
        PlayerPageRenderers.registerPanel("sale",cn.yumis.fight.modules.economy.client.SalePanel::new);
        PlayerPageRenderers.register("affix_compendium",cn.yumis.fight.modules.equipment.client.AffixCompendiumPage::render);
        PlayerPageRenderers.registerPanel("damage_analysis",cn.yumis.fight.modules.combat.client.DamageAnalysisPanel::new);
        PlayerPageRenderers.register("character",page->{cn.yumis.fight.modules.character.api.client.CareerProfilesClient.render(page,state.progress);CharacterPage.render(page,characterView(),stat->points("allocate","stat",stat),()->state.progress("resetAttributes","unused",0),enabled->state.progress("sync","enabled",enabled));});
        PlayerPageRenderers.register("skills",page->{cn.yumis.fight.modules.character.api.client.CareerProfilesClient.render(page,state.progress);SkillsPage.render(page,state.progress,state.stats,skill->points("learn","skill",skill),skill->points("unlearn","skill",skill),()->state.progress("resetSkills","unused",0));});
        PlayerPageRenderers.register("talents",page->{cn.yumis.fight.modules.character.api.client.CareerProfilesClient.render(page,state.progress);cn.yumis.fight.modules.talents.client.TalentPage.render(page,state.progress,(kind,talent)->state.progress(kind,"talent",talent),state::message);});
        PlayerPageRenderers.register("professions",page->ProfessionPage.render(page,state.professions,state.gold.balance,()->state.send("professions"),UiTheme.GOLD,UiTheme.MUTED));
        PlayerPageRenderers.register("party",page->PartyPage.render(page,state.party,state.own.target,id->{var connection=Minecraft.getInstance().getConnection();var info=connection==null?null:connection.getPlayerInfo(id);return info==null?id+" [离线]":info.getProfile().name()+" [在线]";},(kind,name)->{var request=state.operation(kind);request.addProperty(kind.equals("kick")||kind.equals("transfer")?"target":"name",name);state.send("party",request);}));
        PlayerPageRenderers.register("dungeons",page->DungeonPage.render(page,state.dungeon,state.progress.level,state.progress.dungeonSync,strandedInDungeon(),join->{
            state.upload();var request=state.operation("join");
            request.addProperty("dungeon",join.dungeon());request.addProperty("difficulty",join.difficulty().name());request.addProperty("sync",join.sync());
            if(join.abyssChallenge())request.addProperty("abyssChallenge",true);
            state.send("dungeon",request);
        },()->state.send("dungeon",state.operation("leave")),state::message));
        PlayerPageRenderers.register("settings",SettingsPage::render);
        PlayerPageRenderers.register("other_games",page->cn.yumis.fight.modules.arkguard.client.ArkGuardClient.render(page,!state.own.dead&&state.dungeon==null&&state.own.instance.isEmpty()));
        PlayerPageRenderers.register("shop",page->{page.line("无色小晶块 "+(state.profile.has("colorlessCrystals")?state.profile.get("colorlessCrystals").getAsLong():0L),UiTheme.TEXT);ShopPage.render(page,page.screen(),state.gold.balance,state.profile.has("points")?state.profile.get("points").getAsLong():0L,state.session,state.profile.has("contractUntil")?state.profile.get("contractUntil").getAsLong():0L,()->state.operation("purchase"),request->state.send("purchase",request),UiTheme.GOLD,UiTheme.MUTED);});
    }
    private static void points(String kind,String key,String value){var state=ClientSession.INSTANCE;state.upload();var request=state.operation(kind);request.addProperty(key,value);request.addProperty("count",Minecraft.getInstance().hasShiftDown()?5:1);state.send("progress",request);}
    /** 滞留判定：身处地下城维度但无进行中的副本状态；方舟战场共用该维度，且另有自己的离场入口，不算滞留。 */
    private static boolean strandedInDungeon(){
        var player=Minecraft.getInstance().player;
        return player!=null&&cn.yumis.fight.modules.dungeons.client.DungeonActions.inDungeonDimension()
            &&!cn.yumis.fight.modules.arkguard.api.ArkWar.contains(player.level(),player.blockPosition());
    }
    private static CharacterPage.Value value(String name,String value){return new CharacterPage.Value(name,value,(g,x,y)->g.setComponentTooltipForNextFrame(UiFonts.font(),cn.yumis.fight.modules.skills.client.CombatAttributeTooltip.lines(name),x,y));}
    private static CharacterPage.View characterView(){
        var state=ClientSession.INSTANCE;var stats=cn.yumis.fight.modules.skills.api.client.SkillController.INSTANCE.snapshot();List<CharacterPage.Allocation> allocated=new ArrayList<>();List<CharacterPage.Value> values=new ArrayList<>();
        var resource=cn.yumis.fight.modules.skills.api.client.PrimaryResource.current();
        for(Stat stat:List.of(Stat.STRENGTH,Stat.INTELLIGENCE,Stat.SPIRIT))allocated.add(new CharacterPage.Allocation(stat.name().toLowerCase(Locale.ROOT),value(AttributeLabels.statName(stat),AffixText.integer(stats.get(stat)))));
        for(Stat stat:Stat.values()){
            if(resource!=null&&stat==Stat.MP){
                values.add(new CharacterPage.Value(resource.title(),AffixText.integer(resource.current())+" / "+AffixText.integer(resource.maximum()),(g,x,y)->g.setComponentTooltipForNextFrame(UiFonts.font(),resource.help().stream().map(net.minecraft.network.chat.Component::literal).map(UiFonts::decorate).toList(),x,y)));
            }else if(stat!=Stat.STRENGTH&&stat!=Stat.INTELLIGENCE&&stat!=Stat.SPIRIT&&(resource==null||stat!=Stat.MANA_REGENERATION))values.add(value(AttributeLabels.statName(stat),AffixText.integer(stats.get(stat)*(stat==Stat.MOVEMENT_SPEED||stat==Stat.CAST_SPEED||stat==Stat.MANA_REGENERATION||stat==Stat.COOLDOWN_RECOVERY||stat==Stat.EXPERIENCE_GAIN?100:1))+(stat==Stat.ATTACK_SPEED||stat==Stat.MOVEMENT_SPEED||stat==Stat.CAST_SPEED||stat==Stat.MANA_REGENERATION||stat==Stat.COOLDOWN_RECOVERY||stat==Stat.EXPERIENCE_GAIN?"%":"")));
        }
        values.add(value("吸血率",AffixText.integer(cn.yumis.fight.modules.combat.api.EquipmentDamage.lifeSteal(stats.get(Stat.LIFE_STEAL))*100)+"%"));
        values.add(value("闪避率",AffixText.integer(CombatRules.evasionChance(stats.get(Stat.EVASION))*100)+"%"));values.add(value("暴击率",AffixText.integer(stats.modifiers.getOrDefault("crit",0d)*100)+"%"));values.add(value("伤害提升",AffixText.integer(stats.modifiers.getOrDefault("final",0d)*100)+"%"));
        values.add(value("物理面板攻击",AffixText.integer(stats.panel(false))));values.add(value("魔法面板攻击",AffixText.integer(stats.panel(true))));
        return new CharacterPage.View(state.progress,ClientSession.now()<state.resources.combatUntil,allocated,values);
    }
    /** 看板条目：里程碑与副本由 PlayGuide 从会话进度派生，副职业摘要来自会话快照，全部只读。 */
    private static List<PlayGuide.GuideEntry> guideEntries(){
        var state=ClientSession.INSTANCE;
        var tutorial=cn.yumis.fight.modules.quests.client.TutorialClient.state();
        boolean tutorialDone=tutorial.started&&cn.yumis.fight.modules.quests.api.NpcTutorial.current(tutorial)==null;
        boolean abyss=false,treasure=false;
        var player=Minecraft.getInstance().player;
        if(player!=null)for(int slot=0;slot<36;slot++){
            var stack=player.getInventory().getItem(slot);
            if(cn.yumis.fight.modules.dungeons.api.AbyssParty.invitation(stack))abyss=true;
            if(stack.is(cn.yumis.fight.modules.dungeons.api.DungeonContentTypes.TREASURE_MAP.get()))treasure=true;
        }
        var entries=new ArrayList<>(PlayGuide.entries(new PlayGuide.Facts(state.progress.level,state.progress.jobId(),tutorialDone,state.progress.awakened,abyss,treasure)));
        entries.addAll(professionGuideEntries(state.professions));
        return entries;
    }
    private static List<PlayGuide.GuideEntry> professionGuideEntries(JsonObject snapshot){
        if(snapshot==null||!snapshot.has("professions"))return List.of();
        var all=snapshot.getAsJsonObject("professions");
        List<String> started=new ArrayList<>();
        int open=0;
        for(var definition:ProfessionCatalog.active()){
            var progress=all.has(definition.id())?all.getAsJsonObject(definition.id()):null;
            if(progress!=null&&progress.has("level")&&progress.get("level").getAsInt()>0)started.add(definition.name());
            else open++;
        }
        if(started.isEmpty())return open==0?List.of():List.of(new PlayGuide.GuideEntry("副职业","十条生活线：采集、钓鱼、炼金等，产出材料与实用药剂","professions"));
        var result=new ArrayList<>(List.of(new PlayGuide.GuideEntry("副职业进行中","已开启："+String.join("、",started),"professions")));
        if(open>0)result.add(new PlayGuide.GuideEntry("可开启更多副职业","还有生活线没有开始","professions"));
        return result;
    }
}
