/*
 * (c) Copyright 2018 Palantir Technologies Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.palantir.gradle.conjure

import com.palantir.gradle.conjure.api.ConjureProductDependenciesExtension
import com.palantir.gradle.dist.RecommendedProductDependencies

import java.util.jar.Attributes
import java.util.jar.Manifest
import java.util.zip.ZipFile
import nebula.test.IntegrationSpec

class ConjureServiceDependencyTest extends IntegrationSpec {

    def setup() {
        addSubproject('api')
        addSubproject('api:api-objects')
        addSubproject('api:api-jersey')
        addSubproject('api:api-undertow')
        addSubproject('api:api-typescript')

        buildFile << """
        buildscript {
            repositories {
                mavenCentral()
            }
        }
        
        allprojects {
            version '0.1.0'
            group 'com.palantir.conjure.test'

            repositories {
                mavenCentral()
            }
            configurations.all {
                resolutionStrategy {
                    force 'com.palantir.conjure.java:conjure-java:${TestVersions.CONJURE_JAVA}'
                    force 'com.palantir.conjure.java:conjure-lib:${TestVersions.CONJURE_JAVA}'
                    force 'com.palantir.conjure.java:conjure-undertow-lib:${TestVersions.CONJURE_JAVA}'
                    force 'com.palantir.conjure:conjure:${TestVersions.CONJURE}'
                    force 'com.palantir.conjure.typescript:conjure-typescript:${TestVersions.CONJURE_TYPESCRIPT}'
                }
            }   
        }
        """.stripIndent()

        createFile('api/build.gradle') << '''
        apply plugin: 'com.palantir.conjure'
        '''.stripIndent()

        createFile('api/src/main/conjure/api.yml') << '''
        types:
          definitions:
            default-package: test.test.api
            objects:
              StringExample:
                fields:
                  string: string
        services:
          TestServiceFoo:
            name: Test Service Foo
            package: test.test.api

            endpoints:
              post:
                http: POST /post
                args:
                  object: StringExample
                returns: StringExample
        '''.stripIndent()
        file("gradle.properties") << "org.gradle.daemon=false"
    }

    def "generates empty product dependencies if not configured"() {
        when:
        runTasksSuccessfully(':api:generateConjureServiceDependencies')

        then:
        fileExists("api/build/service-dependencies.json")
        file('api/build/service-dependencies.json').text == '[]'
    }

    def "generates product dependencies if extension is configured"() {
        file('api/build.gradle') << '''
        serviceDependencies {
            serviceDependency {
                productGroup = "com.palantir.conjure"
                productName = "conjure"
                minimumVersion = "1.2.0"
                recommendedVersion = "1.2.0"
                maximumVersion = "2.x.x"
            }
        }
        '''.stripIndent()
        when:
        runTasksSuccessfully(':api:generateConjureServiceDependencies')

        then:
        fileExists('api/build/service-dependencies.json')
        file('api/build/service-dependencies.json').text.contains('"product-group":"com.palantir.conjure"')
        file('api/build/service-dependencies.json').text.contains('"product-name":"conjure"')
        file('api/build/service-dependencies.json').text.contains('"minimum-version":"1.2.0"')
        file('api/build/service-dependencies.json').text.contains('"maximum-version":"2.x.x"')
        file('api/build/service-dependencies.json').text.contains('"recommended-version":"1.2.0"')
    }

    def "correctly passes product dependencies to conjure"() {
        file('api/build.gradle') << '''
        serviceDependencies {
            serviceDependency {
                productGroup = "com.palantir.conjure"
                productName = "conjure"
                minimumVersion = "1.2.0"
                recommendedVersion = "1.2.0"
                maximumVersion = "2.x.x"
            }
        }
        '''.stripIndent()
        when:
        def result = runTasksSuccessfully(':api:compileConjure')

        then:
        result.standardOutput.find('with args: \\[.*, --extensions, '
                + '\\{"recommended-product-dependencies":\\[\\{'
                + '"product-group":"com.palantir.conjure",'
                + '"product-name":"conjure",'
                + '"minimum-version":"1.2.0",'
                + '"maximum-version":"2.x.x",'
                + '"recommended-version":"1.2.0",'
                + '"optional":false\\}]\\}]') != null
    }

    def "correctly passes product dependencies to generators"() {
        file('api/build.gradle') << '''
        serviceDependencies {
            serviceDependency {
                productGroup = "com.palantir.conjure"
                productName = "conjure"
                minimumVersion = "1.2.0"
                recommendedVersion = "1.2.0"
                maximumVersion = "2.x.x"
            }
        }
        '''.stripIndent()
        when:
        def result = runTasksSuccessfully(':api:compileConjure')

        then:
        result.wasExecuted(':api:generateConjureServiceDependencies')
        file('api/api-typescript/src/package.json').text.contains('sls')
    }

    def "does not pass product dependencies to java objects or undertow"() {
         file('api/build.gradle') << '''
        serviceDependencies {
            serviceDependency {
                productGroup = "com.palantir.conjure"
                productName = "conjure"
                minimumVersion = "1.2.0"
                recommendedVersion = "1.2.0"
                maximumVersion = "2.x.x"
            }
        }
        '''.stripIndent()
        when:
        def result = runTasksSuccessfully(':api:api-objects:Jar')
        def result2 = runTasksSuccessfully(':api:api-undertow:Jar')

        then:
        !result.wasExecuted(':api:generateConjureServiceDependencies')
        !result2.wasExecuted(':api:generateConjureServiceDependencies')
        readRecommendedProductDeps(file('api/api-objects/build/libs/api-objects-0.1.0.jar')) == null
        readRecommendedProductDeps(file('api/api-undertow/build/libs/api-undertow-0.1.0.jar')) == null
    }

    def "correctly configures manifest for java jersey"() {
        file('api/build.gradle') << '''
        serviceDependencies {
            serviceDependency {
                productGroup = "com.palantir.conjure"
                productName = "conjure"
                minimumVersion = "1.2.0"
                recommendedVersion = "1.2.0"
                maximumVersion = "2.x.x"
                optional = false
            }
        }
        '''.stripIndent()
        when:
        def result = runTasksSuccessfully(':api:api-jersey:Jar')

        then:
        !result.wasExecuted(':api:generateConjureServiceDependencies')
        def recommendedDeps = readRecommendedProductDeps(file('api/api-jersey/build/libs/api-jersey-0.1.0.jar'))
        recommendedDeps == '{"recommended-product-dependencies":[{' +
                '"product-group":"com.palantir.conjure",' +
                '"product-name":"conjure",' +
                '"minimum-version":"1.2.0",' +
                '"recommended-version":"1.2.0",' +
                '"maximum-version":"2.x.x",' +
                '"optional":false}]}'
    }

    def "correctly configures manifest for java jersey with two optional dependencies"() {
        file('api/build.gradle') << '''
        serviceDependencies {
            serviceDependency {
                productGroup = "com.palantir.conjure"
                productName = "conjure"
                minimumVersion = "1.2.0"
                recommendedVersion = "1.2.0"
                maximumVersion = "2.x.x"
                optional = true
            }
            serviceDependency {
                productGroup = "com.palantir.conjure"
                productName = "conjure-variant"
                minimumVersion = "1.2.0"
                recommendedVersion = "1.2.0"
                maximumVersion = "2.x.x"
                optional = true
            }
        }
        '''.stripIndent()
        when:
        def result = runTasksSuccessfully(':api:api-jersey:Jar')

        then:
        !result.wasExecuted(':api:generateConjureServiceDependencies')
        def recommendedDeps = readRecommendedProductDeps(file('api/api-jersey/build/libs/api-jersey-0.1.0.jar'))
        recommendedDeps == '{"recommended-product-dependencies":[{' +
                '"product-group":"com.palantir.conjure",' +
                '"product-name":"conjure",' +
                '"minimum-version":"1.2.0",' +
                '"recommended-version":"1.2.0",' +
                '"maximum-version":"2.x.x",' +
                '"optional":true},' +
                '{"product-group":"com.palantir.conjure",' +
                '"product-name":"conjure-variant",' +
                '"minimum-version":"1.2.0",' +
                '"recommended-version":"1.2.0",' +
                '"maximum-version":"2.x.x",' +
                '"optional":true}' +
                ']}'
    }

    def "fails on a single optional dependency"() {
        file('api/build.gradle') << '''
        serviceDependencies {
            serviceDependency {
                productGroup = "com.palantir.conjure"
                productName = "conjure"
                minimumVersion = "1.2.0"
                recommendedVersion = "1.2.0"
                maximumVersion = "2.x.x"
                optional = true
            }
        }
        '''.stripIndent()
        expect:
        runTasksWithFailure(':api:api-jersey:Jar')
    }

    def "fails on absent fields"() {
        file('api/build.gradle') << '''
        serviceDependencies {
            serviceDependency {
                productName = "conjure"
                minimumVersion = "1.2.0"
                recommendedVersion = "1.2.0"
                maximumVersion = "2.x.x"
            }
        }
        '''.stripIndent()

        expect:
        runTasksWithFailure(':api:generateConjureServiceDependencies')
    }

    def "fails on invalid version"() {
        file('api/build.gradle') << '''
        serviceDependencies {
            serviceDependency {
                productGroup = "com.palantir.conjure"
                productName = "conjure"
                minimumVersion = "1.x.0"
                recommendedVersion = "1.2.0"
                maximumVersion = "2.x.x"
            }
        }
        '''.stripIndent()

        expect:
        runTasksWithFailure(':api:generateConjureServiceDependencies')
    }

    def "fails on invalid group"() {
        file('api/build.gradle') << '''
        serviceDependencies {
            serviceDependency {
                productGroup = "com.palantir:conjure"
                productName = "conjure"
                minimumVersion = "1.0.0"
                recommendedVersion = "1.2.0"
                maximumVersion = "2.x.x"
            }
        }
        '''.stripIndent()

        expect:
        runTasksWithFailure(':api:generateConjureServiceDependencies')
    }

    def "no endpoint versions attribute if no min versions configured"() {
        setup:
        file('api/build.gradle') << '''
        serviceDependencies {
            serviceDependency {
                productGroup = "com.palantir.conjure"
                productName = "conjure"
                minimumVersion = "1.2.0"
                recommendedVersion = "1.2.0"
                maximumVersion = "2.x.x"
            }
        }
        '''.stripIndent()
        when:
        def result = runTasksSuccessfully(':api:api-jersey:Jar')

        then:
        Attributes attributes = getAttributes(file('api/api-jersey/build/libs/api-jersey-0.1.0.jar'))
        attributes.containsKey(name(RecommendedProductDependencies.SLS_RECOMMENDED_PRODUCT_DEPS_KEY))
        !attributes.containsKey(name(ConjureProductDependenciesExtension.ENDPOINT_VERSIONS_MANIFEST_KEY))
    }

    def "endpoint information written if configured"() {
        setup:
        file('api/build.gradle') << '''
        serviceDependencies {
            serviceDependency {
                productGroup = "com.palantir.conjure"
                productName = "conjure"
                minimumVersion = "1.2.0"
                recommendedVersion = "1.2.0"
                maximumVersion = "2.x.x"
            }
            endpointVersion {
                httpPath = "/post"
                httpMethod = "POST"
                minVersion = "0.1.0"
                maxVersion = "1.2.0"
            }
        }
        '''.stripIndent()
        when:
        def result = runTasksSuccessfully(':api:api-jersey:Jar')

        then:
        result.wasExecuted(':api:api-jersey:configureEndpointVersionBounds')
        Attributes attributes = getAttributes(file('api/api-jersey/build/libs/api-jersey-0.1.0.jar'))
        def recommendedDeps = attributes.getValue(RecommendedProductDependencies.SLS_RECOMMENDED_PRODUCT_DEPS_KEY)
        //check to make sure we didn't stomp over the recommended-product-dependencies
        recommendedDeps.contains('"recommended-product-dependencies"')
        def endpointVersions = attributes.getValue(ConjureProductDependenciesExtension.ENDPOINT_VERSIONS_MANIFEST_KEY)
        endpointVersions == '{"endpoint-version-bounds":[{"http-path":"/post","http-method":"POST","min-version":"0.1.0","max-version":"1.2.0"}]}'
    }

    def "nullable endpoint maximum version bound"() {
        setup:
        file('api/build.gradle') << '''
        serviceDependencies {
            serviceDependency {
                productGroup = "com.palantir.conjure"
                productName = "conjure"
                minimumVersion = "1.2.0"
                recommendedVersion = "1.2.0"
                maximumVersion = "2.x.x"
            }
            endpointVersion {
                httpPath = "/post"
                httpMethod = "POST"
                minVersion = "0.1.0"
            }
        }
        '''.stripIndent()
        when:
        def result = runTasksSuccessfully(':api:api-jersey:Jar')

        then:
        result.wasExecuted(':api:api-jersey:configureEndpointVersionBounds')
        Attributes attributes = getAttributes(file('api/api-jersey/build/libs/api-jersey-0.1.0.jar'))
        def recommendedDeps = attributes.getValue(RecommendedProductDependencies.SLS_RECOMMENDED_PRODUCT_DEPS_KEY)
        //check to make sure we didn't stomp over the recommended-product-dependencies
        recommendedDeps.contains('"recommended-product-dependencies"')
        def endpointVersions = attributes.getValue(ConjureProductDependenciesExtension.ENDPOINT_VERSIONS_MANIFEST_KEY)
        endpointVersions == '{"endpoint-version-bounds":[{"http-path":"/post","http-method":"POST","min-version":"0.1.0"}]}'
    }

    Attributes getAttributes(File jarFile) {
        def zf = new ZipFile(jarFile)
        def manifestEntry = zf.getEntry("META-INF/MANIFEST.MF")
        def manifest = new Manifest(zf.getInputStream(manifestEntry))
        return manifest.getMainAttributes()
    }

    private Attributes.Name name(String name) {
        new Attributes.Name(name)
    }

    def readRecommendedProductDeps(File jarFile) {
        return getAttributes(jarFile).getValue(RecommendedProductDependencies.SLS_RECOMMENDED_PRODUCT_DEPS_KEY)
    }
}
