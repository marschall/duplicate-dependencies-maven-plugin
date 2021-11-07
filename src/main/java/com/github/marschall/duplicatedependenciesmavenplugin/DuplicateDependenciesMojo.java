package com.github.marschall.duplicatedependenciesmavenplugin;

import static org.apache.maven.plugins.annotations.LifecyclePhase.VERIFY;
import static org.apache.maven.plugins.annotations.ResolutionScope.RUNTIME;

import java.io.IOException;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.DefaultDependencyResolutionRequest;
import org.apache.maven.project.DependencyResolutionException;
import org.apache.maven.project.DependencyResolutionRequest;
import org.apache.maven.project.DependencyResolutionResult;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectDependenciesResolver;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.multimap.list.MutableListMultimap;
import org.eclipse.collections.impl.factory.Multimaps;
import org.sonatype.plexus.build.incremental.BuildContext;

/**
 * Generates a PKCS12 truststore from a collection of certificates located in a folder.
 * Runs during a normal Maven build.
 */
@Mojo(
  name = "detect-duplicate-dependencies",
  threadSafe = true,
  defaultPhase = VERIFY,
  requiresDependencyResolution = RUNTIME
)
public class DuplicateDependenciesMojo extends AbstractMojo {

  @Component
  private ProjectDependenciesResolver resolver;

  @Parameter(defaultValue = "${project}", readonly = true)
  private MavenProject project;

  @Parameter(defaultValue = "${repositorySystemSession}")
  private RepositorySystemSession repositorySession;

  @Component
  private BuildContext buildContext;

  @Override
  public void execute() throws MojoExecutionException, MojoFailureException {
    if (!this.buildContext.hasDelta("pom.xml")) {
      return;
    }

    List<Dependency> dependencies = this.resolveDependencies();

    ClassAggregation aggregation = this.aggregateClasses(dependencies);
    MutableListMultimap<String,Artifact> duplicateClasses = aggregation.getDuplicateClasses();
    if (!duplicateClasses.isEmpty()) {
      for (String duplicateClass : duplicateClasses.keysView().toSortedSet()) {
        MutableList<Artifact> artifacts = duplicateClasses.get(duplicateClass);
        //formatter:off
        String artifactsString = artifacts.stream()
                                          .map(artifact -> artifact.getGroupId() + ":" + artifact.getArtifactId())
                                          .collect(Collectors.joining(", "));
        //formatter:on
        this.getLog().warn("The class: " + duplicateClass + " is present in the artifacts: " + artifactsString);
      }

      MojoFailureException exception = new MojoFailureException("duplicate classes in dependencies detected");
      this.buildContext.addMessage(null, 0, 0, ROLE, BuildContext.SEVERITY_ERROR, exception);
      throw exception;
    }
  }

  private ClassAggregation aggregateClasses(List<Dependency> dependencies)
          throws MojoExecutionException {
    ClassAggregation aggregation = new ClassAggregation();
    for (Dependency dependency : dependencies) {
      Artifact artifact = dependency.getArtifact();
      if (artifact.getExtension().equals("jar")) {
        try (JarFile jarFile = new JarFile(artifact.getFile())) {
          // TODO Java 9 #asIterator
          Enumeration<JarEntry> entries = jarFile.entries();
          while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            String name = entry.getName();
            if (name.endsWith(".class") && !name.startsWith("META-INF/versions")) {
              aggregation.addClass(name, artifact);
            }
          }
        } catch (IOException e) {
          throw new MojoExecutionException("could not open jar of: " + artifact, e);
        }
      }
    }
    return aggregation;
  }

  private List<Dependency> resolveDependencies() throws MojoExecutionException {
    DependencyResolutionRequest request = this.newDependencyResolutionRequest();

    DependencyResolutionResult result;
    try {
      result = this.resolver.resolve(request);
    } catch (DependencyResolutionException e) {
      throw new MojoExecutionException("could not resolve dependencies", e);
    }

    List<Dependency> dependencies = result.getDependencies();
    return dependencies;
  }

  private DependencyResolutionRequest newDependencyResolutionRequest() {
    DefaultDependencyResolutionRequest request = new DefaultDependencyResolutionRequest();
    request.setMavenProject(this.project);
    request.setRepositorySession(this.repositorySession);
    request.setResolutionFilter(CompileOrRuntimeDependencyFilter.INSTANCE);
    return request;
  }

  static final class ClassAggregation {

    private final MutableListMultimap<String, Artifact> classesToArtifacts;

    ClassAggregation() {
      this.classesToArtifacts = Multimaps.mutable.list.empty();
    }

    void addClass(String path, Artifact artifact) {
      // use the JAR path and to the translation to class names only for the duplicates
      this.classesToArtifacts.put(path, artifact);
    }

    MutableListMultimap<String, Artifact> getDuplicateClasses() {
      MutableListMultimap<String, Artifact> result = Multimaps.mutable.list.empty();
      this.classesToArtifacts.forEachKeyMultiValues((path, values) -> {
        RichIterable<?> artifacts = (RichIterable<?>) values;
        if (artifacts.size() > 1) {
          result.putAll(toClassName(path), (Iterable<? extends Artifact>) artifacts);
        }
      });
      return result;
    }

    static String toClassName(String path) {
      return path.substring(0, path.length() - ".class".length()).replace('/', '.');
    }

  }

}
