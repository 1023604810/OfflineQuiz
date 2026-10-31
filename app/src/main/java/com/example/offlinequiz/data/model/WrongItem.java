package com.example.offlinequiz.data.model;

/** Row model for "wrong book" list items. */
public class WrongItem {
    public long questionId;
    public String stem;
    public String type;
    public String knowledge;
    public int wrongCount;
    public long lastWrongAt;
}
