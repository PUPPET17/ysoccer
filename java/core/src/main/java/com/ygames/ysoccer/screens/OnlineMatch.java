package com.ygames.ysoccer.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.esotericsoftware.kryonet.Client;
import com.ygames.ysoccer.framework.Assets;
import com.ygames.ysoccer.framework.EventManager;
import com.ygames.ysoccer.framework.GLGame;
import com.ygames.ysoccer.framework.GLScreen;
import com.ygames.ysoccer.framework.InputDevice;
import com.ygames.ysoccer.match.Match;
import com.ygames.ysoccer.match.MatchCamera;
import com.ygames.ysoccer.match.MatchRenderer;
import com.ygames.ysoccer.match.Player;
import com.ygames.ysoccer.network.dto.MatchSetupDto;
import com.ygames.ysoccer.network.mappers.MatchMapper;

import static com.badlogic.gdx.Gdx.gl;
import static com.ygames.ysoccer.match.Match.AWAY;
import static com.ygames.ysoccer.match.Match.HOME;

public class OnlineMatch extends GLScreen {

    Match match;

    private MatchRenderer matchRenderer;

    int zoom = 100;

    InputDevice inputDevice;

    public OnlineMatch(GLGame game, Client client) {
        super(game);
        usesMouse = false;

        EventManager.clear();
        game.soundManager.subscribeEvents();

        game.inputDevices.setAvailability(true);
        // Prefer a connected gamepad; retained unplugged slots must not capture the online player.
        for (InputDevice device : game.inputDevices) {
            if (device.type == InputDevice.Type.JOYSTICK && device.isConnected()) {
                inputDevice = device;
                inputDevice.setAvailable(false);
                break;
            }
        }
        if (inputDevice == null) inputDevice = game.inputDevices.assignFirstAvailable();
    }

    public void setup(MatchSetupDto matchSetupDto) {
        match = MatchMapper.fromDto(matchSetupDto.matchDto);
        match.setCamera(new MatchCamera(match));
        matchRenderer = new MatchRenderer(game.glGraphics, match);
        game.glGraphics.light = 0;
        Assets.loadStadium(match.getSettings());
        Assets.loadCrowd(match.team[Match.HOME]);
        Assets.loadBall(match.getSettings());
        Assets.loadCornerFlags();
        for (int t = HOME; t <= AWAY; t++) {
            match.team[t].loadImage();
            Assets.loadCoach(match.team[t]);
            int len = match.team[t].lineup.size();
            for (int i = 0; i < len; i++) {
                Player player = match.team[t].lineup.get(i);
                if (player.role == Player.Role.GOALKEEPER) {
                    Assets.loadKeeper(player);
                } else {
                    Assets.loadPlayer(player, match.team[t].kits.get(match.team[t].kitIndex));
                }
                Assets.loadHair(player);
            }
        }
        game.disableMouse();
    }

    @Override
    public void render(float deltaTime) {
        super.render(deltaTime);

        // do not render until match gets initial state
        if (match.getStateId() == null) return;

        // pass ball position to camera
        match.getBall().setX(match.getBall().currentData.x);
        match.getBall().setY(match.getBall().currentData.y);

        float timeLeft = deltaTime;
        while (timeLeft >= GLGame.SUBFRAME_DURATION) {
            match.camera.update();
            match.nextSubframe();
            match.saveCamera(match.subframe);
            timeLeft -= GLGame.SUBFRAME_DURATION;
        }

        match.updateCurrentCamera();
        matchRenderer.render();
    }

    @Override
    public void resize(int width, int height) {
        super.resize(width, height);

        matchRenderer.resize(width, height);
    }

    private void renderBackground() {
        gl.glEnable(GL20.GL_BLEND);
        gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        camera.setToOrtho(true, Gdx.graphics.getWidth() * 100f / zoom, Gdx.graphics.getHeight() * 100f / zoom);
        camera.update();
        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        batch.disableBlending();
        for (int c = 0; c < 4; c++) {
            for (int r = 0; r < 4; r++) {
                batch.draw(Assets.stadium[r][c], 512 * c, 512 * r);
            }
        }
        batch.enableBlending();
        batch.end();
    }
}
