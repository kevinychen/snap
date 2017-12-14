package com.kyc.snap;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;

import com.google.common.base.Joiner;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimap;
import com.google.common.collect.Streams;
import com.google.common.io.CharStreams;
import com.kyc.snap.CrosswordCluePosition.CrosswordClueOrientation;
import com.kyc.snap.CrosswordCluesList.CrosswordClue;
import com.kyc.snap.CrosswordGrid.CrosswordBlank;
import com.kyc.snap.ParsedGrid.ParsedGridSquare;

import lombok.Data;

class CrosswordManager {

    private static final Pattern NUMBER_PATTERN = Pattern.compile("^[0-9]+");

    private final DictionaryManager dictionaryManager;

    CrosswordManager(DictionaryManager dictionaryManager) {
        this.dictionaryManager = dictionaryManager;
    }

    Optional<CrosswordGrid> toCrosswordGrid(ParsedGrid parsedGrid, double confidence) {
        BinaryParsedSquare[][] squares = new BinaryParsedSquare[parsedGrid.getNumRows()][parsedGrid.getNumCols()];
        for (ParsedGridSquare square : parsedGrid.getSquares()) {
            BinaryParsedSquare binarySquare = new BinaryParsedSquare();
            binarySquare.text = square.getText();
            binarySquare.light = ImageUtils.isLight(square.getRgb());
            binarySquare.rightBorderLight = ImageUtils.isLight(square.getRightBorderRgb());
            binarySquare.bottomBorderLight = ImageUtils.isLight(square.getBottomBorderRgb());
            squares[square.getRow()][square.getCol()] = binarySquare;
        }
        for (int row = 0; row < squares.length; row++)
            for (int col = 0; col < squares[row].length; col++)
                if (squares[row][col] == null)
                    squares[row][col] = new BinaryParsedSquare();

        int clueIndex = 0;
        long numCorrectClueIndices = 0;
        List<CrosswordBlank> blanks = new ArrayList<>();
        for (int row = 0; row < squares.length; row++)
            for (int col = 0; col < squares[row].length; col++) {
                if (isAcrossStart(squares, row, col) || isDownStart(squares, row, col)) {
                    String clueNumber = (++clueIndex) + "";
                    if (squares[row][col].text.replaceAll("[^0-9]", "").equals(clueNumber))
                        numCorrectClueIndices++;
                    int acrossLen = 1;
                    while (adjacentToRight(squares, row, col + acrossLen - 1))
                        acrossLen++;
                    if (acrossLen > 1) {
                        blanks.add(new CrosswordBlank(new CrosswordCluePosition(clueNumber, CrosswordClueOrientation.ACROSS),
                            row, col, acrossLen));
                    }
                    int downLen = 1;
                    while (adjacentToBottom(squares, row + downLen - 1, col))
                        downLen++;
                    if (downLen > 1) {
                        blanks.add(new CrosswordBlank(new CrosswordCluePosition(clueNumber, CrosswordClueOrientation.DOWN),
                            row, col, downLen));
                    }
                }
            }
        return numCorrectClueIndices >= confidence * clueIndex
                ? Optional.of(new CrosswordGrid(blanks))
                : Optional.empty();
    }

    ParsedGrid toParsedGrid(ParsedGrid parsedGrid, CrosswordGrid crosswordGrid) {
        String[][] numbers = new String[parsedGrid.getNumRows()][parsedGrid.getNumCols()];
        for (int row = 0; row < numbers.length; row++)
            for (int col = 0; col < numbers[row].length; col++)
                numbers[row][col] = "";
        for (CrosswordBlank blank : crosswordGrid.getBlanks())
            numbers[blank.getRow()][blank.getCol()] = blank.getPosition().getNumber();
        List<ParsedGridSquare> newSquares = parsedGrid.getSquares().stream()
                .map(square -> {
                    ParsedGridSquare newSquare = new ParsedGridSquare(square.getRow(), square.getCol());
                    newSquare.setText(numbers[square.getRow()][square.getCol()]);
                    newSquare.setRgb(square.getRgb());
                    newSquare.setRightBorderRgb(square.getRightBorderRgb());
                    newSquare.setBottomBorderRgb(square.getBottomBorderRgb());
                    return newSquare;
                })
                .collect(Collectors.toList());
        return new ParsedGrid(parsedGrid.getNumRows(), parsedGrid.getNumCols(), newSquares);
    }

    CrosswordCluesList parseStandardCluesFormat(String cluesString) {
        boolean downClues = false;
        long currNumber = -1;
        List<String> currentClue = new ArrayList<>();
        List<CrosswordClue> clues = new ArrayList<>();
        for (String line : cluesString.split("\n")) {
            line = line.trim();
            if (line.equalsIgnoreCase("ACROSS") || line.equalsIgnoreCase("DOWN"))
                continue;
            Matcher matcher = NUMBER_PATTERN.matcher(line);
            if (matcher.find()) {
                long nextNumber = Long.parseLong(matcher.group());
                if (currNumber != -1) {
                    clues.add(new CrosswordClue(
                        new CrosswordCluePosition(String.valueOf(currNumber),
                            (downClues ? CrosswordClueOrientation.DOWN : CrosswordClueOrientation.ACROSS)),
                        Joiner.on(" ").join(currentClue)));
                }
                if (nextNumber < currNumber)
                    downClues = true;
                currNumber = nextNumber;
                currentClue.clear();
                line = line.substring(matcher.group().length());
            }
            currentClue.add(line);
        }
        removeCommonPrefixes(clues);
        return new CrosswordCluesList(clues);
    }

    ParsedGrid solveCrossword(ParsedGrid parsedGrid, CrosswordGrid crosswordGrid, CrosswordCluesList clues) {
        Map<CrosswordCluePosition, CrosswordBlank> blanks = crosswordGrid.getBlanks().stream()
                .collect(Collectors.toMap(CrosswordBlank::getPosition, blank -> blank));
        List<List<CrosswordClueResult>> results = new ArrayList<>();
        for (CrosswordClue clue : clues.getClues()) {
            CrosswordBlank blank = blanks.get(clue.getPosition());
            results.add(solveClue(clue.getClue(), blank.getLength()));
        }

        Multimap<Pos, CluePos> allCluePoses = ArrayListMultimap.create();
        for (int i = 0; i < clues.getClues().size(); i++) {
            CrosswordBlank blank = blanks.get(clues.getClues().get(i).getPosition());
            for (int letterIndex = 0; letterIndex < blank.getLength(); letterIndex++) {
                int row = blank.getRow();
                int col = blank.getCol();
                if (blank.getPosition().getOrientation() == CrosswordClueOrientation.ACROSS)
                    col += letterIndex;
                else
                    row += letterIndex;
                allCluePoses.put(new Pos(row, col), new CluePos(i, letterIndex));
            }
        }

        double[][][] allLetterProbs = new double[parsedGrid.getNumRows()][parsedGrid.getNumCols()][26];
        for (int row = 0; row < parsedGrid.getNumRows(); row++)
            for (int col = 0; col < parsedGrid.getNumCols(); col++)
                for (int letter = 0; letter < 26; letter++)
                    allLetterProbs[row][col][letter] = 1. / 26;
        for (int iteration = 0; iteration < 4; iteration++) {
            List<List<WordProb>> allWordProbs = new ArrayList<>();
            for (int i = 0; i < clues.getClues().size(); i++) {
                CrosswordBlank blank = blanks.get(clues.getClues().get(i).getPosition());
                int length = blank.getLength();

                List<WordProb> wordProbs = dictionaryManager.getWordsWithLength(length).stream()
                    .map(word -> {
                        double prob = 1;
                        for (int letterIndex = 0; letterIndex < length; letterIndex++) {
                            int row = blank.getRow();
                            int col = blank.getCol();
                            if (blank.getPosition().getOrientation() == CrosswordClueOrientation.ACROSS)
                                col += letterIndex;
                            else
                                row += letterIndex;
                            prob *= allLetterProbs[row][col][word.charAt(letterIndex) - 'A'];
                        }
                        return new WordProb(word, prob);
                    })
                    .collect(Collectors.toList());
                wordProbs.add(new WordProb("*", Math.max(0, 1 - wordProbs.stream().mapToDouble(WordProb::getProb).sum())));

                Map<String, Double> mults = new HashMap<>();
                for (CrosswordClueResult result : results.get(i))
                    mults.put(result.getAnswer(), 8 * Math.pow(4, result.getConfidence()));
                mults.put("*", Math.pow(0.5, length));
                wordProbs = wordProbs.stream()
                        .map(wordProb -> new WordProb(wordProb.word, wordProb.prob * mults.getOrDefault(wordProb.word, 1.)))
                        .collect(Collectors.toList());

                double totalProb = wordProbs.stream().mapToDouble(WordProb::getProb).sum() + 1e-12;
                wordProbs = wordProbs.stream()
                    .map(wordProb -> new WordProb(wordProb.word, wordProb.prob / totalProb))
                    .collect(Collectors.toList());
                allWordProbs.add(wordProbs);
            }

            for (int row = 0; row < parsedGrid.getNumRows(); row++)
                for (int col = 0; col < parsedGrid.getNumCols(); col++)
                    for (int letter = 0; letter < 26; letter++)
                        allLetterProbs[row][col][letter] = 1;
            for (int row = 0; row < parsedGrid.getNumRows(); row++)
                for (int col = 0; col < parsedGrid.getNumCols(); col++) {
                    Collection<CluePos> cluePoses = allCluePoses.get(new Pos(row, col));
                    for (CluePos cluePos : cluePoses) {
                        double[] letterProbs = new double[26];
                        for (WordProb wordProb : allWordProbs.get(cluePos.clueIndex))
                            if (wordProb.word.equals("*"))
                                for (int letter = 0; letter < 26; letter++)
                                    letterProbs[letter] += wordProb.prob / 26;
                            else
                                letterProbs[wordProb.word.charAt(cluePos.letterIndex) - 'A'] += wordProb.prob;
                        for (int letter = 0; letter < 26; letter++)
                            allLetterProbs[row][col][letter] *= letterProbs[letter];
                    }
                }
            for (int row = 0; row < parsedGrid.getNumRows(); row++)
                for (int col = 0; col < parsedGrid.getNumCols(); col++) {
                    double totalLetterProb = 0;
                    for (int letter = 0; letter < 26; letter++)
                        totalLetterProb += allLetterProbs[row][col][letter];
                    for (int letter = 0; letter < 26; letter++)
                        allLetterProbs[row][col][letter] /= totalLetterProb;
                }
        }
        List<ParsedGridSquare> squares = parsedGrid.getSquares().stream()
                .map(square -> {
                    int row = square.getRow();
                    int col = square.getCol();
                    int bestLetter = -1;
                    for (int letter = 0; letter < 26; letter++)
                        if (bestLetter == -1 || allLetterProbs[row][col][letter] > allLetterProbs[row][col][bestLetter])
                            bestLetter = letter;
                    String text;
                    if (allLetterProbs[row][col][bestLetter] > 0.1)
                        text = String.valueOf((char) (bestLetter + 'A'));
                    else if (allLetterProbs[row][col][bestLetter] > 0.05)
                        text = String.valueOf((char) (bestLetter + 'a'));
                    else
                        text = "";
                    ParsedGridSquare newSquare = new ParsedGridSquare(row, col);
                    newSquare.setRgb(square.getRgb());
                    newSquare.setText(text);
                    newSquare.setRightBorderRgb(square.getRightBorderRgb());
                    newSquare.setBottomBorderRgb(square.getBottomBorderRgb());
                    return newSquare;
                })
                .collect(Collectors.toList());
        return new ParsedGrid(parsedGrid.getNumRows(), parsedGrid.getNumCols(), squares);
    }

    private boolean isAcrossStart(BinaryParsedSquare[][] squares, int row, int col) {
        return (col == 0 || !squares[row][col - 1].light || !squares[row][col - 1].rightBorderLight)
                && adjacentToRight(squares, row, col);
    }

    private boolean adjacentToRight(BinaryParsedSquare[][] squares, int row, int col) {
        return squares[row][col].rightBorderLight && col != squares[row].length - 1 && squares[row][col + 1].light;
    }

    private boolean isDownStart(BinaryParsedSquare[][] squares, int row, int col) {
        return (row == 0 || !squares[row - 1][col].light || !squares[row - 1][col].bottomBorderLight)
                && adjacentToBottom(squares, row, col);
    }

    private boolean adjacentToBottom(BinaryParsedSquare[][] squares, int row, int col) {
        return squares[row][col].bottomBorderLight && row != squares.length - 1 && squares[row + 1][col].light;
    }

    private void removeCommonPrefixes(List<CrosswordClue> clues) {
        while (clues.stream().allMatch(clue -> !clue.getClue().isEmpty())
                && clues.stream().map(clue -> clue.getClue().charAt(0)).distinct().count() == 1) {
            for (int i = 0; i < clues.size(); i++) {
                CrosswordClue clue = clues.get(i);
                clues.set(i, new CrosswordClue(clue.getPosition(), clue.getClue().substring(1)));
            }
        }
    }

    private List<CrosswordClueResult> solveClue(String clue, int numLetters) {
        try {
            // TODO find website that doesn't rate limit and require waiting 9s between each query
            Thread.sleep(9000);
            System.out.println("Solving clue: " + clue);

            URL url = new URL("http://www.wordplays.com/crossword-solver");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setDoOutput(true);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Accept",
                "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8");
            connection.setRequestProperty("Accept-Language", "en-US,en;q=0.9");
            connection.setRequestProperty("Cache-Control", "max-age=0");
            connection.setRequestProperty("Connection", "keep-alive");
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            connection.setRequestProperty("User-Agent",
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_12_6) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/62.0.3202.94 Safari/537.36");
            try (PrintWriter writer = new PrintWriter(connection.getOutputStream())) {
                writer.print(String.format(
                    "clue=%1$s&pattern=%2$s&phrase=%1$s&anagram-patt=%2$s&anagram-len=&roman-num=&vand=1&rejected=&cks=&ishm=&mvr=&ty=0",
                    URLEncoder.encode(clue, "UTF-8"), Joiner.on("").join(Collections.nCopies(numLetters, "?"))));
            }
            Element wordList = Jsoup.parse(CharStreams.toString(new InputStreamReader(connection.getInputStream(), "UTF-8")))
                .getElementById("wordlists");
            return Streams.concat(wordList.getElementsByClass("even").stream(), wordList.getElementsByClass("odd").stream())
                .map(row -> {
                    String answer = Iterables.getOnlyElement(row.getElementsByTag("a")).text();
                    int numStars = Iterables.getOnlyElement(row.getElementsByClass("stars")).children().size();
                    return new CrosswordClueResult(answer, numStars);
                })
                .collect(Collectors.toList());
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Data
    private static class BinaryParsedSquare {
        String text = "";
        boolean light = false;
        boolean rightBorderLight = false;
        boolean bottomBorderLight = false;
    }

    @Data
    private static class Pos {
        private final int row;
        private final int col;
    }

    @Data
    private static class CluePos {
        private final int clueIndex;
        private final int letterIndex;
    }

    @Data
    private static class WordProb {
        private final String word;
        private final double prob;
    }
}
