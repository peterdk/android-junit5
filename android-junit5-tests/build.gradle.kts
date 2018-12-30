import de.mannodermaus.gradle.plugins.junit5.WriteClasspathResource
import org.apache.tools.ant.filters.ReplaceTokens
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
  id("groovy")
  id("java-gradle-plugin")
  id("java-library")
  id("idea")
  id("jacoco")
  id("kotlin")
}

// ------------------------------------------------------------------------------------------------
// Compilation Tweaks
//
// The plugin currently consists of a codebase wherein Groovy & Kotlin coexist.
// Therefore, the compilation chain has to be well-defined to allow Kotlin
// to call into Groovy code.
//
// The other way around ("call Kotlin from Groovy") is prohibited explicitly.
// ------------------------------------------------------------------------------------------------
val compileTestGroovy = tasks.getByName("compileTestGroovy") as AbstractCompile
val compileTestKotlin = tasks.getByName("compileTestKotlin") as AbstractCompile
val testClassesTask = tasks.getByName("testClasses")

compileTestKotlin.dependsOn.remove("compileTestJava")
compileTestGroovy.dependsOn.add(compileTestKotlin)
compileTestGroovy.classpath += project.files(compileTestKotlin.destinationDir)

// Add custom dependency configurations
configurations {
  create("functionalTest") {
    description = "Local dependencies used for compiling & running " +
        "tests source code in Gradle functional tests"
  }

  create("functionalTestAgp32X") {
    description = "Local dependencies used for compiling & running " +
        "tests source code in Gradle functional tests against AGP 3.2.X"
  }

  create("functionalTestAgp33X") {
    description = "Local dependencies used for compiling & running " +
        "tests source code in Gradle functional tests against AGP 3.3.X"
  }

  create("functionalTestAgp34X") {
    description = "Local dependencies used for compiling & running " +
        "tests source code in Gradle functional tests against AGP 3.4.X"
  }
}

val processTestResources = tasks.getByName("processTestResources") as Copy
processTestResources.apply {
  val tokens = mapOf(
      "COMPILE_SDK_VERSION" to project.extra["android.compileSdkVersion"] as String,
      "BUILD_TOOLS_VERSION" to project.extra["android.buildToolsVersion"] as String,
      "MIN_SDK_VERSION" to (project.extra["android.sampleMinSdkVersion"] as Int).toString(),
      "TARGET_SDK_VERSION" to (project.extra["android.targetSdkVersion"] as Int).toString()
  )

  inputs.properties(tokens)

  from(sourceSets["test"].resources.srcDirs) {
    include("**/testenv.properties")
    filter(ReplaceTokens::class, mapOf("tokens" to tokens))
  }
}

tasks.withType<Test> {
  failFast = true
  useJUnitPlatform()
  testLogging {
    events = setOf(TestLogEvent.PASSED, TestLogEvent.SKIPPED, TestLogEvent.FAILED)
    exceptionFormat = TestExceptionFormat.FULL
  }

  // Uncomment this line to run disable running Functional Tests on the local device
//  environment("CI", "true")
}

dependencies {
  testImplementation(project(":android-junit5"))
  testImplementation(kotlin("gradle-plugin", extra["versions.kotlin"] as String))
  testImplementation(extra["plugins.android"] as String)
  testImplementation(extra["libs.commonsIO"] as String)
  testImplementation(extra["libs.commonsLang"] as String)
  testImplementation(extra["libs.junit4"] as String)
  testImplementation(extra["libs.junitJupiterApi"] as String)
  testImplementation(extra["libs.junitJupiterParams"] as String)
  testImplementation(extra["libs.spekApi"] as String)
  testImplementation(extra["libs.junitPioneer"] as String)
  testImplementation(extra["libs.assertjCore"] as String)
  testImplementation(extra["libs.mockito"] as String)

  testRuntimeOnly(extra["libs.junitJupiterEngine"] as String)
  testRuntimeOnly(extra["libs.junitVintageEngine"] as String)
  testRuntimeOnly(extra["libs.spekEngine"] as String)

  // Compilation of local classpath for functional tests
  val functionalTest by configurations
  functionalTest(kotlin("compiler-embeddable", extra["versions.kotlin"] as String))
  functionalTest(extra["libs.junit4"] as String)
  functionalTest(extra["libs.junitJupiterApi"] as String)
  functionalTest(extra["libs.junitJupiterEngine"] as String)

  val functionalTestAgp32X by configurations
  functionalTestAgp32X(extra["plugins.android.32X"] as String)

  val functionalTestAgp33X by configurations
  functionalTestAgp33X(extra["plugins.android.33X"] as String)

  val functionalTestAgp34X by configurations
  functionalTestAgp34X(extra["plugins.android.34X"] as String)
}

// Resource Writers
tasks.create("writePluginClasspath", WriteClasspathResource::class) {
  inputFiles = sourceSets["test"].runtimeClasspath
  outputDir = File("$buildDir/resources/test")
  resourceFileName = "plugin-classpath.txt"
}

// Create a classpath-generating task for all functional test configurations
listOf("functionalTest", "functionalTestAgp32X", "functionalTestAgp33X", "functionalTestAgp34X").forEach { config ->
  tasks.create("write${config.capitalize()}CompileClasspath", WriteClasspathResource::class) {
    inputFiles = configurations[config]
    outputDir = File("$buildDir/resources/test")
    resourceFileName = "$config-compile-classpath.txt"
  }
}

val testTask = tasks.getByName("test")
tasks.withType<WriteClasspathResource> {
  processTestResources.finalizedBy(this)
  testTask.mustRunAfter(this)
}
