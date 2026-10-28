package cn.yumis.fight.modules.character.client;
import cn.yumis.fight.modules.character.api.Progress;
import cn.yumis.fight.modules.character.api.JobCatalog;

import cn.yumis.fight.modules.character.api.*;
import cn.yumis.fight.modules.presentation.api.client.PlayerPageSurface;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

/** Job UI follows registered qualifications and quest definitions. */
public final class JobPage {
    private JobPage(){}
    public static void render(PlayerPageSurface page,Progress player,BiConsumer<String,String> command,java.util.function.Consumer<String> preview,int titleColor){
        page.line("当前职业："+JobCatalog.require(player.jobId()).title(),titleColor);
        String awakening=JobCatalog.style(player.jobId()).awakening();
        if(!awakening.isBlank()&&player.skills.entrySet().stream().anyMatch(e->e.getValue()>0&&e.getKey().matches(".*:(storm|blache|wave_eye|blood_sword)")))page.line("觉醒称号："+awakening,titleColor);
        page.line("职业防护：怪物直接伤害减免"+Math.round((1-JobCatalog.style(player.jobId()).damageTaken())*100)+"%。");
        if(!player.hasJob(JobCatalog.BASE)){
            page.line("副本外、脱战3秒且所有技能冷却结束后，可免费重转。");
            page.line("保留基础技能，返还旧职业SP；生命和MP保持当前值。");page.gap();
            for(var destination:JobCatalog.all())if(destination.prerequisite().equals(JobCatalog.BASE)){page.rowButton("查看"+destination.title()+"技能",()->preview.accept(destination.id()));if(!player.hasJob(destination.id()))page.rowButton("重转为"+destination.title(),()->command.accept("reclass",destination.id()));}
            return;
        }
        var common=player.quest(JobCatalog.SWORDMASTER);
        page.line("20级共用转职任务：有效等级≥20敌人 "+common.kills+" / 50，其中精英 "+common.elites+" / 3。");
        page.line("提交铁锭32、青金石16，选择四个分支之一。");
        page.rowButton(common.accepted?"转职任务进行中":"接取转职任务",()->command.accept("quest",JobCatalog.SWORDMASTER));
        for(var job:JobCatalog.all()){
            if(job.prerequisite().isEmpty()||!player.hasJob(job.prerequisite()))continue;
            var quest=player.quest(job.id());
            page.line(JobCatalog.require(job.prerequisite()).title()+" → "+job.title(),titleColor);
            page.line("达到"+job.minimumLevel()+"级后接取任务，以实际终结击杀计数。");
            page.line("有效等级≥"+job.minimumLevel()+"敌人："+quest.kills+" / "+job.kills());
            page.line("其中精英："+quest.elites+" / "+job.elites());
            String materials=job.materials().entrySet().stream().map(e->new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(e.getKey()))).getHoverName().getString()+e.getValue()).collect(Collectors.joining("、"));
            page.line("提交"+materials+"；转职保留已有进度。");page.gap();
            page.rowButton("提交材料并转职为"+job.title(),()->command.accept("job",job.id()));
            page.rowButton("查看"+job.title()+"技能",()->preview.accept(job.id()));
        }
    }
}
