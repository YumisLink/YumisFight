package cn.yumis.fight.bootstrap;

import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Connection-scoped definition assembly and versioned personal snapshots. No gameplay state is owned here. */
public final class ProfileSync {
    private ProfileSync(){}
    public static String digest(JsonObject value){
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.toString().getBytes(StandardCharsets.UTF_8)));}
        catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
    public static final class Sender {
        private String session="",definitions="";
        private JsonObject previous;
        private long revision;
        private final ArrayDeque<JsonObject> parts=new ArrayDeque<>();
        public void resetPersonal(){previous=null;}
        public void resetDefinitions(){definitions="";parts.clear();previous=null;}
        public void publish(String connection,String definitionKey,JsonObject definition,JsonObject personal,BiConsumer<String,JsonObject> send){
            if(!connection.equals(session)){session=connection;definitions="";previous=null;revision=0;parts.clear();}
            boolean definitionsChanged=!definitionKey.equals(definitions);
            if(definitionsChanged){
                definitions=definitionKey;parts.clear();
                String text=definition.toString();List<String> chunks=new ArrayList<>();
                for(int from=0;from<text.length();){int to=Math.min(text.length(),from+16000);if(to<text.length()&&Character.isHighSurrogate(text.charAt(to-1)))to--;chunks.add(text.substring(from,to));from=to;}
                for(int i=0;i<chunks.size();i++){JsonObject part=new JsonObject();part.addProperty("session",session);part.addProperty("key",definitions);part.addProperty("part",i);part.addProperty("count",chunks.size());part.addProperty("text",chunks.get(i));parts.add(part);}
            }
            JsonObject delta=new JsonObject();
            JsonArray removed=new JsonArray();
            for(var field:personal.entrySet())if(!field.getKey().equals("serverNow")&&(previous==null||!field.getValue().equals(previous.get(field.getKey())))){
                if(field.getValue().isJsonNull())removed.add(field.getKey());else delta.add(field.getKey(),field.getValue());
            }
            if(previous!=null)for(String key:previous.keySet())if(!personal.has(key))removed.add(key);
            if(previous!=null&&delta.isEmpty()&&removed.isEmpty()&&!definitionsChanged)return;
            // The sample clock is metadata, never the cause of a personal revision.
            delta.add("serverNow",personal.get("serverNow"));
            JsonObject packet=new JsonObject();packet.addProperty("session",session);packet.addProperty("definitionsKey",definitions);packet.addProperty("base",previous==null?0:revision);packet.addProperty("revision",++revision);packet.addProperty("full",previous==null);packet.add("fields",delta);packet.add("removed",removed);
            previous=personal.deepCopy();send.accept("profile",packet);
        }
        public void flush(BiConsumer<String,JsonObject> send){for(int i=0;i<2&&!parts.isEmpty();i++)send.accept("definitionPart",parts.removeFirst());}
    }
    public static final class Receiver {
        private String session="",definitionsKey="",pendingKey="";
        private JsonObject definitions=new JsonObject(),personal=new JsonObject();
        private String[] parts;
        private int received;
        private long revision;
        private JsonObject waiting;
        public void clear(){session=definitionsKey=pendingKey="";definitions=new JsonObject();personal=new JsonObject();parts=null;received=0;revision=0;waiting=null;}
        public void retryDefinitions(){pendingKey="";parts=null;received=0;}
        public void definition(JsonObject packet,Consumer<JsonObject> install,Consumer<JsonObject> publish){
            String incoming=packet.get("session").getAsString(),key=packet.get("key").getAsString();
            if(!session.isEmpty()&&!incoming.equals(session))return;
            if(session.isEmpty())session=incoming;
            int count=packet.get("count").getAsInt(),index=packet.get("part").getAsInt();
            if(count<1||count>2048||index<0||index>=count)throw new IllegalArgumentException("规则分片无效");
            if(!key.equals(pendingKey)){pendingKey=key;parts=new String[count];received=0;}
            if(parts==null||parts.length!=count)return;
            if(parts[index]==null){parts[index]=packet.get("text").getAsString();received++;}
            if(received!=count)return;
            JsonObject candidate=JsonParser.parseString(String.join("",parts)).getAsJsonObject();
            if(!digest(candidate).equals(key))throw new IllegalArgumentException("规则分片摘要不一致");
            install.accept(candidate);definitions=candidate;definitionsKey=key;parts=null;received=0;
            if(waiting!=null&&definitionsKey.equals(waiting.get("definitionsKey").getAsString())){JsonObject next=waiting;waiting=null;publish(next,publish);}
        }
        public boolean personal(JsonObject packet,long receivedAt,Consumer<JsonObject> publish){
            String incoming=packet.get("session").getAsString();
            if(!session.isEmpty()&&!session.equals(incoming))return false;
            session=incoming;
            long next=packet.get("revision").getAsLong(),base=packet.get("base").getAsLong();
            if(next<=revision)return true;
            if(!packet.get("full").getAsBoolean()&&base!=revision)return false;
            if(packet.get("full").getAsBoolean())personal=new JsonObject();
            packet.getAsJsonObject("fields").entrySet().forEach(e->personal.add(e.getKey(),e.getValue()));revision=next;
            if(packet.has("removed"))packet.getAsJsonArray("removed").forEach(e->personal.remove(e.getAsString()));
            JsonObject assembled=personal.deepCopy();assembled.addProperty("definitionsKey",packet.get("definitionsKey").getAsString());assembled.addProperty("_receivedAt",receivedAt);
            if(!definitionsKey.equals(packet.get("definitionsKey").getAsString()))waiting=assembled;
            else publish(assembled,publish);
            return true;
        }
        private void publish(JsonObject state,Consumer<JsonObject> consumer){JsonObject complete=state.deepCopy();definitions.entrySet().forEach(e->complete.add(e.getKey(),e.getValue()));consumer.accept(complete);}
    }
}
