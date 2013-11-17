package org.inigma.maven;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.installer.ArtifactInstallationException;
import org.apache.maven.artifact.installer.ArtifactInstaller;
import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.install.AbstractInstallMojo;
import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.util.ReaderFactory;
import org.codehaus.plexus.util.WriterFactory;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

/**
 * Installs the project's main artifact in the local repository.
 *
 * @author <a href="mailto:sejal.patel@stratixcorp.com">Sejal Patel</a>
 * @goal pomInstall
 * @phase install
 * @since 11/16/13 12:32 PM
 */
public class VersionPomInstallMojo extends AbstractInstallMojo {
    /**
     * @parameter default-value="${project.packaging}"
     * @required
     * @readonly
     */
    protected String packaging;
    /**
     * @component
     */
    protected ArtifactFactory artifactFactory;
    /**
     * @component
     */
    protected ArtifactInstaller myInstaller;
    /**
     * @parameter default-value="${project.file}"
     * @required
     * @readonly
     */
    private File pomFile;
    /**
     * @parameter default-value="${project.artifact}"
     * @required
     * @readonly
     */
    private Artifact artifact;

    public void execute() throws MojoExecutionException {
        super.installer = myInstaller;
        // TODO: push into transformation
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

    private File getPomFile() {
        Reader reader = null;
        Writer writer = null;
        Model model;
        try {
            reader = ReaderFactory.newXmlReader(pomFile);
            model = new MavenXpp3Reader().read(reader);

            File tmpFile = File.createTempFile("mvninstall", ".pom");
            writer = WriterFactory.newXmlWriter(tmpFile);
            Parent parent = model.getParent();
            if (parent != null && parent.getVersion().endsWith("-SNAPSHOT")) {
                parent.setVersion(artifact.getVersion());
            }
            if (model.getVersion() != null && model.getVersion().endsWith("-SNAPSHOT")) {
                model.setVersion(artifact.getVersion());
            }
            new MavenXpp3Writer().write(writer, model);
            return tmpFile;
        } catch (IOException e) {
            getLog().error("Unable to read pom file " + pomFile, e);
        } catch (XmlPullParserException e) {
            getLog().error("Unable to understand pom file " + pomFile, e);
        } finally {
            if (reader != null) {
                IOUtil.close(reader);
            }
            if (writer != null) {
                IOUtil.close(writer);
            }
        }
        return pomFile;
    }
}
