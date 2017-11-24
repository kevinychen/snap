package com.kyc.snap;

import io.dropwizard.Application;
import io.dropwizard.setup.Environment;

public class SnapServer extends Application<SnapConfiguration> {

    public static void main(String[] args) throws Exception {
        new SnapServer().run(args);
    }

    @Override
    public void run(SnapConfiguration configuration, Environment environment) throws Exception {
        environment.jersey().register(new SnapResource(configuration.getProductName()));
    }
}
