package com.example.offlinequiz.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.example.offlinequiz.data.entity.Question;

import org.junit.Test;

/** Difficulty scoring: knowledge coverage, structure and measured error rate. */
public class DifficultyAnalyzerTest {

    private static Question question(String stem, String... options) {
        Question q = new Question();
        q.stem = stem;
        q.type = "single";
        q.answer = "A";
        q.knowledge = "测试";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < options.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append('"').append((char) ('A' + i)).append(". ").append(options[i]).append('"');
        }
        sb.append(']');
        q.options = sb.toString();
        return q;
    }

    @Test
    public void simpleQuestionIsEasy() {
        Question q = question("TCP 协议工作在 OSI 的哪一层？", "物理层", "数据链路层", "传输层", "应用层");
        DifficultyAnalyzer.Breakdown b = DifficultyAnalyzer.analyze(q, 0, 0, 0, 0);
        assertTrue("score=" + b.score + " reason=" + b.reason, b.score < DifficultyAnalyzer.HARD_THRESHOLD);
        assertEquals(DifficultyAnalyzer.LEVEL_EASY, b.level);
        assertTrue(b.reason.contains("知识点覆盖"));
    }

    @Test
    public void negationCalculationAndLongOptionsRaiseDifficulty() {
        Question q = question(
                "如图所示，某园区网中所有路由器运行 OSPF 协议，管理员希望计算 RTA 到达 10.0.0.0/8 的最多可用路径开销，"
                        + "并要求配置 ACL 限制 VLAN 间访问，下列说法错误的是？",
                "根据图中链路开销累加计算得到 60，且需在接口下配置 ACL 规则",
                "根据图中链路开销累加计算得到 70，并在 system-view 下配置静态路由",
                "使用 display ip routing-table 查看路由表后再判断，结果为 20",
                "在 OSPF 区域 0 内使用 display ospf peer 查看邻居状态",
                "以上说法都需要结合组网图才能判断");
        DifficultyAnalyzer.Breakdown b = DifficultyAnalyzer.analyze(q, 0, 0, 0, 0);
        assertTrue("score=" + b.score + " reason=" + b.reason, b.score >= DifficultyAnalyzer.HARD_THRESHOLD);
        assertEquals(DifficultyAnalyzer.LEVEL_HARD, b.level);
        assertTrue(b.reason.contains("否定问法"));
    }

    @Test
    public void measuredErrorRateInfluencesScore() {
        Question q = question("静态路由的缺省优先级是多少？", "0", "10", "60", "100");
        int neutral = DifficultyAnalyzer.analyze(q, 0, 0, 0, 0).score;
        int measured = DifficultyAnalyzer.analyze(q, 10, 9, 20, 14).score;
        assertTrue("measured=" + measured + " neutral=" + neutral, measured > neutral);
        assertTrue(DifficultyAnalyzer.analyze(q, 10, 9, 20, 14).reason.contains("错误率"));
    }

    @Test
    public void scoreIsDeterministicAndBounded() {
        Question q = question("OSPF 的缺省协议优先级是？", "10", "60", "100", "0");
        DifficultyAnalyzer.Breakdown a = DifficultyAnalyzer.analyze(q, 3, 1, 6, 2);
        DifficultyAnalyzer.Breakdown b = DifficultyAnalyzer.analyze(q, 3, 1, 6, 2);
        assertEquals(a.score, b.score);
        assertEquals(a.level, b.level);
        assertTrue(a.score >= 0 && a.score <= 100);
        assertEquals("简单", DifficultyAnalyzer.levelLabel(DifficultyAnalyzer.LEVEL_EASY));
        assertEquals("中等", DifficultyAnalyzer.levelLabel(DifficultyAnalyzer.LEVEL_MEDIUM));
        assertEquals("困难", DifficultyAnalyzer.levelLabel(DifficultyAnalyzer.LEVEL_HARD));
    }

    @Test
    public void generatedAnalysisFillsEmptyExplanation() {
        Question q = question("VRP 中修改设备名称的命令是？", "sysname", "hostname", "rename", "name");
        q.analysis = null;
        String text = AnalysisFactory.generate(q);
        assertTrue(text.length() > 20);
        assertTrue(text.contains("sysname"));
    }
}
