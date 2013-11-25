package org.inigma.maven;

import java.io.IOException;
import java.util.List;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

/**
 * @author <a href="mailto:sejal@inigma.org">Sejal Patel</a>
 * @goal gitVersion
 * @phase validate
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
    /**
     * Define the desired pattern to use for snapshots of branched code. Valid substitution variables are
     * <ul>
     * <li>scmVersion.number - The original version number without the -SNAPSHOT component.</li>
     * <li>scmVersion.date - The current date in yyyy.MM.dd.hh.mm.ss format.</li>
     * <li>scmVersion.branch - The name of the current branch.</li>
     * </ul>
     *
     * @parameter expression="${versionPattern}" default-value="${scmVersion.branch}-SNAPSHOT"
     * @readonly
     */
    private String versionPattern;

    public void execute() throws MojoExecutionException, MojoFailureException {
        boolean abortVersioning = false;
        getLog().info("Executing GitVersionBranchMojo with pattern " + versionPattern);
        VersionInformation version = new VersionInformation(versionPattern);
        String versionString = artifact.getVersion();
        version.setSnapshot(versionString.endsWith("-SNAPSHOT"));
        if (version.isSnapshot()) {
            version.setVersion(versionString.substring(0, versionString.indexOf("-SNAPSHOT")));
        } else {
            version.setVersion(versionString);
        }

        if (version.isSnapshot()) {
            FileRepositoryBuilder repositoryBuilder = new FileRepositoryBuilder().findGitDir(project.getBasedir());
            if (repositoryBuilder == null) {
                getLog().warn("Git Repository could not be found. Not executing versioning ...");
                abortVersioning = true;
            }
            String branch = "master";
            try {
                Repository repository = repositoryBuilder.build();
                String name = repository.getBranch();
                if (repository.getRef(name) != null) {
                    branch = name;
                }
            } catch (IllegalArgumentException e) {
                getLog().warn("Git Repository could not be found. Not executing versioning");
                abortVersioning = true;
            } catch (IOException e) {
                getLog().warn("Unable to understand the git repository. Not executing versioning ...", e);
                abortVersioning = true;
            }

            version.setBranchName(branch);
        }

        String finalVersion = version.getFinalVersion();
        if (abortVersioning) {
            finalVersion = artifact.getVersion();
        }
        getLog().info("Altering versions to " + finalVersion);
        project.getProperties().put("scmVersion", finalVersion); // branch-SNAPSHOT
        project.setVersion(finalVersion);
        project.getArtifact().setVersion(finalVersion);
        project.getArtifact().setBaseVersion(finalVersion);

        for (MavenProject subproj : reactorProjects) {
            subproj.getProperties().put("scmVersion", finalVersion);
            subproj.setVersion(finalVersion);
            subproj.getArtifact().setVersion(finalVersion);
            subproj.getArtifact().setBaseVersion(finalVersion);
        }
    }
}
