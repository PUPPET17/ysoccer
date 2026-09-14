package com.ygames.ysoccer.match;

import java.util.Objects;
import java.util.Random;

/**
 * 为未运行实时比赛场景的赛事生成进球数，将球队评分、守门员修正与泊松抽样集中管理。
 * 不保存比赛状态，不修改球队或存档；调用方负责保存比分并分配进球球员。
 * 参数以旧版比分均值和内置球队为游戏平衡基准，并非经过真实比赛数据拟合的预测模型。
 */
public final class MatchScoreSimulator {
    /** 本队在真实比赛场地中的身份；缺少场地规则时必须使用 NEUTRAL。 */
    public enum Venue {HOME, AWAY, NEUTRAL}

    /**
     * 原始千分比表逐行的精确期望，索引为 (进攻评分 - 对手防守评分 + 300) / 60。
     * 保留旧版实力差与均值的关系；旧版各比分权重仅保留在测试基准中。
     */
    private static final double[] BASE_GOALS_BY_ADVANTAGE = {
        0, 0.166, 0.344, 0.704, 0.943, 1.38, 1.659, 1.985, 2.82, 3.37, 3.7
    };

    /** 内置 24 支非自定义且首发外场能力非全零球队的首发门将能力中位数，量纲 0～49。 */
    static final double REFERENCE_KEEPER_VALUE = 28;
    /** 门将标准化能力每增加 1，对数预期失球降低 0.4；是保守的游戏平衡参数。 */
    static final double KEEPER_INFLUENCE = 0.4;
    /** 明确主场身份时增加 8% 预期进球，客场不另扣减；中立场完全不使用该加成。 */
    static final double HOME_MULTIPLIER = 1.08;
    /** 90 分钟最低期望，避免最低攻防档位变成永远无法进球；仍允许绝大多数比赛零进球。 */
    static final double MIN_GOALS_PER_90 = 0.05;
    /** 90 分钟期望的保护上界；限制进球速率而非实际比分，仍可抽到 7 球或两位数。 */
    static final double MAX_GOALS_PER_90 = 5;

    private MatchScoreSimulator() {
    }

    /**
     * 生成指定比赛时段的单队进球数。
     * @param teamFor 提供原有外场进攻评分的球队，需具有完整首发名单
     * @param teamAgainst 提供外场防守及实际首发守门员能力的球队，需具有完整首发名单
     * @param minutes 足球规则中的模拟分钟数，范围 0～120；不是玩家设置的真实游玩时长
     * @param venue 本队真实场地身份，不可为 null
     * @param random 可复现的随机源，不可为 null；本方法会推进其状态
     * @return 非负进球数，不对抽样结果设置比分上限
     * @throws IllegalArgumentException 时长超出范围或不是有限值
     */
    public static int generateGoals(Team teamFor, Team teamAgainst, double minutes, Venue venue, Random random) {
        Objects.requireNonNull(random, "random");
        return samplePoisson(expectedGoals(teamFor, teamAgainst, minutes, venue), random);
    }

    /**
     * 计算指定时段的预期进球数，用于生成比分和离线平衡验证，不消耗随机数。
     * @param teamFor 具有完整首发名单的进攻球队
     * @param teamAgainst 具有完整首发名单的防守球队
     * @param minutes 模拟时长，0～120 分钟
     * @param venue 本队场地身份，不可为 null
     * @return 修正后的时段均值；0 分钟返回 0
     * @throws IllegalArgumentException 时长超出范围或不是有限值
     */
    public static double expectedGoals(Team teamFor, Team teamAgainst, double minutes, Venue venue) {
        double factor = (teamFor.offenseRating() - (double) teamAgainst.defenseRating() + 300) / 60.0;
        Player keeper = teamAgainst.playerAtPosition(0);
        // 只看实际首发守门位置；替补门将不影响本场，非门将客串按最低守门能力处理。
        double keeperValue = keeper.role == Player.Role.GOALKEEPER ? keeper.getValue() : 0;
        return expectedGoals(factor, keeperValue, minutes, venue);
    }

    /**
     * 按已提取的攻防档位和门将能力计算均值，供确定性测试和离线校准使用。
     * 档位、门将能力须有限，超出游戏正常范围时钳制；时长须在 0～120，场地不可为 null。
     * 先限制 90 分钟速率再乘时长，因此相同条件下加时赛均值严格为整场的三分之一。
     */
    static double expectedGoals(double factor, double keeperValue, double minutes, Venue venue) {
        Objects.requireNonNull(venue, "venue");
        if (!Double.isFinite(keeperValue) || !Double.isFinite(minutes) || minutes < 0 || minutes > 120) {
            throw new IllegalArgumentException("Keeper value must be finite and minutes must be in [0, 120]");
        }
        double keeper = Math.max(0, Math.min(49, keeperValue));
        double rate = baseExpectedGoals(factor)
            * Math.exp(-KEEPER_INFLUENCE * (keeper - REFERENCE_KEEPER_VALUE) / 49.0);
        if (venue == Venue.HOME) {
            rate *= HOME_MULTIPLIER;
        }
        rate = Math.max(MIN_GOALS_PER_90, Math.min(MAX_GOALS_PER_90, rate));
        return rate * minutes / 90.0;
    }

    /** 按有限的攻防优势档位插值旧版整场均值，超界时使用端点；不应用门将或场地修正。 */
    static double baseExpectedGoals(double factor) {
        if (!Double.isFinite(factor)) {
            throw new IllegalArgumentException("Attack advantage must be finite");
        }
        factor = Math.max(0, Math.min(BASE_GOALS_BY_ADVANTAGE.length - 1, factor));
        int lower = (int) Math.floor(factor);
        int upper = (int) Math.ceil(factor);
        return BASE_GOALS_BY_ADVANTAGE[lower]
            + (BASE_GOALS_BY_ADVANTAGE[upper] - BASE_GOALS_BY_ADVANTAGE[lower]) * (factor - lower);
    }

    /**
     * 用乘积法抽取本模拟器范围内的泊松进球数，随机源须非空。
     * 均值必须有限且在 0～(5×120/90) 内；较小的比赛均值使算法无需截断结果或复杂近似。
     * 零时长对应零均值，直接返回 0 而不消耗随机源。
     */
    static int samplePoisson(double mean, Random random) {
        if (!Double.isFinite(mean) || mean < 0 || mean > MAX_GOALS_PER_90 * 120 / 90.0) {
            throw new IllegalArgumentException("Poisson mean outside supported match duration range");
        }
        if (mean == 0) {
            return 0;
        }
        double threshold = Math.exp(-mean);
        double product = 1;
        int goals = -1;
        do {
            product *= random.nextDouble();
            goals++;
        } while (product > threshold);
        return goals;
    }
}
