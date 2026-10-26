package cn.yumis.fight;
import cn.yumis.fight.foundation.Journal;
import cn.yumis.fight.rules.Models.*;
import static cn.yumis.fight.rules.Models.JSON;
import com.google.gson.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
class JournalTest {
    @TempDir Path dir;
    @Test void deletionMarkersSurviveRestartAndCheckpoint()throws Exception{
        Path path=dir.resolve("deletions.wal");
        try(Journal journal=new Journal(path)){
            journal.put("team/old",Map.of("leader","player"));
            journal.put("ailment/old",Map.of("expires",100));
            journal.put("team/old",null);
            journal.begin();
            journal.put("ailment/old",null);
            journal.put("receipt/disband",true);
            journal.finish();
        }
        try(Journal journal=new Journal(path)){
            assertTrue(journal.get("team/old").isJsonNull());
            assertTrue(journal.get("ailment/old").isJsonNull());
            assertTrue(journal.get("receipt/disband").getAsBoolean());
            journal.checkpoint();
        }
        try(Journal journal=new Journal(path)){
            assertTrue(journal.keys("team/").isEmpty());
            assertTrue(journal.keys("ailment/").isEmpty());
        }
    }
    @Test void wholeInventoryAndRewardsCommitTogetherAndReplay()throws Exception{
        Path path=dir.resolve("world.wal");
        try(Journal j=new Journal(path)){
            j.transaction(Map.of("inventory/user",JSON.toJsonTree(List.of("gear")),"profession/user/craftsman",JSON.toJsonTree(60),"receipt/op",JSON.toJsonTree("done")));
            j.begin();
            j.put("inventory/user",List.of("lost"));
            j.abort();
        }
        try(Journal j=new Journal(path)){
            assertEquals(60,j.get("profession/user/craftsman",Integer.class,0));
            assertEquals("gear",j.get("inventory/user").getAsJsonArray().get(0).getAsString());
            assertEquals("done",j.get("receipt/op",String.class,""));
        }
    }
    @Test void interruptedTailIsTruncatedButFullCorruptionFails()throws Exception{
        Path path=dir.resolve("state.wal");
        try(Journal j=new Journal(path)){
            j.put("good",42);
        }
        long length=Files.size(path);
        Files.write(path,new byte[]{
            1,2,3
        }
        ,StandardOpenOption.APPEND);
        try(Journal j=new Journal(path)){
            assertEquals(42,j.get("good",Integer.class,0));
        }
        assertEquals(length,Files.size(path));
        byte[] data=Files.readAllBytes(path);
        data[data.length-1]^=4;
        Files.write(path,data);
        assertThrows(java.io.IOException.class,()->new Journal(path));
    }
    @Test void concurrentLethalRequestsHaveOneDeathAndBoundedActualShare()throws Exception{
        Life life=new Life();
        life.target=UUID.randomUUID();
        Set<String> deaths=new HashSet<>();
        double[] actual={
            0
        }
        ;
        try(Journal journal=new Journal(dir.resolve("life.wal"));var executor=Executors.newVirtualThreadPerTaskExecutor()){
            List<Future<?>> all=new ArrayList<>();
            for(int i=0;i<40;i++){
                final int operation=i%20;
                all.add(executor.submit(()->{
                    synchronized(journal){
                        String key="receipt/"+operation;if(!journal.get(key).isJsonNull())return;Receipt r=cn.yumis.fight.rules.Models.apply(life,.17,false);if(!r.deathId().isEmpty())deaths.add(r.deathId());actual[0]+=r.actual();try{
                            journal.transaction(Map.of("life",JSON.toJsonTree(life),key,JSON.toJsonTree(r)));
                        }
                        catch(Exception e){
                            throw new RuntimeException(e);
                        }
                    }
                }
                ));
            }
            for(Future<?> f:all)f.get();
            assertEquals(1,actual[0],1e-9);
            assertEquals(1,deaths.size());
            assertTrue(life.dead);
            assertEquals(20,journal.keys("receipt/").size());
        }
    }
    @Test void checkpointKeepsCommittedReceipts()throws Exception{
        Path path=dir.resolve("checkpoint.wal");
        try(Journal j=new Journal(path)){
            j.put("a",1);
            j.put("a",2);
            j.put("receipt/9",Map.of("accepted",true));
            j.checkpoint();
        }
        try(Journal j=new Journal(path)){
            assertEquals(2,j.get("a",Integer.class,0));
            assertTrue(j.get("receipt/9").getAsJsonObject().get("accepted").getAsBoolean());
        }
    }
    @Test void invalidFractionsCannotModifyLife(){
        Life life=new Life();
        assertThrows(IllegalArgumentException.class,()->cn.yumis.fight.rules.Models.apply(life,Double.NaN,false));
        assertThrows(IllegalArgumentException.class,()->cn.yumis.fight.rules.Models.apply(life,-1,false));
        assertEquals(1,life.q);
    }
}
