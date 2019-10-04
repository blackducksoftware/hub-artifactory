package com.synopsys.integration.blackduck.artifactory

import org.artifactory.repo.Repositories
import org.artifactory.repo.RepositoryConfiguration
import org.artifactory.search.Searches
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when` as mockWhen

internal class ArtifactoryPAPIServiceTest {
    @Test
    fun getPackageType() {
        val repositoryConfiguration = mock(RepositoryConfiguration::class.java)
        mockWhen(repositoryConfiguration.packageType).thenReturn("maven")
        val repositories = mock(Repositories::class.java)
        mockWhen(repositories.getRepositoryConfiguration("maven-local")).thenReturn(repositoryConfiguration)

        val artifactoryPAPIService = ArtifactoryPAPIService(PluginRepoPathFactory(false), repositories, null)
        val packageType = artifactoryPAPIService.getPackageType("maven-local")

        Assertions.assertTrue(packageType.isPresent)
        Assertions.assertEquals("maven", packageType.get())
    }

    @Test
    fun getArtifactCount() {
        val pluginRepoPathFactory = PluginRepoPathFactory(false)
        val repoKeyPath = pluginRepoPathFactory.create("maven-local-3")
        val repoKeyPath2 = pluginRepoPathFactory.create("maven-local-6")

        val mockPluginRepoPathFactory = mock(PluginRepoPathFactory::class.java)
        mockWhen(mockPluginRepoPathFactory.create(repoKeyPath.repoKey)).thenReturn(repoKeyPath)
        mockWhen(mockPluginRepoPathFactory.create(repoKeyPath2.repoKey)).thenReturn(repoKeyPath2)

        val repositories = mock(Repositories::class.java)
        mockWhen(repositories.getArtifactsCount(repoKeyPath)).thenReturn(3L)
        mockWhen(repositories.getArtifactsCount(repoKeyPath2)).thenReturn(6L)

        val artifactoryPAPIService = ArtifactoryPAPIService(mockPluginRepoPathFactory, repositories, null)
        val artifactCount3 = artifactoryPAPIService.getArtifactCount(listOf(repoKeyPath.repoKey))
        val artifactCount6 = artifactoryPAPIService.getArtifactCount(listOf(repoKeyPath2.repoKey))
        val artifactCount9 = artifactoryPAPIService.getArtifactCount(listOf(repoKeyPath.repoKey, repoKeyPath2.repoKey))

        Assertions.assertEquals(3L, artifactCount3)
        Assertions.assertEquals(6L, artifactCount6)
        Assertions.assertEquals(9L, artifactCount9)
    }

    @Test
    fun isValidRepository_Valid() {
        val pluginRepoPathFactory = PluginRepoPathFactory(false)
        val repoKeyPath = pluginRepoPathFactory.create("maven-local")

        val mockPluginRepoPathFactory = mock(PluginRepoPathFactory::class.java)
        mockWhen(mockPluginRepoPathFactory.create(repoKeyPath.repoKey)).thenReturn(repoKeyPath)

        val repositoryConfiguration = mock(RepositoryConfiguration::class.java)
        val repositories = mock(Repositories::class.java)
        mockWhen(repositories.getRepositoryConfiguration("maven-local")).thenReturn(repositoryConfiguration)
        mockWhen(repositories.exists(repoKeyPath)).thenReturn(true)

        val artifactoryPAPIService = ArtifactoryPAPIService(mockPluginRepoPathFactory, repositories, null)
        val isValid = artifactoryPAPIService.isValidRepository("maven-local")

        Assertions.assertTrue(isValid)
    }

    @Test
    fun isValidRepository_Invalid_Nonexistent() {
        val pluginRepoPathFactory = PluginRepoPathFactory(false)
        val repoKeyPath = pluginRepoPathFactory.create("maven-local")

        val mockPluginRepoPathFactory = mock(PluginRepoPathFactory::class.java)
        mockWhen(mockPluginRepoPathFactory.create(repoKeyPath.repoKey)).thenReturn(repoKeyPath)

        val repositories = mock(Repositories::class.java)
        mockWhen(repositories.exists(repoKeyPath)).thenReturn(false)

        val artifactoryPAPIService = ArtifactoryPAPIService(mockPluginRepoPathFactory, repositories, null)
        val isValid = artifactoryPAPIService.isValidRepository("maven-local")

        Assertions.assertFalse(isValid)
    }

    @Test
    fun isValidRepository_Invalid_NoRepositoryConfiguration() {
        val pluginRepoPathFactory = PluginRepoPathFactory(false)
        val repoKeyPath = pluginRepoPathFactory.create("maven-local")

        val mockPluginRepoPathFactory = mock(PluginRepoPathFactory::class.java)
        mockWhen(mockPluginRepoPathFactory.create(repoKeyPath.repoKey)).thenReturn(repoKeyPath)

        val repositories = mock(Repositories::class.java)
        mockWhen(repositories.getRepositoryConfiguration("maven-local")).thenReturn(null)
        mockWhen(repositories.exists(repoKeyPath)).thenReturn(true)

        val artifactoryPAPIService = ArtifactoryPAPIService(mockPluginRepoPathFactory, repositories, null)
        val isValid = artifactoryPAPIService.isValidRepository("maven-local")

        Assertions.assertFalse(isValid)
    }

    @Test
    fun searchForArtifactsByPatterns() {
        val pluginRepoPathFactory = PluginRepoPathFactory(false)
        val artifact1 = pluginRepoPathFactory.create("maven-local/artifact1.jar")
        val artifact2 = pluginRepoPathFactory.create("maven-local/artifact1.tar.gz")

        val searches = mock(Searches::class.java)
        mockWhen(searches.artifactsByName("*.jar", "maven-local")).thenReturn(listOf(artifact1))
        mockWhen(searches.artifactsByName("*.tar.gz", "maven-local")).thenReturn(listOf(artifact2))

        val artifactoryPAPIService = ArtifactoryPAPIService(null, null, searches)
        val artifacts = artifactoryPAPIService.searchForArtifactsByPatterns(listOf("maven-local"), listOf("*.jar", "*.tar.gz"))

        Assertions.assertEquals(2, artifacts.size)
    }
}