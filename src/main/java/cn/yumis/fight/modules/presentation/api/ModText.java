package cn.yumis.fight.modules.presentation.api;
import cn.yumis.fight.platform.ItemData;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;

/** Stable resource name also works in components produced by a dedicated server. */
public final class ModText {
    public static final FontDescription FONT=new FontDescription.Resource(Identifier.fromNamespaceAndPath(ItemData.NS,"ui"));
    private ModText(){}
    public static MutableComponent of(String text){return decorate(Component.literal(text));}
    public static MutableComponent decorate(Component text){return text.copy().withStyle(style->style.withFont(FONT));}
}
