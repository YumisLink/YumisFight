package cn.yumis.fight.modules.equipment.api.client;
import cn.yumis.fight.modules.character.api.Progress;
import cn.yumis.fight.modules.presentation.api.client.ClientUi;
public interface EquipmentClient extends ClientUi {
    final class Binding {private static EquipmentClient current;private Binding(){}}
    static void install(EquipmentClient view){Binding.current=java.util.Objects.requireNonNull(view);}
    static EquipmentClient current(){return java.util.Objects.requireNonNull(Binding.current,"Equipment client is not assembled");}
    Progress progress();
}
