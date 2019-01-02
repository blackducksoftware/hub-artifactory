package com.synopsys.integration.blackduck.artifactory.modules;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.LoggerFactory;

import com.synopsys.integration.blackduck.artifactory.modules.analytics.AnalyticsService;
import com.synopsys.integration.log.IntLogger;
import com.synopsys.integration.log.Slf4jIntLogger;
import com.synopsys.integration.util.BuilderStatus;

/**
 * A list of modules that have passed verification and registers them for analytics collection
 */
public class ModuleRegistry {
    private final IntLogger logger = new Slf4jIntLogger(LoggerFactory.getLogger(this.getClass()));

    private final List<Module> registeredModules = new ArrayList<>();
    private final List<Module> allModules = new ArrayList<>();

    private final AnalyticsService analyticsService;

    public ModuleRegistry(final AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    private void registerModule(final Module module) {
        final ModuleConfig moduleConfig = module.getModuleConfig();
        final BuilderStatus builderStatus = new BuilderStatus();
        moduleConfig.validate(builderStatus);

        allModules.add(module);
        if (builderStatus.isValid()) {
            registeredModules.add(module);
            analyticsService.registerAnalyzable(module);
            logger.info(String.format("Successfully registered '%s'", moduleConfig.getModuleName()));
        } else {
            moduleConfig.setEnabled(false);
            logger.warn(String.format("Can't register module '%s' due to an invalid configuration. See details below. %s%s", moduleConfig.getModuleName(), System.lineSeparator(), builderStatus.getFullErrorMessage(System.lineSeparator())));
        }
    }

    public void registerModules(final Module... modules) {
        for (final Module module : modules) {
            registerModule(module);
        }
    }

    /**
     * @return a list of ModuleConfig from registered modules with valid configurations
     */
    public List<ModuleConfig> getModuleConfigs() {
        return registeredModules.stream()
                   .map(Module::getModuleConfig)
                   .collect(Collectors.toList());
    }

    /**
     * @return a list of all ModuleConfig regardless of validation status
     */
    public List<ModuleConfig> getAllModuleConfigs() {
        return allModules.stream()
                   .map(Module::getModuleConfig)
                   .collect(Collectors.toList());
    }

    public List<ModuleConfig> getModuleConfigsByName(final String moduleName) {
        return getModuleConfigs().stream()
                   .filter(moduleConfig -> moduleConfig.getModuleName().equalsIgnoreCase(moduleName))
                   .collect(Collectors.toList());
    }
}
