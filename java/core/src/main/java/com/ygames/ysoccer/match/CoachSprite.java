package com.ygames.ysoccer.match;

import com.ygames.ysoccer.framework.Assets;
import com.ygames.ysoccer.framework.GLGraphics;

class CoachSprite extends Sprite {

    private Coach coach;

    CoachSprite(GLGraphics glGraphics, Coach coach, MatchViewTransform viewTransform) {
        super(glGraphics, viewTransform);
        this.coach = coach;
    }

    @Override
    public void draw(int subframe) {
        glGraphics.batch.draw(
            Assets.coach[coach.teamIndex][coach.fmx],
            viewTransform.projectX(coach.x, coach.y) - 7,
            viewTransform.projectY(coach.x, coach.y, 0) - 25
        );
    }

    @Override
    public float getDepth() {
        return viewTransform.groundDepth(coach.x, coach.y);
    }
}
