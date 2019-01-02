package com.synopsys.integration.blackduck.artifactory.modules;

import java.io.File;
import java.io.IOException;

import org.slf4j.LoggerFactory;

import com.synopsys.integration.bdio.model.externalid.ExternalIdFactory;
import com.synopsys.integration.blackduck.artifactory.ArtifactoryPAPIService;
import com.synopsys.integration.blackduck.artifactory.ArtifactoryPropertyService;
import com.synopsys.integration.blackduck.artifactory.DateTimeManager;
import com.synopsys.integration.blackduck.artifactory.configuration.ConfigurationPropertyManager;
import com.synopsys.integration.blackduck.artifactory.modules.analytics.AnalyticsModule;
import com.synopsys.integration.blackduck.artifactory.modules.analytics.AnalyticsModuleConfig;
import com.synopsys.integration.blackduck.artifactory.modules.analytics.AnalyticsService;
import com.synopsys.integration.blackduck.artifactory.modules.analytics.FeatureAnalyticsCollector;
import com.synopsys.integration.blackduck.artifactory.modules.analytics.SimpleAnalyticsCollector;
import com.synopsys.integration.blackduck.artifactory.modules.inspection.ArtifactIdentificationService;
import com.synopsys.integration.blackduck.artifactory.modules.inspection.ArtifactoryExternalIdFactory;
import com.synopsys.integration.blackduck.artifactory.modules.inspection.CacheInspectorService;
import com.synopsys.integration.blackduck.artifactory.modules.inspection.InspectionModule;
import com.synopsys.integration.blackduck.artifactory.modules.inspection.InspectionModuleConfig;
import com.synopsys.integration.blackduck.artifactory.modules.inspection.MetaDataPopulationService;
import com.synopsys.integration.blackduck.artifactory.modules.inspection.MetaDataUpdateService;
import com.synopsys.integration.blackduck.artifactory.modules.inspection.PackageTypePatternManager;
import com.synopsys.integration.blackduck.artifactory.modules.inspection.metadata.ArtifactMetaDataService;
import com.synopsys.integration.blackduck.artifactory.modules.policy.PolicyModule;
import com.synopsys.integration.blackduck.artifactory.modules.policy.PolicyModuleConfig;
import com.synopsys.integration.blackduck.artifactory.modules.scan.ArtifactScanService;
import com.synopsys.integration.blackduck.artifactory.modules.scan.RepositoryIdentificationService;
import com.synopsys.integration.blackduck.artifactory.modules.scan.ScanModule;
import com.synopsys.integration.blackduck.artifactory.modules.scan.ScanModuleConfig;
import com.synopsys.integration.blackduck.artifactory.modules.scan.ScanPolicyService;
import com.synopsys.integration.blackduck.configuration.BlackDuckServerConfig;
import com.synopsys.integration.blackduck.service.BlackDuckServicesFactory;
import com.synopsys.integration.exception.IntegrationException;
import com.synopsys.integration.log.Slf4jIntLogger;

public class ModuleFactory {
    private final ConfigurationPropertyManager configurationPropertyManager;
    private final BlackDuckServerConfig blackDuckServerConfig;
    private final ArtifactoryPAPIService artifactoryPAPIService;
    private final ArtifactoryPropertyService artifactoryPropertyService;
    private final DateTimeManager dateTimeManager;

    public ModuleFactory(final ConfigurationPropertyManager configurationPropertyManager, final BlackDuckServerConfig blackDuckServerConfig, final ArtifactoryPAPIService artifactoryPAPIService,
        final ArtifactoryPropertyService artifactoryPropertyService, final DateTimeManager dateTimeManager) {
        this.configurationPropertyManager = configurationPropertyManager;
        this.blackDuckServerConfig = blackDuckServerConfig;
        this.artifactoryPAPIService = artifactoryPAPIService;
        this.artifactoryPropertyService = artifactoryPropertyService;
        this.dateTimeManager = dateTimeManager;
    }

    public ScanModule createScanModule(final File blackDuckDirectory) throws IOException, IntegrationException {
        final File cliDirectory = ScanModule.setUpCliDuckDirectory(blackDuckDirectory);
        final ScanModuleConfig scanModuleConfig = ScanModuleConfig.createFromProperties(configurationPropertyManager, artifactoryPAPIService, cliDirectory, dateTimeManager);
        final RepositoryIdentificationService repositoryIdentificationService = new RepositoryIdentificationService(scanModuleConfig, dateTimeManager, artifactoryPropertyService, artifactoryPAPIService);
        final ArtifactScanService artifactScanService = new ArtifactScanService(scanModuleConfig, blackDuckServerConfig, blackDuckDirectory, repositoryIdentificationService, artifactoryPropertyService, artifactoryPAPIService,
            dateTimeManager);
        final SimpleAnalyticsCollector simpleAnalyticsCollector = new SimpleAnalyticsCollector();
        final ScanPolicyService scanPolicyService = ScanPolicyService.createDefault(blackDuckServerConfig, artifactoryPropertyService, dateTimeManager);

        return new ScanModule(scanModuleConfig, repositoryIdentificationService, artifactScanService, artifactoryPropertyService, artifactoryPAPIService, simpleAnalyticsCollector, scanPolicyService);
    }

    public InspectionModule createInspectionModule() throws IOException {
        final InspectionModuleConfig inspectionModuleConfig = InspectionModuleConfig.createFromProperties(configurationPropertyManager, artifactoryPAPIService);
        final CacheInspectorService cacheInspectorService = new CacheInspectorService(artifactoryPropertyService);
        final PackageTypePatternManager packageTypePatternManager = PackageTypePatternManager.fromInspectionModuleConfig(inspectionModuleConfig);
        final ExternalIdFactory externalIdFactory = new ExternalIdFactory();
        final ArtifactoryExternalIdFactory artifactoryExternalIdFactory = new ArtifactoryExternalIdFactory(externalIdFactory);
        final ArtifactMetaDataService artifactMetaDataService = ArtifactMetaDataService.createDefault(blackDuckServerConfig);
        final MetaDataPopulationService metaDataPopulationService = new MetaDataPopulationService(artifactoryPropertyService, cacheInspectorService, artifactMetaDataService);
        final BlackDuckServicesFactory blackDuckServicesFactory = blackDuckServerConfig.createBlackDuckServicesFactory(new Slf4jIntLogger(LoggerFactory.getLogger(ArtifactIdentificationService.class)));
        final ArtifactIdentificationService artifactIdentificationService = new ArtifactIdentificationService(artifactoryPAPIService, packageTypePatternManager, artifactoryExternalIdFactory, artifactoryPropertyService,
            cacheInspectorService, blackDuckServicesFactory, metaDataPopulationService);
        final MetaDataUpdateService metaDataUpdateService = new MetaDataUpdateService(artifactoryPropertyService, cacheInspectorService, artifactMetaDataService, metaDataPopulationService);
        final SimpleAnalyticsCollector simpleAnalyticsCollector = new SimpleAnalyticsCollector();

        return new InspectionModule(inspectionModuleConfig, artifactIdentificationService, artifactoryPAPIService, metaDataPopulationService, metaDataUpdateService, artifactoryPropertyService, cacheInspectorService,
            simpleAnalyticsCollector);
    }

    public PolicyModule createPolicyModule() {
        final PolicyModuleConfig policyModuleConfig = PolicyModuleConfig.createFromProperties(configurationPropertyManager);
        final FeatureAnalyticsCollector featureAnalyticsCollector = new FeatureAnalyticsCollector(PolicyModule.class);

        return new PolicyModule(policyModuleConfig, artifactoryPropertyService, featureAnalyticsCollector);
    }

    public AnalyticsModule createAnalyticsModule(final AnalyticsService analyticsService, final ModuleRegistry moduleRegistry) {
        final AnalyticsModuleConfig analyticsModuleConfig = AnalyticsModuleConfig.createFromProperties(configurationPropertyManager);
        final SimpleAnalyticsCollector simpleAnalyticsCollector = new SimpleAnalyticsCollector();

        return new AnalyticsModule(analyticsModuleConfig, analyticsService, simpleAnalyticsCollector, moduleRegistry);
    }
}
