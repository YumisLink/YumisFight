package cn.yumis.fight.modules.skills.client;
import cn.yumis.fight.modules.skills.api.client.CloudMovement;
import cn.yumis.fight.modules.skills.api.client.SkillController;
import cn.yumis.fight.modules.skills.client.Effects;
import com.google.gson.*;
import java.util.*;
public final class SkillsClientMessages {
    private SkillsClientMessages(){}
    public static void register(cn.yumis.fight.architecture.CommandRouter<Void,JsonObject> router){
        router.register("skills","fireImpact",(context,j)->CareerClient.INSTANCE.fireImpact(j));
        router.register("skills","careerState",(context,j)->CareerClient.INSTANCE.receive(j));
        router.register("skills","careerStarted",(context,j)->CareerClient.INSTANCE.started(j));
        router.register("skills","careerRejected",(context,j)->CareerClient.INSTANCE.rejected(j));
        router.register("skills","basicStarted",(context,j)->SkillController.INSTANCE.basicStarted(j));
        router.register("skills","basicRejected",(context,j)->SkillController.INSTANCE.basicRejected(j));
        router.register("skills","incoming",(context,j)->{SkillController.INSTANCE.incoming(j);});
        router.register("skills","movement",(context,j)->{CloudMovement.receive(j);});
        router.register("skills","projectile",(context,j)->{SkillController.INSTANCE.projectile(j);});
        router.register("skills","nativeLaunch",(context,j)->SkillController.INSTANCE.nativeLaunch(j));
        router.register("skills","bossAction",(context,j)->{SkillController.INSTANCE.boss(j);});
        router.register("skills","cancelBossAction",(context,j)->{SkillController.INSTANCE.cancelBoss(j.get("id").getAsString());});
        router.register("skills","visual",(context,j)->{Effects.receive(j);});
    }
}
