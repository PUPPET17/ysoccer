package com.ygames.ysoccer.match;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.ygames.ysoccer.framework.Assets;
import com.ygames.ysoccer.framework.GLGraphics;

import static com.ygames.ysoccer.match.Const.GOAL_DEPTH;
import static com.ygames.ysoccer.match.Const.GOAL_LINE;

class GoalTopB extends Sprite {

    GoalTopB(GLGraphics glGraphics, MatchViewTransform viewTransform) {
        super(glGraphics, viewTransform);
        textureRegion = new TextureRegion(Assets.goalTopB);
        textureRegion.flip(false, true);
        x = -69;
        y = -673;
    }

    @Override
    public float getDepth() {
        return viewTransform.groundDepth(0, -GOAL_LINE - GOAL_DEPTH);
    }
}
