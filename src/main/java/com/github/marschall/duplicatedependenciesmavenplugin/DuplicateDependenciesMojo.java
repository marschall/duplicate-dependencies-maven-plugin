package com.github.marschall.duplicatedependenciesmavenplugin;

import static org.apache.maven.plugins.annotations.LifecyclePhase.VERIFY;
import static org.apache.maven.plugins.annotations.ResolutionScope.RUNTIME;

import java.io.File;
import java.io.IOException;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
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
import org.eclipse.aether.graph.DependencyFilter;
import org.eclipse.aether.util.filter.AndDependencyFilter;
import org.eclipse.aether.util.filter.PatternExclusionsDependencyFilter;
import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.bag.sorted.MutableSortedBag;
import org.eclipse.collections.api.multimap.list.MutableListMultimap;
import org.eclipse.collections.api.multimap.sortedbag.MutableSortedBagMultimap;
import org.eclipse.collections.impl.factory.Multimaps;
import org.sonatype.plexus.build.incremental.BuildContext;

/**
 * Detects duplicate dependencies with different coordinates. Fails the build
 * if some of them are found.
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

  @Parameter(defaultValue = "${repositorySystemSession}", readonly = true)
  private RepositorySystemSession repositorySession;

  @Component
  private BuildContext buildContext;

  /**
   * List of artifacts to be excluded in the form of {@code groupId:artifactId:type:classifier}.
   *
   * @since 1.1.0
   */
  @Parameter(property = "excludes")
  private List<String> excludes;

  /**
   * Whether plugin execution should be skipped.
   *
   * @since 1.2.0
   */
  @Parameter(defaultValue = "false", property = "duplicateDependencies.skip")
  private boolean skip;

  @Override
  public void execute() throws MojoExecutionException, MojoFailureException {
    if (!this.buildContext.hasDelta("pom.xml")) {
      return;
    }

    List<Dependency> dependencies = this.resolveDependencies();

    ClassAggregation aggregation = this.aggregateClasses(dependencies);
    MutableSortedBagMultimap<String, Artifact> duplicateClasses = aggregation.getDuplicateClasses();
    if (!duplicateClasses.isEmpty()) {
      for (String duplicateClass : duplicateClasses.keysView().toBag()) {
        MutableSortedBag<Artifact> artifacts = duplicateClasses.get(duplicateClass);
        //formatter:off
        String artifactsString = artifacts.stream()
                                          .map(artifact -> artifact.getGroupId() + ":" + artifact.getArtifactId())
                                          .collect(Collectors.joining(", "));
        //formatter:on
        this.getLog().warn("The class: " + duplicateClass + " is present in the artifacts: " + artifactsString);
      }

      MojoFailureException exception = new MojoFailureException("duplicate classes in dependencies detected");
      this.buildContext.addMessage(new File("pom.xml"), 0, 0, ROLE, BuildContext.SEVERITY_ERROR, exception);
      throw exception;
    }
  }

  private ClassAggregation aggregateClasses(List<Dependency> dependencies) throws MojoExecutionException {
    ClassAggregation aggregation = new ClassAggregation();
    //@formatter:off
    Set<Artifact> artifacts = dependencies.stream()
                                          .map(Dependency::getArtifact)
                                          .filter(artifact -> artifact.getExtension().equals("jar"))
                                          .collect(Collectors.toCollection(() -> new TreeSet<>(artifactComparator())));
    //@formatter:on
    for (Artifact artifact : artifacts) {
      try (JarFile jarFile = new JarFile(artifact.getFile())) {
        // TODO Java 9 #asIterator
        Enumeration<JarEntry> entries = jarFile.entries();
        while (entries.hasMoreElements()) {
          JarEntry entry = entries.nextElement();
          String name = entry.getName();
          if (name.endsWith(".class") && !name.equals("module-info.class") && !name.startsWith("META-INF/versions")) {
            aggregation.addClass(name, artifact);
          }
        }
      } catch (IOException e) {
        throw new MojoExecutionException("could not open jar of: " + artifact, e);
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

    return result.getDependencies();
  }

  private DependencyResolutionRequest newDependencyResolutionRequest() {
    DefaultDependencyResolutionRequest request = new DefaultDependencyResolutionRequest();
    request.setMavenProject(this.project);
    request.setRepositorySession(this.repositorySession);
    request.setResolutionFilter(this.buildDependencyFilter());
    return request;
  }

  private DependencyFilter buildDependencyFilter() {
    if (this.excludes.isEmpty()) {
      return CompileOrRuntimeDependencyFilter.INSTANCE;
    } else {
      return new AndDependencyFilter(
              CompileOrRuntimeDependencyFilter.INSTANCE,
              new PatternExclusionsDependencyFilter(this.excludes));
    }
  }

  static Comparator<Artifact> artifactComparator() {
    return Comparator
                     .comparing(Artifact::getGroupId)
                     .thenComparing(Artifact::getArtifactId);
  }

  static final class ClassAggregation {

    private final MutableListMultimap<String, Artifact> classesToArtifacts;

    ClassAggregation() {
      this.classesToArtifacts = Multimaps.mutable.list.empty();
    }

    void addClass(String path, Artifact artifact) {
      // use the JAR path, do the translation to class names only for the duplicates
      this.classesToArtifacts.put(path, artifact);
    }

    MutableSortedBagMultimap<String, Artifact> getDuplicateClasses() {
      MutableSortedBagMultimap<String, Artifact> result = Multimaps.mutable.sortedBag.empty(artifactComparator());
      this.classesToArtifacts.forEachKeyMultiValues((path, values) -> {
        RichIterable<?> artifacts = values;
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
