package com.example.offlinequiz.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.example.offlinequiz.data.entity.Question;

import org.junit.Test;

public class AnswerEngineTest {

    private static Question q(String type, String answer) {
        Question question = new Question();
        question.type = type;
        question.answer = answer;
        question.analysis = "解析";
        return question;
    }

    @Test
    public void singleExactMatch() {
        Question question = q("single", "B");
        assertEquals(GradeResult.Grade.CORRECT, AnswerEngine.grade(question, "b").grade);
        assertEquals(GradeResult.Grade.WRONG, AnswerEngine.grade(question, "C").grade);
        assertEquals(GradeResult.Grade.WRONG, AnswerEngine.grade(question, "").grade);
    }

    @Test
    public void multipleFullPartialWrong() {
        Question question = q("multiple", "ABD");
        assertEquals(GradeResult.Grade.CORRECT, AnswerEngine.grade(question, "DBA").grade);
        assertEquals(GradeResult.Grade.PARTIAL, AnswerEngine.grade(question, "AB").grade);
        assertEquals(GradeResult.Grade.WRONG, AnswerEngine.grade(question, "ABE").grade);
        assertEquals(GradeResult.Grade.WRONG, AnswerEngine.grade(question, "").grade);
    }

    @Test
    public void judgeAcceptsAliases() {
        Question question = q("judge", "T");
        assertEquals(GradeResult.Grade.CORRECT, AnswerEngine.grade(question, "对").grade);
        assertEquals(GradeResult.Grade.CORRECT, AnswerEngine.grade(question, "正确").grade);
        assertEquals(GradeResult.Grade.WRONG, AnswerEngine.grade(question, "F").grade);
    }

    @Test
    public void blankAllFieldsMustMatch() {
        Question question = q("blank", "Java|Kotlin");
        assertEquals(GradeResult.Grade.CORRECT, AnswerEngine.grade(question, " java | KOTLIN ").grade);
        assertEquals(GradeResult.Grade.WRONG, AnswerEngine.grade(question, "Java").grade);
        assertEquals(GradeResult.Grade.WRONG, AnswerEngine.grade(question, "Java|Go").grade);
    }

    @Test
    public void essayIsSelfEvaluated() {
        Question question = q("essay", "参考答案");
        GradeResult result = AnswerEngine.grade(question, "我的答案");
        assertEquals(GradeResult.Grade.SELF_EVAL, result.grade);
        assertTrue(result.correctAnswer.contains("参考"));
    }

    @Test
    public void normalizeLetterDropsDuplicates() {
        assertEquals("AC", AnswerEngine.normalizeLetter(" a,C,a "));
    }
}
