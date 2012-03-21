package org.inigma.maven;

import java.io.IOException;
import java.util.List;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.eclipse.jgit.storage.file.FileRepository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

/**
 * @goal gitVersionBranch
 * 
 * @author <a href="mailto:sejal@inigma.org">Sejal Patel</a>
 */
public class GitVersionBranchMojo extends AbstractMojo {
    /**
     * @parameter default-value="${project.artifact}"
     */
    private Artifact artifact;

    /**
     * The maven project.
     * 
     * @parameter expression="${project}"
     * @readonly
     */
    private MavenProject project;

    /**
     * Contains the full list of projects in the reactor.
     * 
     * @parameter expression="${reactorProjects}"
     * @readonly
     */
    private List<MavenProject> reactorProjects;

    public void execute() throws MojoExecutionException, MojoFailureException {
        VersionInformation version = new VersionInformation();
        String versionString = artifact.getVersion();
        version.setSnapshot(versionString.endsWith("-SNAPSHOT"));
        if (version.isSnapshot()) {
            version.setVersion(versionString.substring(0, versionString.indexOf("-SNAPSHOT")));
        } else {
            version.setVersion(versionString);
        }

        if (version.isSnapshot()) {
            FileRepositoryBuilder repositoryBuilder = new FileRepositoryBuilder().findGitDir();
            if (repositoryBuilder == null) {
                throw new MojoExecutionException("Git Repository could not be found.");
            }
            String branch = null;
            try {
                FileRepository repository = repositoryBuilder.build();
                branch = repository.getBranch();
                getLog().info("Branch name is " + branch);
            } catch (IOException e) {
                throw new MojoExecutionException("Unable to understand the git repository", e);
            }

            if (!"master".equals(branch)) {
                version.setBranchName(branch);
            }
        }

        setProperties(project, version);
        for (MavenProject subproj : reactorProjects) {
            setProperties(subproj, version);
        }
    }

    private void setProperties(MavenProject project, VersionInformation version) {
        String branchStyle = version.getBranchStyle();
        project.getProperties().put("scmVersion", branchStyle); // version-branch-SNAPSHOT
        project.getProperties().put("scmVersion.branch", branchStyle); // branch-SNAPSHOT
        project.getProperties().put("scmVersion.date", version.getDateStyle()); // date-SNAPSHOT
        project.getProperties().put("scmVersion.branchDate", version.getBranchDateStyle());
    }
}
