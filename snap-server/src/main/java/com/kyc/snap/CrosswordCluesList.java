package com.kyc.snap;

import java.util.List;

import lombok.Data;

@Data
public class CrosswordCluesList {

    private final List<CrosswordClue> clues;

    @Data
    public static class CrosswordClue {

        private final String number;
        private final Orientation orientation;
        private final String clue;
    }

    public static enum Orientation {
        ACROSS,
        DOWN,
    }
}
