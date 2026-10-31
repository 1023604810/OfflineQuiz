package com.example.offlinequiz.importer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.example.offlinequiz.importer.dto.RawQuestion;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class CsvQuestionParserTest {

    @Test
    public void parsesQuotedCellsAndMultilineOptions() throws Exception {
        String csv = "id,stem,type,options,answer,analysis,knowledge,difficulty\n"
                + "Q1,\"题干一, 带逗号\",single,\"A. 甲\nB. 乙\nC. 丙\",B,\"解析文本\",Java 基础,1\n"
                + "Q2,题干二,multiple,A. 甲|B. 乙|C. 丙|D. 丁,ACD,,\"集合/Map\",3\n";
        ImportResult result = new ImportResult();
        List<RawQuestion> out = new ArrayList<>();
        new CsvQuestionParser().parse(
                new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)), result, out);

        assertEquals(2, out.size());
        assertEquals("题干一, 带逗号", out.get(0).stem);
        assertEquals(3, out.get(0).options.size());
        assertEquals("B. 乙", out.get(0).options.get(1));
        assertEquals(1, (long) out.get(0).difficulty);
        assertEquals(4, out.get(1).options.size());
        assertEquals("ACD", out.get(1).answer);
        assertEquals(3, (long) out.get(1).difficulty);
    }

    @Test
    public void supportsChineseHeaders() throws Exception {
        String csv = "题干,题型,选项,答案\n什么是VRP,填空,,虚拟路由平台\n";
        ImportResult result = new ImportResult();
        List<RawQuestion> out = new ArrayList<>();
        new CsvQuestionParser().parse(
                new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)), result, out);
        assertEquals(1, out.size());
        assertEquals("什么是VRP", out.get(0).stem);
        assertEquals("填空", out.get(0).type);
    }

    @Test
    public void decodesGbkFiles() throws Exception {
        String csv = "stem,type,answer\n中文题干,judge,T\n";
        byte[] gbk = csv.getBytes(java.nio.charset.Charset.forName("GBK"));
        ImportResult result = new ImportResult();
        List<RawQuestion> out = new ArrayList<>();
        new CsvQuestionParser().parse(new ByteArrayInputStream(gbk), result, out);
        assertEquals("中文题干", out.get(0).stem);
        assertTrue(result.errors.isEmpty());
    }
}
