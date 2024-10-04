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

import io.restassured.RestAssured;
import io.restassured.config.HttpClientConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.response.Response;
import org.junit.ClassRule;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class HelloIT {

    public static final int DEFAULT_SOCKET_TIMEOUT = 10_000;

    @ClassRule
    public static TestContainerRunnerClassRule testContainerRunnerClassRule = new TestContainerRunnerClassRule();

    // tag::test-hello-endpoint[]
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
    // end::test-hello-endpoint[]
}
