package com.kyc.snap;

import lombok.Data;

@Data
public class SolveCrosswordRequest {

    private final Grid grid;
    private final ParsedGrid parsedGrid;
    private final String cluesString;
}
