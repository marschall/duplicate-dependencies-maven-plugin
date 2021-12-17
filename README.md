Duplicate Dependencies Maven Plugin  [![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.github.marschall/duplicate-dependencies-maven-plugin/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.github.marschall/duplicate-dependencies-maven-plugin)
===================================

Detects duplicate dependencies with different coordinates for example `org.springframework:spring-jcl` and `commons-logging:commons-logging`.

Usage
-----

```xml
<build>
  <plugins>
    <plugin>
      <groupId>com.github.marschall</groupId>
      <artifactId>duplicate-dependencies-maven-plugin</artifactId>
      <version>1.3.0</version>
      <executions>
        <execution>
          <id>verify</id>
          <goals>
            <goal>detect-duplicate-dependencies</goal>
          </goals>
        </execution>
      </executions>
    </plugin>
  </plugins>
</build>
```

For more information see the [plugin page](https://marschall.github.io/duplicate-dependencies-maven-plugin/).
