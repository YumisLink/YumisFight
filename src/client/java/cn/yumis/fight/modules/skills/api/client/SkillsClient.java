package cn.yumis.fight.modules.skills.api.client;
import cn.yumis.fight.modules.presentation.api.client.ClientUi;
import cn.yumis.fight.modules.character.api.Progress;
import cn.yumis.fight.modules.attributes.api.Stats;
import cn.yumis.fight.modules.combat.api.Life;
import cn.yumis.fight.modules.dungeons.api.Target;
import com.google.gson.JsonObject;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import java.util.*;
/** Inputs and prediction operations needed by skill execution, without access to inventory, economy or party state. */
public interface SkillsClient extends ClientUi {
    final class Binding {private static SkillsClient current;private Binding(){}}
    static void install(SkillsClient view){Binding.current=java.util.Objects.requireNonNull(view);}
    static SkillsClient current(){return java.util.Objects.requireNonNull(Binding.current,"Skills client is not assembled");}
    Progress progress();
    Stats stats();
    Life own();
    cn.yumis.fight.modules.combat.api.Runtime resources();
    boolean encounterSync();
    Map<UUID,Target> targets();
    Target context(LivingEntity entity);
    double predicted(Life life);
    Stats projectileStats(ItemStack weapon);
    void report(Life life,double fraction,boolean heal,double maximum,int color,boolean critical,JsonObject extras);
    JsonObject ailment(String target,String kind);
}
