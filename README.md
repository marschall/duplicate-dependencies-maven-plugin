Duplicate Dependencies Maven Plugin
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
      <version>1.0.0</version>
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
