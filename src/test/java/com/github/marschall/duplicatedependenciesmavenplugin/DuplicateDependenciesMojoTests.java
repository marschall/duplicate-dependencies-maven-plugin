package com.github.marschall.duplicatedependenciesmavenplugin;

import java.io.File;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.takari.maven.testing.TestResources;
import io.takari.maven.testing.executor.MavenExecution;
import io.takari.maven.testing.executor.MavenExecutionResult;
import io.takari.maven.testing.executor.MavenRuntime;
import io.takari.maven.testing.executor.MavenRuntime.MavenRuntimeBuilder;
import io.takari.maven.testing.executor.MavenVersions;
import io.takari.maven.testing.executor.junit.MavenJUnitTestRunner;

@RunWith(MavenJUnitTestRunner.class)
@MavenVersions("3.8.3")
public class DuplicateDependenciesMojoTests {

  @Rule
  public final TestResources resources = new TestResources();

  private final MavenRuntime mavenRuntime;

  public DuplicateDependenciesMojoTests(MavenRuntimeBuilder builder) throws Exception {
    this.mavenRuntime = builder.build();
  }

  @Test
  public void duplicateClasses() throws Exception {
    File basedir = this.resources.getBasedir("servlets");
    MavenExecution execution = this.mavenRuntime.forProject(basedir);

    MavenExecutionResult result = execution.execute("clean", "verify");
    result.assertLogText("The class: javax.servlet.Servlet is present in the artifacts: "
            + "org.jboss.spec.javax.servlet:jboss-servlet-api_4.0_spec, "
            + "javax.servlet:servlet-api, javax.servlet:javax.servlet-api, jakarta.servlet:jakarta.servlet-api");
    result.assertLogText("BUILD FAILURE");
  }

  @Test
  public void scopeProvided() throws Exception {
    File basedir = this.resources.getBasedir("jcl");
    MavenExecution execution = this.mavenRuntime.forProject(basedir);

    MavenExecutionResult result = execution.execute("clean", "verify");
    result.assertErrorFreeLog();
  }

}
