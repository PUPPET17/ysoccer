package com.ygames.ysoccer.match;

import com.ygames.ysoccer.framework.Assets;
import com.ygames.ysoccer.framework.Font;
import com.ygames.ysoccer.framework.GLGraphics;
import com.ygames.ysoccer.framework.Settings;

class BallSprite extends Sprite {

    Ball ball;

    BallSprite(GLGraphics glGraphics, Ball ball, MatchViewTransform viewTransform) {
        super(glGraphics, viewTransform);
        this.ball = ball;
    }

    @Override
    public void draw(int subframe) {
        FrameData d = ball.currentData;
        float viewX = viewTransform.projectX(d.x, d.y);
        float viewY = viewTransform.projectY(d.x, d.y, d.z);
        glGraphics.batch.draw(Assets.ball[d.fmx], viewX - Const.BALL_R, viewY - 2 - Const.BALL_R);

        if (Settings.showDevelopmentInfo) {
            Assets.font3.draw(
                glGraphics.batch,
                d.x + "," + d.y + "," + d.z,
                Math.round(viewX),
                Math.round(viewY + d.z + 22),
                Font.Align.CENTER
            );
        }
    }

    public float getDepth() {
        FrameData d = ball.currentData;
        return viewTransform.groundDepth(d.x, d.y);
    }
}
