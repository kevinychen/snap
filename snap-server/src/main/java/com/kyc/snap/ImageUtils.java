
package com.kyc.snap;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.imageio.ImageIO;
import javax.ws.rs.BadRequestException;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.TermCriteria;
import org.opencv.imgproc.Imgproc;

import com.kyc.snap.Grid.GridCol;
import com.kyc.snap.Grid.GridRow;
import com.kyc.snap.ParsedGrid.ParsedGridSquare;

import jersey.repackaged.com.google.common.base.Preconditions;
import lombok.Data;
import nu.pattern.OpenCV;

class ImageUtils {

    static void load() {
        OpenCV.loadShared();
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
    }

    static BufferedImage from(byte[] imageData) {
        try {
            return ImageIO.read(new ByteArrayInputStream(imageData));
        } catch (IOException e) {
            throw new BadRequestException("Failed to read request data");
        }
    }

    static byte[] toBytes(BufferedImage image) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new BadRequestException("Failed to convert image");
        }
    }

    static Grid findGrid(BufferedImage image, int cannyThreshold1, int cannyThreshold2, int houghThreshold, double houghMinLineLength,
            int minDistBetweenGridLines) {
        Mat mat = toMat(image);
        Mat edges = canny(mat, cannyThreshold1, cannyThreshold2);
        Mat lines = hough(edges, houghThreshold, houghMinLineLength);

        List<Integer> xs = new ArrayList<>();
        List<Integer> ys = new ArrayList<>();
        for (int i = 0; i < lines.rows(); i++) {
            double[] data = lines.get(i, 0);
            boolean vertical = data[0] == data[2];
            boolean horizontal = data[1] == data[3];
            Preconditions.checkArgument(horizontal != vertical,
                "Expected horizontal or vertical line but got (%s, %s), (%s, %s)",
                data[0], data[1], data[2], data[3]);
            if (vertical)
                xs.add((int) data[0]);
            else
                ys.add((int) data[1]);
        }

        Collections.sort(xs);
        Collections.sort(ys);
        List<GridRow> rows = new ArrayList<>();
        List<GridCol> cols = new ArrayList<>();
        for (int i = 0; i + 1 < xs.size(); i++)
            if (xs.get(i + 1) - xs.get(i) >= minDistBetweenGridLines)
                cols.add(new GridCol(xs.get(i), xs.get(i + 1) - xs.get(i)));
        for (int i = 0; i + 1 < ys.size(); i++)
            if (ys.get(i + 1) - ys.get(i) >= minDistBetweenGridLines)
                rows.add(new GridRow(ys.get(i), ys.get(i + 1) - ys.get(i)));
        return new Grid(rows, cols);
    }

    static ParsedGrid parseGrid(BufferedImage image, Grid grid, int numClusters, double crosswordThreshold,
            GoogleAPIManager googleAPIManager, CrosswordManager crosswordManager) {
        List<GridSquare> squares = new ArrayList<>();
        for (int i = 0; i < grid.getRows().size(); i++)
            for (int j = 0; j < grid.getCols().size(); j++) {
                GridRow row = grid.getRows().get(i);
                GridCol col = grid.getCols().get(j);
                GridSquare square = new GridSquare(i, j);
                square.rgb = averageRgbs(image, col.getStartX(), row.getStartY(), col.getWidth(), row.getHeight());
                square.image = toBinaryImage(image.getSubimage(col.getStartX(), row.getStartY(), col.getWidth(), row.getHeight()));
                if (i + 1 < grid.getRows().size()) {
                    square.bottomBorderRgb = averageRgbs(image, col.getStartX(), row.getStartY() + row.getHeight(),
                        col.getWidth(), grid.getRows().get(i + 1).getStartY() - (row.getStartY() + row.getHeight()));
                }
                if (j + 1 < grid.getCols().size()) {
                    square.rightBorderRgb = averageRgbs(image, col.getStartX() + col.getWidth(), row.getStartY(),
                        grid.getCols().get(j + 1).getStartX() - (col.getStartX() + col.getWidth()), row.getHeight());
                }
                squares.add(square);
            }

        Map<Integer, Integer> squareRgbClusters = cluster(squares.stream()
            .map(GridSquare::getRgb)
            .filter(rgb -> rgb != -1)
            .collect(Collectors.toList()), numClusters);
        Map<BufferedImage, String> texts = googleAPIManager.batchFindText(squares.stream()
            .map(GridSquare::getImage)
            .collect(Collectors.toList()));
        Map<Integer, Integer> borderRgbClusters = cluster(squares.stream()
            .flatMap(square -> Stream.of(square.rightBorderRgb, square.bottomBorderRgb))
            .filter(rgb -> rgb != -1)
            .collect(Collectors.toList()), numClusters);

        List<ParsedGridSquare> parsedSquares = squares.stream()
                .map(square -> {
                    ParsedGridSquare parsedSquare = new ParsedGridSquare(square.row, square.col);
                    parsedSquare.setRgb(squareRgbClusters.get(square.rgb));
                    parsedSquare.setText(texts.get(square.image));
                    parsedSquare.setRightBorderRgb(borderRgbClusters.getOrDefault(square.rightBorderRgb, -1));
                    parsedSquare.setBottomBorderRgb(borderRgbClusters.getOrDefault(square.bottomBorderRgb, -1));
                    return parsedSquare;
                })
                .collect(Collectors.toList());
        ParsedGrid parsedGrid = new ParsedGrid(parsedSquares);
        parsedGrid = crosswordManager.toCrossword(grid, parsedGrid, crosswordThreshold);
        return parsedGrid;
    }

    static boolean isLight(int rgb) {
        Color color = new Color(rgb);
        return color.getRed() + color.getGreen() + color.getBlue() > 3 * 128;
    }

    private static Mat toMat(BufferedImage image) {
        BufferedImage newImage = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_3BYTE_BGR);
        for (int x = 0; x < image.getWidth(); x++)
            for (int y = 0; y < image.getHeight(); y++)
                newImage.setRGB(x, y, image.getRGB(x, y));
        Mat mat = new Mat(image.getHeight(), image.getWidth(), CvType.CV_8UC3);
        byte[] data = ((DataBufferByte) newImage.getRaster().getDataBuffer()).getData();
        mat.put(0, 0, data);
        return mat;
    }

    private static Mat canny(Mat image, int threshold1, int threshold2) {
        Mat edges = new Mat();
        Imgproc.Canny(image, edges, threshold1, threshold2);
        return edges;
    }

    private static Mat hough(Mat edges, int threshold, double minLineLength) {
        Mat lines = new Mat();
        Imgproc.HoughLinesP(edges, lines, 1, Math.PI / 2, threshold, minLineLength, 0);
        return lines;
    }

    private static int averageRgbs(BufferedImage image, int startX, int startY, int width, int height) {
        long sumRed = 0, sumGreen = 0, sumBlue = 0, numRgbs = 0;
        for (int x = startX; x < startX + width; x++)
            for (int y = startY; y < startY + height; y++) {
                AdjustedRGB adjusted = AdjustedRGB.from(image.getRGB(x, y));
                sumRed += adjusted.getRedSquared();
                sumGreen += adjusted.getGreenSquared();
                sumBlue += adjusted.getBlueSquared();
                numRgbs++;
            }
        if (numRgbs == 0)
            return -1;
        return new AdjustedRGB(sumRed / numRgbs, sumGreen / numRgbs, sumBlue / numRgbs).toRGB();
    }

    private static Map<Integer, Integer> cluster(List<Integer> rgbs, int numClusters) {
        Mat data = new Mat(rgbs.size(), 3, CvType.CV_32F);
        for (int i = 0; i < rgbs.size(); i++) {
            AdjustedRGB adjusted = AdjustedRGB.from(rgbs.get(i));
            data.put(i, 0, adjusted.getRedSquared());
            data.put(i, 1, adjusted.getGreenSquared());
            data.put(i, 2, adjusted.getBlueSquared());
        }

        Mat labels = new Mat();
        Mat centers = new Mat();
        Core.kmeans(data, numClusters, labels, new TermCriteria(TermCriteria.COUNT, 100, 0), 1, Core.KMEANS_PP_CENTERS, centers);

        List<Integer> centerRGBs = new ArrayList<>();
        for (int i = 0; i < numClusters; i++) {
            AdjustedRGB adjusted = new AdjustedRGB(
                (int) centers.get(i, 0)[0],
                (int) centers.get(i, 1)[0],
                (int) centers.get(i, 2)[0]);
            centerRGBs.add(adjusted.toRGB());
        }

        Map<Integer, Integer> clusters = new HashMap<>();
        for (int i = 0; i < rgbs.size(); i++) {
            int label = (int) labels.get(i, 0)[0];
            clusters.put(rgbs.get(i), centerRGBs.get(label));
        }
        return clusters;
    }

    private static BufferedImage toBinaryImage(BufferedImage image) {
        BufferedImage binaryImage = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_BYTE_BINARY);
        for (int x = 0; x < image.getWidth(); x++)
            for (int y = 0; y < image.getHeight(); y++)
                binaryImage.setRGB(x, y, isLight(image.getRGB(x, y)) ? Color.white.getRGB() : Color.black.getRGB());
        return binaryImage;
    }

    @Data
    private static class GridSquare {
        private final int row;
        private final int col;

        private int rgb;
        private BufferedImage image;
        private int rightBorderRgb;
        private int bottomBorderRgb;
    }

    private ImageUtils() {
    }
}
