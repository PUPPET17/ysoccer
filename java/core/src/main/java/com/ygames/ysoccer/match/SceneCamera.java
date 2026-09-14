package com.ygames.ysoccer.match;

import com.badlogic.gdx.math.Vector2;
import com.ygames.ysoccer.framework.EMath;

import static com.ygames.ysoccer.match.Const.CENTER_X;
import static com.ygames.ysoccer.match.Const.CENTER_Y;
import static com.ygames.ysoccer.match.Const.GOAL_LINE;
import static com.ygames.ysoccer.match.Const.PITCH_H;
import static com.ygames.ysoccer.match.Const.PITCH_W;
import static com.ygames.ysoccer.match.Const.SECOND;
import static com.ygames.ysoccer.match.Const.TOUCH_LINE;

public abstract class SceneCamera<SceneT extends Scene<?, ?>> {

    enum Mode {
        STILL,
        FOLLOW_BALL,
        REACH_TARGET
    }

    enum Speed {
        NORMAL,
        FAST,
        WARP
    }

    final SceneT scene;
    final Ball ball;

    Mode mode = Mode.STILL;
    Speed speed = Speed.NORMAL;

    float x; // position
    float y;

    private float width;
    private float height;

    private float dx;
    private float dy;

    private float vx;
    private float vy;

    float offsetX;
    float offsetY;

    boolean xLimited;
    boolean yLimited;

    final Vector2 target = new Vector2();
    private float targetDistance;

    public SceneCamera(SceneT scene, Ball ball) {
        this.scene = scene;
        this.ball = ball;
    }

    Mode getMode() {
        return mode;
    }

    Speed getSpeed() {
        return speed;
    }

    Vector2 getCurrentTarget() {
        Vector2 t = new Vector2();

        switch (mode) {
            case STILL:
                t.set(x - dx, y - dy);
                break;

            case FOLLOW_BALL:
                t.set(ball.x + offsetX, ball.y + offsetY);
                break;

            case REACH_TARGET:
                t.set(target);
                break;
        }

        clampTarget(t);

        return t;
    }

    private void clampTarget(Vector2 t) {
        float txMin = -CENTER_X + width / 2;
        float txMax = -CENTER_X + PITCH_W - width / 2;
        float tyMin = -CENTER_Y + height / 2;
        float tyMax = -CENTER_Y + PITCH_H - height / 2;

        if (xLimited && width < 1600) {
            txMin = -TOUCH_LINE - width / 16 + width / 2;
            txMax = +TOUCH_LINE + width / 16 - width / 2;
        }
        if (yLimited) {
            tyMin = -GOAL_LINE - height / 4 + height / 2;
            tyMax = +GOAL_LINE + height / 4 - height / 2;
        }

        t.x = EMath.clamp(t.x, txMin, txMax);
        t.y = EMath.clamp(t.y, tyMin, tyMax);
    }

    void setScreenParameters(int screenWidth, int screenHeight, int zoom, MatchViewTransform viewTransform) {
        x += width / 2;
        y += height / 2;
        float viewWidth = screenWidth / (zoom / 100.0f);
        float viewHeight = screenHeight / (zoom / 100.0f);
        width = viewTransform.worldViewportWidth(viewWidth, viewHeight);
        height = viewTransform.worldViewportHeight(viewWidth, viewHeight);
        x -= width / 2;
        y -= height / 2;
        dx = CENTER_X - width / 2;
        dy = CENTER_Y - height / 2;
    }

    /** Converts a replayed viewport origin into its current world-space center. */
    float worldCenterX(float viewportOriginX) {
        return viewportOriginX - dx;
    }

    /** Converts a replayed viewport origin into its current world-space center. */
    float worldCenterY(float viewportOriginY) {
        return viewportOriginY - dy;
    }

    float getWorldViewportWidth() {
        return width;
    }

    float getWorldViewportHeight() {
        return height;
    }

    public float getTargetDistance() {
        return targetDistance;
    }

    abstract void updateSettings();

    public void update() {
        updateSettings();

        switch (mode) {

            case STILL:
                // do nothing
                break;

            case FOLLOW_BALL:
                // "remember" last direction of movement
                if (Math.abs(ball.v * EMath.cos(ball.a)) > 1) {
                    offsetX = width / 20.0f * EMath.cos(ball.a);
                }
                if (Math.abs(ball.v * EMath.sin(ball.a)) > 1) {
                    offsetY = height / 20.0f * EMath.sin(ball.a);
                }

                // speed
                if (Math.abs(vx) > Math.abs(ball.v * EMath.cos(ball.a))) {
                    // decelerate
                    vx = (1 - 5.0f / SECOND) * vx;
                } else {
                    // accelerate
                    vx = vx + (2.5f / SECOND) * Math.signum(offsetX) * Math.abs(vx - ball.v * EMath.cos(ball.a));
                }
                if (Math.abs(vy) > Math.abs(ball.v * EMath.sin(ball.a))) {
                    // decelerate
                    vy = (1 - 5.0f / SECOND) * vy;
                } else {
                    // accelerate
                    vy = vy + (2.5f / SECOND) * Math.signum(offsetY) * Math.abs(vy - ball.v * EMath.sin(ball.a));
                }

                x = x + vx / SECOND;
                y = y + vy / SECOND;

                // near the point "ball+offset"
                if (Math.abs(ball.x + offsetX - (x - dx)) >= 10) {
                    float f = ball.x + offsetX - (x - dx);
                    x = x + (10.0f / SECOND) * (1 + speed.ordinal()) * Math.signum(f) * (float) Math.sqrt(Math.abs(f));
                }
                if (Math.abs(ball.y + offsetY - (y - dy)) >= 10) {
                    float f = ball.y + offsetY - (y - dy);
                    y = y + (10.0f / SECOND) * (1 + speed.ordinal()) * Math.signum(f) * (float) Math.sqrt(Math.abs(f));
                }
                break;

            case REACH_TARGET:
                float x0 = target.x - (x - dx);
                float y0 = target.y - (y - dy);
                float a = EMath.aTan2(y0, x0);
                targetDistance = EMath.sqrt(Math.abs(x0) + Math.abs(y0));
                if (targetDistance > 1) {
                    x += (10.0f / SECOND) * (1 + speed.ordinal()) * targetDistance * EMath.cos(a);
                    y += (10.0f / SECOND) * (1 + speed.ordinal()) * targetDistance * EMath.sin(a);
                }
                break;
        }

        // keep inside pitch
        float xMin = 0;
        float xMax = PITCH_W - width;
        float yMin = 0;
        float yMax = PITCH_H - height;

        if (xLimited && width < 1600) {
            xMin = CENTER_X - TOUCH_LINE - width / 16;
            xMax = CENTER_X + TOUCH_LINE + width / 16 - width;
        }
        if (yLimited) {
            yMin = CENTER_Y - GOAL_LINE - height / 4;
            yMax = CENTER_Y + GOAL_LINE + height / 4 - height;
        }

        if (x < xMin) {
            x = xMin;
        }

        if (x > xMax) {
            x = xMax;
        }

        if (y < yMin) {
            y = yMin;
        }

        if (y > yMax) {
            y = yMax;
        }
    }

    public String limitedToString() {
        if (xLimited && yLimited) return "XY LIMITED";
        if (xLimited) return "X LIMITED";
        if (yLimited) return "Y LIMITED";
        return "UNLIMITED";
    }

    public String targetToString() {
        return "TARGET (" + (int) target.x + ", " + (int) target.y + ")";
    }

    public String offsetToString() {
        return "OFFSET (" + (int) offsetX + ", " + (int) offsetY + ")";
    }
}
