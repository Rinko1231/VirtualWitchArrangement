package com.rinko1231.vwa.config;


import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

public final class ResearchLayoutClientConfig {

    public static final ForgeConfigSpec CLIENT_SPEC;
    public static final ForgeConfigSpec.IntValue LAYOUT_MODE;

    public enum Mode {
        SPIRAL(-1),   // 螺旋式：由圆心向外
        CIRCLE(0),    // 同心圆式：原版风格
        SQUARE(1);    // 方形环绕阵列式

        public final int code;
        Mode(int code) { this.code = code; }
        public static Mode from(int v) {
            for (Mode m : values()) if (m.code == v) return m;
            return CIRCLE; // 兜底为原版布局
        }
    }

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

        b.push("eldritch_research");
        LAYOUT_MODE = b
                .comment(
                        "Layout mode of the Eldritch Research screen.",
                        " -1 = Spiral from center",
                        "  0 = Concentric circle",
                        "  1 = Square ring array")
                .translation("config.vwa.eldritch_research.layout_mode")
                .defineInRange("layout_mode", 0, -1, 1);
        b.pop();

        CLIENT_SPEC = b.build();
    }

    @SuppressWarnings("removal")
    public static void register() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, CLIENT_SPEC);
    }

    /** 供渲染/排布端读取当前模式 */
    public static Mode current() {
        return Mode.from(LAYOUT_MODE.get());
    }

    private ResearchLayoutClientConfig() {}
}
