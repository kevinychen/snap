package com.kyc.snap;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.google.common.io.Files;

class DictionaryManager {

    private final Map<Integer, Set<String>> wordsByLength;

    private DictionaryManager(Map<Integer, Set<String>> wordsByLength) {
        this.wordsByLength = wordsByLength;
    }

    Set<String> getWordsWithLength(int length) {
        return wordsByLength.get(length);
    }

    static DictionaryManager load(String... dictionaryFiles) throws IOException {
        Map<Integer, Set<String>> wordsByLength = new HashMap<>();
        for (String file : dictionaryFiles)
            for (String line : Files.readLines(new File(file), StandardCharsets.UTF_8)) {
                wordsByLength.computeIfAbsent(line.length(), length -> new HashSet<>()).add(line.toUpperCase());
            }
        return new DictionaryManager(wordsByLength);
    }
}
