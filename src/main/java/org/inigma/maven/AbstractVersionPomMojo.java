package org.inigma.maven;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.apache.maven.plugin.AbstractMojo;
import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.util.ReaderFactory;
import org.codehaus.plexus.util.WriterFactory;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

/**
 * @author <a href="mailto:sejal.patel@stratixcorp.com">Sejal Patel</a>
 * @since 11/17/13 12:53 AM
 */
public abstract class AbstractVersionPomMojo extends AbstractMojo {
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
     * @parameter default-value="${project.file}"
     * @required
     * @readonly
     */
    protected File pomFile;
    /**
     * @parameter default-value="${project.artifact}"
     * @required
     * @readonly
     */
    protected Artifact artifact;
    /**
     * Whether to update the metadata to make the artifact a release version.
     *
     * @parameter expression="${updateReleaseInfo}" default-value="false"
     */
    protected boolean updateReleaseInfo;
    /**
     * @parameter expression="${localRepository}"
     * @required
     * @readonly
     */
    protected ArtifactRepository localRepository;


    protected File getPomFile() {
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
