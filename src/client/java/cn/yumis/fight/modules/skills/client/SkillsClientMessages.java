package cn.yumis.fight.modules.skills.client;
import cn.yumis.fight.modules.skills.api.client.CloudMovement;
import cn.yumis.fight.modules.skills.api.client.SkillController;
import cn.yumis.fight.modules.skills.client.Effects;
import com.google.gson.*;
import java.util.*;
public final class SkillsClientMessages {
    private SkillsClientMessages(){}
    public static void register(cn.yumis.fight.architecture.CommandRouter<Void,JsonObject> router){
        CoronaBlur.register();
        DemonHunterClient.registerResource();
        router.register("skills","skillReduced",(context,j)->CareerClient.INSTANCE.reduced(j));
        router.register("skills","summonAction",(context,j)->MageClient.INSTANCE.action(j));
        router.register("skills","herderState",(context,j)->MageClient.INSTANCE.state(j));
        router.register("skills","herderIncoming",(context,j)->MageClient.INSTANCE.incoming(j));
        router.register("skills","careerState",(context,j)->CareerClient.INSTANCE.receive(j));
        router.register("skills","incoming",(context,j)->{SkillController.INSTANCE.incoming(j);});
        router.register("skills","projectile",(context,j)->{SkillController.INSTANCE.projectile(j);});
        router.register("skills","nativeLaunch",(context,j)->SkillController.INSTANCE.nativeLaunch(j));
        router.register("skills","visual",(context,j)->{Effects.receive(j);});
    }
}
