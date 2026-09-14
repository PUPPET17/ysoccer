package com.ygames.ysoccer.match;

import com.ygames.ysoccer.framework.Assets;
import com.ygames.ysoccer.framework.GLGraphics;
import com.ygames.ysoccer.framework.Settings;

import static com.ygames.ysoccer.framework.Font.Align.CENTER;

public class PlayerSprite extends Sprite {

    Player player;

    /** Keeps editor and developer previews in their original vertical presentation. */
    public PlayerSprite(GLGraphics glGraphics, Player player) {
        this(glGraphics, player, new MatchViewTransform(MatchViewMode.VERTICAL));
    }

    public PlayerSprite(GLGraphics glGraphics, Player player, MatchViewTransform viewTransform) {
        super(glGraphics, viewTransform);
        this.player = player;
    }

    @Override
    public void draw(int subframe) {
        FrameData d = player.currentData;
        if (!d.isVisible) {
            return;
        }

        int frameX = viewTransform.projectDirectionFrame(d.fmx);
        float viewX = viewTransform.projectX(d.x, d.y);
        float viewY = viewTransform.projectY(d.x, d.y, d.z);

        if (player.role == Player.Role.GOALKEEPER) {
            Integer[] origin = Assets.keeperOrigins[d.fmy][frameX];
            glGraphics.batch.draw(
                    Assets.keeper[player.team.index][player.skinColor.ordinal()][frameX][d.fmy],
                    viewX - origin[0],
                    viewY - origin[1]
            );

            Integer[] hairMap = Assets.keeperHairMap[d.fmy][frameX];
            if (hairMap[2] != 0 || hairMap[3] != 0) {
                glGraphics.batch.draw(
                        Assets.hairs.get(player.hair)[hairMap[0]][hairMap[1]],
                        viewX - origin[0] + hairMap[2],
                        viewY - origin[1] + hairMap[3]
                );
            }
        } else {
            Integer[] origin = Assets.playerOrigins[d.fmy][frameX];
            glGraphics.batch.draw(
                    Assets.player[player.team.index][player.skinColor.ordinal()][frameX][d.fmy],
                    viewX - origin[0],
                    viewY - origin[1]
            );

            Integer[] hairMap = Assets.playerHairMap[d.fmy][frameX];
            if (hairMap[2] != 0 || hairMap[3] != 0) {
                glGraphics.batch.draw(
                        Assets.hairs.get(player.hair)[hairMap[0]][hairMap[1]],
                        viewX - origin[0] - 9 + hairMap[2],
                        viewY - origin[1] - 9 + hairMap[3]
                );
            }
        }

        // development
        if (Settings.showDevelopmentInfo) {
            if (Settings.showPlayerState && player.fsm != null) {
                Assets.font3.draw(glGraphics.batch, PlayerFsm.Id.values()[d.playerState].toString(), Math.round(viewX), Math.round(viewY - 50), CENTER);
            }
            if (Settings.showPlayerAiState && d.playerAiState != -1) {
                Assets.font3.draw(glGraphics.batch, AiFsm.Id.values()[d.playerAiState].toString(), Math.round(viewX), Math.round(viewY - 40), CENTER);
            }
            if (Settings.showBestDefender && d.isBestDefender) {
                Assets.font6.draw(glGraphics.batch, "_", Math.round(viewX), Math.round(viewY - 12), CENTER);
            }
            if (Settings.showFrameDistance) {
                Assets.font3.draw(glGraphics.batch, d.frameDistance, Math.round(viewX), Math.round(viewY + 4), CENTER);
            }
        }
    }

    @Override
    public float getDepth() {
        FrameData d = player.currentData;
        return viewTransform.groundDepth(d.x, d.y);
    }
}
