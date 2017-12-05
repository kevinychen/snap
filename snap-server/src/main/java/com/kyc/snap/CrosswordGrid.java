package com.kyc.snap;

import java.util.List;

import lombok.Data;

@Data
public class CrosswordGrid {

    private final List<CrosswordBlank> blanks;

    @Data
    public static final class CrosswordBlank {

        private final String number;
        private final CrosswordClueOrientation orientation;
        private final int row;
        private final int col;
        private final int length;
    }
}
