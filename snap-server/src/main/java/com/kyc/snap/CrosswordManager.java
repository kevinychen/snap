package com.kyc.snap;

import java.util.List;
import java.util.stream.Collectors;

import com.kyc.snap.ParsedGrid.ParsedGridSquare;

import lombok.Data;

class CrosswordManager {

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

    @Data
    private static class BinaryParsedSquare {
        String text = "";
        boolean light = false;
        boolean rightBorderLight = false;
        boolean bottomBorderLight = false;
    }
}
