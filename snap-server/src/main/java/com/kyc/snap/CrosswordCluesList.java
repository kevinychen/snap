package com.kyc.snap;

import java.util.List;

import lombok.Data;

@Data
public class CrosswordCluesList {

    private final List<CrosswordClue> clues;

    @Data
    public static class CrosswordClue {

        private final CrosswordCluePosition position;
        private final String clue;
    }
}
