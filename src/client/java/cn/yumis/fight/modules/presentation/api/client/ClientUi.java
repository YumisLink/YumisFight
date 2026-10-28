package cn.yumis.fight.modules.presentation.api.client;
import com.google.gson.JsonObject;
/** UI command boundary; business modules never acquire the complete connection runtime. */
public interface ClientUi {
    final class Binding {private static ClientUi current;private Binding(){}}
    static void install(ClientUi ui){Binding.current=java.util.Objects.requireNonNull(ui);}
    static ClientUi current(){return java.util.Objects.requireNonNull(Binding.current,"Client UI is not assembled");}
    static long now(){return net.minecraft.util.Util.getMillis();}
    boolean ready();
    String session();
    long serverNow();
    void send(String action,JsonObject value);
    void send(String action);
    JsonObject operation(String kind);
    void upload();
    void message(String text);
}
