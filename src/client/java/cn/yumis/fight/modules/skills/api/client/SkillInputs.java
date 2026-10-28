package cn.yumis.fight.modules.skills.api.client;
import cn.yumis.fight.modules.presentation.api.client.MenuInput;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;
public final class SkillInputs {
    public static final KeyMapping LEAP=new KeyMapping("key.ydnf.cloud_leap",GLFW.GLFW_KEY_C,MenuInput.CATEGORY);
    public static final KeyMapping[] SKILLS=new KeyMapping[8];
    static {int[] keys={GLFW.GLFW_KEY_R,GLFW.GLFW_KEY_T,GLFW.GLFW_KEY_Z,GLFW.GLFW_KEY_X,GLFW.GLFW_KEY_N,GLFW.GLFW_KEY_V,GLFW.GLFW_KEY_F,GLFW.GLFW_KEY_G};for(int i=0;i<8;i++)SKILLS[i]=new KeyMapping("key.ydnf.skill_"+(i+1),keys[i],MenuInput.CATEGORY);}
    private SkillInputs(){}
}
