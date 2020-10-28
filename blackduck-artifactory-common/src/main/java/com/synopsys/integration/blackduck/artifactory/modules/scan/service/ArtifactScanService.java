/**
 * blackduck-artifactory-common
 *
 * Copyright (c) 2020 Synopsys, Inc.
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
package com.synopsys.integration.blackduck.artifactory.modules.scan.service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.fs.FileLayoutInfo;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.resource.ResourceStreamHandle;
import org.slf4j.LoggerFactory;

import com.synopsys.integration.blackduck.artifactory.ArtifactoryPAPIService;
import com.synopsys.integration.blackduck.artifactory.ArtifactoryPropertyService;
import com.synopsys.integration.blackduck.artifactory.BlackDuckArtifactoryProperty;
import com.synopsys.integration.blackduck.artifactory.modules.scan.ScanModuleConfig;
import com.synopsys.integration.blackduck.codelocation.Result;
import com.synopsys.integration.blackduck.codelocation.signaturescanner.ScanBatch;
import com.synopsys.integration.blackduck.codelocation.signaturescanner.ScanBatchBuilder;
import com.synopsys.integration.blackduck.codelocation.signaturescanner.ScanBatchOutput;
import com.synopsys.integration.blackduck.codelocation.signaturescanner.ScanBatchRunner;
import com.synopsys.integration.blackduck.codelocation.signaturescanner.command.ScanCommandOutput;
import com.synopsys.integration.blackduck.codelocation.signaturescanner.command.ScanTarget;
import com.synopsys.integration.blackduck.configuration.BlackDuckServerConfig;
import com.synopsys.integration.blackduck.http.client.BlackDuckHttpClient;
import com.synopsys.integration.blackduck.service.model.ProjectNameVersionGuess;
import com.synopsys.integration.blackduck.service.model.ProjectNameVersionGuesser;
import com.synopsys.integration.exception.IntegrationException;
import com.synopsys.integration.log.IntLogger;
import com.synopsys.integration.log.Slf4jIntLogger;
import com.synopsys.integration.util.HostNameHelper;
import com.synopsys.integration.util.IntEnvironmentVariables;
import com.synopsys.integration.util.NameVersion;
import com.synopsys.integration.util.NoThreadExecutorService;

public class ArtifactScanService {
    private final IntLogger logger = new Slf4jIntLogger(LoggerFactory.getLogger(this.getClass()));

    private final ScanModuleConfig scanModuleConfig;
    private final BlackDuckServerConfig blackDuckServerConfig;
    private final File blackDuckDirectory;
    private final RepositoryIdentificationService repositoryIdentificationService;
    private final ArtifactoryPropertyService artifactoryPropertyService;
    private final ArtifactoryPAPIService artifactoryPAPIService;

    public ArtifactScanService(ScanModuleConfig scanModuleConfig, BlackDuckServerConfig blackDuckServerConfig, File blackDuckDirectory, RepositoryIdentificationService repositoryIdentificationService,
        ArtifactoryPropertyService artifactoryPropertyService, ArtifactoryPAPIService artifactoryPAPIService) {
        this.scanModuleConfig = scanModuleConfig;
        this.blackDuckServerConfig = blackDuckServerConfig;
        this.blackDuckDirectory = blackDuckDirectory;
        this.repositoryIdentificationService = repositoryIdentificationService;
        this.artifactoryPropertyService = artifactoryPropertyService;
        this.artifactoryPAPIService = artifactoryPAPIService;
    }

    public void scanArtifactPaths(Set<RepoPath> repoPaths) {
        logger.info(String.format("Found %d repoPaths to scan", repoPaths.size()));
        List<RepoPath> shouldScanRepoPaths = new ArrayList<>();
        for (RepoPath repoPath : repoPaths) {
            logger.debug(String.format("Verifying if repoPath should be scanned: %s", repoPath.toPath()));
            if (repositoryIdentificationService.shouldRepoPathBeScannedNow(repoPath)) {
                logger.info(String.format("Adding repoPath to scan list: %s", repoPath.toPath()));
                shouldScanRepoPaths.add(repoPath);
            }
        }

        for (RepoPath repoPath : shouldScanRepoPaths) {
            try {
                artifactoryPropertyService.setPropertyFromDate(repoPath, BlackDuckArtifactoryProperty.SCAN_TIME, new Date(), logger);
                NameVersion projectNameVersion = determineProjectNameVersion(repoPath);

                ScanBatchOutput scanBatchOutput = scanArtifact(repoPath, projectNameVersion.getName(), projectNameVersion.getVersion());

                writeScanProperties(repoPath, projectNameVersion.getName(), projectNameVersion.getVersion(), scanBatchOutput);
            } catch (Exception e) {
                logger.error(String.format("Please investigate the scan logs for details - the Black Duck Scan did not complete successfully on %s", repoPath.getName()), e);
                artifactoryPropertyService.setProperty(repoPath, BlackDuckArtifactoryProperty.SCAN_RESULT, Result.FAILURE.toString(), logger);
                artifactoryPropertyService.setProperty(repoPath, BlackDuckArtifactoryProperty.SCAN_RESULT_MESSAGE, e.getMessage(), logger);
            } finally {
                deletePathArtifact(repoPath.getName());
            }
        }
    }

    private FileLayoutInfo getArtifactFromPath(RepoPath repoPath) {
        FileLayoutInfo fileLayoutInfo = artifactoryPAPIService.getLayoutInfo(repoPath);

        try (ResourceStreamHandle resourceStream = artifactoryPAPIService.getContent(repoPath)) {
            try (
                InputStream inputStream = resourceStream.getInputStream();
                FileOutputStream fileOutputStream = new FileOutputStream(new File(blackDuckDirectory, repoPath.getName()))
            ) {
                IOUtils.copy(inputStream, fileOutputStream);
            } catch (IOException e) {
                logger.error(String.format("There was an error getting %s", repoPath.getName()), e);
            }
        }

        return fileLayoutInfo;
    }

    private ScanBatchOutput scanArtifact(RepoPath repoPath, String projectName, String projectVersionName) throws IntegrationException, IOException {
        int scanMemory = scanModuleConfig.getMemory();
        boolean dryRun = scanModuleConfig.getDryRun();
        boolean useRepoPathAsCodeLocationName = scanModuleConfig.getRepoPathCodelocation();
        boolean useHostnameInCodeLocationName = scanModuleConfig.getCodelocationIncludeHostname();
        IntEnvironmentVariables intEnvironmentVariables = IntEnvironmentVariables.includeSystemEnv();
        BlackDuckHttpClient blackDuckHttpClient = blackDuckServerConfig.createBlackDuckHttpClient(logger);
        ScanBatchRunner scanBatchRunner = ScanBatchRunner.createDefault(new Slf4jIntLogger(LoggerFactory.getLogger("SignatureScanner")), blackDuckHttpClient, intEnvironmentVariables, new NoThreadExecutorService());
        ScanBatchBuilder scanJobBuilder = new ScanBatchBuilder()
                                              .fromBlackDuckServerConfig(blackDuckServerConfig)
                                              .scanMemoryInMegabytes(scanMemory)
                                              .dryRun(dryRun)
                                              .installDirectory(scanModuleConfig.getCliDirectory())
                                              .outputDirectory(blackDuckDirectory)
                                              .projectAndVersionNames(projectName, projectVersionName);

        File scanFile = new File(blackDuckDirectory, repoPath.getName());
        String scanTargetPath = scanFile.getCanonicalPath();

        String codeLocationName = null;
        if (useRepoPathAsCodeLocationName) {
            if (useHostnameInCodeLocationName) {
                String hostName = HostNameHelper.getMyHostName("UNKNOWN_HOST");
                codeLocationName = String.format("%s#%s", hostName, repoPath.toPath());
            } else {
                codeLocationName = repoPath.toPath();
            }
        }

        ScanTarget scanTarget = ScanTarget.createBasicTarget(scanTargetPath, null, codeLocationName);
        scanJobBuilder.addTarget(scanTarget);

        ScanBatch scanBatch = scanJobBuilder.build();
        logger.info(String.format("Performing scan on '%s'", scanTargetPath));

        return scanBatchRunner.executeScans(scanBatch);
    }

    private NameVersion determineProjectNameVersion(RepoPath repoPath) {
        FileLayoutInfo fileLayoutInfo = getArtifactFromPath(repoPath);
        String fileName = repoPath.getName();
        String project = artifactoryPropertyService.getProperty(repoPath, BlackDuckArtifactoryProperty.BLACKDUCK_PROJECT_NAME)
                             .orElse(fileLayoutInfo.getModule());
        String version = artifactoryPropertyService.getProperty(repoPath, BlackDuckArtifactoryProperty.BLACKDUCK_PROJECT_VERSION_NAME)
                             .orElse(fileLayoutInfo.getBaseRevision());

        boolean missingProjectName = StringUtils.isBlank(project);
        boolean missingProjectVersionName = StringUtils.isBlank(version);
        if (missingProjectName || missingProjectVersionName) {
            String filenameWithoutExtension = FilenameUtils.getBaseName(fileName);
            ProjectNameVersionGuesser guesser = new ProjectNameVersionGuesser();
            ProjectNameVersionGuess guess = guesser.guessNameAndVersion(filenameWithoutExtension);

            if (missingProjectName) {
                project = guess.getProjectName();
            }
            if (missingProjectVersionName) {
                version = guess.getVersionName();
            }
        }

        RepoPath repoKeyPath = RepoPathFactory.create(repoPath.getRepoKey());
        Optional<String> projectName = artifactoryPropertyService.getProperty(repoKeyPath, BlackDuckArtifactoryProperty.BLACKDUCK_PROJECT_NAME);
        String finalProjectName = project;
        String finalProjectVersion = version;
        if (projectName.isPresent()) {
            finalProjectName = projectName.get();
            finalProjectVersion = String.format("%s-%s", project, version);
        }

        return new NameVersion(finalProjectName, finalProjectVersion);
    }

    private void writeScanProperties(RepoPath repoPath, String projectName, String projectNameVersion, ScanBatchOutput scanBatchOutput) {
        Optional<ScanCommandOutput> scanCommandOutput = getFirstScanCommandOutput(scanBatchOutput);
        if (!scanCommandOutput.isPresent()) {
            logger.warn("No scan summaries were available for a successful scan. This is expected if this was a dry run, but otherwise there should be summaries.");
            return;
        }
        Result scanResult = scanCommandOutput.get().getResult();
        artifactoryPropertyService.setProperty(repoPath, BlackDuckArtifactoryProperty.SCAN_RESULT, scanResult.name(), logger);

        if (scanResult.equals(Result.FAILURE)) {
            logger.warn(String.format("The BlackDuck CLI failed to scan %s", repoPath.getName()));
            Optional<String> errorMessage = scanCommandOutput.get().getErrorMessage();
            errorMessage.ifPresent(message -> artifactoryPropertyService.setProperty(repoPath, BlackDuckArtifactoryProperty.SCAN_RESULT_MESSAGE, message, logger));
            return;
        }

        logger.info(String.format("%s was successfully scanned by the BlackDuck CLI.", repoPath.getName()));

        artifactoryPropertyService.deleteProperty(repoPath, BlackDuckArtifactoryProperty.SCAN_RESULT_MESSAGE, logger);
        artifactoryPropertyService.setProperty(repoPath, BlackDuckArtifactoryProperty.BLACKDUCK_PROJECT_NAME, projectName, logger);
        artifactoryPropertyService.setProperty(repoPath, BlackDuckArtifactoryProperty.BLACKDUCK_PROJECT_VERSION_NAME, projectNameVersion, logger);
        artifactoryPropertyService.setProperty(repoPath, BlackDuckArtifactoryProperty.POST_SCAN_ACTION_STATUS, PostScanActionStatus.PENDING.name(), logger);
    }

    private Optional<ScanCommandOutput> getFirstScanCommandOutput(ScanBatchOutput scanBatchOutput) {
        List<ScanCommandOutput> scanCommandOutputs = scanBatchOutput.getOutputs();
        ScanCommandOutput scanCommandOutput = null;
        if (scanCommandOutputs != null && !scanCommandOutputs.isEmpty()) {
            scanCommandOutput = scanCommandOutputs.get(0);
        }

        return Optional.ofNullable(scanCommandOutput);
    }

    private void deletePathArtifact(String fileName) {
        try {
            Files.delete(new File(blackDuckDirectory, fileName).toPath());
            logger.info(String.format("Successfully deleted temporary file %s", fileName));
        } catch (Exception e) {
            logger.error(String.format("Exception deleting %s", fileName), e);
        }
    }
}
