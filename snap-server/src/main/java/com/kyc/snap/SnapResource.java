package com.kyc.snap;

class SnapResource implements SnapService {

    private final String productName;

    public SnapResource(String productName) {
        this.productName = productName;
    }

    @Override
    public String getProductName() {
        return productName;
    }
}
