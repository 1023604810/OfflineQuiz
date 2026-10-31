package com.example.offlinequiz.engine;

import com.example.offlinequiz.data.entity.Question;
import com.example.offlinequiz.util.QuestionText;

import java.util.List;

/**
 * Builds a readable explanation for imported questions that shipped without one.
 *
 * <p>A bundled bank usually carries hand-written analyses; this factory only runs for
 * user-imported banks whose {@code analysis} column is empty, so every question still
 * has a usable explanation in 背题模式 and after 交卷.</p>
 */
public final class AnalysisFactory {

    private AnalysisFactory() {
    }

    public static String generate(Question q) {
        String type = q.type == null ? "single" : q.type;
        String knowledge = q.knowledge == null || q.knowledge.trim().isEmpty()
                ? "综合知识" : q.knowledge.trim();
        String answer = q.answer == null ? "" : q.answer.trim();
        switch (type) {
            case "multiple":
                return multiple(q, knowledge, answer);
            case "judge":
                return judge(knowledge, answer);
            case "blank":
                return blank(knowledge, answer);
            case "essay":
                return essay(knowledge, answer);
            default:
                return single(q, knowledge, answer);
        }
    }

    private static String single(Question q, String knowledge, String answer) {
        String chosen = optionText(q, answer);
        StringBuilder sb = new StringBuilder();
        sb.append("本题考查「").append(knowledge).append("」。正确答案是 ")
                .append(answer).append(" 项");
        if (!chosen.isEmpty()) {
            sb.append("「").append(chosen).append("」");
        }
        sb.append("，它符合题干的要求；其余选项与题意不符，可以逐项排除。\n");
        sb.append("判断方法：先看清题干问的是“正确”还是“错误”，再结合").append(knowledge)
                .append("的基本概念核对每个选项，避免只凭印象作答。");
        return sb.toString();
    }

    private static String multiple(Question q, String knowledge, String answer) {
        int count = answer.replaceAll("[^A-H]", "").length();
        StringBuilder sb = new StringBuilder();
        sb.append("本题考查「").append(knowledge).append("」，正确答案为 ").append(answer)
                .append("，共 ").append(count).append(" 项。\n");
        List<String> options = QuestionText.optionList(q);
        for (String option : options) {
            String[] parts = QuestionText.splitOption(option);
            if (!parts[0].isEmpty() && answer.contains(parts[0])) {
                sb.append("· ").append(parts[0]).append(" 项「").append(parts[1])
                        .append("」符合题意；\n");
            }
        }
        sb.append("多选题需要逐项判断：漏选按半对计分，选错则整题判错，因此拿不准的选项要回到概念本身确认。");
        return sb.toString();
    }

    private static String judge(String knowledge, String answer) {
        boolean yes = "T".equalsIgnoreCase(answer) || "正确".equals(answer) || "对".equals(answer);
        return "本题考查「" + knowledge + "」，题干说法" + (yes ? "正确" : "错误")
                + "。\n判断时抓住定义和适用条件：与标准定义一致的为正确，出现“必须”“所有”“一定”等绝对表述而"
                + "实际存在例外时，通常应判断为错误。";
    }

    private static String blank(String knowledge, String answer) {
        String[] blanks = answer.split("\\|", -1);
        StringBuilder sb = new StringBuilder();
        sb.append("本题考查「").append(knowledge).append("」，标准答案为：")
                .append(answer.replace("|", " / ")).append("。\n");
        sb.append("共 ").append(blanks.length)
                .append(" 个空，按顺序逐空填写；比对时忽略首尾空格与大小写，但多空必须全部正确才计分。");
        return sb.toString();
    }

    private static String essay(String knowledge, String answer) {
        return "本题考查「" + knowledge + "」，参考要点如下：\n" + answer
                + "\n主观题不自动判分，请对照要点检查关键概念是否齐全、表述是否准确，再完成自评。";
    }

    private static String optionText(Question q, String answer) {
        for (String option : QuestionText.optionList(q)) {
            String[] parts = QuestionText.splitOption(option);
            if (!parts[0].isEmpty() && parts[0].equalsIgnoreCase(answer)) {
                return parts[1];
            }
        }
        return "";
    }
}
