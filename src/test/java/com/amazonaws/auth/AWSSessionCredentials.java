package com.amazonaws.auth;

/*
 * Copyright 2011-2020 Amazon Technologies, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at:
 *
 *    http://aws.amazon.com/apache2.0
 *
 * This file is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES
 * OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * Copy of the original class for testing {@link io.fabric8.maven.docker.util.aws.AwsSdkAuthConfigFactory}.
 * Based on <a href="https://github.com/aws/aws-sdk-java/blob/1.11.707/aws-java-sdk-core/src/main/java/com/amazonaws/auth/AWSSessionCredentials.java">com.amazonaws:aws-java-sdk-core:1.11.707</a> (also APL licensed).
 * We can't use a direct dependency here, as we have
 * to keep d-m-p agnostic of the AWS SDK and only access it via reflection.
 */
public class AWSSessionCredentials extends AWSCredentials {

    private final String sessionKey;

    public AWSSessionCredentials(String accessKeyId, String secretKey, String sessionKey) {
        super(accessKeyId, secretKey);
        this.sessionKey = sessionKey;
    }

    public String getSessionToken() {return sessionKey;}

}
