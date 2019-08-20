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
package com.synopsys.integration.blackduck.artifactory.modules.inspection.service;

import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.artifactory.repo.RepoPath;
import org.slf4j.LoggerFactory;

import com.synopsys.integration.bdio.model.externalid.ExternalId;
import com.synopsys.integration.blackduck.api.UriSingleResponse;
import com.synopsys.integration.blackduck.api.generated.view.ComponentSearchResultView;
import com.synopsys.integration.blackduck.api.generated.view.ComponentVersionView;
import com.synopsys.integration.blackduck.api.generated.view.ProjectVersionView;
import com.synopsys.integration.blackduck.api.generated.view.VersionBomComponentView;
import com.synopsys.integration.blackduck.artifactory.modules.inspection.InspectionModule;
import com.synopsys.integration.blackduck.artifactory.modules.inspection.exception.FailedInspectionException;
import com.synopsys.integration.blackduck.artifactory.modules.inspection.model.Artifact;
import com.synopsys.integration.blackduck.artifactory.modules.inspection.model.ComponentViewWrapper;
import com.synopsys.integration.blackduck.exception.BlackDuckApiException;
import com.synopsys.integration.blackduck.exception.BlackDuckIntegrationException;
import com.synopsys.integration.blackduck.service.BlackDuckService;
import com.synopsys.integration.blackduck.service.ComponentService;
import com.synopsys.integration.blackduck.service.ProjectBomService;
import com.synopsys.integration.exception.IntegrationException;
import com.synopsys.integration.log.IntLogger;
import com.synopsys.integration.log.Slf4jIntLogger;
import com.synopsys.integration.rest.exception.IntegrationRestException;

public class BlackDuckBOMService {
    private final IntLogger logger = new Slf4jIntLogger(LoggerFactory.getLogger(BlackDuckBOMService.class));

    private final ProjectBomService projectBomService;
    private final ComponentService componentService;
    private final BlackDuckService blackDuckService;

    public BlackDuckBOMService(final ProjectBomService projectBomService, final ComponentService componentService, final BlackDuckService blackDuckService) {
        this.projectBomService = projectBomService;
        this.componentService = componentService;
        this.blackDuckService = blackDuckService;
    }

    public ComponentViewWrapper addArtifactToProjectVersion(final Artifact artifact, final ProjectVersionView projectVersionView) throws FailedInspectionException {
        final RepoPath repoPath = artifact.getRepoPath();
        final ComponentViewWrapper componentViewWrapper;

        if (artifact.getExternalId().isPresent()) {
            final ExternalId externalId = artifact.getExternalId().get();
            try {
                final Optional<ComponentViewWrapper> componentViewWrapperOptional = addComponentToProjectVersion(repoPath, externalId, projectVersionView);
                componentViewWrapper = componentViewWrapperOptional.orElseThrow(() -> new FailedInspectionException(repoPath, "Failed to find component match."));
            } catch (final BlackDuckIntegrationException e) {
                logger.warn(String.format("Cannot find component match for artifact at %s.", repoPath.toPath()));
                throw new FailedInspectionException(repoPath, "Failed to find component match.");
            } catch (final FailedInspectionException e) {
                // If we don't catch and throw, the inspectionStatus message will be too vague.
                throw e;
            } catch (final Exception e) {
                logger.warn(String.format("The Black Duck %s could not successfully inspect '%s'.", InspectionModule.class.getSimpleName(), repoPath.toPath()));
                logger.debug(e.getMessage(), e);
                throw new FailedInspectionException(repoPath, "See logs for details.");
            }
        } else {
            throw new FailedInspectionException(repoPath, "Artifactory failed to provide sufficient information to identify the artifact.");
        }

        return componentViewWrapper;
    }

    private ComponentViewWrapper handleIntegrationRestException(final RepoPath repoPath, final ProjectVersionView projectVersionView, final ComponentVersionView componentVersionView, final IntegrationRestException exception)
        throws FailedInspectionException {
        final int statusCode = exception.getHttpStatusCode();
        final ComponentViewWrapper componentViewWrapper;

        if (statusCode == 412) {
            logger.info(String.format("Unable to add manual BOM component because it already exists '%s'.", repoPath.toPath()));
            try {
                logger.debug("Will attempt to grab policy status from Black Duck directly.");
                componentViewWrapper = getComponentViewWrapper(componentVersionView, projectVersionView);
            } catch (final IntegrationException e) {
                logger.debug("Failed to populate artifact with policy info even though it already exists in the BOM.", e);
                throw new FailedInspectionException(repoPath, "Failed to retrieve policy information.");
            }
        } else if (statusCode == 401) {
            logger.warn(String.format("The Black Duck %s could not successfully inspect %s because plugin is unauthorized (%d). Ensure the plugin is configured with the correct credentials", InspectionModule.class.getSimpleName(),
                repoPath.toPath(), statusCode));
            throw new FailedInspectionException(repoPath, String.format("Unauthorized (%s)", statusCode));
        } else {
            logger.warn(String.format("The Black Duck %s could not successfully inspect %s because of a %d status code", InspectionModule.class.getSimpleName(), repoPath.toPath(), statusCode));
            logger.debug(String.format(exception.getMessage(), repoPath), exception);
            throw new FailedInspectionException(repoPath, String.format("Status code: %s", statusCode));
        }

        return componentViewWrapper;
    }

    // Mostly copied from the ProjectBomService in blackduck-common. Made tweaks so the component that was attempting to be added is always returned.
    private Optional<ComponentViewWrapper> addComponentToProjectVersion(final RepoPath repoPath, final ExternalId componentExternalId, final ProjectVersionView projectVersionView) throws IntegrationException {
        final String projectVersionComponentsUrl = projectVersionView.getFirstLink(ProjectVersionView.COMPONENTS_LINK).orElse(null);
        final Optional<ComponentSearchResultView> componentSearchResultView = componentService.getFirstOrEmptyResult(componentExternalId);
        ComponentViewWrapper componentViewWrapper = null;
        if (componentSearchResultView.isPresent()) {
            final String componentVersionUrl;
            if (StringUtils.isNotBlank(componentSearchResultView.get().getVariant())) {
                componentVersionUrl = componentSearchResultView.get().getVariant();
            } else {
                componentVersionUrl = componentSearchResultView.get().getVersion();
            }
            final ComponentVersionView componentVersionView = blackDuckService.getResponse(new UriSingleResponse<>(componentVersionUrl, ComponentVersionView.class));
            componentViewWrapper = getComponentViewWrapper(componentVersionView, projectVersionView);

            try {
                projectBomService.addComponentToProjectVersion("application/json", projectVersionComponentsUrl, componentVersionUrl);
            } catch (final IntegrationRestException e) {
                componentViewWrapper = handleIntegrationRestException(repoPath, projectVersionView, componentVersionView, e);
            } catch (final BlackDuckApiException e) {
                componentViewWrapper = handleIntegrationRestException(repoPath, projectVersionView, componentVersionView, e.getOriginalIntegrationRestException());
            }
        }

        return Optional.ofNullable(componentViewWrapper);
    }

    private ComponentViewWrapper getComponentViewWrapper(final ComponentVersionView componentVersionView, final ProjectVersionView projectVersionView) throws IntegrationException {
        final Optional<String> projectVersionViewHref = projectVersionView.getHref();
        final Optional<String> componentVersionViewHref = componentVersionView.getHref();

        // This is bad practice but...
        // The link to a VersionBomComponentView cannot be obtained without searching the BOM or manually constructing the link. So for performance in Black Duck, we manually construct the link
        if (projectVersionViewHref.isPresent() && componentVersionViewHref.isPresent()) {
            final String apiComponentsLinkPrefix = "/api/components/";
            final int apiComponentsStart = componentVersionViewHref.get().indexOf(apiComponentsLinkPrefix) + apiComponentsLinkPrefix.length();
            final String versionBomComponentUri = projectVersionViewHref.get() + "/components/" + componentVersionViewHref.get().substring(apiComponentsStart);
            final UriSingleResponse<VersionBomComponentView> versionBomComponentViewUriResponse = new UriSingleResponse<>(versionBomComponentUri, VersionBomComponentView.class);
            final VersionBomComponentView versionBomComponentView = blackDuckService.getResponse(versionBomComponentViewUriResponse);

            return new ComponentViewWrapper(versionBomComponentView, componentVersionView);
        } else {
            throw new IntegrationException("projectVersionViewHref or componentVersionViewHref is not present");
        }
    }

}
