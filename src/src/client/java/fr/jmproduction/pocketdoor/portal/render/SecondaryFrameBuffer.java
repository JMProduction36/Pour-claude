package fr.jmproduction.pocketdoor.portal.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import net.minecraft.client.Minecraft;

/**
 * Faithful port of qouteall.imm_ptl.core.render.SecondaryFrameBuffer (Immersive Portals 1.19.2, v2.3.2).
 * A TextureTarget kept at the size of the main framebuffer, used to render the pocket world
 * before its content is composited into the doorway.
 */
public class SecondaryFrameBuffer {
    public TextureTarget fb;

    public void prepare() {
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        prepare(main.viewWidth, main.viewHeight);
    }

    public void prepare(int width, int height) {
        if (fb == null) {
            fb = new TextureTarget(width, height, true, Minecraft.ON_OSX);
            fb.checkStatus();
        }
        if (width != fb.viewWidth || height != fb.viewHeight) {
            fb.resize(width, height, Minecraft.ON_OSX);
            fb.checkStatus();
        }
    }

    public void clear() {
        if (fb != null) fb.clear(true);
    }
}
