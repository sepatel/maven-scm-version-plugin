package org.inigma.maven;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.installer.ArtifactInstallationException;
import org.apache.maven.artifact.installer.ArtifactInstaller;
import org.apache.maven.artifact.metadata.ArtifactMetadata;
import org.apache.maven.plugin.MojoExecutionException;
import org.codehaus.plexus.digest.Digester;
import org.codehaus.plexus.digest.DigesterException;
import org.codehaus.plexus.util.FileUtils;

/**
 * Installs the project's main artifact in the local repository.
 *
 * @author <a href="mailto:sejal.patel@stratixcorp.com">Sejal Patel</a>
 * @goal pomInstall
 * @phase install
 * @since 11/16/13 12:32 PM
 */
public class VersionPomInstallMojo extends AbstractVersionPomMojo {
    /**
     * @component
     */
    protected ArtifactInstaller installer;
    /**
     * Flag whether to create checksums (MD5, SHA-1) or not.
     *
     * @parameter expression="${createChecksum}" default-value="false"
     * @since 2.2
     */
    protected boolean createChecksum;
    /**
     * Digester for MD5.
     *
     * @component role-hint="md5"
     */
    protected Digester md5Digester;
    /**
     * Digester for SHA-1.
     *
     * @component role-hint="sha1"
     */
    protected Digester sha1Digester;

    public void execute() throws MojoExecutionException {
        boolean isPomArtifact = "pom".equals(packaging);

        if (updateReleaseInfo) {
            artifact.setRelease(true);
        }

        try {
            File pomFile = getPomFile(); // need the translated version of the pom file
            if (isPomArtifact) {
                installer.install(pomFile, artifact, localRepository);
                installChecksums(artifact);
            } else {
                Artifact pomArtifact = artifactFactory.createProjectArtifact(artifact.getGroupId(),
                        artifact.getArtifactId(), artifact.getBaseVersion());
                pomArtifact.setFile(pomFile);
                if (updateReleaseInfo) {
                    pomArtifact.setRelease(true);
                }

                installer.install(pomFile, pomArtifact, localRepository);
                installChecksums(pomArtifact);
            }
            pomFile.delete();
        } catch (ArtifactInstallationException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
    }

    protected File getLocalRepoFile(Artifact artifact) {
        String path = localRepository.pathOf(artifact);
        return new File(localRepository.getBasedir(), path);
    }

    protected File getLocalRepoFile(ArtifactMetadata metadata) {
        String path = localRepository.pathOfLocalRepositoryMetadata(metadata, localRepository);
        return new File(localRepository.getBasedir(), path);
    }

    protected void installChecksums(Artifact artifact) throws MojoExecutionException {
        if (!createChecksum) {
            return;
        }

        File artifactFile = getLocalRepoFile(artifact);
        installChecksums(artifactFile);

        Collection metadatas = artifact.getMetadataList();
        if (metadatas != null) {
            for (ArtifactMetadata metadata : artifact.getMetadataList()) {
                File metadataFile = getLocalRepoFile(metadata);
                installChecksums(metadataFile);
            }
        }
    }

    private void installChecksum(File originalFile, File installedFile, Digester digester, String ext)
            throws MojoExecutionException {
        String checksum;
        getLog().debug("Calculating " + digester.getAlgorithm() + " checksum for " + originalFile);
        try {
            checksum = digester.calc(originalFile);
        } catch (DigesterException e) {
            throw new MojoExecutionException("Failed to calculate " + digester.getAlgorithm() + " checksum for "
                    + originalFile, e);
        }

        File checksumFile = new File(installedFile.getAbsolutePath() + ext);
        getLog().debug("Installing checksum to " + checksumFile);
        try {
            checksumFile.getParentFile().mkdirs();
            FileUtils.fileWrite(checksumFile.getAbsolutePath(), "UTF-8", checksum);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to install checksum to " + checksumFile, e);
        }
    }

    private void installChecksums(File installedFile) throws MojoExecutionException {
        boolean signatureFile = installedFile.getName().endsWith(".asc");
        if (installedFile.isFile() && !signatureFile) {
            installChecksum(installedFile, installedFile, md5Digester, ".md5");
            installChecksum(installedFile, installedFile, sha1Digester, ".sha1");
        }
    }
}
