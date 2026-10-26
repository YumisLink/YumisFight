package cn.yumis.fight.network;
import cn.yumis.fight.game.GameItems;
import cn.yumis.fight.server.ServerRuntime;
import com.google.gson.*;
import static cn.yumis.fight.rules.Models.JSON;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import java.util.function.BiConsumer;
public final class Network {
    public static BiConsumer<String,JsonObject> CLIENT=(a,j)->{
    }
    ;
    private Network(){
    }
    public record Request(String action,String json) implements CustomPacketPayload {
        public static final Type<Request> TYPE=new Type<>(Identifier.fromNamespaceAndPath(GameItems.NS,"request"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Request> CODEC=StreamCodec.of((b,v)->{
            b.writeUtf(v.action,64);b.writeUtf(v.json,262144);
        }
        ,b->new Request(b.readUtf(64),b.readUtf(262144)));
        @Override public Type<Request> type(){
            return TYPE;
        }
    }
    public record Snapshot(String action,String json) implements CustomPacketPayload {
        public static final Type<Snapshot> TYPE=new Type<>(Identifier.fromNamespaceAndPath(GameItems.NS,"snapshot"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Snapshot> CODEC=StreamCodec.of((b,v)->{
            b.writeUtf(v.action,64);b.writeUtf(v.json,262144);
        }
        ,b->new Snapshot(b.readUtf(64),b.readUtf(262144)));
        @Override public Type<Snapshot> type(){
            return TYPE;
        }
    }
    public record State(String action,String json) implements CustomPacketPayload {
        public static final Type<State> TYPE=new Type<>(Identifier.fromNamespaceAndPath(GameItems.NS,"state"));
        public static final StreamCodec<RegistryFriendlyByteBuf,State> CODEC=StreamCodec.of((b,v)->{
            b.writeUtf(v.action,40);b.writeUtf(v.json,262144);
        }
        ,b->new State(b.readUtf(40),b.readUtf(262144)));
        @Override public Type<State> type(){
            return TYPE;
        }
    }
    public static void register(RegisterPayloadHandlersEvent event){
        var r=event.registrar(Integer.toString(cn.yumis.fight.rules.Rules.PROTOCOL));
        r.playToServer(Request.TYPE,Request.CODEC,(p,c)->c.enqueueWork(()->ServerRuntime.get().handle((ServerPlayer)c.player(),p.action,parse(p.json))));
        r.playToClient(Snapshot.TYPE,Snapshot.CODEC,(p,c)->c.enqueueWork(()->CLIENT.accept(p.action,parse(p.json))));
        r.playBidirectional(State.TYPE,State.CODEC,(p,c)->c.enqueueWork(()->ServerRuntime.get().handle((ServerPlayer)c.player(),p.action,parse(p.json))),(p,c)->c.enqueueWork(()->CLIENT.accept(p.action,parse(p.json))));
    }
    private static JsonObject parse(String s){
        JsonElement e=JsonParser.parseString(s);
        return e.isJsonObject()?e.getAsJsonObject():new JsonObject();
    }
    public static void send(ServerPlayer p,String action,Object value){
        PacketDistributor.sendToPlayer(p,new Snapshot(action,JSON.toJson(value)));
    }
}
