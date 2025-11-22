package com.rinko1231.vwa.gui;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import com.rinko1231.vwa.config.EldritchNodePositionsConfig;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.capabilities.magic.SyncedSpellData;

import io.redspace.ironsspellbooks.network.spells.LearnSpellPacket;
import io.redspace.ironsspellbooks.player.ClientMagicData;
import io.redspace.ironsspellbooks.registries.ItemRegistry;
import io.redspace.ironsspellbooks.registries.SoundRegistry;
import io.redspace.ironsspellbooks.render.RenderHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.FastColor;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec2;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Vector4f;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

@OnlyIn(Dist.CLIENT)
public class EldritchResearchScreenNew extends Screen {
        private static final ResourceLocation WINDOW_LOCATION = ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "textures/gui/eldritch_research_screen/window.png");
        private static final ResourceLocation FRAME_LOCATION = ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "textures/gui/eldritch_research_screen/spell_frame.png");
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
        List<EldritchResearchScreenNew.SpellNode> nodes;
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

    boolean isDraggingNode = false;
    int draggingNodeIndex = -1;
    int lastMouseX, lastMouseY;


        public EldritchResearchScreenNew(Component pTitle, InteractionHand activeHand) {
            super(pTitle);
            this.activeHand = activeHand;
        }

        protected void init() {
            this.learnableSpells = SpellRegistry.getEnabledSpells().stream().filter((spell) -> spell.getSchoolType().equals(SchoolRegistry.ELDRITCH.get())).toList();
            if (this.minecraft != null) {
                this.playerData = ClientMagicData.getSyncedSpellData(this.minecraft.player);
            }

            this.viewportOffset = Vec2.ZERO;
            this.leftPos = (this.width - WINDOW_WIDTH) / 2;
            this.topPos = (this.height - WINDOW_HEIGHT) / 2;
            this.nodes = new ArrayList();
            RandomSource randomSource = RandomSource.create(431L);
            float f = ((float)Math.PI / 3F);
            float r = 35.0F;
            float circumference = 0.0F;
            float aOffset = 0.5F;

            for (AbstractSpell spell : this.learnableSpells) {
                String id = spell.getSpellId();

                // 优先读取保存的坐标
                EldritchNodePositionsConfig.NodePos saved = EldritchNodePositionsConfig.get(id);
                if (saved != null) {
                    nodes.add(new SpellNode(spell, saved.x, saved.y));
                    continue;
                }

                // 否则走原本的自动排布
                if (circumference > r * (Math.PI * 2F)) {
                    r += 40;
                    f = 35F / r;
                    aOffset -= f;
                    circumference = 0;
                }

                aOffset += f;
                int x = this.leftPos + 126 - 8 + (int) (r * Mth.cos(aOffset));
                int y = this.topPos + 128 - 8 + (int) (r * Mth.sin(aOffset));

                nodes.add(new SpellNode(spell, x, y));
                circumference += r * f * 1.1F;
            }


            float maxDistX = 0.0F;
            float maxDistY = 0.0F;

            for(int i = 0; i < this.nodes.size(); ++i) {
                for(int j = 1; j < this.nodes.size(); ++j) {
                    int x = Math.abs(((EldritchResearchScreenNew.SpellNode)this.nodes.get(i)).x - ((EldritchResearchScreenNew.SpellNode)this.nodes.get(j)).x);
                    if ((float)x > maxDistX) {
                        maxDistX = (float)x;
                    }

                    int y = Math.abs(((EldritchResearchScreenNew.SpellNode)this.nodes.get(i)).y - ((EldritchResearchScreenNew.SpellNode)this.nodes.get(j)).y);
                    if ((float)y > maxDistY) {
                        maxDistY = (float)y;
                    }
                }
            }

            this.maxViewportOffset = new Vec2((float)((int)maxDistX), (float)((int)maxDistY));
        }

        public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            super.render(guiGraphics, mouseX, mouseY, partialTick);
            guiGraphics.fillGradient(0, 0, this.width, this.height, -1072689136, -804253680);
            this.drawBackdrop(guiGraphics, this.leftPos + WINDOW_INSIDE_X, this.topPos + WINDOW_INSIDE_Y);
            LocalPlayer player = Minecraft.getInstance().player;
            if (player != null) {
                if (player.tickCount != this.lastPlayerTick) {
                    this.lastPlayerTick = player.tickCount;
                    if (this.isMouseHoldingSpell && this.heldSpellIndex >= 0 && this.heldSpellIndex < this.nodes.size() && !((EldritchResearchScreenNew.SpellNode)this.nodes.get(this.heldSpellIndex)).spell.isLearned(player)) {
                        if (this.heldSpellTime > TIME_TO_HOLD) {
                            this.heldSpellTime = -1;
                            PacketDistributor.sendToServer(new LearnSpellPacket(this.activeHand, ((EldritchResearchScreenNew.SpellNode)this.nodes.get(this.heldSpellIndex)).spell.getSpellId()), new CustomPacketPayload[0]);
                            player.playNotifySound((SoundEvent) SoundRegistry.LEARN_ELDRITCH_SPELL.get(), SoundSource.MASTER, 1.0F, (float) Utils.random.nextIntBetweenInclusive(9, 11) * 0.1F);
                        }

                        ++this.heldSpellTime;
                        if (this.lastPlayerTick % 2 == 0) {
                            player.playNotifySound((SoundEvent) SoundEvents.SOUL_ESCAPE.value(), SoundSource.MASTER, 1.0F, Mth.lerp((float)this.heldSpellTime / 15.0F, 0.5F, 1.5F));
                            player.playNotifySound((SoundEvent)SoundRegistry.UI_TICK.get(), SoundSource.MASTER, 1.0F, Mth.lerp((float)this.heldSpellTime / 15.0F, 0.5F, 1.5F));
                        }
                    } else if (this.heldSpellTime >= 0) {
                        this.heldSpellTime = Math.max(this.heldSpellTime - 3, -1);
                    }
                }

                this.handleConnections(guiGraphics, partialTick);
                List<FormattedCharSequence> tooltip = null;

                for(int i = 0; i < this.nodes.size(); ++i) {
                    EldritchResearchScreenNew.SpellNode node = (EldritchResearchScreenNew.SpellNode)this.nodes.get(i);
                    this.drawNode(guiGraphics, node, player, i == this.heldSpellIndex && this.heldSpellTime > 0);
                    if (this.isHoveringNode(node, mouseX, mouseY)) {
                        tooltip = buildTooltip(node.spell, this.font);
                    }
                }

                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
                guiGraphics.blit(WINDOW_LOCATION, this.leftPos, this.topPos, 0, 0, WINDOW_WIDTH, WINDOW_HEIGHT);
                if (tooltip != null) {
                    guiGraphics.renderTooltip(Minecraft.getInstance().font, tooltip, mouseX, mouseY);
                }

            }
        }

        private void renderProgressOverlay(GuiGraphics gui, int x, int y, float progress) {
            x += (int)this.viewportOffset.x;
            y += (int)this.viewportOffset.y;
            gui.fill(x, y, x + Mth.ceil(16.0F * progress), y + 16, FastColor.ARGB32.color(127, 244, 65, 255));
        }

        private void drawNode(GuiGraphics guiGraphics, EldritchResearchScreenNew.SpellNode node, LocalPlayer player, boolean drawProgress) {
            this.drawWithClipping(node.spell.getSpellIconResource(), guiGraphics, node.x, node.y, 0, 0, BACKGROUND_TILE_WIDTH, BACKGROUND_TILE_HEIGHT, BACKGROUND_TILE_WIDTH, BACKGROUND_TILE_HEIGHT, this.leftPos + WINDOW_INSIDE_X, this.topPos + WINDOW_INSIDE_Y, WINDOW_INSIDE_WIDTH, WINDOW_INSIDE_HEIGHT);
            if (drawProgress) {
                this.renderProgressOverlay(guiGraphics, node.x, node.y, (float)this.heldSpellTime / 15.0F);
            }

            this.drawWithClipping(FRAME_LOCATION, guiGraphics, node.x - 8, node.y - 8, node.spell.isLearned(player) ? 32 : 0, 0, 32, 32, 64, 32, this.leftPos + WINDOW_INSIDE_X, this.topPos + WINDOW_INSIDE_Y, WINDOW_INSIDE_WIDTH, WINDOW_INSIDE_HEIGHT);
        }

        private void drawWithClipping(ResourceLocation texture, GuiGraphics guiGraphics, int x, int y, int uvx, int uvy, int width, int height, int imageWidth, int imageHeight, int bbx, int bby, int bbw, int bbh) {
            x += (int)this.viewportOffset.x;
            if (x < bbx) {
                int xDiff = bbx - x;
                width -= xDiff;
                uvx += xDiff;
                x += xDiff;
            } else if (x > bbx + bbw - width) {
                int xDiff = x - (bbx + bbw - width);
                width -= xDiff;
            }

            y += (int)this.viewportOffset.y;
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
            RenderSystem.enableDepthTest();
            float f = Mth.sin(((float)Minecraft.getInstance().player.tickCount + partialTick) * 0.1F);
            float glowIntensity = f * f * 0.8F + 0.2F;
            Vector4f color = new Vector4f(0.5294118F, 0.6039216F, 0.68235296F, 0.5F);
            Vector4f glowcolor = new Vector4f(0.95686275F, 0.25490198F, 1.0F, 0.5F);

            for(int i = 0; i < this.nodes.size() - 1; ++i) {
                Vec2 a = new Vec2((float)((EldritchResearchScreenNew.SpellNode)this.nodes.get(i)).x, (float)((EldritchResearchScreenNew.SpellNode)this.nodes.get(i)).y);
                Vec2 b = new Vec2((float)((EldritchResearchScreenNew.SpellNode)this.nodes.get(i + 1)).x, (float)((EldritchResearchScreenNew.SpellNode)this.nodes.get(i + 1)).y);
                Vec2 orth = (new Vec2(-(b.y - a.y), b.x - a.x)).normalized().scale(1.5F);
                float x1m1 = a.x + orth.x + 8.0F + (float)((int)this.viewportOffset.x);
                float x2m1 = b.x + orth.x + 8.0F + (float)((int)this.viewportOffset.x);
                float y1m1 = a.y + orth.y + 8.0F + (float)((int)this.viewportOffset.y);
                float y2m1 = b.y + orth.y + 8.0F + (float)((int)this.viewportOffset.y);
                float x1m2 = a.x - orth.x + 8.0F + (float)((int)this.viewportOffset.x);
                float x2m2 = b.x - orth.x + 8.0F + (float)((int)this.viewportOffset.x);
                float y1m2 = a.y - orth.y + 8.0F + (float)((int)this.viewportOffset.y);
                float y2m2 = b.y - orth.y + 8.0F + (float)((int)this.viewportOffset.y);
                Vector4f color1 = lerpColor(color, glowcolor, glowIntensity * (float)(((EldritchResearchScreenNew.SpellNode)this.nodes.get(i)).spell.isLearned(Minecraft.getInstance().player) ? 1 : 0));
                Vector4f color2 = lerpColor(color, glowcolor, glowIntensity * (float)(((EldritchResearchScreenNew.SpellNode)this.nodes.get(i + 1)).spell.isLearned(Minecraft.getInstance().player) ? 1 : 0));
                RenderHelper.quadBuilder().vertex(x1m1, y1m1).color(this.fadeOutTowardEdges(guiGraphics, (double)x1m1, (double)y1m1, color1)).vertex(x2m1, y2m1).color(this.fadeOutTowardEdges(guiGraphics, (double)x2m1, (double)y2m1, color2)).vertex(x2m2, y2m2).color(this.fadeOutTowardEdges(guiGraphics, (double)x2m2, (double)y2m2, color2)).vertex(x1m2, y1m2).color(this.fadeOutTowardEdges(guiGraphics, (double)x1m2, (double)y1m2, color1)).build(guiGraphics, RenderType.gui());
            }

        }

        private Vector4f fadeOutTowardEdges(GuiGraphics guiGraphics, double x, double y, Vector4f color) {
            float margin = 40.0F;
            int maxWidth = WINDOW_WIDTH;
            int maxHeight = WINDOW_HEIGHT;
            int boundXMin = (int)Mth.clamp(x + (double)this.viewportOffset.x - (double)this.leftPos, (double)0.0F, (double)maxWidth);
            int boundXMax = maxWidth - (int)Mth.clamp(x + (double)this.viewportOffset.x - (double)this.leftPos, (double)0.0F, (double)maxWidth);
            int boundYMin = (int)Mth.clamp(y + (double)this.viewportOffset.y - (double)this.topPos, (double)0.0F, (double)maxHeight);
            int boundYMax = maxHeight - (int)Mth.clamp(y + (double)this.viewportOffset.y - (double)this.topPos, (double)0.0F, (double)maxHeight);
            float px = Mth.clamp((float)Math.min(boundXMin, boundXMax) / margin, 0.0F, 1.0F);
            float py = Mth.clamp((float)Math.min(boundYMin, boundYMax) / margin, 0.0F, 1.0F);
            float alpha = Mth.sqrt(px * py);
            return new Vector4f(color.x, color.y, color.z, color.w * alpha);
        }

        private int colorFromRGBA(Vector4f rgba) {
            int r = (int)(rgba.x() * 255.0F) & 255;
            int g = (int)(rgba.y() * 255.0F) & 255;
            int b = (int)(rgba.z() * 255.0F) & 255;
            int a = (int)(rgba.w() * 255.0F) & 255;
            return (r << 24) + (g << 16) + (b << 8) + a;
        }

        private void drawBackdrop(GuiGraphics guiGraphics, int left, int top) {
            float f = Minecraft.getInstance().player != null ? (float)Minecraft.getInstance().player.tickCount * 0.02F : 0.0F;
            float color = (Mth.sin(f) + 1.0F) * 0.25F + 0.15F;
            RenderHelper.QuadBuilder definitelynothowabuilderworks = RenderHelper.quadBuilder().vertex((float)left, (float)(top + WINDOW_INSIDE_HEIGHT)).vertex((float)(left + WINDOW_INSIDE_WIDTH), (float)(top + WINDOW_INSIDE_HEIGHT)).vertex((float)(left + WINDOW_INSIDE_WIDTH), (float)top).vertex((float)left, (float)top).color(0.0F, 0.0F, 0.0F, color);
            definitelynothowabuilderworks.build(guiGraphics, RenderType.endPortal());
            definitelynothowabuilderworks.build(guiGraphics, RenderType.guiOverlay());
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

        boolean altDown = InputConstants.isKeyDown(Minecraft.getInstance().getWindow().getWindow(), GLFW.GLFW_KEY_LEFT_ALT)
                || InputConstants.isKeyDown(Minecraft.getInstance().getWindow().getWindow(), GLFW.GLFW_KEY_RIGHT_ALT);

        // 拖动不学习
        if (altDown && pButton == 0) {
            for (int i = 0; i < nodes.size(); i++) {
                if (isHoveringNode(nodes.get(i), mouseX, mouseY)) {
                    draggingNodeIndex = i;
                    isDraggingNode = true;
                    lastMouseX = mouseX;
                    lastMouseY = mouseY;
                    return true;
                }
            }
        }

        // 原学习逻辑
        if (!altDown && pButton == 0 && Minecraft.getInstance().player != null &&
                Minecraft.getInstance().player.getItemInHand(activeHand).is(ItemRegistry.ELDRITCH_PAGE.get())) {

            for(int i = 0; i < this.nodes.size(); ++i) {
                if (this.isHoveringNode(this.nodes.get(i), mouseX, mouseY)) {
                    this.heldSpellIndex = i;
                    this.isMouseHoldingSpell = true;
                    break;
                }
            }
        }

        //原有拖动逻辑
        if (!this.isMouseHoldingSpell && this.isHovering(this.leftPos + WINDOW_INSIDE_X, this.topPos + WINDOW_INSIDE_Y, WINDOW_INSIDE_WIDTH, WINDOW_INSIDE_HEIGHT, mouseX, mouseY)) {
            this.isMouseDragging = true;
        }

        return super.mouseClicked(pMouseX, pMouseY, pButton);
    }

        public boolean isHoveringNode(EldritchResearchScreenNew.SpellNode node, int mouseX, int mouseY) {
            return this.isHovering(node.x - 2 + (int)this.viewportOffset.x, node.y - 2 + (int)this.viewportOffset.y, 20, 20, mouseX, mouseY);
        }

    public boolean mouseReleased(double pMouseX, double pMouseY, int pButton) {
        if (isDraggingNode && draggingNodeIndex >= 0) {
            SpellNode node = nodes.get(draggingNodeIndex);

            // 保存坐标
            String id = node.spell().getSpellId();
            EldritchNodePositionsConfig.set(id, node.x(), node.y());
            EldritchNodePositionsConfig.save();
        }

        isDraggingNode = false;
        draggingNodeIndex = -1;

        return super.mouseReleased(pMouseX, pMouseY, pButton);
    }

    public boolean mouseDragged(double pMouseX, double pMouseY, int pButton, double pDragX, double pDragY) {
        int mouseX = (int)pMouseX;
        int mouseY = (int)pMouseY;

        if (isDraggingNode && draggingNodeIndex >= 0) {
            SpellNode node = nodes.get(draggingNodeIndex);

            int dx = mouseX - lastMouseX;
            int dy = mouseY - lastMouseY;

            // 更新节点位置
            node = new SpellNode(node.spell(), node.x() + dx, node.y() + dy);
            nodes.set(draggingNodeIndex, node);

            lastMouseX = mouseX;
            lastMouseY = mouseY;
            return true;
        }

        // 原有拖动逻辑
        if (this.isMouseDragging) {
            Vec2 sbMojang = new Vec2((float)pDragX,(float)pDragY);
            this.viewportOffset = this.viewportOffset.add(sbMojang);
            return true;
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

        static record NodeConnection(EldritchResearchScreenNew.SpellNode node1, EldritchResearchScreenNew.SpellNode node2) {
            NodeConnection(EldritchResearchScreenNew.SpellNode node1, EldritchResearchScreenNew.SpellNode node2) {
                this.node1 = node1;
                this.node2 = node2;
            }

            public EldritchResearchScreenNew.SpellNode node1() {
                return this.node1;
            }

            public EldritchResearchScreenNew.SpellNode node2() {
                return this.node2;
            }
        }
    }
