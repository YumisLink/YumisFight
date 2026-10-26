package cn.yumis.fight.foundation;
import com.google.gson.*;
import cn.yumis.fight.rules.Models;
import java.io.*;
import java.nio.*;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;
import java.util.zip.CRC32;
/** One owner per world. Publish only after a full record has reached durable storage. */
public final class Journal implements AutoCloseable {
    private static final Gson WAL_JSON=new GsonBuilder().disableHtmlEscaping().serializeNulls().create();
    private final Path path;
    private FileChannel channel;
    private final Map<String,JsonElement> data=new LinkedHashMap<>();
    private Map<String,JsonElement> batch;
    private boolean healthy=true;
    public Journal(Path path)throws IOException{
        this.path=path;
        Files.createDirectories(path.getParent());
        channel=FileChannel.open(path,StandardOpenOption.CREATE,StandardOpenOption.READ,StandardOpenOption.WRITE);
        replay();
    }
    private void replay()throws IOException{
        long valid=0;
        while(valid<channel.size()){
            channel.position(valid);
            ByteBuffer header=ByteBuffer.allocate(12);
            if(!read(header)){
                channel.truncate(valid);
                break;
            }
            header.flip();
            int length=header.getInt();
            long expected=header.getLong();
            if(length<2||length>64*1024*1024)throw new IOException("Invalid WAL length at "+valid);
            ByteBuffer body=ByteBuffer.allocate(length);
            if(!read(body)){
                channel.truncate(valid);
                break;
            }
            byte[] bytes=body.array();
            CRC32 crc=new CRC32();
            crc.update(bytes);
            if(crc.getValue()!=expected)throw new IOException("WAL CRC mismatch at "+valid);
            try{
                JsonObject changes=JsonParser.parseString(new String(bytes,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                apply(changes);
            }
            catch(RuntimeException ex){
                throw new IOException("Corrupt WAL JSON at "+valid,ex);
            }
            valid+=12L+length;
        }
        channel.position(channel.size());
    }
    private boolean read(ByteBuffer b)throws IOException{
        while(b.hasRemaining()){
            if(channel.read(b)<0)return false;
        }
        return true;
    }
    private void apply(JsonObject changes){
        for(var e:changes.entrySet())if(e.getValue().isJsonNull())data.remove(e.getKey());
        else data.put(e.getKey(),e.getValue().deepCopy());
    }
    public synchronized JsonElement get(String key){
        JsonElement v=batch!=null&&batch.containsKey(key)?batch.get(key):data.get(key);
        return v==null?JsonNull.INSTANCE:v.deepCopy();
    }
    public synchronized <T>T get(String key,Class<T> type,T fallback){
        JsonElement v=get(key);
        return v.isJsonNull()?fallback:Models.JSON.fromJson(v,type);
    }
    public synchronized Set<String> keys(String prefix){
        Set<String> result=new LinkedHashSet<>();
        for(String k:data.keySet())if(k.startsWith(prefix))result.add(k);
        if(batch!=null)for(String k:batch.keySet())if(k.startsWith(prefix))result.add(k);
        return result;
    }
    public synchronized void begin(){
        if(batch!=null)throw new IllegalStateException("Nested journal batch");
        batch=new LinkedHashMap<>();
    }
    public synchronized void put(String key,Object value)throws IOException{
        if(!healthy)throw new IOException("Journal unavailable");
        JsonElement v=Models.JSON.toJsonTree(value);
        if(batch!=null)batch.put(key,v);
        else commit(Map.of(key,v));
    }
    public synchronized void finish()throws IOException{
        if(batch==null)throw new IllegalStateException("No batch");
        Map<String,JsonElement> changes=batch;
        batch=null;
        if(!changes.isEmpty())commit(changes);
    }
    public synchronized void abort(){
        batch=null;
    }
    public synchronized void transaction(Map<String,JsonElement> changes)throws IOException{
        if(batch!=null){
            batch.putAll(changes);
            return;
        }
        commit(changes);
    }
    private void commit(Map<String,JsonElement> changes)throws IOException{
        JsonObject object=new JsonObject();
        changes.forEach(object::add);
        byte[] bytes=WAL_JSON.toJson(object).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if(bytes.length>64*1024*1024)throw new IOException("WAL record too large");
        CRC32 crc=new CRC32();
        crc.update(bytes);
        ByteBuffer record=ByteBuffer.allocate(12+bytes.length).putInt(bytes.length).putLong(crc.getValue()).put(bytes);
        record.flip();
        try{
            while(record.hasRemaining())channel.write(record);
            channel.force(true);
            apply(object);
        }
        catch(IOException failure){
            healthy=false;
            throw failure;
        }
    }
    public synchronized void checkpoint()throws IOException{
        if(batch!=null)throw new IOException("Batch pending");
        Path temp=path.resolveSibling(path.getFileName()+".next");
        Files.deleteIfExists(temp);
        try(Journal next=new Journal(temp)){
            next.transaction(new LinkedHashMap<>(data));
        }
        channel.close();
        try{
            Files.move(temp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        }
        finally{
            channel=FileChannel.open(path,StandardOpenOption.READ,StandardOpenOption.WRITE);
            channel.position(channel.size());
        }
    }
    public boolean healthy(){
        return healthy;
    }
    @Override public synchronized void close()throws IOException{
        if(channel.isOpen()){
            channel.force(true);
            channel.close();
        }
    }
}
