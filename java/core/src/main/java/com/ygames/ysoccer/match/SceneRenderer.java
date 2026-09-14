package com.ygames.ysoccer.match;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector3;
import com.ygames.ysoccer.framework.Assets;
import com.ygames.ysoccer.framework.EMath;
import com.ygames.ysoccer.framework.Font;
import com.ygames.ysoccer.framework.GLGame;
import com.ygames.ysoccer.framework.GLGraphics;
import com.ygames.ysoccer.framework.GLShapeRenderer;
import com.ygames.ysoccer.framework.GLSpriteBatch;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

import static com.ygames.ysoccer.framework.Assets.gettext;
import static com.ygames.ysoccer.match.Const.BALL_R;
import static com.ygames.ysoccer.match.Const.CROSSBAR_H;
import static com.ygames.ysoccer.match.Const.GOAL_LINE;
import static com.ygames.ysoccer.match.Const.POST_X;
import static com.ygames.ysoccer.match.Const.isInsideGoal;

public abstract class SceneRenderer<SceneT extends Scene<?, ?>> {

    private static final float VISIBLE_FIELD_WIDTH_MAX = 1.0f;
    private static final float VISIBLE_FIELD_WIDTH_OPT = 0.75f;
    private static final float VISIBLE_FIELD_WIDTH_MIN = 0.65f;

    public static int zoomMin() {
        return 5 * (int) (20.0f * VISIBLE_FIELD_WIDTH_OPT / VISIBLE_FIELD_WIDTH_MAX);
    }

    public static int zoomMax() {
        return 5 * (int) (20.0f * VISIBLE_FIELD_WIDTH_OPT / VISIBLE_FIELD_WIDTH_MIN);
    }

    static final float guiAlpha = 0.9f;

    final SceneT scene;
    final GLGraphics glGraphics;
    final GLSpriteBatch batch;
    final protected GLShapeRenderer shapeRenderer;
    final OrthographicCamera camera;
    final MatchViewTransform viewTransform;
    int screenWidth;
    int screenHeight;
    int zoom;
    final int guiWidth = 1280;
    int guiHeight;
    Ball ball;

    SceneHotKeys hotKeys;

    final List<Sprite> allSprites = new ArrayList<>();
    final Sprite.SpriteComparator spriteComparator = new Sprite.SpriteComparator();
    CornerFlagSprite[] cornerFlagSprites;

    private final int modW = Const.REPLAY_FRAMES;
    private final int modH = 2 * Const.REPLAY_FRAMES;
    private final int modX = (int) Math.ceil(Const.PITCH_W / ((float) modW));
    private final int modY = (int) Math.ceil(Const.PITCH_H / ((float) modH));

    protected SceneRenderer(GLGraphics glGraphics, SceneT scene) {
        this.glGraphics = glGraphics;
        this.scene = scene;

        this.batch = glGraphics.batch;
        this.shapeRenderer = glGraphics.shapeRenderer;
        this.camera = glGraphics.camera;
        this.viewTransform = scene.settings.getViewTransform();
    }

    abstract public void render();

    public void resize(int width, int height) {
        // A minimized LWJGL window temporarily has a zero-sized framebuffer.
        // Keep the previous camera and HUD dimensions until the window is
        // restored, because width is also the divisor for the HUD aspect ratio.
        if (width <= 0 || height <= 0) {
            return;
        }

        screenWidth = width;
        screenHeight = height;
        float zoomMin = viewTransform.fitScale(width, height, VISIBLE_FIELD_WIDTH_MAX);
        float zoomOpt = viewTransform.fitScale(width, height, VISIBLE_FIELD_WIDTH_OPT);
        float zoomMax = viewTransform.fitScale(width, height, VISIBLE_FIELD_WIDTH_MIN);
        zoom = 20 * (int) (5.0f * Math.min(Math.max(0.01f * scene.settings.zoom * zoomOpt, zoomMin), zoomMax));

        scene.camera.setScreenParameters(screenWidth, screenHeight, zoom, viewTransform);

        guiHeight = guiWidth * height / width;
    }

    /** Configures the shared orthographic camera in projected match-view coordinates. */
    void configureWorldCamera() {
        float viewWidth = Gdx.graphics.getWidth() * 100f / zoom;
        float viewHeight = Gdx.graphics.getHeight() * 100f / zoom;
        camera.setToOrtho(true, viewWidth, viewHeight);
        float worldCenterX = scene.camera.worldCenterX(scene.cameraX);
        float worldCenterY = scene.camera.worldCenterY(scene.cameraY);
        camera.position.set(
            viewTransform.projectX(worldCenterX, worldCenterY),
            viewTransform.groundDepth(worldCenterX, worldCenterY),
            0
        );
        camera.update();
        batch.setProjectionMatrix(camera.combined);
    }

    /** Applies the affine transform only to the reusable stadium artwork. */
    void beginGroundTransform() {
        batch.setTransformMatrix(viewTransform.getGroundMatrix());
    }

    /** Restores upright drawing for players, props, weather and overlays. */
    void endGroundTransform() {
        batch.setTransformMatrix(viewTransform.getIdentityMatrix());
    }

    void renderSprites() {
        // Shadows lie on the pitch plane, so unlike upright actors their artwork receives the
        // complete affine ground transform (including horizontal depth compression).
        beginGroundTransform();
        drawShadows();
        endGroundTransform();

        allSprites.sort(spriteComparator);

        for (Sprite sprite : allSprites) {
            sprite.draw(scene.subframe);
        }
    }

    abstract void drawShadows();

    void drawBallShadow(Ball ball, boolean redrawing) {
        FrameData d = ball.currentData;
        for (int i = 0; i < (scene.settings.time == MatchSettings.Time.NIGHT ? 4 : 1); i++) {
            float oX = (i == 0 || i == 3) ? -1 : -5;
            float mX = (i == 0 || i == 3) ? 0.65f : -0.65f;
            float oY = -3;
            float mY = (i == 0 || i == 1) ? 0.46f : -0.46f;
            float shadowX = d.x + oX + mX * d.z;
            float shadowY = d.y + oY + mY * d.z;

            boolean overTheGoal = false;
            if (d.z > CROSSBAR_H) {
                float x1 = d.x + oX + mX * (d.z - CROSSBAR_H);
                float y1 = d.y + oY + mY * (d.z - CROSSBAR_H);
                if (isInsideGoal(x1 + BALL_R, y1 + BALL_R)) {
                    overTheGoal = true;
                    shadowX = d.x + oX + mX * (d.z - CROSSBAR_H);
                    shadowY = d.y + oY + mY * (d.z - CROSSBAR_H);
                    if (viewTransform.isHorizontal()) {
                        // The batch currently transforms the ground plane. Feed the inverse depth
                        // offset so the goal-roof shadow still rises vertically on screen.
                        shadowX -= CROSSBAR_H / MatchViewTransform.HORIZONTAL_DEPTH_SCALE;
                    } else {
                        shadowY -= CROSSBAR_H;
                    }
                }
            }

            // while drawing all shadows (redrawing == false) -> draw only on-the-ground shadows
            // while redrawing ball shadows (redrawing == true) -> draw only if over the goal
            if (!overTheGoal ^ redrawing) {
                batch.draw(Assets.ball[4], shadowX, shadowY);
            }
        }
    }

    void redrawBallShadowsOverGoals(Ball ball) {
        batch.setColor(0xFFFFFF, scene.settings.shadowAlpha);
        beginGroundTransform();
        drawBallShadow(ball, true);
        endGroundTransform();
        batch.setColor(0xFFFFFF, 1f);
    }

    void drawRain() {
        if (viewTransform.isHorizontal()) {
            drawHorizontalRain();
            return;
        }
        batch.setColor(0xFFFFFF, 0.6f);
        int subframe = scene.subframe;
        Assets.random.setSeed(1);
        for (int i = 1; i <= 40 * scene.settings.weatherStrength; i++) {
            int x = Assets.random.nextInt(modW);
            int y = Assets.random.nextInt(modH);
            int h = (Assets.random.nextInt(modH) + subframe) % modH;
            if (h > 0.3f * modH) {
                for (int fx = 0; fx <= modX; fx++) {
                    for (int fy = 0; fy <= modY; fy++) {
                        int px = ((x + modW - Math.round(subframe / ((float) GLGame.SUBFRAMES))) % modW) + modW * (fx - 1);
                        int py = ((y + 4 * Math.round(1f * subframe / GLGame.SUBFRAMES)) % modH) + modH * (fy - 1);
                        int f = 3 * h / modH;
                        if (h > 0.9f * modH) {
                            f = 3;
                        }
                        batch.draw(Assets.rain[f], -Const.CENTER_X + px, -Const.CENTER_Y + py);
                    }
                }
            }
        }
        Assets.random.setSeed(System.currentTimeMillis());
        batch.setColor(0xFFFFFF, 1f);
    }

    void drawSnow() {
        if (viewTransform.isHorizontal()) {
            drawHorizontalSnow();
            return;
        }
        batch.setColor(0xFFFFFF, 0.7f);

        int subframe = scene.subframe;
        Assets.random.setSeed(1);
        for (int i = 1; i <= 30 * scene.settings.weatherStrength; i++) {
            int x = Assets.random.nextInt(modW);
            int y = Assets.random.nextInt(modH);
            int s = i % 3;
            int a = Assets.random.nextInt(360);
            for (int fx = 0; fx <= modX; fx++) {
                for (int fy = 0; fy <= modY; fy++) {
                    int px = (int) (((x + modW + 30 * EMath.sin(360 * subframe / ((float) Const.REPLAY_SUBFRAMES) + a)) % modW) + modW * (fx - 1));
                    int py = ((y + 2 * Math.round(1f * subframe / GLGame.SUBFRAMES)) % modH) + modH * (fy - 1);
                    batch.draw(Assets.snow[s], -Const.CENTER_X + px, -Const.CENTER_Y + py);
                }
            }
        }
        Assets.random.setSeed(System.currentTimeMillis());
        batch.setColor(0xFFFFFF, 1f);
    }

    void drawFog() {
        if (viewTransform.isHorizontal()) {
            drawHorizontalFog();
            return;
        }
        batch.setColor(0xFFFFFF, 0.25f * scene.settings.weatherStrength);

        int subframe = scene.subframe;
        int TILE_WIDTH = 256;
        int fogX = -Const.CENTER_X + scene.cameraX - 2 * TILE_WIDTH
            + ((Const.CENTER_X - scene.cameraX) % TILE_WIDTH + 2 * TILE_WIDTH) % TILE_WIDTH;
        int fogY = -Const.CENTER_Y + scene.cameraY - 2 * TILE_WIDTH
            + ((Const.CENTER_Y - scene.cameraY) % TILE_WIDTH + 2 * TILE_WIDTH) % TILE_WIDTH;
        int x = fogX;
        while (x < (fogX + screenWidth + 2 * TILE_WIDTH)) {
            int y = fogY;
            while (y < (fogY + screenHeight + 2 * TILE_WIDTH)) {
                batch.draw(Assets.fog, x + ((1f * subframe / GLGame.SUBFRAMES) % TILE_WIDTH), y + ((2f * subframe / GLGame.SUBFRAMES) % TILE_WIDTH), 256, 256, 0, 0, 256, 256, false, true);
                y = y + TILE_WIDTH;
            }
            x = x + TILE_WIDTH;
        }
        batch.setColor(0xFFFFFF, 1f);
    }

    private void drawHorizontalRain() {
        batch.setColor(0xFFFFFF, 0.6f);
        int width = Math.max(1, (int) Math.ceil(camera.viewportWidth) + 256);
        int height = Math.max(1, (int) Math.ceil(camera.viewportHeight) + 256);
        int left = (int) Math.floor(camera.position.x - camera.viewportWidth / 2f) - 128;
        int top = (int) Math.floor(camera.position.y - camera.viewportHeight / 2f) - 128;
        Assets.random.setSeed(1);
        for (int i = 1; i <= 40 * scene.settings.weatherStrength; i++) {
            int frame = Assets.random.nextInt(4);
            int x = Assets.random.nextInt(width);
            int y = (Assets.random.nextInt(height) + 4 * scene.subframe / GLGame.SUBFRAMES) % height;
            batch.draw(Assets.rain[frame], left + x, top + y);
        }
        Assets.random.setSeed(System.currentTimeMillis());
        batch.setColor(0xFFFFFF, 1f);
    }

    private void drawHorizontalSnow() {
        batch.setColor(0xFFFFFF, 0.7f);
        int width = Math.max(1, (int) Math.ceil(camera.viewportWidth) + 256);
        int height = Math.max(1, (int) Math.ceil(camera.viewportHeight) + 256);
        int left = (int) Math.floor(camera.position.x - camera.viewportWidth / 2f) - 128;
        int top = (int) Math.floor(camera.position.y - camera.viewportHeight / 2f) - 128;
        Assets.random.setSeed(1);
        for (int i = 1; i <= 30 * scene.settings.weatherStrength; i++) {
            int sprite = i % 3;
            int phase = Assets.random.nextInt(360);
            float x = Assets.random.nextInt(width)
                + 30 * EMath.sin(360f * scene.subframe / Const.REPLAY_SUBFRAMES + phase);
            int y = (Assets.random.nextInt(height) + 2 * scene.subframe / GLGame.SUBFRAMES) % height;
            batch.draw(Assets.snow[sprite], left + x, top + y);
        }
        Assets.random.setSeed(System.currentTimeMillis());
        batch.setColor(0xFFFFFF, 1f);
    }

    private void drawHorizontalFog() {
        batch.setColor(0xFFFFFF, 0.25f * scene.settings.weatherStrength);
        final int tileWidth = 256;
        float left = camera.position.x - camera.viewportWidth / 2f - tileWidth;
        float top = camera.position.y - camera.viewportHeight / 2f - tileWidth;
        float startX = tileWidth * (float) Math.floor(left / tileWidth);
        float startY = tileWidth * (float) Math.floor(top / tileWidth);
        float offsetX = (1f * scene.subframe / GLGame.SUBFRAMES) % tileWidth;
        float offsetY = (2f * scene.subframe / GLGame.SUBFRAMES) % tileWidth;
        for (float x = startX; x < left + camera.viewportWidth + 3 * tileWidth; x += tileWidth) {
            for (float y = startY; y < top + camera.viewportHeight + 3 * tileWidth; y += tileWidth) {
                batch.draw(Assets.fog, x + offsetX, y + offsetY, 256, 256, 0, 0, 256, 256, false, true);
            }
        }
        batch.setColor(0xFFFFFF, 1f);
    }

    void drawBallPredictions(Ball ball) {
        batch.end();
        shapeRenderer.setAutoShapeType(true);
        shapeRenderer.setProjectionMatrix(camera.combined);
        shapeRenderer.begin();

        shapeRenderer.setColor(255, 255, 255, 255);
        for (int frm = 0; frm < Const.BALL_PREDICTION; frm += 10) {
            Vector3 p = ball.predictionL[frm];
            shapeRenderer.circle(viewTransform.projectX(p.x, p.y), viewTransform.groundDepth(p.x, p.y), 1);
        }

        shapeRenderer.setColor(255, 255, 0, 255);
        for (int frm = 0; frm < Const.BALL_PREDICTION; frm += 10) {
            Vector3 p = ball.prediction[frm];
            shapeRenderer.circle(viewTransform.projectX(p.x, p.y), viewTransform.groundDepth(p.x, p.y), 1);
        }

        shapeRenderer.setColor(255, 0, 0, 255);
        for (int frm = 0; frm < Const.BALL_PREDICTION; frm += 10) {
            Vector3 p = ball.predictionR[frm];
            shapeRenderer.circle(viewTransform.projectX(p.x, p.y), viewTransform.groundDepth(p.x, p.y), 1);
        }

        shapeRenderer.end();
        batch.begin();
    }

    void fadeRect(int x0, int y0, int x1, int y1, float alpha, int color) {
        shapeRenderer.setColor(color, alpha);
        shapeRenderer.rect(x0, y0, x1 - x0, y1 - y0);
    }

    void drawFrame(int x, int y, int w, int h) {
        int r = x + w;
        int b = y + h;

        // top
        shapeRenderer.rect(x + 5, y, w - 8, 1);
        shapeRenderer.rect(x + 3, y + 1, w - 4, 1);

        // top-left
        shapeRenderer.rect(x + 2, y + 2, 4, 1);
        shapeRenderer.rect(x + 2, y + 3, 1, 3);
        shapeRenderer.rect(x + 3, y + 3, 1, 1);

        // top-right
        shapeRenderer.rect(r - 4, y + 2, 4, 1);
        shapeRenderer.rect(r - 1, y + 3, 1, 3);
        shapeRenderer.rect(r - 2, y + 3, 1, 1);

        // left
        shapeRenderer.rect(x, y + 5, 1, h - 8);
        shapeRenderer.rect(x + 1, y + 3, 1, h - 4);

        // right
        shapeRenderer.rect(r + 1, y + 5, 1, h - 8);
        shapeRenderer.rect(r, y + 3, 1, h - 4);

        // bottom-left
        shapeRenderer.rect(x + 2, b - 4, 1, 3);
        shapeRenderer.rect(x + 2, b - 1, 4, 1);
        shapeRenderer.rect(x + 3, b - 2, 1, 1);

        // bottom-right
        shapeRenderer.rect(r - 1, b - 4, 1, 3);
        shapeRenderer.rect(r - 4, b - 1, 4, 1);
        shapeRenderer.rect(r - 2, b - 2, 1, 1);

        // bottom
        shapeRenderer.rect(x + 5, b + 1, w - 8, 1);
        shapeRenderer.rect(x + 3, b, w - 4, 1);
    }

    void drawHelp(TreeMap<Integer, String[]> keyMap) {

        int rows = keyMap.size() + 1;
        int rowHeight = 52;

        int left = 13 + guiWidth / 5 + 2;
        int right = guiWidth - left + 2;
        int width = right - left;
        int top = guiHeight / 2 - rowHeight * rows / 2 + 2;
        int bottom = top + rowHeight * rows;
        int halfWay = guiWidth / 2;

        // fading
        batch.end();
        Gdx.gl.glEnable(GL20.GL_BLEND);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        // top strip
        fadeRect(left + 2, top + 2, right - 2, top + rowHeight + 1, 0.35f, 0x000000);

        // middle strips
        int i = top + rowHeight + 2;
        for (int j = 1; j < rows - 1; j++) {
            fadeRect(left + 2, i + 1, right - 2, i + rowHeight - 1, 0.35f, 0x000000);
            i = i + rowHeight;
        }

        // bottom strip
        fadeRect(left + 2, i + 1, right - 2, bottom - 2, 0.35f, 0x000000);

        // frame shadow
        shapeRenderer.setColor(0x242424, guiAlpha);
        drawFrame(left, top, right - left, bottom - top);

        left = left - 2;
        right = right - 2;
        top = top - 2;
        bottom = bottom - 2;

        // frame
        shapeRenderer.setColor(0xFFFFFF, guiAlpha);
        drawFrame(left, top, right - left, bottom - top);

        shapeRenderer.end();
        batch.begin();
        batch.setColor(0xFFFFFF, guiAlpha);

        int lc = left + 3 * width / 18;
        int rc = right - 12 * width / 18;
        i = top + rowHeight / 2 - 8;
        Assets.font14.draw(batch, gettext("HELP"), halfWay, i, Font.Align.CENTER);

        for (Integer key : keyMap.keySet()) {
            i = i + rowHeight;
            String[] info = keyMap.get(key);
            Assets.font14.draw(batch, info[0], lc, i, Font.Align.CENTER);
            Assets.font14.draw(batch, info[1], rc, i, Font.Align.LEFT);
        }

        batch.setColor(0xFFFFFF, 1f);
    }

    void redrawBallOverTopGoal(BallSprite ballSprite) {
        FrameData d = ball.currentData;
        if (EMath.isIn(d.x, -POST_X, POST_X)
            && d.y < -GOAL_LINE
            && d.z > (CROSSBAR_H - (Math.abs(d.y) - GOAL_LINE) / 3f)) {
            ballSprite.draw(scene.subframe);
        }
    }

    void redrawBallOverBottomGoal(BallSprite ballSprite) {
        FrameData d = ball.currentData;
        if (EMath.isIn(d.x, -POST_X - BALL_R, POST_X + BALL_R)
            && (d.y >= GOAL_LINE + 21 || (d.y > GOAL_LINE && d.z > (CROSSBAR_H - (Math.abs(d.y) - GOAL_LINE) / 3f)))) {
            ballSprite.draw(scene.subframe);
        }
    }

    /** Draws the reusable wire net and rear goal frame behind horizontal-view actors. */
    void drawHorizontalGoalBacks() {
        batch.end();
        shapeRenderer.setProjectionMatrix(camera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        shapeRenderer.setColor(0.78f, 0.82f, 0.86f, 0.72f);
        drawHorizontalGoalBack(-1);
        drawHorizontalGoalBack(1);
        shapeRenderer.end();
        batch.begin();
    }

    /** Draws the bright front posts and crossbars over horizontal-view actors. */
    void drawHorizontalGoalFronts() {
        batch.end();
        shapeRenderer.setProjectionMatrix(camera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        shapeRenderer.setColor(1f, 1f, 1f, 1f);
        drawHorizontalGoalFront(-1);
        drawHorizontalGoalFront(1);
        shapeRenderer.end();
        batch.begin();
    }

    private void drawHorizontalGoalBack(int ySide) {
        float frontX = viewTransform.projectX(0, ySide * GOAL_LINE);
        float backX = viewTransform.projectX(0, ySide * (GOAL_LINE + Const.GOAL_DEPTH));
        float farY = viewTransform.groundDepth(-POST_X, 0);
        float nearY = viewTransform.groundDepth(POST_X, 0);
        float farTop = farY - CROSSBAR_H;
        float nearTop = nearY - CROSSBAR_H;

        shapeRenderer.line(backX, farY, backX, farTop);
        shapeRenderer.line(backX, nearY, backX, nearTop);
        shapeRenderer.line(backX, farTop, backX, nearTop);
        shapeRenderer.line(frontX, farY, backX, farY);
        shapeRenderer.line(frontX, nearY, backX, nearY);
        shapeRenderer.line(frontX, farTop, backX, farTop);
        shapeRenderer.line(frontX, nearTop, backX, nearTop);

        for (int i = 1; i < 4; i++) {
            float amount = i / 4f;
            float x = frontX + amount * (backX - frontX);
            shapeRenderer.line(x, farY, x, farTop);
            shapeRenderer.line(x, nearY, x, nearTop);
        }
        for (int i = 1; i < 4; i++) {
            float amount = i / 4f;
            float y = farY + amount * (nearY - farY);
            shapeRenderer.line(frontX, y, backX, y);
            shapeRenderer.line(frontX, y - CROSSBAR_H, backX, y - CROSSBAR_H);
        }
    }

    private void drawHorizontalGoalFront(int ySide) {
        float frontX = viewTransform.projectX(0, ySide * GOAL_LINE);
        float farY = viewTransform.groundDepth(-POST_X, 0);
        float nearY = viewTransform.groundDepth(POST_X, 0);
        shapeRenderer.line(frontX, farY, frontX, farY - CROSSBAR_H);
        shapeRenderer.line(frontX, nearY, frontX, nearY - CROSSBAR_H);
        shapeRenderer.line(frontX, farY - CROSSBAR_H, frontX, nearY - CROSSBAR_H);
    }

    void redrawBallOverHorizontalGoals(BallSprite ballSprite) {
        FrameData d = ball.currentData;
        if (EMath.isIn(d.x, -POST_X - BALL_R, POST_X + BALL_R)
            && Math.abs(d.y) > GOAL_LINE
            && d.z > CROSSBAR_H - (Math.abs(d.y) - GOAL_LINE) / 3f) {
            ballSprite.draw(scene.subframe);
        }
    }

    void drawPlayerNumber(Player player) {
        FrameData d = player.currentData;

        int f0 = player.number % 10;
        int f1 = (player.number - f0) / 10 % 10;

        float dx = viewTransform.projectX(d.x, d.y);
        float dy = viewTransform.projectY(d.x, d.y, d.z) - 40;

        int w0 = 6 - ((f0 == 1) ? 2 : 1);
        int w1 = 6 - ((f1 == 1) ? 2 : 1);

        int fy = scene.settings.pitchType == Pitch.Type.WHITE ? 1 : 0;
        if (f1 > 0) {
            dx = dx - (w0 + 2 + w1) / 2f;
            batch.draw(Assets.playerNumbers[f1][fy], dx, dy, 6, 10);
            dx = dx + w1 + 2;
            batch.draw(Assets.playerNumbers[f0][fy], dx, dy, 6, 10);
        } else {
            batch.draw(Assets.playerNumbers[f0][fy], dx - w0 / 2f, dy, 6, 10);
        }
    }

    /** Draws a high-contrast arrow and ground ring around the player receiving human input. */
    void drawControlledPlayerMarker(Player player) {
        if (player == null || !player.currentData.isVisible) {
            return;
        }

        FrameData d = player.currentData;
        float markerX = viewTransform.projectX(d.x, d.y);
        float markerY = viewTransform.projectY(d.x, d.y, d.z) - 54;
        float groundY = viewTransform.groundDepth(d.x, d.y);

        batch.end();
        shapeRenderer.setProjectionMatrix(camera.combined);

        // A dark border keeps the marker readable over snow, grass, crowd, and bright kits.
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(0.08f, 0.08f, 0.08f, 1f);
        shapeRenderer.triangle(markerX - 9, markerY - 12, markerX + 9, markerY - 12, markerX, markerY + 2);
        shapeRenderer.setColor(1f, 0.9f, 0f, 1f);
        shapeRenderer.triangle(markerX - 6, markerY - 9, markerX + 6, markerY - 9, markerX, markerY);
        shapeRenderer.end();

        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        shapeRenderer.setColor(0.08f, 0.08f, 0.08f, 1f);
        shapeRenderer.circle(markerX, groundY + 3, 12, 24);
        shapeRenderer.setColor(1f, 0.9f, 0f, 1f);
        shapeRenderer.circle(markerX, groundY + 3, 10, 24);
        shapeRenderer.circle(markerX, groundY + 3, 11, 24);
        shapeRenderer.end();

        batch.begin();
        batch.setColor(0xFFFFFF, 1f);
    }

    void drawPlayerNumberAndName(Player player) {
        Assets.font10.draw(batch, player.number + " " + player.shirtName, 10, 2, Font.Align.LEFT);
    }

    public void update() {
        hotKeys.update();
    }
}
