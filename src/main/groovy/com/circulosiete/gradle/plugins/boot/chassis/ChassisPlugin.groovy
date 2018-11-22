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
import org.yaml.snakeyaml.Yaml

class ChassisPlugin implements Plugin<Project> {

  public static final String DEFAULT_SPRING_BOOT_VERSION = '2.1.0.RELEASE'
  public static final String EXTENSION_NAME = 'service'
  public static final String DOCKERFILE_EXTENSION_NAME = 'dockerfile'

  @Override
  void apply(Project project) {
    ChassisExtension chassisExtension = createExtension(project)
    DockerFile dockerFile = ((ExtensionAware) chassisExtension).extensions.create(DOCKERFILE_EXTENSION_NAME, DockerFile, project)

    Logger logger = project.getLogger()
    String springBootVersion = Optional.ofNullable(project.properties['springBootVersion'])
      .orElse(DEFAULT_SPRING_BOOT_VERSION)

    if (!springBootVersion.equals(DEFAULT_SPRING_BOOT_VERSION)) {
      logger.warn('Se ha detectado diferente versión de Spring Boot en el proyecto.')
      logger.warn('\tVersión default de  Spring Boot: {}', DEFAULT_SPRING_BOOT_VERSION)
      logger.warn('\tVersión definida en el proyecto: {}\n', springBootVersion)
    }

    logger.warn('Spring Boot version: {}\n', springBootVersion)

    project.buildscript.repositories {
      jcenter()
      mavenCentral()
      mavenLocal()
    }

    logger.warn('Aplicando plugins...')

    [
      'java', 'jacoco', 'eclipse', 'eclipse-wtp', 'idea', 'com.bmuschko.docker-remote-api',
      'org.springframework.boot', 'io.spring.dependency-management',
      'com.github.ben-manes.versions'
    ].each { plugin ->

      project.getPlugins().apply(plugin)
      logger.warn(" * {}", plugin)
    }

    project.repositories {
      jcenter()
      mavenCentral()
      mavenLocal()
    }

    project.dependencies.add('implementation', "org.springframework.boot:spring-boot-starter-actuator:${ springBootVersion }")
    project.dependencies.add('implementation', "org.springframework.boot:spring-boot-starter-web:${ springBootVersion }")
    project.dependencies.add('implementation', 'org.apache.commons:commons-lang3:3.8.1')

    //TODO: agregar mas dependencias para realizar pruebas (spock, etc)
    project.dependencies.add('testRuntimeOnly', "org.springframework.boot:spring-boot-starter-test:${ springBootVersion }")



    project.task([type: com.bmuschko.gradle.docker.tasks.image.Dockerfile, group: 'Docker', description: 'Crea el Dockerfile del Microservicio'], 'dockerfile') {
      dependsOn 'assemble'

      //TODO: agregar soporte para dependsOn personalizado
      //dependsOn copyProps

      destFile = project.file('build/libs/Dockerfile')

      def baseImage = dockerFile.from.get()
      project.logger.warn('Usando "{}" como imagen base.', baseImage)
      from baseImage
      //TODO: agregar soporte para actualizar paquetes de la imagen base

      label(['maintainer': 'AMIS dev@amis.org'])

      //TODO: agregar soporte para INSTRUCTIONS personalizadas
      //Ejemplo de INSTRUCTIONS personalizadas
      //copyFile "application.properties", '/application.properties'

      copyFile getFatJarName(project), '/opt/service.jar'

      def applicationPort = getApplicationPort(project)
      exposePort applicationPort
      //TODO: Agregar soporte para obtener el puerto de administración

      //TODO: Agregar soporte para parámetros personalizados a la JVM y a la aplicación Spring
      entryPoint 'java', "-Djava.awt.headless=true", "-Xms256m", "-Xmx256m", '-jar', '/opt/service.jar'

    }

    project.task([type: com.bmuschko.gradle.docker.tasks.image.DockerBuildImage, group: 'Docker', description: 'Construye la imagen de Docker del Microservicio'], 'buildImage') {
      inputDir = project.tasks.getByName('dockerfile').destFile.get().asFile.parentFile
      tag = "domix/wonky:${ project.version }".toLowerCase()
      //TODO: mejorar la generación de la imagen. Considerar la configuración del tag.
    }

    //TODO: tarea para empujar la imagen al repositorio remoto

    project.tasks.getByName('buildImage').dependsOn('dockerfile')
  }


  private Integer getApplicationPort(Project project) {
    def result = 8080

    def propertiesFilePath = './src/main/resources/application.properties'
    def propertiesFile = new File(propertiesFilePath)
    def yamlFilePath = './src/main/resources/application.yaml'
    def ymlFilePath = './src/main/resources/application.yml'

    if (propertiesFile.exists()) {
      Properties properties = new Properties()
      propertiesFile.withInputStream {
        properties.load(it)
      }

      result = Optional.ofNullable(properties['server.port'])
        .filter { it instanceof String }
        .map { (String) it }
        .filter({ it.isNumber() })
        .map {
          def port = Integer.valueOf(it)
          project.logger.warn('Defined port in properties file: {}', port)
          port
        }.orElse(result)
    } else {
      def yamlFile = new File(yamlFilePath)

      if (!yamlFile.exists()) {
        yamlFile = new File(ymlFilePath)
      }

      if (yamlFile.exists()) {
        Yaml parser = new Yaml()
        result = Optional.ofNullable(parser.load(yamlFile.text)).map({
          Optional.ofNullable(it['server.port'])
            .filter {
              it instanceof Integer
            }.map {
              project.logger.warn('Defined port in yaml file: {}', it)
              it
            }
            .orElse(result)
        }).orElse(result)
      }
    }

    project.logger.warn('Application using port {}', result)

    result
  }

  private String getFatJarName(Project project) {
    def jar = project.getTasks().getByPath('jar')
    def lo = new File('build/libs/')
    String path = jar.archivePath.path
    path.replaceAll("$lo.absolutePath/", '')
  }

  private ChassisExtension createExtension(Project project) {
    project.extensions.create(EXTENSION_NAME, ChassisExtension, project)
  }
}
