package com.ygames.ysoccer.match;

import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import java.lang.reflect.Proxy;
import com.ygames.ysoccer.competitions.League;
import com.ygames.ysoccer.competitions.TableRow;
import com.ygames.ysoccer.framework.Assets;
import com.ygames.ysoccer.gui.Label;
import java.util.Arrays;
import java.util.Random;
import static com.ygames.ysoccer.match.MatchScoreSimulator.Venue.*;

/** 无图形环境的自动比分检查，涵盖旧版均值、守门员、场地、泊松分布及大比分存档。 */
public final class GoalsRegressionTest {
    /** 已执行的业务断言数量。 */
    private static int checks;

    /** 验证模拟器及比分消费链，临时战术和随机源在结束时恢复。 */
    public static void main(String[] args) {
        com.badlogic.gdx.Files originalFiles = Gdx.files;
        // Assets 初始化只需创建本地路径句柄；测试无需启动后端或加载纹理。
        Gdx.files = (com.badlogic.gdx.Files) Proxy.newProxyInstance(com.badlogic.gdx.Files.class.getClassLoader(),
            new Class<?>[]{com.badlogic.gdx.Files.class}, (proxy, method, arguments) -> {
                if (method.getName().equals("local")) return new FileHandle((String) arguments[0]);
                throw new UnsupportedOperationException(method.getName());
            });
        Tactics originalTactics = Assets.tactics[0];
        Random originalRandom = Assets.random;
        try {
            Assets.tactics[0] = new Tactics();
            meansAndGuards();
            keeperVenueAndDuration();
            poissonDistribution();
            teamsAndReplay();
            largeScores();
            System.out.println("Goals regression checks passed: " + checks);
        } finally {
            Assets.tactics[0] = originalTactics;
            Assets.random = originalRandom;
            Gdx.files = originalFiles;
        }
    }

    /** 基础曲线保留旧表整数和小数档位均值，仅最低速率保护改变零端点。 */
    private static void meansAndGuards() {
        for (int row = 0; row <= 10; row++) {
            near(MatchScoreSimulator.baseExpectedGoals(row), LegacyGoalsBaseline.mean(row), "old row mean");
            if (row < 10) near(MatchScoreSimulator.baseExpectedGoals(row + 0.25),
                0.75 * LegacyGoalsBaseline.mean(row) + 0.25 * LegacyGoalsBaseline.mean(row + 1), "fractional mean");
        }
        near(MatchScoreSimulator.baseExpectedGoals(-100), 0, "lower tier clamp");
        near(MatchScoreSimulator.baseExpectedGoals(100), 3.7, "upper tier clamp");
        near(expected(-100, 28, 90), 0.05, "zero tier permits rare goals");
        near(expected(5, -99, 90), expected(5, 0, 90), "minimum keeper clamp");
        near(expected(5, 999, 90), expected(5, 49, 90), "maximum keeper clamp");
        near(MatchScoreSimulator.expectedGoals(100, 0, 90, HOME), 5, "maximum rate clamp");
        rejects(() -> expected(Double.NaN, 28, 90));
        rejects(() -> expected(Double.POSITIVE_INFINITY, 28, 90));
        rejects(() -> expected(5, Double.POSITIVE_INFINITY, 90));
        rejects(() -> expected(5, 28, -1));
        rejects(() -> expected(5, 28, 121));
        rejects(() -> expected(5, 28, Double.NaN));
        rejects(() -> MatchScoreSimulator.samplePoisson(-1, new Random(1)));
        rejects(() -> MatchScoreSimulator.samplePoisson(Double.NaN, new Random(1)));
        rejects(() -> MatchScoreSimulator.samplePoisson(1000, new Random(1)));
    }

    /** 门将越强失球越少，主场只修正主队，加时赛在速率保护后仍严格按时长缩放。 */
    private static void keeperVenueAndDuration() {
        near(expected(5, 28, 90), 1.38, "reference keeper");
        near(expected(5, 28, 30), 0.46, "extra time expectation");
        near(expected(5, 28, 0), 0, "zero duration");
        near(MatchScoreSimulator.expectedGoals(5, 28, 90, HOME), 1.38 * 1.08, "home boost");
        near(MatchScoreSimulator.expectedGoals(5, 28, 90, AWAY), 1.38, "no extra away penalty");
        for (int tier = -1; tier <= 11; tier++) {
            double previous = Double.POSITIVE_INFINITY;
            for (int keeper = 0; keeper <= 49; keeper++) {
                double mean = expected(tier, keeper, 90);
                check(mean <= previous, "keeper monotonicity");
                previous = mean;
                near(expected(tier, keeper, 30), mean / 3, "neutral duration scaling");
                near(MatchScoreSimulator.expectedGoals(tier, keeper, 30, HOME),
                    MatchScoreSimulator.expectedGoals(tier, keeper, 90, HOME) / 3, "home duration scaling");
            }
        }
        check(expected(5, 29, 90) < expected(5, 28, 90), "single keeper point matters");
    }

    /** 固定种子检查均值、方差和零进球率；显式序列检查无六球硬上限。 */
    private static void poissonDistribution() {
        check(MatchScoreSimulator.samplePoisson(0, new SequenceRandom()) == 0, "zero mean consumes no draw");
        check(MatchScoreSimulator.samplePoisson(1.38, new SequenceRandom(0)) == 0, "zero draw");
        double[] draws = new double[13];
        Arrays.fill(draws, 0.99);
        draws[12] = 0;
        check(MatchScoreSimulator.samplePoisson(1.38, new SequenceRandom(draws)) == 12, "no score clipping");
        for (double mean : new double[]{0.05, 0.46, 1.38, 3.7, 5, 5 * 120 / 90.0}) {
            Random random = new Random(20260913L);
            int samples = 200000;
            double sum = 0, squares = 0;
            int zeros = 0;
            for (int sample = 0; sample < samples; sample++) {
                int goals = MatchScoreSimulator.samplePoisson(mean, random);
                check(goals >= 0, "nonnegative score");
                sum += goals;
                squares += goals * goals;
                if (goals == 0) zeros++;
            }
            double observed = sum / samples;
            double variance = squares / samples - observed * observed;
            // 六倍标准误差容纳抽样波动；固定种子使检查每次可复现。
            check(Math.abs(observed - mean) < 6 * Math.sqrt(mean / samples), "Poisson mean");
            check(Math.abs(variance - mean) < 6 * Math.sqrt((mean + 2 * mean * mean) / samples), "Poisson variance");
            double p0 = Math.exp(-mean);
            check(Math.abs(zeros / (double) samples - p0) < 6 * Math.sqrt(p0 * (1 - p0) / samples), "zero goals");
        }
    }

    /** 通过真实 Team 评分路径检查首发门将选择与 Match 兼容入口。 */
    private static void teamsAndReplay() {
        Team first = team(28), second = team(28);
        near(MatchScoreSimulator.expectedGoals(first, second, 90, NEUTRAL),
            MatchScoreSimulator.expectedGoals(second, first, 90, NEUTRAL), "neutral symmetry");
        double before = MatchScoreSimulator.expectedGoals(first, second, 90, NEUTRAL);
        Player reserve = new Player();
        reserve.role = Player.Role.GOALKEEPER;
        reserve.value = 49;
        second.players.add(reserve);
        near(MatchScoreSimulator.expectedGoals(first, second, 90, NEUTRAL), before, "reserve ignored");
        second.players.get(0).value = 49;
        check(MatchScoreSimulator.expectedGoals(first, second, 90, NEUTRAL) < before, "starter matters");
        second.players.get(0).role = Player.Role.ATTACKER;
        double standIn = MatchScoreSimulator.expectedGoals(first, second, 90, NEUTRAL);
        second.players.get(0).role = Player.Role.GOALKEEPER;
        second.players.get(0).value = 0;
        near(standIn, MatchScoreSimulator.expectedGoals(first, second, 90, NEUTRAL), "outfield stand-in");
        Random replay = new Random(83);
        Assets.random = new Random(83);
        for (int game = 0; game < 1000; game++) {
            boolean extra = game % 2 == 0;
            check(Match.generateGoals(first, second, extra)
                == MatchScoreSimulator.generateGoals(first, second, extra ? 30 : 90, NEUTRAL, replay), "seed replay");
        }
        Assets.random = new Random(83);
        replay = new Random(83);
        check(Match.generateGoals(first, second, false, HOME)
            == MatchScoreSimulator.generateGoals(first, second, 90, HOME, replay), "explicit venue entry");
    }

    /** 两位数比分完整保存和显示，并全部计入积分榜和射手统计。 */
    private static void largeScores() {
        Match match = new Match();
        match.teams[0] = 0;
        match.teams[1] = 1;
        match.setResult(12, 10, Match.ResultType.AFTER_90_MINUTES);
        match.setResult(14, 11, Match.ResultType.AFTER_EXTRA_TIME);
        Json json = new Json();
        Match restored = json.fromJson(Match.class, json.toJson(match));
        check(Arrays.equals(restored.resultAfter90, new int[]{12, 10}), "normal-time score save");
        check(Arrays.equals(restored.getResult(), new int[]{14, 11}), "extra-time score save");
        TableRow row = new TableRow(0);
        row.update(14, 11, 3);
        check(row.goalsFor == 14 && row.goalsAgainst == 11 && row.points == 3, "table full score");
        League league = new League();
        Team scoringTeam = team(28);
        league.generateScorers(scoringTeam, 14);
        int scorerGoals = 0;
        for (Player player : scoringTeam.players) scorerGoals += league.getScorerGoals(player);
        check(scorerGoals == 14, "all goals assigned");
        ScoreLabel label = new ScoreLabel();
        label.setText(14);
        check("14".equals(label.value()), "both digits displayed");
    }

    /** 建立无需美术资源的完整首发，攻防技能相同以隔离门将因素。 */
    private static Team team(int keeperValue) {
        Team team = new Team();
        team.name = "TEST TEAM";
        for (int index = 0; index < 11; index++) {
            Player player = new Player();
            player.name = player.shirtName = "PLAYER " + index;
            player.team = team;
            player.role = index == 0 ? Player.Role.GOALKEEPER : Player.Role.MIDFIELDER;
            player.value = index == 0 ? keeperValue : 0;
            if (index > 0) {
                player.skills.passing = player.skills.tackling = player.skills.heading = 4;
                player.skills.speed = player.skills.control = player.skills.shooting = player.skills.finishing = 4;
            }
            team.players.add(player);
        }
        return team;
    }

    private static double expected(double factor, double keeper, double minutes) {
        return MatchScoreSimulator.expectedGoals(factor, keeper, minutes, NEUTRAL);
    }

    private static void near(double actual, double expected, String message) {
        check(Math.abs(actual - expected) < 1e-12, message + ": " + actual);
    }

    private static void rejects(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException expected) { checks++; return; }
        throw new AssertionError("invalid input was accepted");
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    /** 验证整数转换，不初始化字体纹理或 OpenGL。 */
    private static final class ScoreLabel extends Label {
        String value() { return text; }
    }

    /** 精确控制泊松乘积何时结束，额外消费随机值立即失败。 */
    private static final class SequenceRandom extends Random {
        private final double[] sequence;
        private int index;
        private SequenceRandom(double... sequence) { this.sequence = sequence; }
        @Override
        public double nextDouble() {
            if (index >= sequence.length) throw new AssertionError("unexpected random draw");
            return sequence[index++];
        }
    }
}
