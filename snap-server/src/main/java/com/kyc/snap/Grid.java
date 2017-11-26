package com.kyc.snap;

import java.util.Set;

import lombok.Data;

@Data
public class Grid {

    private final Set<Integer> rows;
    private final Set<Integer> cols;
}
