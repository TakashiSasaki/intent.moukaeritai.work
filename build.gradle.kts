// Top-level build file where you can add configuration options common to all sub-projects/modules.
import java.util.Properties
import java.io.FileInputStream
import java.io.FileOutputStream

plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.kotlin.compose) apply false
  alias(libs.plugins.google.devtools.ksp) apply false
  alias(libs.plugins.roborazzi) apply false
  alias(libs.plugins.secrets) apply false
}

tasks.register("bumpVersionCode") {
    notCompatibleWithConfigurationCache("Modifies files in place")
    doLast {
        val propsFile = project.file("version.properties")
        if (propsFile.exists()) {
            val props = Properties()
            props.load(FileInputStream(propsFile))
            val currentCode = props.getProperty("VERSION_CODE", "1").toInt()
            props.setProperty("VERSION_CODE", (currentCode + 1).toString())
            props.store(FileOutputStream(propsFile), "Auto-incremented version code")
            println("Bumped VERSION_CODE to ${currentCode + 1}")
        } else {
            println("version.properties not found")
        }
    }
}

