package cn.yumis.fight;
import cn.yumis.fight.modules.inventory.api.BagOption;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** 次元背包自动收纳偏好：默认 1关 2开，宽容解码与持久化往返。 */
class BagOptionTest {
    @Test void missingOrMalformedFallsBackToDefaults(){
        assertEquals(BagOption.DEFAULT,new BagOption(false,true));
        assertEquals(BagOption.DEFAULT,BagOption.parse(null));
        assertEquals(BagOption.DEFAULT,BagOption.parse(JsonParser.parseString("null")));
        assertEquals(BagOption.DEFAULT,BagOption.parse(JsonParser.parseString("[true]")));
        assertEquals(BagOption.DEFAULT,BagOption.parse(JsonParser.parseString("{\"autoStore\":null,\"onlyWhenFull\":null}")));
    }
    @Test void explicitValuesAreParsed(){
        assertEquals(new BagOption(true,true),BagOption.parse(JsonParser.parseString("{\"autoStore\":true,\"onlyWhenFull\":true}")));
        assertEquals(new BagOption(true,false),BagOption.parse(JsonParser.parseString("{\"autoStore\":true,\"onlyWhenFull\":false}")));
        assertEquals(new BagOption(false,false),BagOption.parse(JsonParser.parseString("{\"autoStore\":false,\"onlyWhenFull\":false}")));
    }
    @Test void eachFieldFallsBackIndependently(){
        assertEquals(new BagOption(false,false),BagOption.parse(JsonParser.parseString("{\"onlyWhenFull\":false}")));
        assertEquals(new BagOption(true,true),BagOption.parse(JsonParser.parseString("{\"autoStore\":true}")));
        assertEquals(new BagOption(false,true),BagOption.parse(JsonParser.parseString("{\"autoStore\":{},\"onlyWhenFull\":\"true\"}")));
        assertEquals(new BagOption(true,true),BagOption.parse(JsonParser.parseString("{\"autoStore\":\"true\",\"onlyWhenFull\":[]}")));
    }
    @Test void encodeRoundTrip(){
        for(BagOption option:new BagOption[]{new BagOption(false,true),new BagOption(true,true),new BagOption(true,false),new BagOption(false,false)}){
            assertEquals(option,BagOption.parse(JsonParser.parseString(option.encode().toString())));
        }
    }
    @Test void encodeCarriesBothSwitches(){
        var encoded=BagOption.parse(JsonParser.parseString("{\"autoStore\":true,\"onlyWhenFull\":false}")).encode().getAsJsonObject();
        assertEquals(2,encoded.size());
        assertTrue(encoded.get("autoStore").getAsBoolean());
        assertFalse(encoded.get("onlyWhenFull").getAsBoolean());
    }
}
