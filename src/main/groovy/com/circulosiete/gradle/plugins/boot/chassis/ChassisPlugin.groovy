/*
 *
 * Copyright (C) 2014-2016 the original author or authors.
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
package com.circulosiete.gradle.plugins.boot.chassis

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.logging.Logger

class ChassisPlugin implements Plugin<Project> {

  public static final String DEFAULT_SPRING_BOOT_VERSION = "2.0.6"

  @Override
  void apply(Project project) {
    Logger logger = project.getLogger()
    String springBootVersion = Optional.ofNullable(project.properties['springBootVersion'])
      .orElse(DEFAULT_SPRING_BOOT_VERSION)

    if (!springBootVersion.equals(DEFAULT_SPRING_BOOT_VERSION)) {
      logger.warn("Se ha detectado diferente versión de Spring Boot en el proyecto.")
      logger.warn("\tVersión default de  Spring Boot: {}", DEFAULT_SPRING_BOOT_VERSION)
      logger.warn("\tVersión definida en el proyecto: {}\n", springBootVersion)
    }

    logger.warn("Spring Boot version: {}\n", springBootVersion)

    project.buildscript.repositories {
      jcenter()
      mavenCentral()
      mavenLocal()
    }

    project.buildscript.dependencies {
      classpath("org.springframework.boot:spring-boot-gradle-plugin:${ springBootVersion }")
    }

    logger.warn("Aplicando plugins...")

    [
      "java", "eclipse", "idea", "com.bmuschko.docker-remote-api",
      'org.springframework.boot', 'io.spring.dependency-management'
    ].each { plugin ->

      project.getPlugins().apply(plugin)
      logger.warn("* {}", plugin)
    }
  }
}
