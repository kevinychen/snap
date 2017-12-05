package com.kyc.snap;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;

import com.google.common.base.Joiner;
import com.google.common.collect.Iterables;
import com.google.common.collect.Streams;
import com.google.common.io.CharStreams;
import com.kyc.snap.CrosswordCluesList.CrosswordClue;
import com.kyc.snap.CrosswordGrid.CrosswordBlank;
import com.kyc.snap.ParsedGrid.ParsedGridSquare;

import lombok.Data;

class CrosswordManager {

    private static final Pattern NUMBER_PATTERN = Pattern.compile("^[0-9]+");

    Optional<CrosswordGrid> toCrosswordGrid(Grid grid, ParsedGrid parsedGrid, double confidence) {
        BinaryParsedSquare[][] squares = new BinaryParsedSquare[grid.getRows().size()][grid.getCols().size()];
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
                        blanks.add(new CrosswordBlank(clueNumber, CrosswordClueOrientation.ACROSS, row, col, acrossLen));
                    }
                    int downLen = 1;
                    while (adjacentToBottom(squares, row + downLen - 1, col))
                        downLen++;
                    if (downLen > 1) {
                        blanks.add(new CrosswordBlank(clueNumber, CrosswordClueOrientation.DOWN, row, col, downLen));
                    }
                }
            }
        return numCorrectClueIndices > confidence * clueIndex
                ? Optional.of(new CrosswordGrid(blanks))
                : Optional.empty();
    }

    ParsedGrid toParsedGrid(Grid grid, ParsedGrid parsedGrid, CrosswordGrid crosswordGrid) {
        String[][] numbers = new String[grid.getRows().size()][grid.getCols().size()];
        for (int row = 0; row < numbers.length; row++)
            for (int col = 0; col < numbers[row].length; col++)
                numbers[row][col] = "";
        for (CrosswordBlank blank : crosswordGrid.getBlanks())
            numbers[blank.getRow()][blank.getCol()] = blank.getNumber();
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
        return new ParsedGrid(newSquares);
    }

    CrosswordCluesList parseStandardCluesFormat(String cluesString) {
        boolean downClues = false;
        long currNumber = -1;
        List<String> currentClue = new ArrayList<>();
        List<CrosswordClue> clues = new ArrayList<>();
        for (String line : cluesString.split("\n")) {
            line = line.trim();
            Matcher matcher = NUMBER_PATTERN.matcher(line);
            if (matcher.find()) {
                long nextNumber = Long.parseLong(matcher.group());
                if (nextNumber < currNumber)
                    downClues = true;
                if (currNumber != -1)
                    clues.add(new CrosswordClue(
                        String.valueOf(currNumber),
                        (downClues ? CrosswordClueOrientation.DOWN : CrosswordClueOrientation.ACROSS),
                        Joiner.on(" ").join(currentClue)));
                currNumber = nextNumber;
                currentClue.clear();
                line = line.substring(matcher.group().length());
            }
            currentClue.add(line);
        }
        removeCommonPrefixes(clues);
        return new CrosswordCluesList(clues);
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
                clues.set(i, new CrosswordClue(clue.getNumber(), clue.getOrientation(), clue.getClue().substring(1)));
            }
        }
    }

    private List<CrosswordClueResult> solveClue(String clue, int numLetters) {
        try {
            URL url = new URL("http://www.wordplays.com/crossword-solver");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setDoOutput(true);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
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
        } catch (IOException e) {
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
}
