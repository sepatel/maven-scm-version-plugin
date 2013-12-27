package org.inigma.maven;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.ReflectionUtils;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.sonatype.aether.repository.WorkspaceReader;

/**
 * @author <a href="mailto:sejal@inigma.org">Sejal Patel</a>
 * @goal gitVersion
 * @phase validate
 */
public class GitVersionBranchMojo extends AbstractVersionPomMojo {
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
    /**
     * @parameter expression="${session}"
     * @required
     * @readonly
     */
    private MavenSession mavenSession;

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
        if (abortVersioning || artifact.getVersion().equals(finalVersion)) {
            return;
        }

        String originalVersion = artifact.getVersion();

        getLog().info("Altering versions to " + finalVersion);
        updateProjectInformation(project, finalVersion);

        for (MavenProject subproj : reactorProjects) {
            updateProjectInformation(subproj, finalVersion);
        }

        hackReactorReaderField("projectsByGAV", originalVersion, finalVersion);
    }

    private void hackReactorReaderField(String field, String originalVersion, String newVersion) {
        WorkspaceReader reader = mavenSession.getRepositorySession().getWorkspaceReader();
        try {
            Field f = ReflectionUtils.getFieldByNameIncludingSuperclasses(field, Class.forName("org.apache.maven.ReactorReader"));
            f.setAccessible(true);
            Map<String, MavenProject> newprojects = new HashMap<String, MavenProject>();
            Map<String, MavenProject> projects = (Map<String, MavenProject>) f.get(reader);
            //getLog().debug("What is in " + field + ": " + projects);
            for (Entry<String, MavenProject> entry : projects.entrySet()) {
                MavenProject mavenProject = entry.getValue();
                //getLog().debug("Source: " + mavenProject);
                File hackPom = getPomFile(mavenProject.getFile());
                mavenProject.setFile(hackPom);
                //getLog().debug("New: " + mavenProject);
                newprojects.put(entry.getKey().replaceAll(originalVersion, newVersion), mavenProject);
            }
            projects.clear();
            projects.putAll(newprojects);
            //getLog().debug("This is now: " + projects);
        } catch (Exception e) {
            getLog().error("Doh! Something broke!", e);
        }
    }

    private void updateProjectInformation(MavenProject prj, String finalVersion) {
        prj.getProperties().put("scmVersion", finalVersion); // branch-SNAPSHOT
        prj.setVersion(finalVersion);
        prj.getArtifact().setVersion(finalVersion);
        prj.getArtifact().setBaseVersion(finalVersion);

        for (Dependency dependency : prj.getDependencies()) {
            if (artifact.getGroupId().equals(dependency.getGroupId())) {
                dependency.setVersion(finalVersion);
            }
        }
    }
}
