/**
 * blackduck-artifactory-common
 *
 * Copyright (c) 2019 Synopsys, Inc.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.synopsys.integration.blackduck.artifactory.modules.inspection.model;

import com.synopsys.integration.blackduck.artifactory.configuration.ConfigurationProperty;

public enum InspectionModuleProperty implements ConfigurationProperty {
    ENABLED("enabled"),
    CRON("cron"),
    METADATA_BLOCK("metadata.block"),
    PATTERNS_RUBYGEMS("patterns.rubygems"),
    PATTERNS_MAVEN("patterns.maven"),
    PATTERNS_GRADLE("patterns.gradle"),
    PATTERNS_PYPI("patterns.pypi"),
    PATTERNS_NUGET("patterns.nuget"),
    PATTERNS_NPM("patterns.npm"),
    REPOS("repos"),
    REPOS_CSV_PATH("repos.csv.path"),
    RETRY_COUNT("retry.count");

    private final String key;

    InspectionModuleProperty(final String key) {
        this.key = "blackduck.artifactory.inspect." + key;
    }

    @Override
    public String getKey() {
        return key;
    }
}
