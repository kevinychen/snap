package com.kyc.snap;

import java.util.List;

import lombok.Data;

@Data
public class ParsedGrid {

    private final int numRows;
    private final int numCols;
    private final List<ParsedGridSquare> squares;

    @Data
    public static class ParsedGridSquare {

        private final int row;
        private final int col;

        private int rgb = -1;
        private String text = "";
        private int rightBorderRgb = -1;
        private int bottomBorderRgb = -1;
    }
}
