package com.kyc.snap;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
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
import com.kyc.snap.CrosswordCluesList.Orientation;
import com.kyc.snap.ParsedGrid.ParsedGridSquare;

import lombok.Data;

class CrosswordManager {

    private static final Pattern NUMBER_PATTERN = Pattern.compile("^[0-9]+");

    ParsedGrid toCrossword(Grid grid, ParsedGrid parsedGrid, double confidence) {
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
        int numCorrectClueIndices = 0;
        for (int row = 0; row < squares.length; row++)
            for (int col = 0; col < squares[row].length; col++)
                if (isClueStartSquare(squares, row, col)) {
                    String clue = clueIndex + 1 + "";
                    if (squares[row][col].text.replaceAll("[^0-9]", "").equals(clue))
                        numCorrectClueIndices++;
                    squares[row][col].text = clue;
                    clueIndex++;
                } else
                    squares[row][col].text = "";

        if (numCorrectClueIndices <= confidence * clueIndex)
            return parsedGrid;

        List<ParsedGridSquare> newSquares = parsedGrid.getSquares().stream()
            .map(square -> {
                ParsedGridSquare newSquare = new ParsedGridSquare(square.getRow(), square.getCol());
                newSquare.setText(squares[square.getRow()][square.getCol()].text);
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
                        (downClues ? Orientation.DOWN : Orientation.ACROSS),
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

    List<CrosswordClueResult> solveClue(String clue, int numLetters) {
        try {
            URL url = new URL("http://www.wordplays.com/crossword-solver");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setDoOutput(true);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            try (PrintWriter writer = new PrintWriter(connection.getOutputStream())) {
                writer.print(String.format(
                    "clue=%1$s&pattern=&phrase=%1$s&anagram-patt=&anagram-len=&roman-num=&vand=1&rejected=&cks=&ishm=&mvr=&ty=0",
                    URLEncoder.encode(clue, "UTF-8")));
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

    private boolean isClueStartSquare(BinaryParsedSquare[][] squares, int row, int col) {
        if ((row == 0 || !squares[row - 1][col].light || !squares[row - 1][col].bottomBorderLight)
                && squares[row][col].bottomBorderLight && row != squares.length - 1 && squares[row + 1][col].light) {
            return true;
        }
        if ((col == 0 || !squares[row][col - 1].light || !squares[row][col - 1].rightBorderLight)
                && squares[row][col].rightBorderLight && col != squares[row].length - 1 && squares[row][col + 1].light) {
            return true;
        }
        return false;
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

    @Data
    private static class BinaryParsedSquare {
        String text = "";
        boolean light = false;
        boolean rightBorderLight = false;
        boolean bottomBorderLight = false;
    }
}
