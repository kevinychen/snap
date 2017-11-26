package com.kyc.snap;

import java.awt.image.BufferedImage;

class SnapResource implements SnapService {

    private final String productName;

    public SnapResource(String productName) {
        this.productName = productName;
    }

    @Override
    public String getProductName() {
        return productName;
    }

    @Override
    public Grid gridify(GridifyRequest request) {
        BufferedImage image = ImageUtils.from(request.getData());
        return ImageUtils.findGrid(
            image,
            request.getCannyThreshold1(),
            request.getCannyThreshold2(),
            request.getHoughThreshold(),
            request.getHoughMinLineLength(),
            request.getMinDistBetweenGridLines());
    }
}
