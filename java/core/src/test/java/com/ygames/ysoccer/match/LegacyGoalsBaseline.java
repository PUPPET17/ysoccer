package com.ygames.ysoccer.match;

import java.util.Random;

/** 第一阶段概率表的只读测试基准，仅用于验证均值和离线比较，不参与正式比赛。 */
final class LegacyGoalsBaseline {
    /** 行为攻防优势档位，列为 0～6 球，各行权重合计 1000。 */
    static final int[][] WEIGHTS = {
        {1000, 0, 0, 0, 0, 0, 0},
        {870, 100, 25, 4, 1, 0, 0},
        {730, 210, 50, 7, 2, 1, 0},
        {510, 320, 140, 20, 6, 4, 0},
        {390, 370, 180, 40, 10, 7, 3},
        {220, 410, 190, 150, 15, 10, 5},
        {130, 390, 240, 200, 18, 15, 7},
        {40, 300, 380, 230, 25, 15, 10},
        {20, 220, 240, 220, 120, 100, 80},
        {10, 150, 190, 190, 170, 150, 140},
        {0, 100, 150, 200, 200, 200, 150}
    };

    /** 精确计算原表某档位的整场期望进球数。 */
    static double mean(int row) {
        double mean = 0;
        for (int goals = 0; goals < WEIGHTS[row].length; goals++) {
            mean += goals * WEIGHTS[row][goals] / 1000.0;
        }
        return mean;
    }

    /** 重现第一阶段的累计插值及加时赛逐球保留算法。 */
    static int sample(double factor, boolean extraTime, Random random) {
        factor = Math.max(0, Math.min(10, factor));
        int lower = (int) Math.floor(factor);
        int upper = (int) Math.ceil(factor);
        int a = 0;
        int b = 0;
        int goals = 6;
        double draw = random.nextDouble() * 1000;
        for (int candidate = 0; candidate <= 6; candidate++) {
            a += WEIGHTS[lower][candidate];
            b += WEIGHTS[upper][candidate];
            if (draw < a + (b - a) * (factor - lower)) {
                goals = candidate;
                break;
            }
        }
        if (!extraTime) return goals;
        int retained = 0;
        for (int goal = 0; goal < goals; goal++) {
            if (random.nextDouble() < 1.0 / 3.0) retained++;
        }
        return retained;
    }
}
