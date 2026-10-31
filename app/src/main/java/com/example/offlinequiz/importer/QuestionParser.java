package com.example.offlinequiz.importer;

import com.example.offlinequiz.importer.dto.RawQuestion;

import java.io.InputStream;
import java.util.List;

public interface QuestionParser {

    /**
     * Parses the stream into raw questions. Rows that cannot be parsed at all are
     * reported into {@code result} and skipped; field validation happens later in
     * {@link BankImporter}.
     */
    void parse(InputStream in, ImportResult result, List<RawQuestion> out) throws Exception;
}
