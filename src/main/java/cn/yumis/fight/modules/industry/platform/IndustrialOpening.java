package cn.yumis.fight.modules.industry.platform;
import cn.yumis.fight.modules.industry.api.IndustrialPorts;
import cn.yumis.fight.modules.industry.api.IndustrialRules;
import cn.yumis.fight.network.Network;
import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** One pending, bounded-lifetime opening per player, owned by this world's industry service. */
public final class IndustrialOpening {
    private record Pending(String token,MachineBlockEntity machine,String spec,IndustrialRules rules,net.minecraft.world.inventory.AbstractContainerMenu origin,long expires){}
    private final Map<UUID,Pending> pending=new HashMap<>();
    public static String digest(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    public void prepare(ServerPlayer player,MachineBlockEntity machine){
        if(!machine.stillValid(player)||player.level().getBlockEntity(machine.getBlockPos())!=machine)return;
        String token=UUID.randomUUID().toString();var world=IndustrialWorld.get(player.level());
        JsonObject snapshot=new JsonObject();snapshot.add("info",world.info(machine,0,false));JsonArray slots=new JsonArray();
        var codec=player.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        for(int i=0;i<machine.getContainerSize();i++){JsonObject cell=new JsonObject();cell.add("sample",ItemStack.OPTIONAL_CODEC.encodeStart(codec,machine.items.getResource(i).toStack(machine.items.getResource(i).isEmpty()?0:1)).getOrThrow());cell.addProperty("amount",machine.items.getAmountAsLong(i));slots.add(cell);}
        for(int i=0;i<3;i++)slots.add(cell(codec,machine.plugins.getItem(i)));
        for(int i=9;i<36;i++)slots.add(cell(codec,player.getInventory().getItem(i)));
        for(int i=0;i<9;i++)slots.add(cell(codec,player.getInventory().getItem(i)));
        snapshot.add("slots",slots);String raw=snapshot.toString(),hash=digest(raw);List<String> parts=new ArrayList<>();
        for(int at=0;at<raw.length();){int end=Math.min(raw.length(),at+16000);if(end<raw.length()&&Character.isHighSurrogate(raw.charAt(end-1)))end--;parts.add(raw.substring(at,end));at=end;}
        if(parts.size()>1024){IndustrialPorts.message(player,"设备数据过大，无法打开");return;}
        pending.put(player.getUUID(),new Pending(token,machine,machine.spec().id(),IndustrialRules.current(),player.containerMenu,System.currentTimeMillis()+10000));
        for(int i=0;i<parts.size();i++){JsonObject part=new JsonObject();part.addProperty("token",token);part.addProperty("part",i);part.addProperty("count",parts.size());part.addProperty("hash",hash);part.addProperty("text",parts.get(i));Network.send(player,"industrial_prepare",part);}
    }
    private static JsonObject cell(com.mojang.serialization.DynamicOps<com.google.gson.JsonElement> codec,ItemStack stack){JsonObject cell=new JsonObject();cell.add("sample",ItemStack.OPTIONAL_CODEC.encodeStart(codec,stack).getOrThrow());cell.addProperty("amount",stack.getCount());return cell;}
    public void ready(ServerPlayer player,JsonObject request){
        Pending open=pending.get(player.getUUID());if(open==null||!open.token.equals(request.get("token").getAsString()))return;
        pending.remove(player.getUUID());var machine=open.machine;
        if(System.currentTimeMillis()>open.expires||player.containerMenu!=open.origin||!machine.stillValid(player)||player.level().getBlockEntity(machine.getBlockPos())!=machine){IndustrialPorts.message(player,"设备读取已失效，请重新打开");return;}
        if(!machine.spec().id().equals(open.spec)||IndustrialRules.current()!=open.rules){prepare(player,machine);return;}
        player.openMenu(machine,buf->IndustrialMenu.writeOpen(buf,machine,open.token));
    }
    public void tick(MinecraftServer server){long now=System.currentTimeMillis();pending.entrySet().removeIf(entry->{ServerPlayer player=server.getPlayerList().getPlayer(entry.getKey());if(player==null)return true;if(entry.getValue().expires<now){IndustrialPorts.message(player,"设备读取超时，请重试");return true;}return false;});}
}
