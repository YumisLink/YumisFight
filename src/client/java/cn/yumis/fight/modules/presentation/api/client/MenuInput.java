package cn.yumis.fight.modules.presentation.api.client;
import cn.yumis.fight.platform.ItemData;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
/** Menu key registration and navigation callback owned by presentation. */
public final class MenuInput {
    public static final KeyMapping.Category CATEGORY=new KeyMapping.Category(Identifier.fromNamespaceAndPath(ItemData.NS,"combat"));
    public static final KeyMapping MENU=new KeyMapping("key.ydnf.menu",GLFW.GLFW_KEY_I,CATEGORY);
    private static Runnable open=()->{};
    private MenuInput(){}
    public static void navigation(Runnable action){open=java.util.Objects.requireNonNull(action);}
    public static void open(){open.run();}
}
