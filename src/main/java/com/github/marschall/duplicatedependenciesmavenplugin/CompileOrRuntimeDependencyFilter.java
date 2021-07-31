package com.github.marschall.duplicatedependenciesmavenplugin;

import java.util.List;

import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyFilter;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.util.artifact.JavaScopes;

/**
 * A dependency filter that only allows {@value JavaScopes#COMPILE} and {@value JavaScopes#RUNTIME}.
 */
public final class CompileOrRuntimeDependencyFilter implements DependencyFilter {

  @Override
  public boolean accept(DependencyNode node, List<DependencyNode> parents) {
    Dependency dependency = node.getDependency();

    if (dependency == null) {
      return true;
    }

    String scope = node.getDependency().getScope();
    return scope.equals(JavaScopes.COMPILE) || scope.equals(JavaScopes.RUNTIME);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }

    if (!(obj instanceof CompileOrRuntimeDependencyFilter)) {
      return false;
    }

    return true;
  }

  @Override
  public int hashCode() {
    return CompileOrRuntimeDependencyFilter.class.hashCode();
  }

}