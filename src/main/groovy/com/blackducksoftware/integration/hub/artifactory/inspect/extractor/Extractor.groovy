package com.blackducksoftware.integration.hub.artifactory.inspect.extractor

import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.io.FilenameUtils
import org.apache.commons.lang3.StringUtils
import org.slf4j.Logger
import org.springframework.beans.factory.annotation.Autowired

import com.blackducksoftware.integration.hub.bdio.BdioNodeFactory
import com.blackducksoftware.integration.hub.bdio.BdioPropertyHelper
import com.blackducksoftware.integration.hub.bdio.model.BdioComponent
import com.blackducksoftware.integration.hub.bdio.model.externalid.ExternalIdFactory

abstract class Extractor {
    @Autowired
    BdioPropertyHelper bdioPropertyHelper

    @Autowired
    BdioNodeFactory bdioNodeFactory

    @Autowired
    ExternalIdFactory externalIdFactory

    abstract boolean shouldAttemptExtract(String artifactName, Map jsonObject)
    abstract BdioComponent extract(String artifactName, Map jsonObject)

    String getExtension(String artifactName) {
        StringUtils.trimToEmpty(FilenameUtils.getExtension(artifactName)).toLowerCase()
    }

    byte[] decompressTarContents(Logger logger, String filename, String artifactName, TarArchiveInputStream tarArchiveInputStream, TarArchiveEntry tarArchiveEntry) {
        long entrySize = tarArchiveEntry.size
        if (entrySize > Integer.MAX_VALUE) {
            logger.warn("${filename} is too large to consume for ${artifactName}")
            return null
        }

        int fileSize = (int)entrySize
        byte[] entryBuffer = new byte[fileSize]
        int offset = 0
        while (offset < fileSize) {
            int entryBytes = tarArchiveInputStream.read(entryBuffer, offset, fileSize - offset)
            offset += entryBytes
        }

        entryBuffer
    }
}