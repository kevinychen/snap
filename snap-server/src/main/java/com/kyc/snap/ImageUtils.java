
package com.kyc.snap;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;

import javax.imageio.ImageIO;
import javax.ws.rs.BadRequestException;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

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

    static Grid findGrid(BufferedImage image, int cannyThreshold1, int cannyThreshold2, int houghThreshold, double houghMinLineLength,
            int minDistBetweenGridLines) {
        Mat mat = toMat(image);
        Mat edges = canny(mat, cannyThreshold1, cannyThreshold2);
        Mat lines = hough(edges, houghThreshold, houghMinLineLength);

        Set<Integer> rows = new TreeSet<>();
        Set<Integer> cols = new TreeSet<>();
        for (int i = 0; i < lines.rows(); i++) {
            double[] data = lines.get(i, 0);
            System.out.println(Arrays.toString(data));
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

    private ImageUtils() {
    }
}
