# SIT - Getting Started with the Test Containers

## Overview

Below:

- TL;DR = Quick Start with major components and boilerplate snippets

- After that is more depth.

- Also, see the source code under the project folder for a minimal,
  fully-functional example.

**NOTE** this project does not include any containers with System
dependencies (e.g. DB, JMS, etc) for the application.

## TL;DR

<figure>
<img src="./assets/3rdparty/pexels-kelly-1179532-2519370.jpg"
alt="pexels kelly 1179532 2519370" />
</figure>

Below are templates with most of the boilerplate to get started:

1.  Try the Example

2.  Create Multi-Module Project

3.  Write the Application

4.  Configure Test Containers and Wire into Failsafe

5.  Write the Integration Tests

6.  Configure the Failsafe Plugin

7.  Profit

### Try the Example

``` bash
$ cd project
$ mvn clean install
```

### Create Multi-Module Project

- main - application code

- docker-it - tests and maven build instructions to run the docker
  containers

### Write the Application

**ProjectMain.java**

``` java
@SpringBootApplication
public class ProjectMain {

    public static void main(String[] args) {
        SpringApplication.run(ProjectMain.class, args);
    }
}
```

**ProjectRestResource.java**

``` java
@Component
@Path("/api")
public class ProjectRestResource {

    @GET
    @Path("/hi")
    public String helloEndpoint() {
        return "Hello";
    }
}
```

**JerseyWiring.java**

``` java
@Configuration
public class JerseyWiring extends ResourceConfig {
    public JerseyWiring(@Autowired ProjectRestResource projectRestResource) {
        this.registerInstances(projectRestResource);
    }
}
```

### Configure Test Containers and Wire into Failsafe

``` java
@SuppressWarnings("rawtypes")
public class TestContainerRunnerClassRule extends ExternalResource {

    private static final Logger DEFAULT_LOGGER = LoggerFactory.getLogger(TestContainerRunnerClassRule.class);

    private Logger LOG = DEFAULT_LOGGER;

    private GenericContainer applicationContainer;

    private Network network;
    private String projectVersion;
    private String applicationDependencyDirPath;

    public TestContainerRunnerClassRule() {
        this.applicationContainer = new GenericContainer(DockerImageName.parse("eclipse-temurin").withTag("21-jre").toString());
    }

    @Override
    protected void before() throws Throwable {
        this.network = Network.newNetwork();
        LOG.info("USING TEST DOCKER NETWORK {}", network.getId());

        this.readConfiguration();
        this.startApplicationContainer();
    }

    @Override
    protected void after() {
        this.applicationContainer.stop();
    }

//========================================
// Container Startups
//----------------------------------------

    private void readConfiguration() {
        this.projectVersion = System.getProperty("PROJECT-VERSION");
        if (this.projectVersion == null) {
            throw new RuntimeException("Test requires system property, PROJECT-VERSION; aborting");
        }

        this.applicationDependencyDirPath = System.getProperty("application-dependency-dir-path");
        if (this.applicationDependencyDirPath == null) {
            throw new RuntimeException("Test requires system property, application-dependency-dir-path; aborting");
        }
    }

    private void startApplicationContainer() {
        this.applicationContainer
            .withNetwork(network)
            .withNetworkAliases("application", "application-host")
            .withExposedPorts(8080, 5005)
            .withStartupTimeout(Duration.ofMinutes(5))
            .withEnv("JAVA_TOOL_OPTIONS", "-Djava.security.egd=file:/dev/./urandom -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005")
            .withLogConsumer(new Slf4jLogConsumer(LOG).withPrefix("APPLICATION"))
            .withCopyFileToContainer(
                MountableFile.forHostPath(this.applicationDependencyDirPath), "/app"
            )
            .withCommand("java -Dloader.path=/app/config -jar /app/black-box-systest-tc-main-" + this.projectVersion + ".jar")
            ;

        // DEBUGGING: uncomment to force local port 5005
        // this.applicationContainer.getPortBindings().add("5005:5005");
        this.applicationContainer.start();

        var httpPort = this.applicationContainer.getMappedPort(8080); // application-http-port
        var debuggerPort = this.applicationContainer.getMappedPort(5005);

        LOG.info("APPLICATION MAPPED PORTS: http={}; debugger={}",
            httpPort,
            debuggerPort
            );

        System.setProperty("application.base-url", "http://localhost:" + httpPort);
    }
}
```

### Write the Integration Tests

``` java
public class HelloIT {

    @ClassRule
    public static TestContainerRunnerClassRule testContainerRunnerClassRule = new TestContainerRunnerClassRule();

    @Test
    public void testHelloEndpoint() {
        ...
    }
}
```

### Configure the Failsafe Plugin

``` xml
<!--                -->
<!-- TEST EXECUTION -->
<!--                -->
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-failsafe-plugin</artifactId>
    <version>3.0.0-M5</version>
    <executions>
        <execution>
            <id>projname-integration-test</id>
            <goals>
                <goal>integration-test</goal>
            </goals>
            <phase>integration-test</phase>
            <configuration>
                <excludes>
                    <exclude>none</exclude>
                </excludes>
                <includes>
                    <include>**/*IT.java</include>
                </includes>
            </configuration>
        </execution>

        <!-- Fail the build on IT Failures.  Executed as a separate step so that post-integration-test -->
        <!--  phase executes even after an IT failure.                                                 -->
        <execution>
            <id>projname-verify-it</id>
            <goals>
                <goal>verify</goal>
            </goals>
        </execution>
    </executions>
    <configuration>
        <!--suppress MavenModelInspection -->
        <skipITs>${skipITs}</skipITs>
        <reuseForks>true</reuseForks>
        <useSystemClassLoader>false</useSystemClassLoader>
        <systemProperties>
            <property>
                <name>application-dependency-dir-path</name>
                <!--suppress MavenModelInspection -->
                <value>${project.build.directory}/dependency</value>
            </property>
            <property>
                <name>PROJECT-VERSION</name>
                <!--suppress MavenModelInspection -->
                <value>${project.version}</value>
            </property>
        </systemProperties>
    </configuration>
</plugin>
```

### Profit

``` bash
$ mvn clean install
```

## What’s the Opposite of TL;DR?

<figure>
<img src="./assets/3rdparty/pexels-tima-miroshnichenko-10625976.jpg"
alt="pexels tima miroshnichenko 10625976" />
</figure>

There is a good amount of boiler-plate to get started with Test
Containers, and some gotchas.

Here we cover the basics, provide an example, and discuss the basics of
the example which can be used as a template for your own projects.

Note that this article specifically covers the use of `Test Containers`
and not the use of the alternative with `docker-maven-plugin` (See the
article [SIT - Getting Started with the Docker Maven
Plugin](https://github.com/savoirtech/black-box-system-test-dmp) for
that approach). The main advantages of Test Containers over the plugin
include:

- Programmable Container Setup - containers can be tweaked and inspected
  through Java code using APIs provided by Test Containers, including
  inspecting running containers, and running commands inside those
  running containers during test startup.

  - For example, Kafka servers must be properly configured to advertise
    their addresses to clients, which causes problems with docker and
    ephemeral ports since host mapped port numbers are not known until
    the container starts. The kafka test container works around this
    problem using Java code that executes a `kafka-configs` command to
    update Kafka, after it has already started, with
    `advertised.listeners` that includes both (a) the
    container-to-container network host + port, and (b) the host-mapped
    port and hostname that can be used from the host to reach Kafka
    running inside the container. Note that this problem is challenging
    to solve when using the `docker-maven-plugin` and is solved
    automatically by using the reusable `KafkaContainer` implementation
    included with Test Containers. (Feel free to open start a discussion
    on this topic if you are interested in more details.)

- Container Cleanup - Test Containers use an extra container, named with
  prefix `testcontainers-ryuk-`, that watches for shutdown conditions
  and automatically removes the test containers when needed. This is
  great for Maven builds that are interrupted.

**NOTE** a notable disadvantage of Test Containers is a lack of
alignment/parity with the Maven lifecycle. Care must be taken to avoid
spinning up and shutting down containers repeatedly as this leads to
significant overhead that can lead to, or at least contribute to, builds
that take many hours to run instead of (ideally, a small number of)
minutes.

## Multi-Module Project Structure

While it is possible to use the `Test Containers` in a single-module
project, doing so makes the POM harder to read, and can complicate the
flow. This author is a huge fan of modularity, within reason, and
strongly advocates for the use of a multi-module project here.

The structure of the example project:

- Parent

  - Main

  - Docker-IT

### Parent POM

Links together the entire project through Maven modules:

``` xml
<modules>
    <module>main</module>
    <module>docker-it</module>
</modules>
```

The parent is also a great place for the following, although it is not
demonstrated in this example project:

- Defining common versions of dependencies to use across the entire
  project

- Defining common versions, and configuration, for plugins used across
  the entire project

- Defining common profiles and/or build properties to support build
  parameters (e.g. skipping tests)

### Main JAR

The entire demo application is contained in this one JAR file using the
spring-boot-maven-plugin to generate an "executable jar" file.

Included in this example project is a web service with a simple endpoint
that returns a fixed response with the text `Hello` served at the path
`/api/hi`.

To view the code, please see the TL;DR section above for a quick
overview, or view the full code itself under the project folder. Here
are links to the full code of the Main module:

- [pom.xml](https://github.com/savoirtech/black-box-system-test-tc/blob/main/project/main/pom.xml)

- [ProjectMain.java](https://github.com/savoirtech/black-box-system-test-tc/blob/main/project/main/src/main/java/com/savoirtech/blog/systest/ProjectMain.java)

- [ProjectRestResource.java](https://github.com/savoirtech/black-box-system-test-tc/blob/main/project/main/src/main/java/com/savoirtech/blog/systest/rest/ProjectRestResource.java)

- [JerseyWiring.java](https://github.com/savoirtech/black-box-system-test-tc/blob/main/project/main/src/main/java/com/savoirtech/blog/systest/rest/JerseyWiring.java)

#### Generating the Executable JAR

Below is the section of the `pom.xml` in the Main module that packages
the JAR file into an "executable jar".

``` xml
<plugin>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-maven-plugin</artifactId>
    <version>${spring-boot.version}</version>
    <configuration>
        <layout>ZIP</layout>
    </configuration>
    <executions>
        <execution>
            <goals>
                <goal>repackage</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

Note the use of the ZIP layout - this enables deployments to easily add
to the executions class path through environment variables or system
properties. Adding to the classpath enhances support for externalized
configuration. Here’s an example using an environment variable to add to
the class path:

``` bash
$ LOADER_PATH="/opt/application/config"
$ export LOADER_PATH
$ java -jar ...
```

### Docker IT

Here are the key parts for the System Tests:

- creation of a test container for the application

- instructions to spin up and shut down test containers

- test code itself

- handling errors reported by the tests

Please see the TL;DR section above for a quick overview of the code, or
view the full code itself under the project folder. Here are links to
the full code of the Docker IT module:

- [pom.xml](https://github.com/savoirtech/black-box-system-test-tc/blob/main/project/docker-it/pom.xml)

- [TestContainerRunnerClassRule.java](https://github.com/savoirtech/black-box-system-test-tc/blob/main/project/docker-it/src/test/java/com/savoirtech/blog/systest/it/TestContainerRunnerClassRule.java)

- [HelloIT.java](https://github.com/savoirtech/black-box-system-test-tc/blob/main/project/docker-it/src/test/java/com/savoirtech/blog/systest/it/HelloIT.java)

### Creation of a Test Container for the Application

Applications may or may not require a docker container to be created as
a product of the build. This example excludes a separate image as a
product of the build, and instead creates a test-specific image only.

Here is the **from** definition for the container. For this example, a
simple Java-based container is all we need.

``` xml
this.applicationContainer = new GenericContainer(DockerImageName.parse("eclipse-temurin").withTag("21-jre").toString());
```

A subtle, but valuable part of the image creation here uses the
`maven-dependency-plugin` to copy the main JAR into the `docker-it`
project’s target folder. While it is feasible to directly link to the
JAR file in the main sub-module’s folder, there are cases where that is
not the right JAR file to use, or where the JAR file may not be there.
Note that we’re talking about an edge-case here, but it can happen and
when it does, it can create significant confusion due to the wrong
version of the project main JAR running in the test.

Using the maven dependency plugin, we ensure that Maven gives us the
correct version of the JAR file based on the way the developer runs the
build. For example, when running a full build from the parent folder,
maven builds the main JAR, attaches it to the build, and then the
dependency plugin picks up that version of the JAR. On the other hand,
if the developer runs a build from the `docker-it` module folder itself
(not using `-pl` arguments), then maven will use the version of the main
JAR file from the developer’s `~/.m2/repository` cache.

There are more combinations and possible outcomes here. There’s even the
case of testing against a released version of the main JAR file,
downloaded from Maven Central (or other release repository). The safe
advice for new developers is to always run the full build from the
parent folder while developing and testing your local code. Please feel
free to discuss further on the Github discussion pages for this project.

``` xml
<!-- MAVEN DEPENDENCY PLUGIN -->
<!-- Be sure to pick up the correct version of the MAIN jar for this build, whether is comes -->
<!--  from the same build/reactor, maven cache, or other.                                    -->
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-dependency-plugin</artifactId>
    <version>3.1.2</version>
    <executions>
        <execution>
            <id>copy-main-artifact</id>
            <phase>package</phase>
            <goals>
                <goal>copy</goal>
            </goals>
            <configuration>
                <skip>${skipITs}</skip> <!-- not needed if we are skipping the docker build -->
                <artifactItems>
                    <artifactItem>
                        <groupId>com.savoirtech</groupId>
                        <artifactId>black-box-systest-tc-main</artifactId>
                        <version>${project.version}</version>
                    </artifactItem>
                </artifactItems>
            </configuration>
        </execution>
    </executions>
</plugin>
```

And here we tell Test Containers to add our main project JAR file into
the docker image under the directory `/app`, using the dependency
extracted by the `maven-dependency-plugin`.

``` java
.withCopyFileToContainer(
    MountableFile.forHostPath(this.applicationDependencyDirPath), "/app"
)
```

Notice the use of `this.applicationDependencyDirPath` and
`this.projectVersion`. These object fields are populated here by reading
their values from System properties:

``` java
this.projectVersion = System.getProperty("PROJECT-VERSION");
if (this.projectVersion == null) {
    throw new RuntimeException("Test requires system property, PROJECT-VERSION; aborting");
}

this.applicationDependencyDirPath = System.getProperty("application-dependency-dir-path");
if (this.applicationDependencyDirPath == null) {
    throw new RuntimeException("Test requires system property, application-dependency-dir-path; aborting");
}
```

These system properties are written by the Failsafe plugin here, after
maven replaces the `${…​}` expressions with their build values:

``` xml
<property>
    <name>application-dependency-dir-path</name>
    <!--suppress MavenModelInspection -->
    <value>${project.build.directory}/dependency</value>
</property>
<property>
    <name>PROJECT-VERSION</name>
    <!--suppress MavenModelInspection -->
    <value>${project.version}</value>
</property>
```

The `.withCommand()` method contains the instructions for starting the
application at container startup time. Note the command is

``` bash
java -Dloader.path=/app/config -jar /app/black-box-systest-tc-main-${project.version}.jar
```

#### Application Test Image - Putting it All Together

``` java
this.applicationContainer
    .withNetwork(network)
    .withNetworkAliases("application", "application-host")
    .withExposedPorts(8080, 5005)
    .withStartupTimeout(Duration.ofMinutes(5))
    .withEnv("JAVA_TOOL_OPTIONS", "-Djava.security.egd=file:/dev/./urandom -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005")
    .withLogConsumer(new Slf4jLogConsumer(LOG).withPrefix("APPLICATION"))
    .withCopyFileToContainer(
        MountableFile.forHostPath(this.applicationDependencyDirPath), "/app"
    )
    .withCommand("java -Dloader.path=/app/config -jar /app/black-box-systest-tc-main-" + this.projectVersion + ".jar")
    ;
```

#### HelloIT Test

Placing the test file, `HelloIT`, under the project’s `src/main/test`
folder, the Failsafe plugin matches the file based on the naming pattern
`**/*IT.java` and executes the test code within.

All methods marked with the `@Test` annotation are executed by Failsafe.

Note the use of RestAssured for a fairly easy way to build the client
call to our service.

``` java
@Test
public void testHelloEndpoint() {
    String baseUrl = System.getProperty("application.base-url");
    String url = baseUrl + "/api/hi";

    RestAssuredConfig config =
            RestAssuredConfig.config()
                    .httpClient(
                            HttpClientConfig.httpClientConfig()
                                    .setParam("http.connection.timeout", DEFAULT_SOCKET_TIMEOUT)
                                    .setParam("http.socket.timeout", DEFAULT_SOCKET_TIMEOUT)
                    );

    Response response =
            RestAssured.given()
                    .config(config)
                    .get(url)
                    .thenReturn();

    assertEquals(200, response.getStatusCode());
    assertEquals("Hello", response.getBody().asString());
}
```

Note the use of `System.getProperty("application.base-url");` in the
test method. That property is injected by `TestContainerRunnerClassRule`
using the following statement:

``` java
System.setProperty("application.base-url", "http://localhost:" + httpPort);
```

The port lookup paired with the system property enables use of the
dynamically-allocated, ephemeral, port from docker, which is significant
for minimizing environment-specific failures, such as attempting to
build on a machine where the developer has other applications listening
on the same ports.

Here are key parts exposing ports from the test container and capturing
the associated ephemeral port numbers.

``` java
.withExposedPorts(8080, 5005)
```

``` java
// DEBUGGING: uncomment to force local port 5005
// this.applicationContainer.getPortBindings().add("5005:5005");
```

``` java
var httpPort = this.applicationContainer.getMappedPort(8080); // application-http-port
var debuggerPort = this.applicationContainer.getMappedPort(5005);
```

The commented-out `.getPortBindings().add("5005:5005")` port binding is
a convenience to simplify the effort needed to debug the application
while it is running inside the container. Uncommenting this line grants
access to the applications debug port inside the container from the host
port 5005. Note that the image is also created with `JAVA_TOOL_OPTIONS`
configured with debugging enabled for the application. Debugging is
possible without using fixed host port 5005 by attaching to the port
number returned by the `applicationContainer.getMappedPort(5005)`
expression.

#### Handling Errors in the Test

Once integration tests have completed, it is important to inform Maven
of failures. This is not done automatically, so we need to configure the
plugin to do so. Here is the boilerplate that goes in the `executions`
section of the `maven-failsafe-plugin`.

``` xml
<!-- Fail the build on IT Failures.  Executed as a separate step so that post-integration-test -->
<!--  phase executes even after an IT failure.                                                 -->
<execution>
    <id>projname-verify-it</id>
    <goals>
        <goal>verify</goal>
    </goals>
</execution>
```

Without this, after tests fail, Maven will continue the build and ignore
the failures. Adding this section, Maven fails the build when there are
test failures (ignoring possible maven options to explicitly ignore
failures).

## Cleanup after Interrupted Tests

Imagine this - you’re running the integration tests and notice a
failure, or remember that you forgot to finish that one thing that will
make it all fail. So you decide not to wait for the tests to complete.

So you hit Ctrl-C and stop the process during the integration-test
phase.

As opposed to the approach with the `docker-maven-plugin`, Test
Containers will detect and cleanup the containers, docker network, and
other docker resources we allocated for the test.

However, if that automatic cleanup fails for any reason, or you don’t
want to wait for it to kick in, then here’s what you are left with:

- All of the containers successfully started by Test Containers continue
  to run (this includes the `testcontainers-ryuk-` special container
  that normally handles the cleanup automatically)

- The custom network used for the test remains

- Temporary volumes created via Test Containers remain

Here are docker instructions that you can use to help with cleanup:

``` bash
$ docker ps
$ docker stop a0b1c2d3
$ docker container rm a0b1c2d3
$ docker network ls
$ docker network rm projname-docker-it-network
$ docker volume ls
$ docker volume rm aa00bb11cc22...
```

## Try it Out

``` bash
$ git clone https://github.com/savoirtech/black-box-system-test-tc.git
$ cd black-box-system-test-tc
$ mvn -f project clean install
```

## Limitations

The entire solution presented here has some notable limitations. There
are ways to address these but they were not included here to keep down
the complexity in this article.

- Multiple test scenarios or cases split across multiple `*IT.java`
  files will create an anti-pattern here, spinning up and shutting down
  the test application for each IT class file.

  - The author solves this problem using Cucumber to execute all of the
    tests using a single IT class file.

  - While it may be tempting to allow this clean spin-up of the
    application on every IT class file, over time - as the project grows
    in size - the overhead of spinning up and shutting down all of the
    containers can easily lead to build times in hours. Especially once
    the application starts to use external dependencies that need to be
    spun up and shutdown as well (e.g. database, messaging, etc).

## Author’s Notes

- Initially, the plan was to include 1 external system dependency - such
  as a DB or JMS system - but due to the length of the article, it was
  removed.

- JAX-RS was used in this example

  - The main motivation is to avoid "vendor lock-in", and use the
    wider-reaching standard

  - Spring’s `@RestController` could just as easily have been used.

  - There is additional boilerplate wiring for JAX-RS

- Always using `clean` in `mvn clean install`

  - Maven will attempt incremental builds to save time

  - There are many cases Maven does not catch, which leads to the
    incremental build being incorrect

  - The amount of confusion and time to resolve these cases just isn’t
    worth the savings **most** of the time

  - If there are very slow parts of the build, such as the integration
    tests described in this article, skipping them while iterating over
    the code, and then making sure to run them later - before commit and
    push - is effective.

  - The `install` target is ideal in most cases because it makes sure
    that working with a subset of the build - such as running maven out
    of a sub-module’s folder - gives the developer the expected, latest
    version of built in-project dependencies.

  - Feel free to use different targets as they suit your needs - just be
    aware of the potential pitfalls.

# About the Authors

[Arthur
Naseef](https://github.com/savoirtech/blogs/blob/main/authors/ArthurNaseef.md)

## Reaching Out

Please do not hesitate to reach out with questions and comments, here on
the Blog, or through the Savoir Technologies website at
<https://www.savoirtech.com>.

Also note the discussion section on Github for the project is probably
the best place to discuss this article since it is shared with everyone
and makes the entire conversation easy to find and follow.

# With Thanks

Thank you to the following individuals for the stock photos!

- Kelly for
  <https://www.pexels.com/photo/black-motorcycle-on-road-2519370>

- Tima Miroshnichenko for
  <https://www.pexels.com/photo/a-chess-pieces-on-a-wooden-table-10625976>

\(c\) 2024 Savoir Technologies
