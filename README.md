Purpose
-------

The purpose of this plugin is to allow a dynamic version of the project
being worked on if and only if it is a SNAPSHOT. The reason one might
desire this may be a use case such as you branch your code base and
want the artifacts to automatically be versioned to reflect the branch
name instead of the original version number.

This specific case is beneficial to development for many reasons some
of which include allowing multiple parallel development initiatives in
a group without manual alteration of version numbers. Thus any system
for continuous build would not stomp upon one another giving the most
recent committer the winning artifact for use downstream by others.

It would also make merges back into the main codebase easier in that it
would not cause for conflicts on the pom.xml file itself simply because
the version number was manually changed to resolve the above mentioned
issue.

There are another reasons for the version numbers to be dynamically set
at build time rather then via a predetermined value and this plugin is
to help work around the lack of said capability within the core maven
capabilities.

Quick Start Guide
-----------------

A typical use case for utilizing these plugins in the intended fashion
is to put in the following snippet into the parent pom of the project.

    <build>
       <!-- scmVersion is the variable dynamically created -->
       <finalName>${project.artifactId}-${scmVersion}</finalName>
       <plugins>
         <plugin>
           <groupId>org.inigma.maven</groupId>
           <artifactId>maven-scm-version-plugin</artifactId>
           <version>1.1</version>
           <executions>
             <execution>
               <goals>
                 <goal>gitVersion</goal>
                 <goal>pomInstall</goal>
                 <goal>pomDeploy</goal>
               </goals>
               <!-- customize version pattern if desired
               <configuration>
                 <versionPattern>${scmVersion.date}-SNAPSHOT</versionPattern>
               </configuration>
               -->
             </execution>
           </executions>
         </plugin>
       </plugins>
     </build>

