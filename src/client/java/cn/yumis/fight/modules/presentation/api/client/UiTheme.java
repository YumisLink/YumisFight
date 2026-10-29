package cn.yumis.fight.modules.presentation.api.client;
import cn.yumis.fight.modules.presentation.api.ThemeDefinition;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.neoforged.fml.loading.FMLPaths;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;

/** Every owned screen reads the same palette and surface primitives. */
public final class UiTheme {
    public static int OVERLAY,PANEL,CARD,BUTTON,HOVER,SELECTED,DISABLED,TEXT,MUTED,ACCENT,GOLD,ERROR,SUCCESS,NUMERIC;
    public static int BORDER,LIGHT,SHADOW,SLOT,SLOT_SHADOW,SLOT_LIGHT,SCROLL_TRACK,SCROLL_THUMB;
    public static int TIP_TEXT,TIP_MUTED,TIP_TITLE,TIP_NUMBER,HUD_PANEL,HUD_TEXT,HUD_MUTED,HUD_GOLD,DAMAGE_BAR;
    public static long revision;
    public static String status="";
    private static ThemeDefinition definition,lightDefinition,darkDefinition;
    private static int paletteDepth;
    private static long nextCheck,stamp=Long.MIN_VALUE;
    private static final String DEFAULTS;
    static {
        try(var source=UiTheme.class.getResourceAsStream("/assets/yumis_dungeon_and_fight/ui-theme.json")){
            if(source==null)throw new IllegalStateException("缺少默认界面主题");
            DEFAULTS=new String(source.readAllBytes(),StandardCharsets.UTF_8);lightDefinition=ThemeDefinition.parse(DEFAULTS,null);darkDefinition=dark(DEFAULTS,lightDefinition);apply(lightDefinition);
        }catch(Exception failure){throw new ExceptionInInitializerError(failure);}
    }
    private UiTheme(){}
    public static Path path(){return FMLPaths.CONFIGDIR.get().resolve("yumis_dungeon_and_fight/ui-theme.json");}
    public static void load(){
        try{Files.createDirectories(path().getParent());if(!Files.exists(path()))Files.writeString(path(),DEFAULTS);reload();}
        catch(Exception failure){status="主题读取失败，保留上次有效样式："+failure.getMessage();}
    }
    public static void check(){
        if(paletteDepth>0)return;long now=System.currentTimeMillis();if(now<nextCheck)return;nextCheck=now+1000;
        try{if(Files.exists(path())&&Files.getLastModifiedTime(path()).toMillis()!=stamp)reload();}
        catch(Exception failure){status="主题读取失败，保留上次有效样式："+failure.getMessage();}
    }
    private static void reload()throws Exception{
        long modified=Files.getLastModifiedTime(path()).toMillis();if(stamp==modified)return;
        ThemeDefinition next=ThemeDefinition.parse(Files.readString(path()),ThemeDefinition.parse(DEFAULTS,null));
        ThemeDefinition nextDark=dark(Files.readString(path()),next);
        lightDefinition=next;darkDefinition=nextDark;apply(next);stamp=modified;status="";revision++;
    }
    private static ThemeDefinition dark(String text,ThemeDefinition light){
        var defaults=com.google.gson.JsonParser.parseString(DEFAULTS).getAsJsonObject();var json=com.google.gson.JsonParser.parseString(text).getAsJsonObject();
        ThemeDefinition fallback=ThemeDefinition.parse(defaults.getAsJsonObject("dark").toString(),light);
        return json.has("dark")?ThemeDefinition.parse(json.getAsJsonObject("dark").toString(),fallback):fallback;
    }
    public static void preferencesChanged(){revision++;}
    public static boolean scoped(){return paletteDepth>0;}
    public static PaletteScope use(String theme){check();return new PaletteScope(theme.equals("dark")?darkDefinition:lightDefinition);}
    public static final class PaletteScope implements AutoCloseable {
        private final ThemeDefinition previous;
        private PaletteScope(ThemeDefinition next){previous=definition;paletteDepth++;apply(next);}
        @Override public void close(){apply(previous);paletteDepth--;}
    }
    private static void apply(ThemeDefinition next){
        if(definition==next)return;
        OVERLAY=next.color("OVERLAY");
        PANEL=next.color("PANEL");
        CARD=next.color("CARD");
        BUTTON=next.color("BUTTON");
        HOVER=next.color("HOVER");
        SELECTED=next.color("SELECTED");
        DISABLED=next.color("DISABLED");
        TEXT=next.color("TEXT");
        MUTED=next.color("MUTED");
        ACCENT=next.color("ACCENT");
        GOLD=next.color("GOLD");
        ERROR=next.color("ERROR");
        SUCCESS=next.color("SUCCESS");
        NUMERIC=next.color("NUMERIC");
        BORDER=next.color("BORDER");
        LIGHT=next.color("LIGHT");
        SHADOW=next.color("SHADOW");
        SLOT=next.color("SLOT");
        SLOT_SHADOW=next.color("SLOT_SHADOW");
        SLOT_LIGHT=next.color("SLOT_LIGHT");
        SCROLL_TRACK=next.color("SCROLL_TRACK");
        SCROLL_THUMB=next.color("SCROLL_THUMB");
        TIP_TEXT=next.color("TIP_TEXT");
        TIP_MUTED=next.color("TIP_MUTED");
        TIP_TITLE=next.color("TIP_TITLE");
        TIP_NUMBER=next.color("TIP_NUMBER");
        HUD_PANEL=next.color("HUD_PANEL");
        HUD_TEXT=next.color("HUD_TEXT");
        HUD_MUTED=next.color("HUD_MUTED");
        HUD_GOLD=next.color("HUD_GOLD");
        DAMAGE_BAR=next.color("DAMAGE_BAR");
        definition=next;
    }
    public static int textInset(){return definition.textInset();}
    public static int tooltipColor(int color){return color==TEXT?TIP_TEXT:color==MUTED?TIP_MUTED:color==GOLD||color==ACCENT?TIP_TITLE:color;}
    private static void frame(GuiGraphicsExtractor g,int x,int y,int w,int h,int fill,boolean inset){
        if(w<=0||h<=0)return;check();g.fill(x,y,x+w,y+h,fill);
        if(definition.style().equals("flat")){g.fill(x,y,x+w,y+1,BORDER);g.fill(x,y+h-1,x+w,y+h,BORDER);return;}
        int size=Math.min(definition.border(),Math.min(w,h)/2),top=inset?SHADOW:LIGHT,bottom=inset?LIGHT:SHADOW;
        g.fill(x,y,x+w,y+size,top);g.fill(x,y,x+size,y+h,top);g.fill(x,y+h-size,x+w,y+h,bottom);g.fill(x+w-size,y,x+w,y+h,bottom);
    }
    public static void panel(GuiGraphicsExtractor g,int x,int y,int w,int h){g.fill(x-1,y-1,x+w+1,y+h+1,BORDER);frame(g,x,y,w,h,PANEL,false);}
    public static void card(GuiGraphicsExtractor g,int x,int y,int w,int h){frame(g,x,y,w,h,CARD,true);}
    public static void slot(GuiGraphicsExtractor g,int x,int y){g.fill(x-1,y-1,x+17,y+17,SLOT);g.fill(x-1,y-1,x+17,y,SLOT_SHADOW);g.fill(x-1,y-1,x,y+17,SLOT_SHADOW);g.fill(x,y+16,x+17,y+17,SLOT_LIGHT);g.fill(x+16,y,x+17,y+17,SLOT_LIGHT);}
    public static void button(GuiGraphicsExtractor g,int x,int y,int w,int h,boolean active,boolean hover,boolean selected){frame(g,x,y,w,h,!active?DISABLED:selected?SELECTED:hover?HOVER:BUTTON,selected);}
    public static void scrollbar(GuiGraphicsExtractor g,int x,int y,int h,int thumb,int offset){g.fill(x,y,x+10,y+h,SCROLL_TRACK);frame(g,x+1,y+offset,8,thumb,SCROLL_THUMB,false);}
}
