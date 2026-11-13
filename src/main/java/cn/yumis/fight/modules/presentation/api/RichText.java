package cn.yumis.fight.modules.presentation.api;

import net.minecraft.network.chat.Component;
import java.util.*;

/** Presentation-only spans: zero colors inherit, one is solid, two form a vertical gradient. */
public record RichText(List<Span> spans) {
    public static final String GRADIENT="ydnf:vertical/";
    public record Span(String text,List<String> colors) {
        public Span {
            Objects.requireNonNull(text,"文字片段缺少text");colors=colors==null?List.of():List.copyOf(colors);
            if(colors.size()>2||colors.stream().anyMatch(c->c==null||!c.matches("#[0-9a-fA-F]{6}")))throw new IllegalArgumentException("文字片段只允许零至两个 #RRGGBB 色值");
        }
        public int color(int index){return Integer.parseInt(colors.get(index).substring(1),16);}
    }
    public RichText {spans=List.copyOf(Objects.requireNonNull(spans,"缺少文字片段"));}
    public static RichText of(String text){return new RichText(List.of(new Span(text,List.of())));}
    public String plain(){var value=new StringBuilder();for(var span:spans)value.append(span.text());return value.toString();}
    public Component component(){
        var value=Component.empty();for(var span:spans){var part=Component.literal(span.text());
            if(!span.colors().isEmpty())part.withStyle(style->style.withColor(span.color(0)));
            if(span.colors().size()==2)part.withStyle(style->style.withInsertion(GRADIENT+span.colors().get(0).substring(1)+"/"+span.colors().get(1).substring(1)));
            value.append(part);
        }return value;
    }
}
