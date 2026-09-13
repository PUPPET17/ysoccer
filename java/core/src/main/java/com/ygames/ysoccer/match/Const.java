package com.ygames.ysoccer.match;

import com.ygames.ysoccer.framework.EMath;
import com.ygames.ysoccer.framework.GLGame;

/**
 * 集中定义比赛场地几何参数、物理调校参数和球队配置数据。
 *
 * <p>除非另有说明，坐标均使用比赛场地坐标系。可变参数保持包级可见，供比赛设置在运行时调整。</p>
 */
public class Const {

    /** 一秒真实时间包含的模拟子帧数量。 */
    static final int SECOND = GLGame.SUBFRAMES_PER_SECOND;
    /** 足球半径（场地像素），用于碰撞和球门通行空间计算。 */
    static final int BALL_R = 4;
    /** 每个模拟子帧施加给足球的向下重力加速度。 */
    static float GRAVITY = 332.8f / SECOND;
    /** 运动中足球的空气阻力系数。 */
    static float AIR_FRICTION = 0.28f;
    /** 将足球旋转转换为横向位移的倍率。 */
    static float SPIN_FACTOR = 12.0f;
    /** 足球旋转随时间衰减的速率。 */
    static float SPIN_DAMPENING = 7.0f;
    /** 足球反弹后保留的速度比例。 */
    static float BOUNCE = 0.9f;
    /** 球员跑动动画单个循环的时长（秒）。 */
    static float PLAYER_RUN_ANIMATION = 0.18f;
    /** 将一次踢球判定为传球所需的最小方向对齐阈值。 */
    static float PASSING_THRESHOLD = 0.1f;
    /** 根据球员踢球力度计算传球速度时使用的缩放系数。 */
    static float PASSING_SPEED_FACTOR = 0.3f;
    /** 判断球员是否能射门时使用的角度容差（度）。 */
    static float SHOOTING_ANGLE_TOLERANCE = 22.5f;

    /** 作用于所有球员跑动速度的全局倍率。 */
    static final float PLAYER_MOVEMENT_SPEED_FACTOR = 0.85f;

    /** 原始游戏中球员近距离控球的最大距离（场地像素）。 */
    static final float ORIGINAL_DRIBBLE_CONTROL_DISTANCE = 9f;

    /** 持球球员可在运行时调整的最大控球距离。 */
    static float DRIBBLE_CONTROL_DISTANCE = 12f;

    /**
     * 原始游戏中的持球解除距离（场地像素）。
     */
    static final float ORIGINAL_BALL_OWNER_RELEASE_DISTANCE = 11f;

    /**
     * 运行时可调整的正常持球解除距离。该距离大于控球距离，避免在控球边界处频繁切换持球状态。
     */
    static float BALL_OWNER_RELEASE_DISTANCE = 15f;

    /** 原始游戏中带球时的跑动速度降低比例（10%）。 */
    static final float ORIGINAL_POSSESSION_SPEED_PENALTY = 0.1f;

    /** 带球时可在运行时调整的跑动速度降低比例。 */
    static float POSSESSION_SPEED_PENALTY = 0.05f;

    /** 回放可保存的最长时长（秒）。 */
    static final int REPLAY_DURATION = 8;
    /** 一段完整回放需要保留的虚拟渲染帧数。 */
    static final int REPLAY_FRAMES = REPLAY_DURATION * GLGame.VIRTUAL_REFRESH_RATE;
    /** 一段完整回放需要保留的模拟子帧数。 */
    static final int REPLAY_SUBFRAMES = REPLAY_DURATION * SECOND;
    /** 预测足球轨迹时使用的帧数。 */
    static final int BALL_PREDICTION = 2 * SECOND / GLGame.SUBFRAMES;

    /**
     * 自动比赛比分生成时，按本队进攻评分相对对手防守评分的优势档位索引的进球数权重。
     * 行索引对应 {@code (进攻评分 - 对手防守评分 + 300) / 60}，超界时使用端点档位，
     * 小数档位在相邻行之间线性插值。列索引表示常规时间单队进球数 0～6，最多生成 6 球。
     * 每行非负权重之和必须为 1000，单位为千分比；运行期间不应修改表内容。
     */
    static final int[][] GOALS_WEIGHTS_BY_ATTACK_ADVANTAGE = new int[][]{
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

    /** 创建国家队时可选的 FIFA 风格协会代码。 */
    public static String[] associations = new String[]{
        "AFG", "AIA", "ALB", "ALG", "AND", "ANG", "ARG", "ARM",
        "ARU", "ASA", "ATG", "AUS", "AUT", "AZE", "BAH", "BAN",
        "BDI", "BEL", "BEN", "BER", "BFA", "BHR", "BHU", "BIH",
        "BLR", "BLZ", "BOL", "BOT", "BRA", "BRB", "BRU", "BUL",
        "CAM", "CAN", "CAY", "CGO", "CHA", "CHI", "CHN", "CIV",
        "CMR", "COD", "COK", "COL", "COM", "CPV", "CRC", "CRO",
        "CTA", "CUB", "CUS", "CUW", "CYP", "CZE", "DEN", "DJI",
        "DMA", "DOM", "ECU", "EGY", "ENG", "EQG", "ERI", "ESP",
        "EST", "ETH", "FIJ", "FIN", "FRA", "FRO", "GAB", "GAM",
        "GEO", "GER", "GHA", "GNB", "GRE", "GRN", "GUA", "GUI",
        "GUM", "GUY", "HAI", "HKG", "HON", "HUN", "IDN", "IND",
        "IRL", "IRN", "IRQ", "ISL", "ISR", "ITA", "JAM", "JOR",
        "JPN", "KAZ", "KEN", "KGZ", "KOR", "KSA", "KUW", "LAO",
        "LBR", "LBY", "LCA", "LES", "LIB", "LIE", "LTU", "LUX",
        "LVA", "MAC", "MAD", "MAR", "MAS", "MDA", "MDV", "MEX",
        "MGL", "MKD", "MLI", "MLT", "MNE", "MOZ", "MRI", "MSR",
        "MTN", "MWI", "MYA", "NAM", "NCA", "NCL", "NED", "NEP",
        "NGA", "NIG", "NIR", "NOR", "NZL", "OMA", "PAK", "PAN",
        "PAR", "PER", "PHI", "PLE", "PNG", "POL", "POR", "PRK",
        "PUR", "QAT", "ROU", "RSA", "RUS", "RWA", "SAM", "SCO",
        "SDN", "SEN", "SEY", "SIN", "SKN", "SLE", "SLV", "SMR",
        "SOL", "SOM", "SRB", "SRI", "SSD", "STP", "SUI", "SUR",
        "SVK", "SVN", "SWE", "SWZ", "SYR", "TAH", "TAN", "TCA",
        "TGA", "THA", "TJK", "TKM", "TLS", "TOG", "TPE", "TRI",
        "TUN", "TUR", "UAE", "UGA", "UKR", "URU", "USA", "UZB",
        "VAN", "VEN", "VGB", "VIE", "VIN", "VIR", "WAL", "YEM",
        "ZAM", "ZIM"
    };

    /** 单支球队在场上的球员人数。 */
    public static final int TEAM_SIZE = 11;
    /** 未包含额外替补名额时的基础球队名单人数。 */
    public static final int BASE_TEAM = 16;
    /** 完整球队名单支持的最大球员人数。 */
    public static final int FULL_TEAM = 26;

    /** 两条球门线的绝对 Y 坐标。 */
    static final int GOAL_LINE = 640;
    /** 两条边线的绝对 X 坐标。 */
    static final int TOUCH_LINE = 510;
    /** 球门区宽度（场地像素）。 */
    static final int GOAL_AREA_W = 252;
    /** 球门区自球门线向场内延伸的深度（场地像素）。 */
    static final int GOAL_AREA_H = 58;
    /** 禁区宽度（场地像素）。 */
    static final int PENALTY_AREA_W = 572;
    /** 禁区自球门线向场内延伸的深度（场地像素）。 */
    static final int PENALTY_AREA_H = 174;
    /** 场地正 Y 半区中点球点的 Y 坐标。 */
    static final int PENALTY_SPOT_Y = 524;
    /** 球门线到对应点球点的距离（场地像素）。 */
    static final float PENALTY_DISTANCE = GOAL_LINE - PENALTY_SPOT_Y;
    /** 由点球点距离换算得到的一码对应场地像素长度。 */
    static final float YARD = PENALTY_DISTANCE / 12f;
    /** 任意球时对方球员需要保持的最小距离（场地像素）。 */
    static final float FREE_KICK_DISTANCE = 10f * YARD;
    /** AI 球员尝试直接射门时距球门柱的最大距离。 */
    static final float DIRECT_SHOT_DISTANCE = 310;

    /** 下层球门精灵图锚点的 X 坐标。 */
    static final int GOAL_BTM_X = -73;
    /** 下层球门精灵图锚点的 Y 坐标。 */
    static final int GOAL_BTM_Y = 603;

    /** 替补席球员放置位置的 X 坐标。 */
    static final int BENCH_X = -544;
    /** 上侧替补席的 Y 坐标。 */
    static final int BENCH_Y_UP = -198;
    /** 下侧替补席的 Y 坐标。 */
    static final int BENCH_Y_DOWN = 38;

    /** 球员精灵图高度（场地像素）。 */
    static final int PLAYER_H = 22;
    /** 球员精灵图宽度（场地像素）。 */
    static final int PLAYER_W = 12;
    /** 可活动场地区域的左侧 X 坐标边界。 */
    static final int FIELD_XMIN = -565;
    /** 可活动场地区域的右侧 X 坐标边界。 */
    static final int FIELD_XMAX = +572;
    /** 可活动场地区域的上侧 Y 坐标边界。 */
    static final int FIELD_YMIN = -700;
    /** 可活动场地区域的下侧 Y 坐标边界。 */
    static final int FIELD_YMAX = +698;

    /** 角旗杆高度（场地像素）。 */
    static final int FLAGPOST_H = 21;

    /** 完整球场美术资源的宽度（像素）。 */
    static final int PITCH_W = 1700;
    /** 完整球场美术资源的高度（像素）。 */
    static final int PITCH_H = 1800;

    /** 美术资源坐标系中球场中心的 X 坐标。 */
    static final int CENTER_X = 847;
    /** 美术资源坐标系中球场中心的 Y 坐标。 */
    static final int CENTER_Y = 919;

    /** 绘制起跳球员时使用的 X 坐标。 */
    static final int JUMPER_X = 92;
    /** 绘制起跳球员时使用的 Y 坐标。 */
    static final int JUMPER_Y = 684;
    /** 起跳球员精灵图的高度（像素）。 */
    static final int JUMPER_H = 40;

    /** 球门线后方球门的深度（场地像素）。 */
    static final int GOAL_DEPTH = 19;
    /** 任一球门柱中心的绝对 X 坐标。 */
    static final int POST_X = 71;
    /** 球门柱和横梁的半径（场地像素）。 */
    static final int POST_R = 2;
    /** 横梁相对场地平面的高度（场地像素）。 */
    static final int CROSSBAR_H = 33;

    /** 战术板位置之间的水平间距。 */
    public static final int TACT_DX = 68;
    /** 战术板位置之间的垂直间距。 */
    public static final int TACT_DY = 40;
    /** 战术中用于判定足球位置的场地区域数量。 */
    static final int BALL_ZONES = 35;

    /** 一个战术足球位置区域的宽度。 */
    static final int BALL_ZONE_DX = 206;
    /** 一个战术足球位置区域的高度。 */
    static final int BALL_ZONE_DY = 184;


    static boolean isInsidePenaltyArea(float x, float y, int ySide) {
        return Math.abs(x) < (PENALTY_AREA_W / 2)
            && EMath.isIn(y,
            ySide * (GOAL_LINE - PENALTY_AREA_H),
            ySide * GOAL_LINE
        );
    }

    static boolean isInsideGoalArea(float x, float y, int ySide) {
        return Math.abs(x) < (GOAL_AREA_W / 2)
            && EMath.isIn(y,
            ySide * (GOAL_LINE - GOAL_AREA_H),
            ySide * GOAL_LINE
        );
    }

    static boolean isInsideDirectShotArea(float x, float y, int ySide) {
        return ySide * y < GOAL_LINE
            && (EMath.dist(x, ySide * y, -POST_X, GOAL_LINE) < DIRECT_SHOT_DISTANCE
            || EMath.dist(x, ySide * y, POST_X, GOAL_LINE) < DIRECT_SHOT_DISTANCE);
    }

    static boolean seesTheGoal(float x, float y, float a) {
        int ySide = EMath.sgn(y);

        if (EMath.angleDiff(a, ySide * 90) > 90) return false;

        float x0 = -POST_X + (2 * POST_R + 2 * BALL_R);
        float y0 = ySide * GOAL_LINE;
        float m0 = EMath.tan(a - ySide * SHOOTING_ANGLE_TOLERANCE);
        float s0 = EMath.sgn(EMath.cos(a - ySide * SHOOTING_ANGLE_TOLERANCE));
        float b0 = y0 - m0 * x0;
        float x1 = +POST_X - (2 * POST_R + 2 * BALL_R);
        float y1 = ySide * GOAL_LINE;
        float m1 = EMath.tan(a + ySide * SHOOTING_ANGLE_TOLERANCE);
        float s1 = EMath.sgn(EMath.cos(a + ySide * SHOOTING_ANGLE_TOLERANCE));
        float b1 = y1 - m1 * x1;
        return s0 * ySide * y < s0 * ySide * (m0 * x + b0) && s1 * ySide * y > s1 * ySide * (m1 * x + b1);
    }

    static boolean isInsideGoal(float x, float y) {
        return Math.abs(x) < POST_X
            && Math.abs(y) > GOAL_LINE
            && Math.abs(y) < GOAL_LINE + GOAL_DEPTH;
    }

    /** 按球员能力等级索引的转会市场价格。 */
    public static int[] playerPrices = new int[]{
        25, 25, 30, 40, 50, 65, 75, 85, 100, 110,
        130, 150, 160, 180, 200, 250, 300, 350, 450, 500,
        550, 600, 650, 700, 750, 800, 850, 950, 1000, 1100,
        1300, 1500, 1600, 1800, 1900, 2000, 2250, 2750, 3000, 3500,
        4500, 5000, 6000, 7000, 8000, 9000, 10000, 12000, 15000, 15000
    };
};
