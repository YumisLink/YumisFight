package cn.yumis.fight.bootstrap;
import cn.yumis.fight.modules.presentation.api.client.PlayerPageRenderers;
import cn.yumis.fight.modules.character.client.JobPage;
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
import net.minecraft.client.Minecraft;
import java.util.*;

/** Client-only assembly. Each registered page receives only its data and commands. */
public final class PlayerPageAssembly {
    private PlayerPageAssembly(){}
    public static void register(){
        var state=ClientSession.INSTANCE;
        PlayerPageRenderers.register("character",page->CharacterPage.render(page,characterView(),stat->points("allocate","stat",stat),()->state.progress("resetAttributes","unused",0),enabled->state.progress("sync","enabled",enabled)));
        PlayerPageRenderers.register("skills",page->SkillsPage.render(page,state.progress,state.stats,skill->points("learn","skill",skill),()->state.progress("resetSkills","unused",0)));
        PlayerPageRenderers.register("job",page->JobPage.render(page,state.progress,(kind,job)->{var request=state.operation(kind);request.addProperty("job",job);state.send("progress",request);},job->Minecraft.getInstance().gui.pushScreenLayer(new cn.yumis.fight.modules.skills.client.CareerPreviewScreen(job)),UiTheme.GOLD));
        PlayerPageRenderers.register("professions",page->ProfessionPage.render(page,state.professions,state.gold.balance,()->state.send("professions"),UiTheme.GOLD,UiTheme.MUTED));
        PlayerPageRenderers.register("party",page->PartyPage.render(page,state.party,id->{var connection=Minecraft.getInstance().getConnection();var info=connection==null?null:connection.getPlayerInfo(id);return info==null?id+" [离线]":info.getProfile().name()+" [在线]";},(kind,name)->{var request=state.operation(kind);request.addProperty("name",name);state.send("party",request);}));
        PlayerPageRenderers.register("dungeons",page->DungeonPage.render(page,state.dungeon,join->{state.upload();var request=state.operation("join");request.addProperty("dungeon",join.dungeon());request.addProperty("difficulty",join.difficulty().name());request.addProperty("sync",join.sync());state.send("dungeon",request);},()->state.send("dungeon",state.operation("leave")),state::message));
        PlayerPageRenderers.register("settings",page->SettingsPage.render(page,state::message));
        PlayerPageRenderers.register("shop",page->ShopPage.render(page,page.screen(),state.gold.balance,state.session,state.profile.has("contractUntil")?state.profile.get("contractUntil").getAsLong():0L,()->state.operation("purchase"),request->state.send("purchase",request),UiTheme.GOLD,UiTheme.MUTED));
    }
    private static void points(String kind,String key,String value){var state=ClientSession.INSTANCE;state.upload();var request=state.operation(kind);request.addProperty(key,value);request.addProperty("count",Minecraft.getInstance().hasShiftDown()?5:1);state.send("progress",request);}
    private static CharacterPage.Value value(String name,String value){return new CharacterPage.Value(name,value,(g,x,y)->g.setComponentTooltipForNextFrame(UiFonts.font(),cn.yumis.fight.modules.skills.client.CombatAttributeTooltip.lines(name),x,y));}
    private static CharacterPage.View characterView(){
        var state=ClientSession.INSTANCE;var stats=cn.yumis.fight.modules.skills.api.client.SkillController.INSTANCE.snapshot();List<CharacterPage.Allocation> allocated=new ArrayList<>();List<CharacterPage.Value> values=new ArrayList<>();
        for(Stat stat:List.of(Stat.STRENGTH,Stat.INTELLIGENCE,Stat.SPIRIT))allocated.add(new CharacterPage.Allocation(stat.name().toLowerCase(Locale.ROOT),value(AttributeLabels.statName(stat),AffixText.integer(stats.get(stat)))));
        for(Stat stat:Stat.values())if(stat!=Stat.STRENGTH&&stat!=Stat.INTELLIGENCE&&stat!=Stat.SPIRIT)values.add(value(AttributeLabels.statName(stat),AffixText.integer(stats.get(stat)*(stat==Stat.MOVEMENT_SPEED||stat==Stat.CAST_SPEED||stat==Stat.MANA_REGENERATION||stat==Stat.COOLDOWN_RECOVERY||stat==Stat.EXPERIENCE_GAIN?100:1))+(stat==Stat.ATTACK_SPEED||stat==Stat.MOVEMENT_SPEED||stat==Stat.CAST_SPEED||stat==Stat.MANA_REGENERATION||stat==Stat.COOLDOWN_RECOVERY||stat==Stat.EXPERIENCE_GAIN?"%":"")));
        values.add(value("吸血率",AffixText.integer(cn.yumis.fight.modules.combat.api.EquipmentDamage.lifeSteal(stats.get(Stat.LIFE_STEAL))*100)+"%"));
        values.add(value("闪避率",AffixText.integer(CombatRules.evasionChance(stats.get(Stat.EVASION))*100)+"%"));values.add(value("暴击率",AffixText.integer(stats.modifiers.getOrDefault("crit",0d)*100)+"%"));values.add(value("伤害提升",AffixText.integer(stats.modifiers.getOrDefault("final",0d)*100)+"%"));
        values.add(value("物理面板攻击",AffixText.integer(stats.panel(false))));values.add(value("魔法面板攻击",AffixText.integer(stats.panel(true))));
        return new CharacterPage.View(state.progress,ClientSession.now()<state.resources.combatUntil,Math.round((1-JobCatalog.style(state.progress.jobId()).damageTaken())*100)+"%",allocated,values);
    }
}
