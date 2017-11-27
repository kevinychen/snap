package com.kyc.snap;

import io.dropwizard.Configuration;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper=false)
class SnapConfiguration extends Configuration {

    private final String productName;

    private final String googleAPICredentialsFile;
}
