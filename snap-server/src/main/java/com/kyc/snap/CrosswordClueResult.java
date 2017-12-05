package com.kyc.snap;

import lombok.Data;

@Data
public class CrosswordClueResult {

    private final String answer;
    private final int confidence;
}
