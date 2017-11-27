package com.kyc.snap;

import lombok.Data;

@Data
public class GridifyRequest {

    private final byte[] data;

    private int cannyThreshold1 = 60;
    private int cannyThreshold2 = 180;
    private int houghThreshold = 32;
    private int houghMinLineLength = 16;
    private int minDistBetweenGridLines = 16;
}
