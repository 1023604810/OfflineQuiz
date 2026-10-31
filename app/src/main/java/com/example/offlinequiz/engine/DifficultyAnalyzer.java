package com.example.offlinequiz.engine;

import com.example.offlinequiz.data.entity.Question;
import com.example.offlinequiz.util.QuestionText;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Computes a 0-100 difficulty score for one question and maps it to 简单 / 中等 / 困难.
 *
 * <p>Three dimensions, mirroring the product requirement:</p>
 * <ul>
 *   <li>知识点覆盖度 (0-30): how many distinct technical concepts the stem/options touch;</li>
 *   <li>题干结构复杂度 (0-40): negation wording, calculations, long options, command blocks,
 *       figure references and option count - these drive most real mistakes;</li>
 *   <li>实测易错率 (0-30): wrong-answer ratio for this question, blended with its knowledge
 *       tag when both have enough samples. Without data it falls back to a neutral prior.</li>
 * </ul>
 *
 * <p>The result is deterministic for the same question and statistics, so the
 * calibration step can be re-run safely.</p>
 */
public final class DifficultyAnalyzer {

    public static final int LEVEL_EASY = 1;
    public static final int LEVEL_MEDIUM = 2;
    public static final int LEVEL_HARD = 3;

    /** Below this score a question is "简单", at/above {@link #HARD_THRESHOLD} it is "困难". */
    public static final int EASY_THRESHOLD = 38;
    public static final int HARD_THRESHOLD = 55;

    private static final int MIN_QUESTION_ATTEMPTS = 3;
    private static final int MIN_KNOWLEDGE_ATTEMPTS = 5;

    private static final Pattern NEGATION = Pattern.compile(
            "错误|不正确|不包括|不属于|不能|不可能|并非|以下哪个不|哪项不|说法错误");
    private static final Pattern CALCULATION = Pattern.compile(
            "最多|至少|多少|可增加|可用|计算|网段|掩码长度|\\d+\\s*(bit|字节)|/\\d{1,2}\\b");
    private static final Pattern COMMAND = Pattern.compile(
            "display\\s|system-view|\\[huawei|interface\\s|port\\s|rule\\s|sysname");
    private static final Pattern FIGURE = Pattern.compile("如图|图示|下图|上图");

    /** {display name, regex}; distinct display names are counted for coverage. */
    private static final String[][] CONCEPT_SOURCE = {
            {"OSPF", "ospf"},
            {"BGP", "bgp"},
            {"RIP", "rip"},
            {"RSTP", "rstp"},
            {"STP", "stp"},
            {"VLAN", "vlan"},
            {"ACL", "acl"},
            {"NAT", "nat|napt|easy-ip"},
            {"DHCP", "dhcp"},
            {"DNS", "dns"},
            {"SNMP", "snmp"},
            {"MPLS", "mpls|ldp"},
            {"SDN", "sdn|openflow"},
            {"CAPWAP", "capwap"},
            {"WLAN", "wlan|wifi"},
            {"SSID", "ssid"},
            {"PPP", "pppoe|ppp"},
            {"LACP", "lacp"},
            {"VRRP", "vrrp"},
            {"QoS", "qos|dscp"},
            {"IPv6", "ipv6"},
            {"IPv4", "ipv4"},
            {"ARP", "arp"},
            {"ICMP", "icmp"},
            {"TCP", "tcp"},
            {"UDP", "udp"},
            {"Telnet", "telnet"},
            {"FTP", "ftp"},
            {"Python", "python"},
            {"SR", "segment|\\bsr\\b"},
            {"VPN", "vpn|ipsec"},
            {"AAA", "radius|aaa"},
            {"EUI-64", "eui-64"},
            {"TTL", "ttl"},
            {"Tracert", "tracert|traceroute"},
            {"生成树", "生成树"},
            {"链路聚合", "链路聚合"},
            {"子网", "子网|掩码"},
            {"路由", "路由表|静态路由|动态路由"},
            {"组播", "组播"},
            {"广播", "广播"},
            {"分片", "分片"},
            {"认证", "认证"},
            {"隧道", "隧道"},
            {"标签", "标签"},
            {"度量值", "开销|度量值|cost"},
    };

    /** Same order as {@link #CONCEPT_SOURCE}, compiled once. */
    private static final Pattern[] CONCEPT_PATTERNS = new Pattern[CONCEPT_SOURCE.length];

    static {
        for (int i = 0; i < CONCEPT_SOURCE.length; i++) {
            CONCEPT_PATTERNS[i] = Pattern.compile(latinAware(CONCEPT_SOURCE[i][1]));
        }
    }

    private DifficultyAnalyzer() {
    }

    public static final class Breakdown {
        public final int score;
        public final int level;
        public final int coverageScore;
        public final int structureScore;
        public final int errorScore;
        public final List<String> concepts;
        public final String reason;

        Breakdown(int score, int level, int coverageScore, int structureScore, int errorScore,
                  List<String> concepts, String reason) {
            this.score = score;
            this.level = level;
            this.coverageScore = coverageScore;
            this.structureScore = structureScore;
            this.errorScore = errorScore;
            this.concepts = concepts;
            this.reason = reason;
        }
    }

    public static Breakdown analyze(Question q, int questionAttempts, int questionWrong,
                                    int knowledgeAttempts, int knowledgeWrong) {
        String text = ((q.stem == null ? "" : q.stem) + " "
                + (q.options == null ? "" : q.options)).toLowerCase(Locale.ROOT);

        List<String> concepts = conceptsOf(text);
        int coverage = Math.min(32, concepts.size() * 8);
        int structure = structureScore(q, text, concepts);
        structure = Math.min(50, structure + advancedTopicBonus(q.knowledge));
        int structureTotal = structure;

        int questionRate = questionAttempts > 0 ? questionWrong * 100 / questionAttempts : -1;
        int knowledgeRate = knowledgeAttempts > 0 ? knowledgeWrong * 100 / knowledgeAttempts : -1;
        int error;
        String errorNote;
        if (questionAttempts >= MIN_QUESTION_ATTEMPTS
                && knowledgeAttempts >= MIN_KNOWLEDGE_ATTEMPTS) {
            error = Math.round(30 * (0.6f * questionRate + 0.4f * knowledgeRate) / 100f);
            errorNote = "实测错误率 " + questionRate + "%（本题 " + questionAttempts + " 次作答）";
        } else if (questionAttempts >= MIN_QUESTION_ATTEMPTS) {
            error = Math.round(30 * questionRate / 100f);
            errorNote = "实测错误率 " + questionRate + "%（本题 " + questionAttempts + " 次作答）";
        } else if (knowledgeAttempts >= MIN_KNOWLEDGE_ATTEMPTS) {
            error = Math.round(24 * knowledgeRate / 100f);
            errorNote = "该知识点实测错误率 " + knowledgeRate + "%（" + knowledgeAttempts + " 次作答）";
        } else {
            error = 12;
            errorNote = "暂无作答数据，按题干特征预估";
        }

        int score = clamp(coverage + structureTotal + error, 0, 100);
        int level = score < EASY_THRESHOLD ? LEVEL_EASY
                : score >= HARD_THRESHOLD ? LEVEL_HARD : LEVEL_MEDIUM;

        StringBuilder reason = new StringBuilder();
        reason.append("知识点覆盖 ").append(concepts.size()).append(" 项");
        if (!concepts.isEmpty()) {
            reason.append("（");
            for (int i = 0; i < Math.min(3, concepts.size()); i++) {
                if (i > 0) {
                    reason.append('/');
                }
                reason.append(concepts.get(i));
            }
            if (concepts.size() > 3) {
                reason.append(" 等");
            }
            reason.append("）");
        }
        List<String> features = structureFeatures(q, text);
        if (!features.isEmpty()) {
            reason.append("；易错特征：").append(join(features, "、"));
        }
        reason.append("；").append(errorNote);
        reason.append("；综合 ").append(score).append(" 分 → ").append(levelLabel(level));

        return new Breakdown(score, level, coverage, structureTotal, error, concepts,
                reason.toString());
    }

    private static List<String> conceptsOf(String text) {
        Set<String> found = new LinkedHashSet<>();
        for (int i = 0; i < CONCEPT_SOURCE.length; i++) {
            if (CONCEPT_PATTERNS[i].matcher(text).find()) {
                found.add(CONCEPT_SOURCE[i][0]);
            }
        }
        return new ArrayList<>(found);
    }

    /** Adds letter boundaries without breaking against Chinese characters. */
    private static String latinAware(String alternation) {
        String[] parts = alternation.split("\\|");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (sb.length() > 0) {
                sb.append('|');
            }
            if (part.matches("[a-z0-9\\-]+")) {
                sb.append("(?<![a-z0-9])").append(part).append("(?![a-z0-9])");
            } else {
                sb.append(part);
            }
        }
        return sb.toString();
    }

    private static int structureScore(Question q, String text, List<String> concepts) {
        int score = 8; // every question carries a basic reading/recall cost
        int options = QuestionText.optionList(q).size();
        if (options >= 5) {
            score += 8;
        } else if (options >= 3) {
            score += 4;
        }
        String stem = q.stem == null ? "" : q.stem;
        boolean negation = NEGATION.matcher(stem).find();
        boolean calculation = CALCULATION.matcher(text).find();
        boolean figure = FIGURE.matcher(stem).find();
        if (negation) {
            score += 10;
        }
        if (calculation) {
            score += 8;
        }
        if (negation && calculation) {
            score += 5;
        }
        int maxOptionLength = maxOptionLength(q);
        if (maxOptionLength > 45) {
            score += 8;
        } else if (maxOptionLength > 25) {
            score += 5;
        }
        if (COMMAND.matcher(text).find()) {
            score += 7;
        }
        if (figure) {
            score += 6;
        }
        if (figure && calculation) {
            score += 4;
        }
        int latin = distinctLatinTokens(text);
        if (latin >= 6) {
            score += 7;
        } else if (latin >= 3) {
            score += 4;
        }
        int stemLength = stem.length();
        if (stemLength > 80) {
            score += 6;
        } else if (stemLength > 45) {
            score += 3;
        }
        if (concepts.size() >= 4) {
            score += 6;
        }
        return score;
    }

    /** Advanced technologies need more background knowledge than basic switching/routing. */
    private static int advancedTopicBonus(String knowledge) {
        if (knowledge == null) {
            return 0;
        }
        String k = knowledge.toLowerCase(Locale.ROOT);
        if (k.contains("mpls") || k.contains("sdn") || k.contains("qos")
                || k.contains("ipv6") || k.contains("无线") || k.contains("snmp")
                || k.contains("acl") || k.contains("vrrp")) {
            return 6;
        }
        return 0;
    }

    private static List<String> structureFeatures(Question q, String text) {
        List<String> features = new ArrayList<>();
        String stem = q.stem == null ? "" : q.stem;
        if (NEGATION.matcher(stem).find()) {
            features.add("否定问法");
        }
        if (CALCULATION.matcher(text).find()) {
            features.add("需计算/换算");
        }
        if (maxOptionLength(q) > 45) {
            features.add("选项较长");
        }
        if (COMMAND.matcher(text).find()) {
            features.add("命令配置");
        }
        if (FIGURE.matcher(stem).find()) {
            features.add("依赖组网图");
        }
        if (QuestionText.optionList(q).size() >= 5) {
            features.add("五个选项");
        }
        if (advancedTopicBonus(q.knowledge) > 0) {
            features.add("进阶专题");
        }
        return features;
    }

    private static int maxOptionLength(Question q) {
        int max = 0;
        for (String option : QuestionText.optionList(q)) {
            max = Math.max(max, option.length());
        }
        return max;
    }

    private static int distinctLatinTokens(String text) {
        Set<String> tokens = new LinkedHashSet<>();
        Matcher m = Pattern.compile("[a-z][a-z0-9-]{1,}").matcher(text);
        while (m.find()) {
            tokens.add(m.group());
            if (tokens.size() >= 8) {
                break;
            }
        }
        return tokens.size();
    }

    public static String levelLabel(int level) {
        switch (level) {
            case LEVEL_EASY:
                return "简单";
            case LEVEL_HARD:
                return "困难";
            default:
                return "中等";
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String join(List<String> parts, String sep) {
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (sb.length() > 0) {
                sb.append(sep);
            }
            sb.append(p);
        }
        return sb.toString();
    }
}
