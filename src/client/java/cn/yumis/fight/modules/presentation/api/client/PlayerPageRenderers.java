package cn.yumis.fight.modules.presentation.api.client;
import java.util.*;
import java.util.function.Consumer;
/** Client page registry. Adding a module page does not modify the shell's dispatch code. */
public final class PlayerPageRenderers {
    private static final Map<String,Consumer<PlayerPageSurface>> RENDERERS=new LinkedHashMap<>();
    private static final Map<String,java.util.function.Supplier<ClientPagePanel>> PANELS=new LinkedHashMap<>();
    private PlayerPageRenderers(){}
    public static void register(String id,Consumer<PlayerPageSurface> renderer){if(RENDERERS.putIfAbsent(id,Objects.requireNonNull(renderer))!=null)throw new IllegalArgumentException("Duplicate page renderer: "+id);}
    public static void registerPanel(String id,java.util.function.Supplier<ClientPagePanel> factory){if(PANELS.putIfAbsent(id,Objects.requireNonNull(factory))!=null)throw new IllegalArgumentException("Duplicate page panel: "+id);}
    public static ClientPagePanel panel(String id){var factory=PANELS.get(id);return factory==null?null:factory.get();}
    public static void render(String id,PlayerPageSurface surface){var renderer=RENDERERS.get(id);if(renderer==null){surface.line("页面内容尚未加载");return;}renderer.accept(surface);}
}
