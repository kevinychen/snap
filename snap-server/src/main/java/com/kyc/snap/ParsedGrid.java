package com.kyc.snap;

import java.util.Set;

import lombok.Data;

@Data
public class ParsedGrid {

    private final Set<ParsedGridSquare> squares;

    @Data
    public static class ParsedGridSquare {

        private final int row;
        private final int col;

        private final int rgb;
    }
}
