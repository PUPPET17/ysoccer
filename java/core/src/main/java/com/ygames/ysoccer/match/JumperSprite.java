package com.ygames.ysoccer.match;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.ygames.ysoccer.framework.Assets;
import com.ygames.ysoccer.framework.GLGraphics;

import static com.ygames.ysoccer.match.Const.JUMPER_H;
import static com.ygames.ysoccer.match.Const.JUMPER_X;
import static com.ygames.ysoccer.match.Const.JUMPER_Y;

class JumperSprite extends Sprite {

    private final int anchorX;
    private final int anchorY;

    JumperSprite(GLGraphics glGraphics, int xSide, int ySide, MatchViewTransform viewTransform) {
        super(glGraphics, viewTransform);
        textureRegion = new TextureRegion(Assets.jumper);
        textureRegion.flip(false, true);
        anchorX = xSide * JUMPER_X;
        anchorY = ySide * JUMPER_Y;
        x = anchorX - 1;
        y = anchorY - JUMPER_H - 1;
    }

    @Override
    public void draw(int subframe) {
        glGraphics.batch.draw(
            textureRegion,
            viewTransform.projectX(anchorX, anchorY) - 1,
            viewTransform.projectY(anchorX, anchorY, 0) - JUMPER_H - 1
        );
    }

    @Override
    public float getDepth() {
        return viewTransform.groundDepth(anchorX, anchorY);
    }
}
