package cn.yumis.fight.modules.skills.client;
import cn.yumis.fight.platform.ItemData;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Convention library: a skill opts into bone animation by shipping assets/&lt;ns&gt;/player_animation/&lt;skill&gt;.json. */
public final class PlayerPoseLibrary {
    private static final ConcurrentHashMap<String,Optional<PlayerPoseAnimation>> CACHE=new ConcurrentHashMap<>();
    private PlayerPoseLibrary(){
    }
    /** Null when the skill ships no animation document or the document is invalid. */
    public static PlayerPoseAnimation find(String skillId){
        if(skillId==null||skillId.isBlank())return null;
        return CACHE.computeIfAbsent(skillId,PlayerPoseLibrary::load).orElse(null);
    }
    private static Optional<PlayerPoseAnimation> load(String skillId){
        var mc=Minecraft.getInstance();
        if(mc.getResourceManager()==null)return Optional.empty();
        String ownNamespace=ItemData.NS+":";
        // Vanilla resource identifiers reject uppercase paths, so document names are lowercased.
        String key=(skillId.startsWith(ownNamespace)?skillId.substring(ownNamespace.length()):skillId.replace(':','/')).toLowerCase(java.util.Locale.ROOT);
        String path="player_animation/"+key+".json";
        try(var reader=mc.getResourceManager().openAsReader(Identifier.fromNamespaceAndPath(ItemData.NS,path))){
            return Optional.of(PlayerPoseAnimation.parse((JsonObject)JsonParser.parseReader(reader)));
        }
        catch(Exception invalid){
            return Optional.empty();
        }
    }
}
