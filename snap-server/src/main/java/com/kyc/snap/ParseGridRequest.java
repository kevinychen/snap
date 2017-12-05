package com.kyc.snap;

import lombok.Data;

@Data
public class ParseGridRequest {

    private final byte[] data;
    private final Grid grid;

    private int numClusters = 2;
    private double crosswordThreshold = 0.5;
}
