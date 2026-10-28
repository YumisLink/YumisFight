package cn.yumis.fight.modules.presentation.api.client;
import com.mojang.blaze3d.font.GlyphProvider;
import com.mojang.blaze3d.font.TrueTypeGlyphProvider;
import com.mojang.blaze3d.platform.TextureUtil;
import net.minecraft.client.gui.font.providers.FreeTypeUtil;
import net.minecraft.client.gui.font.providers.TrueTypeGlyphProviderDefinition;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.freetype.FT_Face;
import org.lwjgl.util.freetype.FT_MM_Var;
import org.lwjgl.util.freetype.FreeType;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
/** FreeType owns the face and native buffer until the returned provider is closed. */
public final class UiOpenType {
    private static final Set<String> failures=ConcurrentHashMap.newKeySet();
    private UiOpenType(){}
    public static void failed(String path){failures.add(path);}
    public static void clearFailures(){failures.clear();}
    public static boolean failed(){return !failures.isEmpty();}
    public static void validate(Path path) throws IOException {
        long length=Files.size(path);
        if(length<12||length>64L*1024*1024)throw new IOException("字体文件大小必须在12字节至64 MiB之间");
        var definition=new TrueTypeGlyphProviderDefinition(net.minecraft.resources.Identifier.fromNamespaceAndPath("yumis_dungeon_and_fight","imported/check.ttf"),9,2,TrueTypeGlyphProviderDefinition.Shift.NONE,"");
        try(var input=Files.newInputStream(path);var provider=load(input,definition)){
            if(provider.getSupportedGlyphs().isEmpty())throw new IOException("字体没有可用Unicode字符");
            for(int codepoint:new int[]{'A','0','中','文'})provider.getGlyph(codepoint);
        }
    }
    public static GlyphProvider load(InputStream input,TrueTypeGlyphProviderDefinition definition) throws IOException {
        ByteBuffer data=null;
        FT_Face face=null;
        try{
            data=TextureUtil.readResource(input);
            synchronized(FreeTypeUtil.LIBRARY_LOCK){
                try(var stack=MemoryStack.stackPush()){
                    var pointer=stack.mallocPointer(1);
                    FreeTypeUtil.assertError(FreeType.FT_New_Memory_Face(FreeTypeUtil.getLibrary(),data,0,pointer),"读取字体");
                    face=FT_Face.create(pointer.get());
                }
                String format=FreeType.FT_Get_Font_Format(face);
                if(!"TrueType".equals(format)&&!"CFF".equals(format))throw new IOException("不支持的字体轮廓："+format+"；请选择TTF或OTF字体");
                FreeTypeUtil.assertError(FreeType.FT_Select_Charmap(face,FreeType.FT_ENCODING_UNICODE),"读取Unicode字符映射");
                if(definition.location().getPath().equals("noto_sans_sc.ttf"))regularWeight(face);
                return new TrueTypeGlyphProvider(data,face,definition.size(),definition.oversample(),definition.shift().x(),definition.shift().y(),definition.skip());
            }
        }
        catch(Exception failure){
            synchronized(FreeTypeUtil.LIBRARY_LOCK){if(face!=null)FreeType.FT_Done_Face(face);}
            MemoryUtil.memFree(data);
            if(failure instanceof IOException io)throw io;
            throw new IOException("无法读取字体："+failure.getMessage(),failure);
        }
    }
    // The upstream variable Noto file defaults to weight 100. Use its regular 400 instance without modifying the asset.
    private static void regularWeight(FT_Face face){
        try(var stack=MemoryStack.stackPush()){
            var pointer=stack.mallocPointer(1);
            FreeTypeUtil.assertError(FreeType.FT_Get_MM_Var(face,pointer),"读取Noto字体字重");
            var variation=FT_MM_Var.create(pointer.get());
            try{
                var coordinates=stack.mallocCLong(variation.num_axis());
                var axes=variation.axis();
                for(int i=0;i<variation.num_axis();i++){
                    var axis=axes.get(i);
                    coordinates.put(i,axis.tag()==0x77676874L?Math.clamp(400L*65536,axis.minimum(),axis.maximum()):axis.def());
                }
                FreeTypeUtil.assertError(FreeType.FT_Set_Var_Design_Coordinates(face,coordinates),"设置Noto常规字重");
            }
            finally{FreeType.FT_Done_MM_Var(FreeTypeUtil.getLibrary(),variation);}
        }
    }
}
