package com.kyc.snap;

import lombok.Data;

@Data
public class AdjustedRGB {

    private final long redSquared;
    private final long greenSquared;
    private final long blueSquared;

    public int toRGB() {
        int red = (int) Math.sqrt(redSquared);
        int green = (int) Math.sqrt(greenSquared);
        int blue = (int) Math.sqrt(blueSquared);
        return (red << 16) | (green << 8) | blue;
    }

    public static AdjustedRGB from(int rgb) {
        int red = (rgb >> 16) & 0xff;
        int green = (rgb >> 8) & 0xff;
        int blue = rgb & 0xff;
        return new AdjustedRGB(red * red, green * green, blue * blue);
    }
}
