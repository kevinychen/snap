package com.kyc.snap;

import java.awt.image.BufferedImage;

class SnapResource implements SnapService {

    private final String productName;
    private final GoogleAPIManager googleAPIManager;

    public SnapResource(String productName, GoogleAPIManager googleAPIManager) {
        this.productName = productName;
        this.googleAPIManager = googleAPIManager;
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

    @Override
    public ParsedGrid parseGrid(ParseGridRequest request) {
        BufferedImage image = ImageUtils.from(request.getData());
        return ImageUtils.parseGrid(
            image,
            request.getGrid(),
            request.getNumClusters(),
            googleAPIManager);
    }

    @Override
    public String exportToGoogleSheets(ExportToGoogleSheetsRequest request) {
        return googleAPIManager.exportToGoogleSheets(request.getParsedGrid());
    }
}
