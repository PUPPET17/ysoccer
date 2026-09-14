package com.ygames.ysoccer.match;

import com.badlogic.gdx.math.Matrix4;
import com.ygames.ysoccer.framework.EMath;

/**
 * Converts stable match-world coordinates into the selected local presentation.
 *
 * <p>The transform owns reusable matrices and exposes scalar operations so render loops can project
 * entities, shadows and debug geometry without allocating temporary vectors every frame.</p>
 */
public final class MatchViewTransform {

    /** Amount of screen depth retained across the pitch in the horizontal prototype. */
    public static final float HORIZONTAL_DEPTH_SCALE = 0.65f;

    private final MatchViewMode mode;
    private final Matrix4 groundMatrix = new Matrix4();
    private final Matrix4 identityMatrix = new Matrix4();

    public MatchViewTransform(MatchViewMode mode) {
        this.mode = mode == null ? MatchViewMode.VERTICAL : mode;
        configureGroundMatrix();
    }

    public MatchViewMode getMode() {
        return mode;
    }

    public boolean isHorizontal() {
        return mode == MatchViewMode.HORIZONTAL_2_5D;
    }

    /** Projects a world-space ground position onto the horizontal screen axis. */
    public float projectX(float worldX, float worldY) {
        return isHorizontal() ? worldY : worldX;
    }

    /** Projects a world-space position and its height onto the vertical screen axis. */
    public float projectY(float worldX, float worldY, float height) {
        return groundDepth(worldX, worldY) - height;
    }

    /** Returns the screen-space ground depth used for painter-order sorting. */
    public float groundDepth(float worldX, float worldY) {
        return isHorizontal() ? HORIZONTAL_DEPTH_SCALE * worldX : worldY;
    }

    /** Converts the width of the screen-space camera into the corresponding world-X span. */
    public float worldViewportWidth(float viewWidth, float viewHeight) {
        return isHorizontal() ? viewHeight / HORIZONTAL_DEPTH_SCALE : viewWidth;
    }

    /** Converts the height of the screen-space camera into the corresponding world-Y span. */
    public float worldViewportHeight(float viewWidth, float viewHeight) {
        return isHorizontal() ? viewWidth : viewHeight;
    }

    /**
     * Calculates the camera scale required to retain at least the requested playable-pitch fraction.
     * Horizontal mode constrains both the touch-line depth and goal-line length; vertical mode retains
     * the original width-led framing.
     */
    float fitScale(float screenWidth, float screenHeight, float visibleFraction) {
        if (!isHorizontal()) {
            return screenWidth / (visibleFraction * 2 * Const.TOUCH_LINE);
        }
        float projectedFieldDepth = 2 * Const.TOUCH_LINE * HORIZONTAL_DEPTH_SCALE;
        return Math.min(
            screenWidth / (visibleFraction * 2 * Const.GOAL_LINE),
            screenHeight / (visibleFraction * projectedFieldDepth)
        );
    }

    /** Maps a raw screen-horizontal direction to the simulation's world-X axis. */
    public int worldInputX(int screenX, int screenY) {
        return isHorizontal() ? screenY : screenX;
    }

    /** Maps a raw screen-vertical direction to the simulation's world-Y axis. */
    public int worldInputY(int screenX, int screenY) {
        return isHorizontal() ? screenX : screenY;
    }

    /** Returns the simulation angle represented by a screen-relative digital direction. */
    public int worldInputAngle(int screenX, int screenY) {
        return Math.round(EMath.aTan2(worldInputY(screenX, screenY), worldInputX(screenX, screenY)));
    }

    /**
     * Chooses the existing eight-direction sprite frame that visually follows a projected heading.
     *
     * @param worldFrame original direction frame in the range 0..7
     * @return projected direction frame in the range 0..7
     */
    public int projectDirectionFrame(int worldFrame) {
        int normalizedFrame = ((worldFrame % 8) + 8) % 8;
        if (!isHorizontal()) {
            return normalizedFrame;
        }
        float worldAngle = 45f * normalizedFrame;
        float viewDx = EMath.sin(worldAngle);
        float viewDy = HORIZONTAL_DEPTH_SCALE * EMath.cos(worldAngle);
        return Math.round(((EMath.aTan2(viewDy, viewDx) + 360f) % 360f) / 45f) % 8;
    }

    /** Matrix used only while drawing world-aligned ground artwork. */
    Matrix4 getGroundMatrix() {
        return groundMatrix;
    }

    /** Identity matrix used to restore upright sprite drawing after ground artwork. */
    Matrix4 getIdentityMatrix() {
        return identityMatrix;
    }

    private void configureGroundMatrix() {
        groundMatrix.idt();
        identityMatrix.idt();
        if (!isHorizontal()) {
            return;
        }

        // x' = worldY, y' = depthScale * worldX. The reflection keeps the existing far-side bench
        // at the top of the broadcast view while the original texture remains reusable.
        groundMatrix.val[Matrix4.M00] = 0f;
        groundMatrix.val[Matrix4.M01] = 1f;
        groundMatrix.val[Matrix4.M10] = HORIZONTAL_DEPTH_SCALE;
        groundMatrix.val[Matrix4.M11] = 0f;
    }
}
