package com.ygames.ysoccer.match;

import java.util.Arrays;
import java.util.Random;

/** 无图形环境的自动比分回归检查，覆盖抽样边界、概率表插值和加时赛时长折算。 */
public final class GoalsRegressionTest {
    /** 已验证的业务断言数量，用于在 Gradle 输出中确认检查实际执行。 */
    private static int checks;

    /** 执行确定性检查；失败时抛出 AssertionError，使 Gradle 任务失败。 */
    public static void main(String[] args) {
        tableDistributions();
        samplingBoundaries();
        extraTimeDistribution();
        seededReplay();
        System.out.println("Goals regression checks passed: " + checks);
    }

    /** 用均匀网格精确核对各档位频数，避免依赖随机波动判断是否保持原有比分风格。 */
    private static void tableDistributions() {
        int[][] table = Const.GOALS_WEIGHTS_BY_ATTACK_ADVANTAGE;
        for (int row = 0; row < table.length; row++) {
            int total = 0;
            check(table[row].length == 7, "normal-time scores remain 0..6");
            for (int weight : table[row]) {
                check(weight >= 0, "goal weights are nonnegative");
                total += weight;
            }
            check(total == 1000, "each advantage row totals 1000");
            check(Arrays.equals(histogram(row, 1000), table[row]), "integer tier " + row);
            if (row + 1 < table.length) {
                int[] expected = new int[7];
                for (int goal = 0; goal < expected.length; goal++) {
                    // 在四分之一档位使用 4000 个网格点，每格仍有整数个期望样本。
                    expected[goal] = 3 * table[row][goal] + table[row + 1][goal];
                }
                check(Arrays.equals(histogram(row + 0.25, 4000), expected),
                    "fractional tier preserves unrounded weights: " + row);
            }
        }
        check(Arrays.equals(histogram(-100, 1000), table[0]), "lower clamp");
        check(Arrays.equals(histogram(100, 1000), table[table.length - 1]), "upper clamp");
    }

    /** 确认随机区间左闭右开，不返回负进球数，也不选择零权重列。 */
    private static void samplingBoundaries() {
        check(draw(5, false, 0) == 0, "zero random draw is a valid score");
        check(draw(10, false, 0) == 1, "skip zero-weight first column");
        check(draw(0, false, Math.nextDown(1.0)) == 0, "skip zero-weight tail");
        check(draw(5, false, Math.nextDown(1.0)) == 6, "last nonzero interval");
        check(draw(5, false, 0.219999) == 0, "just below first boundary");
        check(draw(5, false, 0.22) == 1, "exact first boundary");
        check(draw(5, false, 0.63) == 2, "exact second boundary");
        check(draw(1.25, false, 0.834999) == 0, "below interpolated boundary");
        check(draw(1.25, false, 0.835) == 1, "exact interpolated boundary");
        check(draw(0, true, 0) == 0, "zero goals needs no extra-time draws");
        check(draw(5, true, 0.3, Math.nextDown(1.0 / 3.0)) == 1,
            "one normal-time goal can survive thinning");
        check(draw(5, true, 0.3, 1.0 / 3.0) == 0, "retention boundary is exclusive");
    }

    /** 枚举每个整场进球数的全部保留组合，按概率加权核对加时赛分布和期望。 */
    private static void extraTimeDistribution() {
        int[] weights = Const.GOALS_WEIGHTS_BY_ATTACK_ADVANTAGE[5];
        int cumulative = 0;
        double totalMean = 0;
        for (int goals = 0; goals < weights.length; goals++) {
            double normalDraw = (cumulative + weights[goals] / 2.0) / 1000;
            cumulative += weights[goals];
            double[] probabilities = new double[goals + 1];
            for (int mask = 0; mask < (1 << goals); mask++) {
                double[] sequence = new double[goals + 1];
                sequence[0] = normalDraw;
                double probability = 1;
                for (int goal = 0; goal < goals; goal++) {
                    boolean retained = (mask & (1 << goal)) != 0;
                    sequence[goal + 1] = retained ? 0.1 : 0.9;
                    probability *= retained ? 1.0 / 3.0 : 2.0 / 3.0;
                }
                int extra = draw(5, true, sequence);
                check(extra == Integer.bitCount(mask), "each retained goal is counted once");
                probabilities[extra] += probability;
            }
            double mean = 0;
            double mass = 0;
            for (int extra = 0; extra < probabilities.length; extra++) {
                mean += extra * probabilities[extra];
                mass += probabilities[extra];
            }
            near(mass, 1, "extra-time probability mass");
            near(mean, goals / 3.0, "extra-time conditional expectation");
            totalMean += weights[goals] / 1000.0 * mean;
            if (goals == 2) {
                near(probabilities[0], 4.0 / 9.0, "two-goal thinning: zero");
                near(probabilities[1], 4.0 / 9.0, "two-goal thinning: one");
                near(probabilities[2], 1.0 / 9.0, "two-goal thinning: two");
            }
        }
        near(totalMean, 0.46, "tier 5 extra-time mean is 1.38 / 3");
    }

    /** 相同种子和相同调用顺序必须能复现常规时间及加时赛比分。 */
    private static void seededReplay() {
        Random first = new Random(20260913L);
        Random replay = new Random(20260913L);
        for (int match = 0; match < 1000; match++) {
            double factor = (match % 121) / 10.0 - 1;
            boolean extra = match % 2 == 0;
            int score = Match.generateGoals(factor, extra, first);
            check(score == Match.generateGoals(factor, extra, replay), "seeded replay");
            check(score >= 0 && score <= 6, "score range");
        }
    }

    /** 将随机区间等分并取中点，返回各进球数占用的网格数量。 */
    private static int[] histogram(double factor, int samples) {
        int[] counts = new int[7];
        for (int sample = 0; sample < samples; sample++) {
            counts[draw(factor, false, (sample + 0.5) / samples)]++;
        }
        return counts;
    }

    /** 使用指定随机序列抽样，并确认没有多消耗或漏消耗加时赛的逐球判定。 */
    private static int draw(double factor, boolean extra, double... sequence) {
        SequenceRandom random = new SequenceRandom(sequence);
        int goals = Match.generateGoals(factor, extra, random);
        check(random.index == sequence.length, "all expected random draws consumed");
        return goals;
    }

    /** 概率枚举仅允许浮点运算误差，不使用宽松的统计容差。 */
    private static void near(double actual, double expected, String message) {
        check(Math.abs(actual - expected) < 1e-12, message + ": " + actual);
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    /** 提供可精确命中累计概率边界的随机序列；额外请求随机值会使测试立即失败。 */
    private static final class SequenceRandom extends Random {
        /** 先提供整场比分抽样值，随后提供每个进球的加时赛保留判定值。 */
        private final double[] sequence;
        /** 下一个待消费的随机值位置。 */
        private int index;

        private SequenceRandom(double[] sequence) {
            this.sequence = sequence;
        }

        @Override
        public double nextDouble() {
            if (index >= sequence.length) {
                throw new AssertionError("unexpected random draw");
            }
            return sequence[index++];
        }
    }
}
