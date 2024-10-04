/*
 * Copyright (c) 2024 Savoir Technologies
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 *
 */

package com.savoirtech.blog.systest.it;

import org.junit.rules.ExternalResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.time.Duration;

// tag::test-container-class-rule[]
@SuppressWarnings("rawtypes")
public class TestContainerRunnerClassRule extends ExternalResource {

    private static final Logger DEFAULT_LOGGER = LoggerFactory.getLogger(TestContainerRunnerClassRule.class);

    private Logger LOG = DEFAULT_LOGGER;

    private GenericContainer applicationContainer;

    private Network network;
    private String projectVersion;
    private String applicationDependencyDirPath;

    public TestContainerRunnerClassRule() {
        // tag::application-test-image-build-from[]
        this.applicationContainer = new GenericContainer(DockerImageName.parse("eclipse-temurin").withTag("21-jre").toString());
        // end::application-test-image-build-from[]
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
        // tag::application-test-image-config-read-property[]
        this.projectVersion = System.getProperty("PROJECT-VERSION");
        if (this.projectVersion == null) {
            throw new RuntimeException("Test requires system property, PROJECT-VERSION; aborting");
        }

        this.applicationDependencyDirPath = System.getProperty("application-dependency-dir-path");
        if (this.applicationDependencyDirPath == null) {
            throw new RuntimeException("Test requires system property, application-dependency-dir-path; aborting");
        }
        // end::application-test-image-config-read-property[]
    }

    private void startApplicationContainer() {
        // tag::application-test-image-complete[]
        this.applicationContainer
            .withNetwork(network)
            .withNetworkAliases("application", "application-host")
            // tag::application-test-image-port-binding[]
            .withExposedPorts(8080, 5005)
            // end::application-test-image-port-binding[]
            .withStartupTimeout(Duration.ofMinutes(5))
            .withEnv("JAVA_TOOL_OPTIONS", "-Djava.security.egd=file:/dev/./urandom -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005")
            .withLogConsumer(new Slf4jLogConsumer(LOG).withPrefix("APPLICATION"))
            // tag::application-test-image-assembly[]
            .withCopyFileToContainer(
                MountableFile.forHostPath(this.applicationDependencyDirPath), "/app"
            )
            // end::application-test-image-assembly[]
            .withCommand("java -Dloader.path=/app/config -jar /app/black-box-systest-tc-main-" + this.projectVersion + ".jar")
            ;
        // end::application-test-image-complete[]

        // tag::application-test-image-debug-port-fixed-mapping[]
        // DEBUGGING: uncomment to force local port 5005
        // this.applicationContainer.getPortBindings().add("5005:5005");
        // end::application-test-image-debug-port-fixed-mapping[]
        this.applicationContainer.start();

        // tag::application-test-image-port-lookup[]
        var httpPort = this.applicationContainer.getMappedPort(8080); // application-http-port
        var debuggerPort = this.applicationContainer.getMappedPort(5005);
        // end::application-test-image-port-lookup[]

        LOG.info("APPLICATION MAPPED PORTS: http={}; debugger={}",
            httpPort,
            debuggerPort
            );

        // tag::application-publish-application-base-url[]
        System.setProperty("application.base-url", "http://localhost:" + httpPort);
        // end::application-publish-application-base-url[]
    }
}
// end::test-container-class-rule[]
