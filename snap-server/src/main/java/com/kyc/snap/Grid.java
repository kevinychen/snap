package com.kyc.snap;

import java.util.List;

import lombok.Data;

@Data
public class Grid {

    private final List<GridRow> rows;
    private final List<GridCol> cols;

    @Data
    public static class GridRow {

        private final int startY;
        private final int height;
    }

    @Data
    public static class GridCol {

        private final int startX;
        private final int width;
    }
}
