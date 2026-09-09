package com.ygames.ysoccer.match;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static com.ygames.ysoccer.match.Player.Role.GOALKEEPER;
import static com.ygames.ysoccer.match.PlayerFsm.Id.STATE_STAND_RUN;

/**
 * Maintains the manual-player-switch cycle for one human controller.
 * Candidates are calculated only when the switch button is pressed; this class does
 * not drive automatic selection or expose a preview target.
 */
final class ManualPlayerSwitcher {

    private static final int CYCLE_WINDOW_SUBFRAMES = Const.SECOND;

    private final List<Player> cycleCandidates = new ArrayList<>();
    private int nextCandidateIndex;
    private int cycleTimer;

    /** Advances the candidate-cycle lifetime by one physics subframe. */
    void update() {
        if (cycleTimer > 0) cycleTimer--;
        if (cycleTimer == 0) {
            cycleCandidates.clear();
            nextCandidateIndex = 0;
        }
    }

    /**
     * Selects the next contextual teammate immediately after a button press.
     *
     * @param players eligible squad pool in stable lineup order
     * @param controlled currently human-controlled player, if any
     * @param preferred contextual first choice, normally the goal-side best defender
     * @return the player to control, or {@code null} when no alternative is eligible
     */
    Player select(List<Player> players, Player controlled, Player preferred) {
        if (cycleCandidates.isEmpty()) {
            rebuildCandidates(players, preferred);
        }

        Player selected = findNextCandidate(controlled);
        if (selected != null) {
            cycleTimer = CYCLE_WINDOW_SUBFRAMES;
        }
        return selected;
    }

    /** Clears the current cycle when possession or match state changes. */
    void reset() {
        cycleCandidates.clear();
        nextCandidateIndex = 0;
        cycleTimer = 0;
    }

    private void rebuildCandidates(List<Player> players, Player preferred) {
        cycleCandidates.clear();
        for (Player player : players) {
            if (isEligible(player)) {
                cycleCandidates.add(player);
            }
        }

        // Java's stable sort preserves lineup order for otherwise identical candidates.
        cycleCandidates.sort(Comparator
            .comparingInt((Player player) -> player == preferred ? 0 : 1)
            .thenComparingInt(player -> player.frameDistance)
            .thenComparingDouble(player -> player.ballDistance));
        nextCandidateIndex = 0;
    }

    private Player findNextCandidate(Player controlled) {
        int size = cycleCandidates.size();
        for (int offset = 0; offset < size; offset++) {
            int index = (nextCandidateIndex + offset) % size;
            Player candidate = cycleCandidates.get(index);
            if (candidate != controlled && isEligible(candidate)) {
                nextCandidateIndex = (index + 1) % size;
                return candidate;
            }
        }
        return null;
    }

    private boolean isEligible(Player player) {
        return player.isActive
            && player.role != GOALKEEPER
            && player.checkState(STATE_STAND_RUN);
    }
}
