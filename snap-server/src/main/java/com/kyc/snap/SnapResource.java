package com.kyc.snap;

import java.awt.image.BufferedImage;

class SnapResource implements SnapService {

    private final String productName;
    private final GoogleAPIManager googleAPIManager;
    private final CrosswordManager crosswordManager;

    public SnapResource(String productName, GoogleAPIManager googleAPIManager, CrosswordManager crosswordManager) {
        this.productName = productName;
        this.googleAPIManager = googleAPIManager;
        this.crosswordManager = crosswordManager;
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
            request.getCrosswordThreshold(),
            googleAPIManager,
            crosswordManager);
    }

    @Override
    public String exportToGoogleSheets(ExportToGoogleSheetsRequest request) {
        return googleAPIManager.exportToGoogleSheets(request.getParsedGrid());
    }
}
