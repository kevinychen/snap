
package com.kyc.snap;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import javax.imageio.ImageIO;
import javax.ws.rs.BadRequestException;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.TermCriteria;
import org.opencv.imgproc.Imgproc;

import com.kyc.snap.ParsedGrid.ParsedGridSquare;

import jersey.repackaged.com.google.common.base.Preconditions;
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

        TreeSet<Integer> rows = new TreeSet<>();
        TreeSet<Integer> cols = new TreeSet<>();
        for (int i = 0; i < lines.rows(); i++) {
            double[] data = lines.get(i, 0);
            boolean vertical = data[0] == data[2];
            boolean horizontal = data[1] == data[3];
            Preconditions.checkArgument(horizontal != vertical,
                "Expected horizontal or vertical line but got (%s, %s), (%s, %s)",
                data[0], data[1], data[2], data[3]);
            if (horizontal)
                rows.add((int) data[1]);
            else
                cols.add((int) data[0]);
        }

        removeCloseValues(rows, minDistBetweenGridLines);
        removeCloseValues(cols, minDistBetweenGridLines);

        return new Grid(rows, cols);
    }

    static ParsedGrid parseGrid(BufferedImage image, Grid grid, int numClusters, GoogleAPIManager googleAPIManager) {
        List<Integer> rows = new ArrayList<>(grid.getRows());
        List<Integer> cols = new ArrayList<>(grid.getCols());

        List<Integer> averageRgbs = new ArrayList<>();
        List<BufferedImage> gridImages = new ArrayList<>();
        for (int i = 0; i + 1 < rows.size(); i++)
            for (int j = 0; j + 1 < cols.size(); j++) {
                List<Integer> rgbs = new ArrayList<>();
                for (int x = cols.get(j); x < cols.get(j + 1); x++)
                    for (int y = rows.get(i); y < rows.get(i + 1); y++)
                        rgbs.add(image.getRGB(x, y));
                averageRgbs.add(averageRgbs(rgbs));

                gridImages.add(toBinaryImage(
                    image.getSubimage(cols.get(j), rows.get(i), cols.get(j + 1) - cols.get(j), rows.get(i + 1) - rows.get(i))));
            }

        Map<Integer, Integer> clusters = cluster(averageRgbs, numClusters);
        List<String> foundText = googleAPIManager.batchFindText(gridImages);

        List<ParsedGridSquare> squares = new ArrayList<>();
        for (int i = 0, index = 0; i + 1 < rows.size(); i++)
            for (int j = 0; j + 1 < cols.size(); j++) {
                squares.add(new ParsedGridSquare(i, j, clusters.get(averageRgbs.get(index)), foundText.get(index)));
                index++;
            }
        return new ParsedGrid(squares);
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

    private static void removeCloseValues(Set<Integer> values, int minDiffBetweenValues) {
        Iterator<Integer> it = values.iterator();
        int prevValue = Integer.MIN_VALUE / 2;
        while (it.hasNext()) {
            int value = it.next();
            if (value - prevValue < minDiffBetweenValues)
                it.remove();
            else
                prevValue = value;
        }
    }

    private static int averageRgbs(Collection<Integer> rgbs) {
        long sumRed = 0, sumGreen = 0, sumBlue = 0;
        for (int rgb : rgbs) {
            AdjustedRGB adjusted = AdjustedRGB.from(rgb);
            sumRed += adjusted.getRedSquared();
            sumGreen += adjusted.getGreenSquared();
            sumBlue += adjusted.getBlueSquared();
        }
        return new AdjustedRGB(sumRed / rgbs.size(), sumGreen / rgbs.size(), sumBlue / rgbs.size()).toRGB();
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

    private static boolean isLight(int rgb) {
        Color color = new Color(rgb);
        return color.getRed() + color.getGreen() + color.getBlue() > 3 * 128;
    }

    private ImageUtils() {
    }
}
