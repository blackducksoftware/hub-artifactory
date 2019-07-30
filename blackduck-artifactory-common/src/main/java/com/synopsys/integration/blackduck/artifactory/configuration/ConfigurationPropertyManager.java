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
package com.synopsys.integration.blackduck.artifactory.configuration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.LoggerFactory;

import com.synopsys.integration.log.IntLogger;
import com.synopsys.integration.log.Slf4jIntLogger;

/**
 * Gets configuration properties and coerces them.
 * Should only be consumed by configs with validation
 */
public class ConfigurationPropertyManager {
    private final IntLogger logger = new Slf4jIntLogger(LoggerFactory.getLogger(this.getClass()));

    private final Properties properties;

    public ConfigurationPropertyManager(final Properties properties) {
        this.properties = properties;
    }

    public List<String> getRepositoryKeysFromProperties(final ConfigurationProperty repositoryKeyListProperty, final ConfigurationProperty repositoryKeyCsvProperty) throws IOException {
        final List<String> repositoryKeys;

        final String repositoryKeyListString = getProperty(repositoryKeyListProperty);
        final String repositoryKeyCsvPath = getProperty(repositoryKeyCsvProperty);
        final File repositoryKeyCsvFile = new File(repositoryKeyCsvPath);

        if (repositoryKeyCsvFile.isFile()) {
            repositoryKeys = Files.readAllLines(repositoryKeyCsvFile.toPath()).stream()
                                 .map(line -> line.split(","))
                                 .flatMap(Arrays::stream)
                                 .filter(StringUtils::isNotBlank)
                                 .collect(Collectors.toList());
        } else {
            repositoryKeys = Arrays.stream(repositoryKeyListString.split(","))
                                 .filter(StringUtils::isNotBlank)
                                 .collect(Collectors.toList());
        }

        return repositoryKeys;
    }

    public Properties getProperties() {
        return properties;
    }

    public Set<Map.Entry<String, String>> getPropertyEntries() {
        return properties.stringPropertyNames().stream()
                   .collect(Collectors.toMap(name -> name, properties::getProperty))
                   .entrySet();
    }

    public String getProperty(final ConfigurationProperty configurationProperty) {
        return properties.getProperty(configurationProperty.getKey());
    }

    private String getProperty(final String propertyKey) {
        return properties.getProperty(propertyKey);
    }

    public Boolean getBooleanProperty(final ConfigurationProperty configurationProperty) {
        return getBooleanProperty(configurationProperty.getKey());
    }

    private Boolean getBooleanProperty(final String propertyKey) {
        return BooleanUtils.toBooleanObject(getProperty(propertyKey));
    }

    public Integer getIntegerProperty(final ConfigurationProperty configurationProperty) {
        return getIntegerProperty(configurationProperty.getKey());
    }

    private Integer getIntegerProperty(final String propertyKey) {
        Integer value = null;

        try {
            value = Integer.valueOf(getProperty(propertyKey));
        } catch (final NumberFormatException e) {
            logger.debug(String.format("Failed to parse integer for property: %s", propertyKey), e);
        }

        return value;
    }

    public List<String> getPropertyAsList(final ConfigurationProperty configurationProperty) {
        final List<String> values;
        final String property = getProperty(configurationProperty);

        if (StringUtils.isNotBlank(property)) {
            final String[] propertyValues = property.split(",");
            values = Arrays.asList(propertyValues);
        } else {
            values = Collections.emptyList();
        }

        return values;
    }
}
