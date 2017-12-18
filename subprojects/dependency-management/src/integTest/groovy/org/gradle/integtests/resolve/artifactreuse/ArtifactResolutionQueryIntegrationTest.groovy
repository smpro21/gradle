/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.integtests.resolve.artifactreuse

import org.gradle.integtests.fixtures.AbstractHttpDependencyResolutionTest
import org.gradle.test.fixtures.server.http.BlockingHttpServer
import spock.lang.Issue
import spock.lang.Timeout

import java.util.concurrent.TimeUnit

class ArtifactResolutionQueryIntegrationTest extends AbstractHttpDependencyResolutionTest {
    BlockingHttpServer server = new BlockingHttpServer()

    def setup() {
        server.start()
    }

    def cleanup() {
        server.stop()
    }

    @Issue('https://github.com/gradle/gradle/issues/3579')
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    def 'can use artifact resolution queries in parallel to file resolution'() {
        given:
        def module = mavenHttpRepo.module('group', "artifact", '1.0').publish()
        server.expectConcurrent(server.file(module.pom.path, module.pom.file), server.resource('/sync'))
        server.expect(server.file(module.artifact.path, module.artifact.file))
        settingsFile << 'include "query", "resolve"'
        buildFile << """ 
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier 

allprojects {
    apply plugin: 'java'
    repositories {
       maven { url '${server.uri}/repo' }
    }
    
    dependencies {
        compile 'group:artifact:1.0'
    }
}

project('query') {
    task query {
        doLast {
            '${server.uri}/sync'.toURL().text
            dependencies.createArtifactResolutionQuery()
                        .forComponents(new DefaultModuleComponentIdentifier('group','artifact','1.0'))
                        .withArtifacts(JvmLibrary)
                        .execute()
        }
    }    
}

project('resolve') {
    task resolve {
        doLast {
            configurations.compile.files.collect { it.file }
        }
    }  
}
"""
        executer.requireOwnGradleUserHomeDir().requireIsolatedDaemons()

        expect:
        succeeds('query:query', ':resolve:resolve', '--parallel')
    }
}
