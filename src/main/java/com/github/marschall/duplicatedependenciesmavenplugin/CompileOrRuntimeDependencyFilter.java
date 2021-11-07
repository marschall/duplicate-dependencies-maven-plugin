package com.github.marschall.duplicatedependenciesmavenplugin;

import java.util.List;

import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyFilter;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.util.artifact.JavaScopes;

/**
 * A dependency filter that only allows {@value JavaScopes#COMPILE} and {@value JavaScopes#RUNTIME}.
 */
final class CompileOrRuntimeDependencyFilter implements DependencyFilter {

  static final DependencyFilter INSTANCE = new CompileOrRuntimeDependencyFilter();

  private CompileOrRuntimeDependencyFilter() {
    super();
  }

  @Override
  public boolean accept(DependencyNode node, List<DependencyNode> parents) {
    Dependency dependency = node.getDependency();

    if (dependency == null) {
      return true;
    }

    String scope = node.getDependency().getScope();
    return scope.equals(JavaScopes.COMPILE) || scope.equals(JavaScopes.RUNTIME);
  }

}