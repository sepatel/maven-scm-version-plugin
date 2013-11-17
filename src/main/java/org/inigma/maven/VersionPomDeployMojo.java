package org.inigma.maven;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.deployer.ArtifactDeployer;
import org.apache.maven.artifact.deployer.ArtifactDeploymentException;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.metadata.ArtifactMetadata;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.ArtifactRepositoryFactory;
import org.apache.maven.artifact.repository.layout.ArtifactRepositoryLayout;
import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.deploy.AbstractDeployMojo;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.artifact.ProjectArtifactMetadata;
import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.util.ReaderFactory;
import org.codehaus.plexus.util.WriterFactory;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

/**
 * Deploys an artifact to remote repository.
 *
 * @author <a href="mailto:sejal.patel@stratixcorp.com">Sejal Patel</a>
 * @goal pomDeploy
 * @phase deploy
 * @threadSafe
 * @since 11/17/13 12:31 AM
 */
public class VersionPomDeployMojo extends AbstractDeployMojo {
    private static final Pattern ALT_REPO_SYNTAX_PATTERN = Pattern.compile("(.+)::(.+)::(.+)");
    /**
     * Component used to create an artifact.
     *
     * @component
     */
    private ArtifactFactory artifactFactory;
    /**
     * @parameter default-value="${project}"
     * @required
     * @readonly
     */
    private MavenProject project;
    /**
     * @parameter default-value="${project.artifact}"
     * @required
     * @readonly
     */
    private Artifact artifact;
    /**
     * @parameter default-value="${project.packaging}"
     * @required
     * @readonly
     */
    private String packaging;
    /**
     * @parameter default-value="${project.file}"
     * @required
     * @readonly
     */
    private File pomFile;
    /**
     * Specifies an alternative repository to which the project artifacts should be deployed ( other
     * than those specified in &lt;distributionManagement&gt; ).
     * <br/>
     * Format: id::layout::url
     *
     * @parameter expression="${altDeploymentRepository}"
     */
    private String altDeploymentRepository;
    /**
     * Set this to 'true' to bypass artifact deploy
     *
     * @parameter expression="${maven.deploy.skip}" default-value="false"
     * @since 2.4
     */
    private boolean skip;
    /**
     * Flag whether Maven is currently in online/offline mode.
     *
     * @parameter default-value="${settings.offline}"
     * @readonly
     */
    private boolean offline;
    /**
     * Component used to create a repository.
     *
     * @component
     */
    private ArtifactRepositoryFactory repositoryFactory;
    /**
     * Map that contains the layouts.
     *
     * @component role="org.apache.maven.artifact.repository.layout.ArtifactRepositoryLayout"
     */
    private Map repositoryLayouts;
    /**
     * @component
     */
    private ArtifactDeployer deployer;

    public void execute() throws MojoExecutionException, MojoFailureException {
        setDeployer(deployer);
        if (skip) {
            getLog().info("Skipping artifact deployment");
            return;
        }

        failIfOffline();

        ArtifactRepository repo = getDeploymentRepository();
        String protocol = repo.getProtocol();
        if (protocol.equalsIgnoreCase("scp")) {
            File sshFile = new File(System.getProperty("user.home"), ".ssh");

            if (!sshFile.exists()) {
                sshFile.mkdirs();
            }
        }

        // Deploy the POM
        boolean isPomArtifact = "pom".equals(packaging);
        if (!isPomArtifact) {
            ArtifactMetadata metadata = new ProjectArtifactMetadata(artifact, pomFile);
            artifact.addMetadata(metadata);
        }

        if (updateReleaseInfo) {
            artifact.setRelease(true);
        }

        try {
            File pomFile = getPomFile(); // need the translated version of the pom file
            if (isPomArtifact) {
                deploy(pomFile, artifact, repo, getLocalRepository());
            } else {
                Artifact pomArtifact = artifactFactory.createProjectArtifact(artifact.getGroupId(),
                        artifact.getArtifactId(), artifact.getBaseVersion());
                pomArtifact.setFile(pomFile);
                if (updateReleaseInfo) {
                    pomArtifact.setRelease(true);
                }

                deploy(pomFile, pomArtifact, repo, getLocalRepository());
            }
            pomFile.delete();
        } catch (ArtifactDeploymentException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
    }

    private void failIfOffline()
            throws MojoFailureException {
        if (offline) {
            throw new MojoFailureException("Cannot deploy artifacts when Maven is in offline mode");
        }
    }

    private ArtifactRepository getDeploymentRepository()
            throws MojoExecutionException, MojoFailureException {
        ArtifactRepository repo = null;

        if (altDeploymentRepository != null) {
            getLog().info("Using alternate deployment repository " + altDeploymentRepository);

            Matcher matcher = ALT_REPO_SYNTAX_PATTERN.matcher(altDeploymentRepository);

            if (!matcher.matches()) {
                throw new MojoFailureException(altDeploymentRepository, "Invalid syntax for repository.",
                        "Invalid syntax for alternative repository. Use \"id::layout::url\".");
            } else {
                String id = matcher.group(1).trim();
                String layout = matcher.group(2).trim();
                String url = matcher.group(3).trim();

                ArtifactRepositoryLayout repoLayout = getLayout(layout);

                repo = repositoryFactory.createDeploymentArtifactRepository(id, url, repoLayout, true);
            }
        }

        if (repo == null) {
            repo = project.getDistributionManagementArtifactRepository();
        }

        if (repo == null) {
            String msg = "Deployment failed: repository element was not specified in the POM inside"
                    + " distributionManagement element or in -DaltDeploymentRepository=id::layout::url parameter";

            throw new MojoExecutionException(msg);
        }

        return repo;
    }

    private ArtifactRepositoryLayout getLayout(String id) throws MojoExecutionException {
        ArtifactRepositoryLayout layout = (ArtifactRepositoryLayout) repositoryLayouts.get(id);

        if (layout == null) {
            throw new MojoExecutionException("Invalid repository layout: " + id);
        }

        return layout;
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
