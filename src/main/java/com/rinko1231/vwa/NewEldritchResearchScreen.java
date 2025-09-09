package com.rinko1231.vwa;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.rinko1231.vwa.config.ResearchLayoutClientConfig;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.capabilities.magic.SyncedSpellData;
import io.redspace.ironsspellbooks.network.ServerboundLearnSpell;
import io.redspace.ironsspellbooks.player.ClientMagicData;
import io.redspace.ironsspellbooks.registries.ItemRegistry;
import io.redspace.ironsspellbooks.registries.SoundRegistry;
import io.redspace.ironsspellbooks.setup.Messages;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.blockentity.TheEndPortalRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.Vec2;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;
@SuppressWarnings("removal")
public class NewEldritchResearchScreen extends Screen {

    private static final ResourceLocation WINDOW_LOCATION = new ResourceLocation("irons_spellbooks", "textures/gui/eldritch_research_screen/square_window.png");
    private static final ResourceLocation FRAME_LOCATION = new ResourceLocation("irons_spellbooks", "textures/gui/eldritch_research_screen/spell_frame.png");
    public static final int WINDOW_WIDTH = 252;
    public static final int WINDOW_HEIGHT = 256;
    private static final int WINDOW_INSIDE_X = 9;
    private static final int WINDOW_INSIDE_Y = 18;
    public static final int WINDOW_INSIDE_WIDTH = 234;
    public static final int WINDOW_INSIDE_HEIGHT = 229;
    private static final int WINDOW_TITLE_X = 8;
    private static final int WINDOW_TITLE_Y = 6;
    public static final int BACKGROUND_TILE_WIDTH = 16;
    public static final int BACKGROUND_TILE_HEIGHT = 16;
    public static final int BACKGROUND_TILE_COUNT_X = 14;
    public static final int BACKGROUND_TILE_COUNT_Y = 7;
    int leftPos;
    int topPos;
    InteractionHand activeHand;
    List<AbstractSpell> learnableSpells;
    List<SpellNode> nodes;
    SyncedSpellData playerData;
    Vec2 maxViewportOffset;
    Vec2 viewportOffset;
    boolean isMouseHoldingSpell;
    boolean isMouseDragging;
    int heldSpellIndex = -1;
    int heldSpellTime = -1;
    int lastPlayerTick;
    static final int TIME_TO_HOLD = 15;
    private static final Component ALREADY_LEARNED;
    private static final Component UNLEARNED;

    public NewEldritchResearchScreen(Component pTitle, InteractionHand activeHand) {
        super(pTitle);
        this.activeHand = activeHand;
    }

    List<int[]> ringSegments; // 每个元素是 {startIndex, count}

    @Override
    protected void init()
    {
        switch (ResearchLayoutClientConfig.current()) {
            case CIRCLE -> {
                initRingRing();
            }
            case SPIRAL -> {
                initSpiral();
            }
            case SQUARE -> {
                initSquare();
            }
        }
    }

    protected void initRingRing() {
        // —— 兼容 irons_restrictions 的法术过滤 ——
        if (net.minecraftforge.fml.ModList.get().isLoaded("irons_restrictions")) {
            this.learnableSpells = SpellRegistry.getEnabledSpells().stream()
                    .filter(spell -> spell.getSchoolType().equals(SchoolRegistry.ELDRITCH.get()))
                    .toList();
        } else {
            this.learnableSpells = SpellRegistry.getEnabledSpells().stream()
                    .filter(spell -> !spell.isLearned((Player) null))
                    .toList();
        }

        if (this.minecraft != null) {
            this.playerData = ClientMagicData.getSyncedSpellData(this.minecraft.player);
        }

        this.viewportOffset = Vec2.ZERO;
        this.leftPos = (this.width  - WINDOW_WIDTH)  / 2;
        this.topPos  = (this.height - WINDOW_HEIGHT) / 2;

        this.nodes = new ArrayList<>();
        this.ringSegments = new ArrayList<>();

        final int n  = this.learnableSpells.size();
        final int cx = this.leftPos + WINDOW_WIDTH  / 2;
        final int cy = this.topPos  + WINDOW_HEIGHT / 2;

        // —— 同心圆参数（256×256 友好；可按需微调）——
        final int ICON    = 16;        // 图标尺寸
        final int GAP_ARC = 10;        // 同圈相邻图标沿弧间距
        final int MARGIN  = 8;         // 边缘留白
        final int R_START = 28;        // 最内圈半径
        final int R_STEP  = ICON + 10; // 圈距

        // 最大半径（避免被裁剪）：
        final float R_MAX = Math.min(WINDOW_WIDTH, WINDOW_HEIGHT) * 0.5f - ICON * 0.5f - MARGIN;

        // 预估各圈容量，直到覆盖 n 个法术
        List<Integer> ringCap = new ArrayList<>();
        List<Float>   ringR   = new ArrayList<>();
        {
            float r = R_START;
            int total = 0;
            while (r <= R_MAX && total < n) {
                int cap = Math.max(6, (int) Math.floor((2 * Math.PI * r) / (ICON + GAP_ARC)));
                ringCap.add(cap);
                ringR.add(r);
                total += cap;
                r += R_STEP;
            }
            while (total < n) { // 极端情况：再多挤几圈
                float r2 = (ringR.isEmpty() ? R_START : ringR.get(ringR.size() - 1) + R_STEP);
                int cap  = Math.max(6, (int) Math.floor((2 * Math.PI * r2) / (ICON + GAP_ARC)));
                ringCap.add(cap);
                ringR.add(r2);
                total += cap;
            }
        }

        // 按圈放置，并记录每圈的 {start,count}
        int remain = n, idx = 0;
        for (int ring = 0; ring < ringCap.size() && remain > 0; ring++) {
            float r   = ringR.get(ring);
            int take  = Math.min(remain, ringCap.get(ring));
            int start = this.nodes.size();

            // 让相邻两圈有个角度错位，避免“直上直下”太死板
            float baseAngle = (ring % 2 == 0) ? 0f : (float) Math.PI / take;
            float step      = Mth.TWO_PI / take;

            for (int k = 0; k < take; k++) {
                float theta = baseAngle + k * step;
                int x = cx - ICON / 2 + Math.round(r * Mth.cos(theta));
                int y = cy - ICON / 2 + Math.round(r * Mth.sin(theta));
                this.nodes.add(new SpellNode(this.learnableSpells.get(idx++), x, y));
                if (--remain == 0) break;
            }

            int count = this.nodes.size() - start;
            if (count > 1) this.ringSegments.add(new int[]{start, count});
        }

        // 视口范围（包围盒估算，避免 O(n^2)）
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
        for (SpellNode sn : this.nodes) {
            if (sn.x < minX) minX = sn.x;
            if (sn.y < minY) minY = sn.y;
            if (sn.x > maxX) maxX = sn.x;
            if (sn.y > maxY) maxY = sn.y;
        }
        this.maxViewportOffset = new Vec2(maxX - minX, maxY - minY);
    }
    private void handleConnectionsRingRing(GuiGraphics guiGraphics, float partialTick) {
        guiGraphics.fill(0, 0, this.width, this.height, 0);
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        LocalPlayer lp = Minecraft.getInstance().player;
        float f = (lp == null) ? 0f : Mth.sin((lp.tickCount + partialTick) * 0.1F);
        float glowIntensity = f * f;

        for (int[] seg : (this.ringSegments == null ? List.<int[]>of() : this.ringSegments)) {
            int start = seg[0];
            int count = seg[1];
            if (count < 2) continue;

            for (int i = 0; i < count; i++) {
                SpellNode na = this.nodes.get(start + i);
                SpellNode nb = this.nodes.get(start + ((i + 1) % count)); // ★ 闭合到首

                Vec2 a = new Vec2(na.x, na.y);
                Vec2 b = new Vec2(nb.x, nb.y);
                Vec2 org = (new Vec2(-(b.y - a.y), b.x - a.x)).normalized().scale(1.5F);

                double x1m1 = a.x + org.x + 8.0F + this.viewportOffset.x;
                double x2m1 = b.x + org.x + 8.0F + this.viewportOffset.x;
                double y1m1 = a.y + org.y + 8.0F + this.viewportOffset.y;
                double y2m1 = b.y + org.y + 8.0F + this.viewportOffset.y;
                double x1m2 = a.x - org.x + 8.0F + this.viewportOffset.x;
                double x2m2 = b.x - org.x + 8.0F + this.viewportOffset.x;
                double y1m2 = a.y - org.y + 8.0F + this.viewportOffset.y;
                double y2m2 = b.y - org.y + 8.0F + this.viewportOffset.y;

                Vector4f base = new Vector4f(0.5294118F, 0.6039216F, 0.68235296F, 0.5F);
                Vector4f glow = new Vector4f(0.95686275F, 0.25490198F, 1.0F, 0.5F);
                Vector4f c1 = lerpColor(base, glow, glowIntensity * (na.spell.isLearned(lp) ? 1f : 0f));
                Vector4f c2 = lerpColor(base, glow, glowIntensity * (nb.spell.isLearned(lp) ? 1f : 0f));

                buffer.vertex(x1m1, y1m1, 0.0F).color(c1.x(), c1.y(), c1.z(), fadeOutTowardEdges(guiGraphics, x1m1, y1m1)).endVertex();
                buffer.vertex(x2m1, y2m1, 0.0F).color(c2.x(), c2.y(), c2.z(), fadeOutTowardEdges(guiGraphics, x2m1, y2m1)).endVertex();
                buffer.vertex(x2m2, y2m2, 0.0F).color(c2.x(), c2.y(), c2.z(), fadeOutTowardEdges(guiGraphics, x2m2, y2m2)).endVertex();
                buffer.vertex(x1m2, y1m2, 0.0F).color(c1.x(), c1.y(), c1.z(), fadeOutTowardEdges(guiGraphics, x1m2, y1m2)).endVertex();
            }
        }

        tesselator.end();
    }



    protected void initSquare() {
        if (net.minecraftforge.fml.ModList.get().isLoaded("irons_restrictions")) {
            this.learnableSpells = SpellRegistry.getEnabledSpells().stream()
                    .filter(spell -> spell.getSchoolType().equals(SchoolRegistry.ELDRITCH.get()))
                    .toList();
        } else {
            this.learnableSpells = SpellRegistry.getEnabledSpells().stream()
                    .filter(spell -> !spell.isLearned((Player) null))
                    .toList();
        }

        this.viewportOffset = Vec2.ZERO;
        this.leftPos = (this.width - WINDOW_WIDTH) / 2;   // 256
        this.topPos  = (this.height - WINDOW_HEIGHT) / 2; // 256
        this.nodes   = new ArrayList<>();

        final int n  = this.learnableSpells.size();
        if (n == 0) {
            this.maxViewportOffset = Vec2.ZERO;
            return;
        }

        // 可视内框（不被边框裁切）：
        final int innerW = WINDOW_INSIDE_WIDTH;  // 234
        final int innerH = WINDOW_INSIDE_HEIGHT; // 229
        final int cx = this.leftPos + WINDOW_WIDTH  / 2;
        final int cy = this.topPos  + WINDOW_HEIGHT / 2;

        // 计算最少需要多少“层”能容纳 n 个点：容量 (2L+1)^2
        final int layers = (int)Math.ceil((Math.sqrt(n) - 1.0) / 2.0); // L >= 0
        // 到最外层格点的最大“格距半径”= L；映射到像素时要留出 16×16 图标半径（±8px）
        final float margin = 16f; // 给每侧保留 16px 作为图标+呼吸
        final float halfW  = innerW * 0.5f - margin;
        final float halfH  = innerH * 0.5f - margin;

        // 选一个统一的格点像素间距，使得最外层不会溢出
        float gapX = layers == 0 ? halfW : halfW / layers;
        float gapY = layers == 0 ? halfH : halfH / layers;
        // 为了保持“方阵”的均匀观感，取两轴的最小值
        float gap  = Math.max(3f, Math.min(gapX, gapY)); // 给个下限，别太拥挤；可调

        // —— 生成“矩形阵列式螺旋”格点序列 —— //
        // 起点 (0,0)，然后：右1、下1、左2、上2、右3、下3、……
        int gx = 0, gy = 0; // 当前格点坐标（以中心为 0,0）
        int stepLen = 1;    // 当前方向步数
        int dirIdx  = 0;    // 0=右,1=下,2=左,3=上
        int placed  = 0;

        // 预先把中心点放进去
        {
            int x = Math.round(cx - 8 + gx * gap);
            int y = Math.round(cy - 8 + gy * gap);
            this.nodes.add(new SpellNode(this.learnableSpells.get(placed++), x, y));
        }

        // 四向单元向量
        final int[][] DIRS = new int[][]{
                { 1, 0}, // 右
                { 0, 1}, // 下
                {-1, 0}, // 左
                { 0,-1}  // 上
        };

        // 螺旋步进直到放满 n
        while (placed < n) {
            for (int turn = 0; turn < 2 && placed < n; turn++) { // 每两次转向，步长+1
                int dx = DIRS[dirIdx][0];
                int dy = DIRS[dirIdx][1];
                for (int s = 0; s < stepLen && placed < n; s++) {
                    gx += dx; gy += dy;

                    int x = Math.round(cx - 8 + gx * gap);
                    int y = Math.round(cy - 8 + gy * gap);
                    this.nodes.add(new SpellNode(this.learnableSpells.get(placed++), x, y));
                }
                dirIdx = (dirIdx + 1) & 3; // 0→1→2→3→0
            }
            stepLen++;
        }

        // —— 估算视口偏移范围（用包围盒替代 O(n^2)）——
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
        for (SpellNode node : this.nodes) {
            if (node.x < minX) minX = node.x;
            if (node.y < minY) minY = node.y;
            if (node.x > maxX) maxX = node.x;
            if (node.y > maxY) maxY = node.y;
        }
        this.maxViewportOffset = new Vec2(maxX - minX, maxY - minY);
    }


    protected void initSpiral() {
        if(net.minecraftforge.fml.ModList.get().isLoaded("irons_restrictions"))
        {this.learnableSpells = SpellRegistry.getEnabledSpells().stream()
                .filter(spell -> spell.getSchoolType().equals(SchoolRegistry.ELDRITCH.get()))
                .toList();}
        else this.learnableSpells = SpellRegistry.getEnabledSpells().stream()
                .filter(spell -> !spell.isLearned((Player)null))
                .toList();

        this.viewportOffset = Vec2.ZERO;
        this.leftPos = (this.width - WINDOW_WIDTH) / 2;
        this.topPos  = (this.height - WINDOW_HEIGHT) / 2;

        this.nodes = new ArrayList<>();

        int n = this.learnableSpells.size();
        int cx = this.leftPos + WINDOW_WIDTH / 2;
        int cy = this.topPos  + WINDOW_HEIGHT / 2;

        float r0 = 6.0f;
        float rMax = Math.min(WINDOW_INSIDE_WIDTH, WINDOW_INSIDE_HEIGHT) * 0.5f - 16.0f;
        float thetaStep = Math.max((float)Math.PI / 10.0f, (float)(2 * Math.PI / Math.max(n, 6)));
        float thetaMax = (n > 1 ? thetaStep * (n-1) : 1f);
        float k = (rMax - r0) / Math.max(thetaMax, 1e-4f);

        for (int i=0; i<n; i++) {
            float theta = i * thetaStep;
            float r     = r0 + k * theta;
            int x = cx - 8 + Math.round(r * Mth.cos(theta));
            int y = cy - 8 + Math.round(r * Mth.sin(theta));
            this.nodes.add(new SpellNode(this.learnableSpells.get(i), x, y));
        }

        // 其它逻辑可以保持不变
    }


    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.fillGradient(0, 0, this.width, this.height, -1072689136, -804253680);
        this.drawBackdrop(this.leftPos + 9, this.topPos + 18);
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            if (player.tickCount != this.lastPlayerTick) {
                this.lastPlayerTick = player.tickCount;
                if (this.isMouseHoldingSpell && this.heldSpellIndex >= 0 && this.heldSpellIndex < this.nodes.size() && !((SpellNode)this.nodes.get(this.heldSpellIndex)).spell.isLearned(player)) {
                    if (this.heldSpellTime > 15) {
                        this.heldSpellTime = -1;
                        Messages.sendToServer(new ServerboundLearnSpell(this.activeHand, ((SpellNode)this.nodes.get(this.heldSpellIndex)).spell.getSpellId()));
                        player.playNotifySound((SoundEvent) SoundRegistry.LEARN_ELDRITCH_SPELL.get(), SoundSource.MASTER, 1.0F, (float) Utils.random.nextIntBetweenInclusive(9, 11) * 0.1F);
                    }

                    ++this.heldSpellTime;
                    if (this.lastPlayerTick % 2 == 0) {
                        player.playNotifySound(SoundEvents.SOUL_ESCAPE, SoundSource.MASTER, 1.0F, Mth.lerp((float)this.heldSpellTime / 15.0F, 0.5F, 1.5F));
                        player.playNotifySound((SoundEvent)SoundRegistry.UI_TICK.get(), SoundSource.MASTER, 1.0F, Mth.lerp((float)this.heldSpellTime / 15.0F, 0.5F, 1.5F));
                    }
                } else if (this.heldSpellTime >= 0) {
                    this.heldSpellTime = Math.max(this.heldSpellTime - 3, -1);
                }
            }

            switch (ResearchLayoutClientConfig.current()) {
                case CIRCLE -> {
                    this.handleConnectionsRingRing(guiGraphics, partialTick);
                }
                case SPIRAL, SQUARE -> {
                    this.handleConnections(guiGraphics, partialTick);
                }
            }

            List<FormattedCharSequence> tooltip = null;

            for(int i = 0; i < this.nodes.size(); ++i) {
                SpellNode node = (SpellNode)this.nodes.get(i);
                this.drawNode(guiGraphics, node, player, i == this.heldSpellIndex && this.heldSpellTime > 0);
                if (this.isHoveringNode(node, mouseX, mouseY)) {
                    tooltip = buildTooltip(node.spell, this.font);
                }
            }

            guiGraphics.blit(WINDOW_LOCATION, this.leftPos, this.topPos, 0, 0, 252, WINDOW_HEIGHT);
            if (tooltip != null) {
                guiGraphics.renderTooltip(this.minecraft.font, tooltip, mouseX, mouseY);
            }

        }
    }

    private void renderProgressOverlay(int x, int y, float progress) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder bufferbuilder = tesselator.getBuilder();
        this.fillRect(bufferbuilder, x, y, Mth.ceil(16.0F * progress), 16, 244, 65, 255, 127);
    }

    private void fillRect(BufferBuilder pRenderer, int pX, int pY, int pWidth, int pHeight, int pRed, int pGreen, int pBlue, int pAlpha) {
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        pRenderer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        pRenderer.vertex((double)(pX + 0), (double)(pY + 0), (double)0.0F).color(pRed, pGreen, pBlue, pAlpha).endVertex();
        pRenderer.vertex((double)(pX + 0), (double)(pY + pHeight), (double)0.0F).color(pRed, pGreen, pBlue, pAlpha).endVertex();
        pRenderer.vertex((double)(pX + pWidth), (double)(pY + pHeight), (double)0.0F).color(pRed, pGreen, pBlue, pAlpha).endVertex();
        pRenderer.vertex((double)(pX + pWidth), (double)(pY + 0), (double)0.0F).color(pRed, pGreen, pBlue, pAlpha).endVertex();
        BufferUploader.drawWithShader(pRenderer.end());
    }

    private void drawNode(GuiGraphics guiGraphics, SpellNode node, LocalPlayer player, boolean drawProgress) {
        this.drawWithClipping(node.spell.getSpellIconResource(), guiGraphics, node.x, node.y, 0, 0, 16, 16, 16, 16, this.leftPos + 9, this.topPos + 18, 234, WINDOW_INSIDE_HEIGHT);
        if (drawProgress) {
            this.renderProgressOverlay(node.x, node.y, (float)this.heldSpellTime / 15.0F);
        }

        this.drawWithClipping(FRAME_LOCATION, guiGraphics, node.x - 8, node.y - 8, node.spell.isLearned(player) ? 32 : 0, 0, 32, 32, 64, 32, this.leftPos + 9, this.topPos + 18, 234, WINDOW_INSIDE_HEIGHT);
    }

    private void drawWithClipping(ResourceLocation texture, GuiGraphics guiGraphics, int x, int y, int uvx, int uvy, int width, int height, int imageWidth, int imageHeight, int bbx, int bby, int bbw, int bbh) {
        x = (int)((float)x + this.viewportOffset.x);
        if (x < bbx) {
            int xDiff = bbx - x;
            width -= xDiff;
            uvx += xDiff;
            x += xDiff;
        } else if (x > bbx + bbw - width) {
            int xDiff = x - (bbx + bbw - width);
            width -= xDiff;
        }

        y = (int)((float)y + this.viewportOffset.y);
        if (y < bby) {
            int yDiff = bby - y;
            height -= yDiff;
            uvy += yDiff;
            y += yDiff;
        } else if (y > bby + bbh - height) {
            int yDiff = y - (bby + bbh - height);
            height -= yDiff;
        }

        if (width > 0 && height > 0) {
            guiGraphics.blit(texture, x, y, width, height, (float)uvx, (float)uvy, width, height, imageWidth, imageHeight);
        }

    }

    public static List<FormattedCharSequence> buildTooltip(AbstractSpell spell, Font font) {
        boolean learned = spell.isLearned(Minecraft.getInstance().player);
        MutableComponent name = spell.getDisplayName((Player)null).withStyle(learned ? ChatFormatting.DARK_AQUA : ChatFormatting.RED);
        List<FormattedCharSequence> description = font.split(Component.translatable(String.format("%s.guide", spell.getComponentId())).withStyle(ChatFormatting.GRAY), 180);
        ArrayList<FormattedCharSequence> hoverText = new ArrayList();
        hoverText.add(FormattedCharSequence.forward(name.getString(), name.getStyle().withUnderlined(true)));
        hoverText.addAll(description);
        hoverText.add(FormattedCharSequence.EMPTY);
        hoverText.add((learned ? ALREADY_LEARNED : UNLEARNED).getVisualOrderText());
        return hoverText;
    }

    private void handleConnections(GuiGraphics guiGraphics, float partialTick) {
        guiGraphics.fill(0, 0, this.width, this.height, 0);
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        for(int i = 0; i < this.nodes.size() - 1; ++i) {
            Vec2 a = new Vec2((float)(( SpellNode)this.nodes.get(i)).x, (float)(( SpellNode)this.nodes.get(i)).y);
            Vec2 b = new Vec2((float)(( SpellNode)this.nodes.get(i + 1)).x, (float)(( SpellNode)this.nodes.get(i + 1)).y);
            Vec2 org = (new Vec2(-(b.y - a.y), b.x - a.x)).normalized().scale(1.5F);
            double x1m1 = (double)(a.x + org.x + 8.0F + this.viewportOffset.x);
            double x2m1 = (double)(b.x + org.x + 8.0F + this.viewportOffset.x);
            double y1m1 = (double)(a.y + org.y + 8.0F + this.viewportOffset.y);
            double y2m1 = (double)(b.y + org.y + 8.0F + this.viewportOffset.y);
            double x1m2 = (double)(a.x - org.x + 8.0F + this.viewportOffset.x);
            double x2m2 = (double)(b.x - org.x + 8.0F + this.viewportOffset.x);
            double y1m2 = (double)(a.y - org.y + 8.0F + this.viewportOffset.y);
            double y2m2 = (double)(b.y - org.y + 8.0F + this.viewportOffset.y);
            float f = Mth.sin(((float)Minecraft.getInstance().player.tickCount + partialTick) * 0.1F);
            float glowIntensity = f * f;
            Vector4f color = new Vector4f(0.5294118F, 0.6039216F, 0.68235296F, 0.5F);
            Vector4f glowcolor = new Vector4f(0.95686275F, 0.25490198F, 1.0F, 0.5F);
            Vector4f color1 = lerpColor(color, glowcolor, glowIntensity * (float)((( SpellNode)this.nodes.get(i)).spell.isLearned(Minecraft.getInstance().player) ? 1 : 0));
            Vector4f color2 = lerpColor(color, glowcolor, glowIntensity * (float)((( SpellNode)this.nodes.get(i + 1)).spell.isLearned(Minecraft.getInstance().player) ? 1 : 0));
            double alphaTopLeft = Mth.clamp(x1m1 + (double)this.viewportOffset.x - (double)this.leftPos, (double)0.0F, (double)18.0F) / (double)9.0F * (double)2.0F * Mth.clamp(y1m1 + (double)this.viewportOffset.y - (double)this.topPos, (double)0.0F, (double)36.0F) / (double)18.0F * (double)2.0F;
            buffer.vertex(x1m1, y1m1, (double)0.0F).color(color1.x(), color1.y(), color1.z(), this.fadeOutTowardEdges(guiGraphics, x1m1, y1m1)).endVertex();
            buffer.vertex(x2m1, y2m1, (double)0.0F).color(color2.x(), color2.y(), color2.z(), this.fadeOutTowardEdges(guiGraphics, x2m1, y2m1)).endVertex();
            buffer.vertex(x2m2, y2m2, (double)0.0F).color(color2.x(), color2.y(), color2.z(), this.fadeOutTowardEdges(guiGraphics, x2m2, y2m2)).endVertex();
            buffer.vertex(x1m2, y1m2, (double)0.0F).color(color1.x(), color1.y(), color1.z(), this.fadeOutTowardEdges(guiGraphics, x1m2, y1m2)).endVertex();
        }

        tesselator.end();
    }

    private float fadeOutTowardEdges(GuiGraphics guiGraphics, double x, double y) {
        int px = (int)Mth.clamp(x + (double)this.viewportOffset.x - (double)this.leftPos, (double)0.0F, (double)18.0F);
        int py = (int)Mth.clamp(y + (double)this.viewportOffset.y - (double)this.topPos, (double)0.0F, (double)36.0F);
        int px2 = (int)Mth.clamp((double)234.0F - (x + (double)this.viewportOffset.x - (double)this.leftPos), (double)0.0F, (double)18.0F);
        int py2 = (int)Mth.clamp((double)WINDOW_INSIDE_HEIGHT - (y + (double)this.viewportOffset.y - (double)this.topPos), (double)0.0F, (double)36.0F);
        return Mth.clamp((float)px / 4.5F, 0.0F, 1.0F) * Mth.clamp((float)py / 9.0F, 0.0F, 1.0F) * Mth.clamp((float)px2 / 4.5F, 0.0F, 1.0F) * Mth.clamp((float)py2 / 9.0F, 0.0F, 1.0F);
    }

    private int colorFromRGBA(Vector4f rgba) {
        int r = (int)(rgba.x() * 255.0F) & 255;
        int g = (int)(rgba.y() * 255.0F) & 255;
        int b = (int)(rgba.z() * 255.0F) & 255;
        int a = (int)(rgba.w() * 255.0F) & 255;
        return (r << 24) + (g << 16) + (b << 8) + a;
    }

    private void drawBackdrop(int left, int top) {
        BufferBuilder bufferbuilder = Tesselator.getInstance().getBuilder();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getRendertypeEndPortalShader);
        RenderSystem.setShaderTexture(0, TheEndPortalRenderer.END_PORTAL_LOCATION);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        float f = Minecraft.getInstance().player != null ? (float)Minecraft.getInstance().player.tickCount * 0.086F : 0.0F;
        bufferbuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        bufferbuilder.vertex((double)((float)left), (double)((float)top + WINDOW_INSIDE_HEIGHT), (double)0.0F).uv(f, f).color(1, 1, 1, 1).endVertex();
        bufferbuilder.vertex((double)((float)left + WINDOW_INSIDE_WIDTH), (double)((float)top + WINDOW_INSIDE_HEIGHT), (double)0.0F).color(1, 1, 1, 1).endVertex();
        bufferbuilder.vertex((double)((float)left + WINDOW_INSIDE_WIDTH), (double)((float)top), (double)0.0F).color(1, 1, 1, 1).endVertex();
        bufferbuilder.vertex((double)((float)left), (double)((float)top), (double)0.0F).color(1, 1, 1, 1).endVertex();
        BufferUploader.drawWithShader(bufferbuilder.end());
        RenderSystem.disableBlend();
    }

    private static Vector4f lerpColor(Vector4f a, Vector4f b, float pDelta) {
        float f = 1.0F - pDelta;
        float x = a.x() * f + b.x() * pDelta;
        float y = a.y() * f + b.y() * pDelta;
        float z = a.z() * f + b.z() * pDelta;
        float w = a.w() * f + b.w() * pDelta;
        return new Vector4f(x, y, z, w);
    }

    public boolean mouseClicked(double pMouseX, double pMouseY, int pButton) {
        int mouseX = (int)pMouseX;
        int mouseY = (int)pMouseY;
        if (Minecraft.getInstance().player != null && Minecraft.getInstance().player.getItemInHand(this.activeHand).is((Item) ItemRegistry.ELDRITCH_PAGE.get())) {
            for(int i = 0; i < this.nodes.size(); ++i) {
                if (this.isHoveringNode(( SpellNode)this.nodes.get(i), mouseX, mouseY)) {
                    this.heldSpellIndex = i;
                    this.isMouseHoldingSpell = true;
                    break;
                }
            }
        }

        if (!this.isMouseHoldingSpell && this.isHovering(this.leftPos + 9, this.topPos + 18, 234, WINDOW_INSIDE_HEIGHT, mouseX, mouseY)) {
            this.isMouseDragging = true;
        }

        return super.mouseClicked(pMouseX, pMouseY, pButton);
    }

    public boolean isHoveringNode( SpellNode node, int mouseX, int mouseY) {
        return this.isHovering(node.x - 2 + (int)this.viewportOffset.x, node.y - 2 + (int)this.viewportOffset.y, 20, 20, mouseX, mouseY);
    }

    public boolean mouseReleased(double pMouseX, double pMouseY, int pButton) {
        this.isMouseHoldingSpell = false;
        this.isMouseDragging = false;
        return super.mouseReleased(pMouseX, pMouseY, pButton);
    }

    public boolean mouseDragged(double pMouseX, double pMouseY, int pButton, double pDragX, double pDragY) {
        if (this.isMouseDragging) {
        }

        return super.mouseDragged(pMouseX, pMouseY, pButton, pDragX, pDragY);
    }

    public boolean keyPressed(int pKeyCode, int pScanCode, int pModifiers) {
        InputConstants.Key mouseKey = InputConstants.getKey(pKeyCode, pScanCode);
        if (this.minecraft.options.keyInventory.isActiveAndMatches(mouseKey)) {
            this.onClose();
            return true;
        } else {
            return super.keyPressed(pKeyCode, pScanCode, pModifiers);
        }
    }

    public boolean isPauseScreen() {
        return false;
    }

    private boolean isHovering(int x, int y, int width, int height, int mouseX, int mouseY) {
        return mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;
    }

    static {
        ALREADY_LEARNED = Component.translatable("ui.irons_spellbooks.research_already_learned").withStyle(ChatFormatting.DARK_AQUA);
        UNLEARNED = Component.translatable("ui.irons_spellbooks.research_warning").withStyle(ChatFormatting.RED);
    }

    static record SpellNode(AbstractSpell spell, int x, int y) {
        SpellNode(AbstractSpell spell, int x, int y) {
            this.spell = spell;
            this.x = x;
            this.y = y;
        }

        public AbstractSpell spell() {
            return this.spell;
        }

        public int x() {
            return this.x;
        }

        public int y() {
            return this.y;
        }
    }

    static record NodeConnection(SpellNode node1, SpellNode node2) {
        NodeConnection(SpellNode node1, SpellNode node2) {
            this.node1 = node1;
            this.node2 = node2;
        }

        public SpellNode node1() {
            return this.node1;
        }

        public SpellNode node2() {
            return this.node2;
        }
    }
}
