package com.kyc.snap;

import lombok.Data;

@Data
public class CrosswordCluePosition {

    private final String number;
    private final CrosswordClueOrientation orientation;

    public enum CrosswordClueOrientation {
        ACROSS,
        DOWN,
    }
}
