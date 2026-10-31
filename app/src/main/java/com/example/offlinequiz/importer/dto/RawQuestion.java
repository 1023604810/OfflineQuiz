package com.example.offlinequiz.importer.dto;

import java.util.List;

/** Raw question read from a bank file, before validation and DB mapping. */
public class RawQuestion {

    /** 1-based position in the source file (row number or array index). */
    public int sourceRow;

    public String id;
    public String stem;
    public String type;
    public List<String> options;
    public String answer;
    public String analysis;
    public String knowledge;
    public Integer difficulty;
}
