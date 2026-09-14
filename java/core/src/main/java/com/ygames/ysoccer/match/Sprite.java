package com.ygames.ysoccer.match;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.ygames.ysoccer.framework.GLGraphics;

import java.util.Comparator;

abstract class Sprite {

    GLGraphics glGraphics;
    final MatchViewTransform viewTransform;

    TextureRegion textureRegion;
    int x;
    int y;
    int z;

    Sprite(GLGraphics glGraphics, MatchViewTransform viewTransform) {
        this.glGraphics = glGraphics;
        this.viewTransform = viewTransform;
    }

    public void draw(int subframe) {
        glGraphics.batch.draw(
            textureRegion,
            viewTransform.projectX(x, y),
            viewTransform.projectY(x, y, z)
        );
    }

    public float getDepth() {
        return viewTransform.groundDepth(x, y);
    }

    static class SpriteComparator implements Comparator<Sprite> {

        @Override
        public int compare(Sprite sprite1, Sprite sprite2) {
            return Float.compare(sprite1.getDepth(), sprite2.getDepth());
        }
    }
}
