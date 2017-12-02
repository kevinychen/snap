package com.kyc.snap;

import java.util.List;

import lombok.Data;

@Data
public class ParsedGrid {

    private final List<ParsedGridSquare> squares;

    @Data
    public static class ParsedGridSquare {

        private final int row;
        private final int col;

        private final int rgb;
        private final String text;
    }
}
