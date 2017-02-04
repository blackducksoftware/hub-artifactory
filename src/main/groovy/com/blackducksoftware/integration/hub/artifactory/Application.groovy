package com.blackducksoftware.integration.hub.artifactory

import javax.annotation.PostConstruct

import org.apache.commons.lang3.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.context.annotation.Bean

import com.blackducksoftware.bdio.model.ExternalIdentifierBuilder
import com.blackducksoftware.integration.hub.artifactory.inspect.ArtifactoryInspector
import com.blackducksoftware.integration.hub.artifactory.scan.ArtifactoryScanConfigurer

@SpringBootApplication
class Application {
    private final Logger logger = LoggerFactory.getLogger(Application.class)

    @Autowired
    ConfigurationManager configurationManager

    @Autowired
    ConfigurationProperties configurationProperties

    @Autowired
    ArtifactoryInspector artifactoryInspector

    @Autowired
    ArtifactoryScanConfigurer artifactoryScanConfigurer

    @Value('${mode}')
    String mode

    static void main(final String[] args) {
        new SpringApplicationBuilder(Application.class).logStartupInfo(false).run(args)
    }

    @PostConstruct
    void init() {
        if (StringUtils.isBlank(mode)) {
            logger.error('You are running without specifying a mode. Please add \'--mode=(run-inspector|configure-inspector|configure-scanner)\' to your command.')
            return
        }

        if (null != System.console() && null != System.out) {
            logger.info('You are running in an interactive mode - if configuration is needed, you should be prompted to provide it.')
            if (mode.contains('configure') || configurationManager.needsBaseConfigUpdate()) {
                configurationManager.updateBaseConfigValues(System.console(), System.out)
            }

            if ('configure-inspector' == mode || ('run-inspector' == mode && configurationManager.needsArtifactoryInspectUpdate())) {
                configurationManager.updateArtifactoryInspectValues(System.console(), System.out)
            } else if ('configure-scanner' == mode) {
                configurationManager.updateArtifactoryScanValues(System.console(), System.out)
            }
        } else {
            logger.info('You are NOT running in an interactive mode - if configuration is needed, and error will occur.')
        }

        if (configurationManager.needsBaseConfigUpdate()) {
            logger.error('You have not provided enough configuration to run either an inspection or a scan - please edit the \'config/application.properties\' file directly, or run from a command line to configure the properties.')
        } else if (('configure-inspector' == mode || 'run-inspector' == mode) && configurationManager.needsArtifactoryInspectUpdate()) {
            logger.error('You have not provided enough configuration to run an inspection - please edit the \'config/application.properties\' file directly, or run from a command line to configure the properties.')
        } else if ('configure-scanner' == mode && configurationManager.needsArtifactoryScanUpdate()) {
            logger.error('You have not provided enough configuration to configure the scan plugin - please edit the \'config/application.properties\' file directly, or run from a command line to configure the properties.')
        } else if ('run-inspector' == mode) {
            artifactoryInspector.performInspect()
        } else if ('configure-scanner' == mode) {
            artifactoryScanConfigurer.createScanPluginFile()
        }
    }

    @Bean
    ExternalIdentifierBuilder externalIdentifierBuilder() {
        ExternalIdentifierBuilder.create()
    }
}
