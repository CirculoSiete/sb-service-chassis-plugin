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

import de.vandermeer.asciitable.AsciiTable
import de.vandermeer.skb.interfaces.document.TableRowStyle
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.logging.Logger
import org.gradle.api.plugins.ExtensionAware
import org.yaml.snakeyaml.Yaml

class ChassisPlugin implements Plugin<Project> {

  public static final String DEFAULT_SPRING_BOOT_VERSION = '2.1.3.RELEASE'
  public static final String DEFAULT_SPRING_CLOUD_VERSION = 'Greenwich.SR1'
  public static final String EXTENSION_NAME = 'service'
  public static final String DOCKERFILE_EXTENSION_NAME = 'dockerfile'
  public final static String LINE_SEPARATOR = "*" * 60

  @Override
  void apply(Project project) {
    ChassisExtension chassisExtension = createExtension(project)
    DockerFile dockerFile = ((ExtensionAware) chassisExtension).extensions.create(DOCKERFILE_EXTENSION_NAME, DockerFile, project)

    Logger logger = project.getLogger()
    String springBootVersion = DEFAULT_SPRING_BOOT_VERSION
    String springCloudVersion = DEFAULT_SPRING_CLOUD_VERSION

    String theTag
    String theImageName

    if (!springBootVersion.equals(DEFAULT_SPRING_BOOT_VERSION)) {
      logger.warn('Se ha detectado diferente versión de Spring Boot en el proyecto.')
      logger.warn('\tVersión default de  Spring Boot: {}', DEFAULT_SPRING_BOOT_VERSION)
      logger.warn('\tVersión definida en el proyecto: {}\n', springBootVersion)
    }

    logger.warn('Spring Boot version:  {}', springBootVersion)
    logger.warn('Spring Cloud version: {}\n', springCloudVersion)

    project.buildscript.repositories {
      jcenter()
      mavenCentral()
      mavenLocal()
      maven {
        url "https://plugins.gradle.org/m2/"
      }
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
      maven { url 'https://repo.spring.io/milestone' }
    }

    project.dependencyManagement {
      imports {
        mavenBom "org.springframework.cloud:spring-cloud-dependencies:${ springCloudVersion }"
      }
    }

    project.dependencies.add('compileOnly', "org.projectlombok:lombok")
    project.dependencies.add('annotationProcessor', "org.projectlombok:lombok")

    project.dependencies.add('implementation', "org.springframework.boot:spring-boot-starter-actuator:${ springBootVersion }")
    project.dependencies.add('implementation', "org.springframework.boot:spring-boot-starter-web:${ springBootVersion }")
    project.dependencies.add('implementation', 'org.apache.commons:commons-lang3:3.8.1')

    //TODO: agregar mas dependencias para realizar pruebas (spock, etc)
    project.dependencies.add('testImplementation', "org.springframework.boot:spring-boot-starter-test:${ springBootVersion }")

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
      def repositoryName = project.name.replace(' ', '').toLowerCase()
      project.logger.warn('repositoryName: {}', repositoryName)
      def registryOwner = ((System.getenv('DOCKER_BUILDER_REGISTRY_OWNER') ?: (project.hasProperty('dockerRegistryOwner') ? project.property('dockerRegistryOwner') : '')) ?: '') ?: (System.getenv('DOCKER_BUILDER_USERNAME') ?: (project.hasProperty('dockerRegistryUsername') ? project.property('dockerRegistryUsername') : '')) ?: ''
      project.logger.warn('registryOwner: {}', registryOwner)

      if (!registryOwner) {
        String error = LINE_SEPARATOR + '\nCONFIGURACION DE DOCKER FALTANTE. \n' + LINE_SEPARATOR + '\n\n' +
          'Es necesario especificar el "Owner del repositorio". \n' +
          'Esto se puede hacer mediante 2 formas: \n\n' +
          '1. Agregar en el archivo global de configuracion de \n' +
          '   Gradle ($HOME/.gradle/gradle.properties),\n' +
          '   la propiedad "dockerRegistryUsername". Ejemplo:\n\n' +
          '     dockerRegistryUsername=foo\n\n' +
          '   NOTA: el archivo puede no existir, crearlo si no existe.\n\n' +
          '2. Agregar una variable de ambiente llamada DOCKER_BUILDER_REGISTRY_OWNER\n' +
          '   Ejemplo: \n\n' +
          '   $ export DOCKER_BUILDER_REGISTRY_OWNER=foo'

        throw new RuntimeException(error)
      }

      def registryHost = (System.getenv('DOCKER_BUILDER_REGISTRY_HOST') ?: (project.hasProperty('dockerRegistryHost') ? project.property('dockerRegistryHost') : '')) ?: ''

      if (registryHost) {
        registryHost = registryHost + "/"
      }

      def tagVersion = project.version
      def buildNumber = System.getenv('BUILD_NUMBER')
      if (buildNumber) {
        tagVersion += "_build_ci_${ buildNumber }"
      }

      inputDir = project.tasks.getByName('dockerfile').destFile.get().asFile.parentFile

      def imageNameInLowerCase = "${ registryHost }${ registryOwner }/${ repositoryName }".toLowerCase()

      def finalTag = "${ imageNameInLowerCase }:${ tagVersion }".toLowerCase()


      theTag = tagVersion
      theImageName = imageNameInLowerCase

      project.logger.warn('Created Image with tag: {}', finalTag)
      tags = [finalTag]
    }

    project.task([type: com.bmuschko.gradle.docker.tasks.image.DockerPushImage, group: 'Docker', description: 'Empuja la imagen del Contenedor al Registro'], 'pushImage') {
      dependsOn 'buildImage'
      def im = project.tasks.getByName('buildImage').tags.get().first()
      imageName = theImageName
      tag = theTag
      def ci = System.getenv('CI') == "true"
      def enabledPush = true
      if (ci && project.version.toString().toLowerCase().endsWith("snapshot")) {
        enabledPush = false
      }
      enabled = enabledPush
      println "Push enabled: ${ enabled }"
    }

    //TODO: tarea para empujar la imagen al repositorio remoto
    project.task([type: at.phatbl.shellexec.ShellExec, group: 'Deployment', description: 'Despliega el Microservicio'], 'deploy') {
      'command -v gcloud'.execute()
      command 'ls -l'
    }

    //TODO: tarea para generar Jenkinsfile

    //TODO: tarea para desplegar

    project.tasks.getByName('buildImage').dependsOn('dockerfile')

    project.ext.registryUrl = (System.getenv('CONTAINER_REGISTRY_URL') ?:
      project.properties.getOrDefault('containerRegistryUrl', ''))
    project.ext.registryUsername = (System.getenv('CONTAINER_REGISTRY_USERNAME') ?:
      project.properties.getOrDefault('containerRegistryUsername', ''))
    project.ext.registryPassword = (System.getenv('CONTAINER_REGISTRY_PASSWORD') ?:
      project.properties.getOrDefault('containerRegistryPassword', ''))
    //TODO: informar como se obtuvieron los valores del Container Registry, para fines de depuracion


    AsciiTable at = new AsciiTable()

    at.addRule()
    at.addRow("Container Registry","")
    at.addRule()
    at.addRow("Registry URL", project.ext.registryUrl)
    at.addRule()
    at.addRow("Registry username", project.ext.registryUsername)
    at.addRule()
    at.addRow("Registry password", '*' * project.ext.registryPassword.length())
    at.addRule()
    String rend = at.render()

    logger.warn(rend)


    def dockerExtension = project.extensions.getByName('docker')

    dockerExtension.registryCredentials {
      url = project.ext.registryUrl
      username = project.ext.registryUsername
      password = project.ext.registryPassword
    }
  }

  private Integer getApplicationPort(Project project) {
    def result = 8080

    def propertiesFilePath = './src/main/resources/application.properties'
    def propertiesFile = new File(propertiesFilePath)
    def yamlFilePath = './src/main/resources/application.yaml'
    def ymlFilePath = './src/main/resources/application.yml'
    println "foo port"

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
            .filter { it instanceof Integer }
            .map {
            project.logger.warn('Defined port in yaml file: {}', it)
            it
          }.orElse(result)
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
