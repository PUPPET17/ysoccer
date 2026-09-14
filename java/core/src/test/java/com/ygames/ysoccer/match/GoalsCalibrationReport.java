package com.ygames.ysoccer.match;

import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import java.lang.reflect.Proxy;
import com.ygames.ysoccer.framework.Assets;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import static com.ygames.ysoccer.match.MatchScoreSimulator.Venue.*;

/** 用内置球队和固定种子生成新旧比分模型对照报告；只读球队数据，不修改赛事或存档。 */
public final class GoalsCalibrationReport {
    /** 每个有序对阵的样本量；每种模型都遍历相同的主客场排列。 */
    private static final int REPEATS = 1000;
    /** 固定随机种子使参数调整前后的离线报告可复现。 */
    private static final long SEED = 20260913L;

    /** 参数依次为内置球队目录和 Markdown 输出路径；无需图形环境。 */
    public static void main(String[] args) throws Exception {
        com.badlogic.gdx.Files originalFiles = Gdx.files;
        // 只为 Assets 的静态路径创建句柄；球队读取使用 java.nio，不启动游戏后端。
        Gdx.files = (com.badlogic.gdx.Files) Proxy.newProxyInstance(com.badlogic.gdx.Files.class.getClassLoader(),
            new Class<?>[]{com.badlogic.gdx.Files.class}, (proxy, method, arguments) -> {
                if (method.getName().equals("local")) return new FileHandle((String) arguments[0]);
                throw new UnsupportedOperationException(method.getName());
            });
        Tactics original = Assets.tactics[0];
        try {
            Assets.tactics[0] = new Tactics();
            List<Team> teams = loadTeams(Paths.get(args[0]));
            if (teams.size() < 2) throw new IllegalArgumentException("At least two rated teams are required");
            List<Integer> keepers = teams.stream().map(t -> t.players.get(0).value).sorted().collect(Collectors.toList());
            double median = (keepers.get((keepers.size() - 1) / 2) + keepers.get(keepers.size() / 2)) / 2.0;
            StringBuilder report = new StringBuilder("# 自动比分模型校准报告\n\n");
            report.append("这是游戏平衡对照，不是对真实足球结果的拟合或预测验证。\n\n");
            report.append(String.format(Locale.ROOT,
                "数据：内置非自定义球队，完整首发且首发外场能力非全零，共 %d 支；排除能力全零占位球队。"
                + "首发门将中位数 %.1f、平均值 %.3f。模型基准门将 %.1f。\n\n",
                teams.size(), median, keepers.stream().mapToInt(Integer::intValue).average().getAsDouble(),
                MatchScoreSimulator.REFERENCE_KEEPER_VALUE));
            report.append("每种模型遍历所有不同球队的有序对阵，每对 1000 场，种子 20260913。"
                + "球队战术只排列首发前 11 人，不改变本次评分总和；门将取首发守门位置。\n\n");
            report.append("参数：门将倍率 exp(-0.4 × (value-28)/49)，主场倍率 1.08，90 分钟均值限制 [0.05, 5]；"
                + "加时赛乘 30/90。0.4 与 1.08 是保守调校值，不是数据拟合系数。\n\n");
            report.append("## 分离模型变化\n\n");
            report.append("泊松基线使用基准门将和中立场，保留原表均值；随后分别启用实际门将与主场修正。\n\n");
            report.append("| 模型 | 场次 | 场均总进球 | 平局率 | 单队零进球率 | 总进球≥6 | 单队≥7 | 主队胜率 | 强队胜率 | 进入点球率 |\n"
                + "|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|\n");
            String[] names = {"第一阶段概率表", "泊松基线", "泊松＋门将，中立场", "泊松＋门将，联赛主客场"};
            for (int model = 0; model < names.length; model++) {
                Random random = new Random(SEED);
                long games = 0, total = 0, draws = 0, zeros = 0, high = 0, seven = 0, homeWins = 0;
                long strongGames = 0, strongWins = 0, penalties = 0;
                for (Team home : teams) for (Team away : teams) {
                    if (home == away) continue;
                    double hf = factor(home, away), af = factor(away, home);
                    double difference = MatchScoreSimulator.baseExpectedGoals(hf) - MatchScoreSimulator.baseExpectedGoals(af);
                    double hm = mean(model, hf, away.players.get(0).value, true);
                    double am = mean(model, af, home.players.get(0).value, false);
                    for (int i = 0; i < REPEATS; i++) {
                        int h = sample(model, hf, hm, false, random), a = sample(model, af, am, false, random);
                        games++; total += h + a;
                        if (h == 0) zeros++;
                        if (a == 0) zeros++;
                        if (h + a >= 6) high++;
                        if (h >= 7) seven++;
                        if (a >= 7) seven++;
                        if (h > a) homeWins++;
                        if (Math.abs(difference) >= 0.5) {
                            strongGames++;
                            if ((h - a) * difference > 0) strongWins++;
                        }
                        if (h == a) {
                            draws++;
                            if (sample(model, hf, hm, true, random) == sample(model, af, am, true, random)) penalties++;
                        }
                    }
                }
                report.append(String.format(Locale.ROOT,
                    "| %s | %d | %.4f | %.2f%% | %.2f%% | %.2f%% | %.3f%% | %.2f%% | %.2f%% | %.2f%% |\n",
                    names[model], games, total / (double) games, 100.0 * draws / games, 50.0 * zeros / games,
                    100.0 * high / games, 50.0 * seven / games, 100.0 * homeWins / games,
                    strongGames == 0 ? 0 : 100.0 * strongWins / strongGames, 100.0 * penalties / games));
            }
            report.append("\n强队：旧版双方预期进球差绝对值≥0.5，强队胜率以这些对阵为分母。"
                + "单队零进球率和单队≥7比例以双方球队样本总数为分母。"
                + "进入点球率：假设所有比赛常规时间平局后均踢30分钟加时，仍平局的场次占全部场次比例；"
                + "联赛实际不会进行加时或点球。未模拟红牌、疲劳、比分导致的战术变化或两回合规则。\n\n");
            report.append("## 门将参数敏感性\n\n| 修正强度 | 门将0倍率 | 门将28倍率 | 门将49倍率 | 数据集平均进球相对旧均值变化 |\n"
                + "|---|---:|---:|---:|---:|\n");
            for (double influence : new double[]{0.2, 0.4, 0.6}) {
                double oldTotal = 0, newTotal = 0;
                for (Team attack : teams) for (Team defense : teams) {
                    if (attack == defense) continue;
                    double base = MatchScoreSimulator.baseExpectedGoals(factor(attack, defense));
                    oldTotal += base;
                    newTotal += Math.max(0.05, Math.min(5, base * Math.exp(-influence * (defense.players.get(0).value - 28) / 49.0)));
                }
                report.append(String.format(Locale.ROOT, "| %.1f | %.3f | 1.000 | %.3f | %+.2f%% |\n", influence,
                    Math.exp(influence * 28 / 49.0), Math.exp(-influence * 21 / 49.0), 100 * (newTotal / oldTotal - 1)));
            }
            report.append("\n选择中间值 0.4，使最低至最高门将的影响温和且单点提升可见。"
                + "主场暂取8%期望进球加成；仅联赛启用，杯赛和锦标赛因缺少明确场地定义保持中立。\n\n");
            report.append("## 均值曲线与范围\n\n| 档位 | 原表90分钟期望 | 新模型基准门将、中立场 |\n|---|---:|---:|\n");
            for (int tier = 0; tier <= 10; tier++) report.append(String.format(Locale.ROOT, "| %d | %.3f | %.3f |\n",
                tier, LegacyGoalsBaseline.mean(tier), MatchScoreSimulator.expectedGoals(tier, 28, 90, NEUTRAL)));
            report.append("\n最低档从必定零进球变为期望0.05；上界5略低于最高档、最低门将与主场叠加的约5.02。"
                + "上限约束的是均值，不是比分。泊松分布会改变平局和零进球概率，即使均值不变。\n\n");
            report.append("## 样本球队\n\n");
            for (Team team : teams) report.append("- ").append(team.name).append("，门将 ").append(team.players.get(0).value).append('\n');
            Path output = Paths.get(args[1]).toAbsolutePath();
            Files.createDirectories(output.getParent());
            Files.write(output, report.toString().getBytes(StandardCharsets.UTF_8));
            System.out.println("Calibration report written: " + output);
        } finally {
            Assets.tactics[0] = original;
            Gdx.files = originalFiles;
        }
    }

    /** 只读取有完整能力数据的非自定义球队，按路径排序以固定对阵顺序。 */
    private static List<Team> loadTeams(Path root) throws Exception {
        List<Path> paths;
        try (Stream<Path> stream = Files.walk(root)) {
            paths = stream.filter(p -> p.getFileName().toString().startsWith("team.")
                && p.toString().endsWith(".json")).sorted().collect(Collectors.toList());
        }
        List<Team> teams = new ArrayList<>();
        Json json = new Json();
        for (Path path : paths) {
            JsonValue raw = new JsonReader().parse(new String(Files.readAllBytes(path), StandardCharsets.UTF_8));
            String type = raw.getString("type", "");
            if (!type.equals("CLUB") && !type.equals("NATIONAL")) continue;
            JsonValue roster = raw.get("players");
            if (roster == null || roster.size < 11) continue;
            Team team = new Team();
            team.name = raw.getString("name");
            int outfieldTotal = 0;
            for (int i = 0; i < 11; i++) {
                Player player = json.readValue(Player.class, roster.get(i));
                player.team = team;
                team.players.add(player);
                if (player.role != Player.Role.GOALKEEPER) outfieldTotal += player.getValue();
            }
            if (outfieldTotal > 0 && team.players.get(0).role == Player.Role.GOALKEEPER) teams.add(team);
        }
        return teams;
    }

    /** 使用正式的球队评分方法，避免校准工具另写一套角色能力权重。 */
    private static double factor(Team attack, Team defense) {
        return (attack.offenseRating() - (double) defense.defenseRating() + 300) / 60.0;
    }

    /** 每组模型只打开对应修正，用于把分布、门将、场地的影响分开比较。 */
    private static double mean(int model, double factor, int keeper, boolean home) {
        return MatchScoreSimulator.expectedGoals(factor, model < 2 ? 28 : keeper, 90,
            model == 3 && home ? HOME : NEUTRAL);
    }

    /** 旧表只在此离线对照使用；新模型调用正式泊松抽样方法。 */
    private static int sample(int model, double factor, double mean, boolean extra, Random random) {
        return model == 0 ? LegacyGoalsBaseline.sample(factor, extra, random)
            : MatchScoreSimulator.samplePoisson(extra ? mean / 3 : mean, random);
    }
}
