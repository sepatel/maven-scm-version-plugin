package org.inigma.maven;

import java.io.File;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.deployer.ArtifactDeployer;
import org.apache.maven.artifact.deployer.ArtifactDeploymentException;
import org.apache.maven.artifact.metadata.ArtifactMetadata;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.ArtifactRepositoryFactory;
import org.apache.maven.artifact.repository.layout.ArtifactRepositoryLayout;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.artifact.ProjectArtifactMetadata;

/**
 * Deploys an artifact to remote repository.
 *
 * @author <a href="mailto:sejal.patel@stratixcorp.com">Sejal Patel</a>
 * @goal pomDeploy
 * @phase deploy
 * @threadSafe
 * @since 11/17/13 12:31 AM
 */
public class VersionPomDeployMojo extends AbstractVersionPomMojo {
    private static final Pattern ALT_REPO_SYNTAX_PATTERN = Pattern.compile("(.+)::(.+)::(.+)");
    /**
     * @parameter default-value="${project}"
     * @required
     * @readonly
     */
    private MavenProject project;
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
    /**
     * Parameter used to control how many times a failed deployment will be retried before giving up and failing.
     * If a value outside the range 1-10 is specified it will be pulled to the nearest value within the range 1-10.
     *
     * @parameter expression="${retryFailedDeploymentCount}" default-value="1"
     * @since 2.7
     */
    private int retryFailedDeploymentCount;

    public void execute() throws MojoExecutionException, MojoFailureException {
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
                deploy(pomFile, artifact, repo, localRepository);
            } else {
                Artifact pomArtifact = artifactFactory.createProjectArtifact(artifact.getGroupId(),
                        artifact.getArtifactId(), artifact.getBaseVersion());
                pomArtifact.setFile(pomFile);
                if (updateReleaseInfo) {
                    pomArtifact.setRelease(true);
                }

                deploy(pomFile, pomArtifact, repo, localRepository);
            }
            pomFile.delete();
        } catch (ArtifactDeploymentException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
    }

    private void deploy(File source, Artifact artifact, ArtifactRepository deploymentRepository,
            ArtifactRepository localRepository) throws ArtifactDeploymentException {
        int retryFailedDeploymentCount = Math.max(1, Math.min(10, this.retryFailedDeploymentCount));
        ArtifactDeploymentException exception = null;
        for (int count = 0; count < retryFailedDeploymentCount; count++) {
            try {
                if (count > 0) {
                    getLog().info("Retrying deployment attempt " + (count + 1) + " of " + retryFailedDeploymentCount);
                }
                deployer.deploy(source, artifact, deploymentRepository, localRepository);
                exception = null;
                break;
            } catch (ArtifactDeploymentException e) {
                if (count + 1 < retryFailedDeploymentCount) {
                    getLog().warn("Encountered issue during deployment: " + e.getLocalizedMessage());
                    getLog().debug(e);
                }
                if (exception == null) {
                    exception = e;
                }
            }
        }
        if (exception != null) {
            throw exception;
        }
    }

    private void failIfOffline() throws MojoFailureException {
        if (offline) {
            throw new MojoFailureException("Cannot deploy artifacts when Maven is in offline mode");
        }
    }

    private ArtifactRepository getDeploymentRepository() throws MojoExecutionException, MojoFailureException {
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
}
