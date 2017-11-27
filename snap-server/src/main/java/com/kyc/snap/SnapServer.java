package com.kyc.snap;

import io.dropwizard.Application;
import io.dropwizard.setup.Environment;

public class SnapServer extends Application<SnapConfiguration> {

    public static void main(String[] args) throws Exception {
        new SnapServer().run(args);
    }

    @Override
    public void run(SnapConfiguration configuration, Environment environment) throws Exception {
        ImageUtils.load();
        GoogleAPIManager googleAPIManager = new GoogleAPIManager(configuration.getGoogleAPICredentialsFile());
        environment.jersey().register(new SnapResource(configuration.getProductName(), googleAPIManager));
    }
}
