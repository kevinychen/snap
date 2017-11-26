package com.kyc.snap;

import java.util.TreeSet;

import lombok.Data;

@Data
public class Grid {

    private final TreeSet<Integer> rows;
    private final TreeSet<Integer> cols;
}
