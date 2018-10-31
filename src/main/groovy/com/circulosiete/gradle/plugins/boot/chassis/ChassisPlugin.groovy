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
import org.gradle.api.plugins.ExtensionAware

class ChassisPlugin implements Plugin<Project> {

  public static final String DEFAULT_SPRING_BOOT_VERSION = "2.0.6.RELEASE"
  public static final String EXTENSION_NAME = 'service'

  @Override
  void apply(Project project) {
    ChassisExtension chassisExtension = project.extensions.create(EXTENSION_NAME, ChassisExtension, project)
    DockerFile dockerFile = ((ExtensionAware) chassisExtension).extensions.create('dockerfile', DockerFile, project)

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

    logger.warn("Aplicando plugins...")

    [
      "java", 'jacoco', "eclipse", "eclipse-wtp", "idea", "com.bmuschko.docker-remote-api",
      'org.springframework.boot', 'io.spring.dependency-management',
      'com.github.ben-manes.versions'
    ].each { plugin ->

      project.getPlugins().apply(plugin)
      logger.warn("* {}", plugin)
    }

    project.repositories {
      jcenter()
      mavenCentral()
      mavenLocal()
    }

    project.dependencies.add('implementation', "org.springframework.boot:spring-boot-starter-actuator:${ springBootVersion }")
    project.dependencies.add('implementation', "org.springframework.boot:spring-boot-starter-web:${ springBootVersion }")
    //project.dependencies.add('implementation', "org.springframework.boot:spring-boot-starter-jdbc:${ springBootVersion }")
    project.dependencies.add('implementation', "org.apache.commons:commons-lang3:3.8.1")

    //TODO: agregar mas dependencias para realizar pruebas (spock, etc)
    project.dependencies.add('testRuntimeOnly', "org.springframework.boot:spring-boot-starter-test:${ springBootVersion }")

    project.task([type: com.bmuschko.gradle.docker.tasks.image.Dockerfile, group: 'Docker', description: 'Crea el Dockerfile del Microservicio'], 'dockerfile') {


      dependsOn 'assemble'

      //TODO: agregar soporte para dependsOn personalizado
      //dependsOn copyProps

      destFile = project.file('build/libs/Dockerfile')
      //TODO: hacer personalizable la imagen base


      def baseImage = dockerFile.from.get()
      project.logger.warn('Usando "{}" como imagen base.', baseImage)
      from baseImage

      label(['maintainer': 'AMIS dev@amis.org'])

      //TODO: obtener archivo del fat gordo de Spring boot
      //copyFile "jar_gordo.jar", '/opt/service.jar'

      //TODO: agregar soporte para INSTRUCTIONS personalizadas
      //Ejemplo de INSTRUCTIONS personalizadas
      //copyFile "application.properties", '/application.properties'

      //TODO: Agregar soporte para obtener el puerto
      //exposePort 8060

      //TODO: Agregar soporte para ejecutar la aplicación
      //entryPoint 'java', "-Djava.awt.headless=true", "-Xms512m", "-Xmx512m", '-jar', '/opt/service.jar'

    }
  }
}
