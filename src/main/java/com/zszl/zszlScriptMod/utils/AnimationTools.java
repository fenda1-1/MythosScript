// 文件路径: src/main/java/com/keycommand2/zszlScriptMod/utils/AnimationTools.java
// (这是一个全新的文件)
package com.zszl.zszlScriptMod.utils;

public class AnimationTools {
    /**
     * 将一个浮点数限制在最小值和最大值之间。
     */
    public static float clamp(float number, float min, float max) {
        return number < min ? min : Math.min(number, max);
    }
}
