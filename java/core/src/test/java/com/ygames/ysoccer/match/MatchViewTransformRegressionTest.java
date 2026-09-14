package com.ygames.ysoccer.match;

import com.badlogic.gdx.math.Vector2;

/** Hardware- and graphics-free regression checks for local match-view projection and input mapping. */
public final class MatchViewTransformRegressionTest {
    private static final float EPSILON = 0.001f;
    private static int checks;

    /** Runs all projection checks and exits nonzero when an invariant is broken. */
    public static void main(String[] args) {
        preferenceFallback();
        verticalCompatibility();
        horizontalProjection();
        inputMapping();
        directionFrames();
        framingAcrossAspectRatios();
        cameraBoundaries();
        System.out.println("Match-view transform regression checks passed: " + checks);
    }

    private static void preferenceFallback() {
        check(MatchViewMode.fromPreference(null) == MatchViewMode.VERTICAL, "missing preference is vertical");
        check(MatchViewMode.fromPreference("obsolete") == MatchViewMode.VERTICAL, "invalid preference is vertical");
        check(MatchViewMode.fromPreference("HORIZONTAL_2_5D") == MatchViewMode.HORIZONTAL_2_5D,
            "horizontal preference round trip");
    }

    private static void verticalCompatibility() {
        MatchViewTransform transform = new MatchViewTransform(MatchViewMode.VERTICAL);
        close(transform.projectX(123, -456), 123, "vertical x is unchanged");
        close(transform.projectY(123, -456, 20), -476, "vertical height uses original offset");
        close(transform.groundDepth(123, -456), -456, "vertical depth is world y");
        close(transform.worldViewportWidth(800, 600), 800, "vertical viewport width");
        close(transform.worldViewportHeight(800, 600), 600, "vertical viewport height");
    }

    private static void horizontalProjection() {
        MatchViewTransform transform = new MatchViewTransform(MatchViewMode.HORIZONTAL_2_5D);
        close(transform.projectX(100, 250), 250, "world y becomes horizontal view position");
        close(transform.projectY(100, 250, 20), 45, "world x supplies compressed depth and height");
        close(transform.groundDepth(-100, 250), -65, "horizontal painter depth");
        close(transform.worldViewportWidth(1280, 720), 720 / 0.65f, "horizontal viewport swaps depth");
        close(transform.worldViewportHeight(1280, 720), 1280, "horizontal viewport swaps length");
    }

    private static void inputMapping() {
        MatchViewTransform vertical = new MatchViewTransform(MatchViewMode.VERTICAL);
        MatchViewTransform horizontal = new MatchViewTransform(MatchViewMode.HORIZONTAL_2_5D);
        check(vertical.worldInputX(1, -1) == 1 && vertical.worldInputY(1, -1) == -1,
            "vertical input is unchanged");
        check(horizontal.worldInputX(1, 0) == 0 && horizontal.worldInputY(1, 0) == 1,
            "screen right maps to world positive y");
        check(horizontal.worldInputX(0, 1) == 1 && horizontal.worldInputY(0, 1) == 0,
            "screen down maps to world positive x");
        check(horizontal.worldInputAngle(1, 0) == 90, "horizontal right angle");
        check(horizontal.worldInputAngle(0, 1) == 0, "horizontal down angle");
    }

    private static void directionFrames() {
        MatchViewTransform vertical = new MatchViewTransform(MatchViewMode.VERTICAL);
        MatchViewTransform horizontal = new MatchViewTransform(MatchViewMode.HORIZONTAL_2_5D);
        for (int frame = 0; frame < 8; frame++) {
            check(vertical.projectDirectionFrame(frame) == frame, "vertical direction frame " + frame);
        }
        int[] projected = {2, 1, 0, 7, 6, 5, 4, 3};
        for (int frame = 0; frame < projected.length; frame++) {
            check(horizontal.projectDirectionFrame(frame) == projected[frame], "horizontal direction frame " + frame);
        }
    }

    private static void framingAcrossAspectRatios() {
        MatchViewTransform horizontal = new MatchViewTransform(MatchViewMode.HORIZONTAL_2_5D);
        int[][] resolutions = {{1280, 720}, {1920, 1080}, {1024, 768}};
        for (int[] resolution : resolutions) {
            float scale = horizontal.fitScale(resolution[0], resolution[1], 0.75f);
            float visibleLength = resolution[0] / scale;
            float visibleDepth = resolution[1] / scale;
            check(visibleLength + EPSILON >= 0.75f * 2 * Const.GOAL_LINE,
                "balanced length framing at " + resolution[0] + "x" + resolution[1]);
            check(visibleDepth + EPSILON >= 0.75f * 2 * Const.TOUCH_LINE
                    * MatchViewTransform.HORIZONTAL_DEPTH_SCALE,
                "balanced depth framing at " + resolution[0] + "x" + resolution[1]);
        }
    }

    private static void cameraBoundaries() {
        MatchViewTransform horizontal = new MatchViewTransform(MatchViewMode.HORIZONTAL_2_5D);
        SceneCamera<Match> camera = new SceneCamera<Match>(null, null) {
            @Override
            void updateSettings() {
                // Bounds are exercised directly; no scene state is needed by this regression.
            }
        };
        camera.setScreenParameters(1280, 720, 100, horizontal);
        camera.xLimited = true;
        camera.yLimited = true;

        camera.x = 1_000_000;
        camera.y = 1_000_000;
        Vector2 maximum = camera.getCurrentTarget();
        close(maximum.x,
            Const.TOUCH_LINE + camera.getWorldViewportWidth() / 16f - camera.getWorldViewportWidth() / 2f,
            "horizontal camera maximum world x");
        close(maximum.y,
            Const.GOAL_LINE + camera.getWorldViewportHeight() / 4f - camera.getWorldViewportHeight() / 2f,
            "horizontal camera maximum world y");

        camera.x = -1_000_000;
        camera.y = -1_000_000;
        Vector2 minimum = camera.getCurrentTarget();
        close(minimum.x,
            -Const.TOUCH_LINE - camera.getWorldViewportWidth() / 16f + camera.getWorldViewportWidth() / 2f,
            "horizontal camera minimum world x");
        close(minimum.y,
            -Const.GOAL_LINE - camera.getWorldViewportHeight() / 4f + camera.getWorldViewportHeight() / 2f,
            "horizontal camera minimum world y");
    }

    private static void close(float actual, float expected, String message) {
        check(Math.abs(actual - expected) <= EPSILON, message + ": expected " + expected + ", got " + actual);
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
