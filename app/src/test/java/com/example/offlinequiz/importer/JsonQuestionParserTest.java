package com.example.offlinequiz.importer;

import static org.junit.Assert.assertEquals;

import com.example.offlinequiz.importer.dto.RawQuestion;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class JsonQuestionParserTest {

    @Test
    public void parsesWrappedBank() throws Exception {
        String json = "{\"subject\":\"Java 基础\",\"version\":\"1.0\",\"questions\":["
                + "{\"id\":\"Q001\",\"stem\":\"题干\",\"type\":\"single\","
                + "\"options\":[\"A. 甲\",\"B. 乙\"],\"answer\":\"B\",\"analysis\":\"解析\","
                + "\"knowledge\":\"Java 基础/数据类型\",\"difficulty\":1},"
                + "{\"stem\":\"第二题\",\"type\":\"essay\",\"answer\":\"参考答案\"}"
                + "]}";
        ImportResult result = new ImportResult();
        List<RawQuestion> out = new ArrayList<>();
        new JsonQuestionParser().parse(
                new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)), result, out);

        assertEquals(2, out.size());
        assertEquals("Q001", out.get(0).id);
        assertEquals(2, out.get(0).options.size());
        assertEquals(1, (int) out.get(0).difficulty);
        assertEquals(2, out.get(1).sourceRow);
    }

    @Test
    public void parsesBareArray() throws Exception {
        String json = "[{\"stem\":\"题干\",\"type\":\"judge\",\"answer\":\"T\"}]";
        ImportResult result = new ImportResult();
        List<RawQuestion> out = new ArrayList<>();
        new JsonQuestionParser().parse(
                new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)), result, out);
        assertEquals(1, out.size());
        assertEquals("T", out.get(0).answer);
    }
}
