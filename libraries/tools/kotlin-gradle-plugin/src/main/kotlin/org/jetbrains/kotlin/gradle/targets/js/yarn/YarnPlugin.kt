/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.targets.js.yarn

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.BasePlugin
import org.jetbrains.kotlin.gradle.plugin.whenEvaluated
import org.jetbrains.kotlin.gradle.targets.js.MultiplePluginDeclarationDetector
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootPlugin
import org.jetbrains.kotlin.gradle.targets.js.npm.RequiresNpmDependencies
import org.jetbrains.kotlin.gradle.targets.js.npm.resolver.implementing
import org.jetbrains.kotlin.gradle.targets.js.npm.tasks.KotlinNpmInstallTask
import org.jetbrains.kotlin.gradle.targets.js.npm.tasks.RootPackageJsonTask
import org.jetbrains.kotlin.gradle.tasks.CleanDataTask
import org.jetbrains.kotlin.gradle.tasks.registerTask

open class YarnPlugin : Plugin<Project> {
    override fun apply(project: Project): Unit = project.run {
        MultiplePluginDeclarationDetector.detect(project)

        check(project == project.rootProject) {
            "YarnPlugin can be applied only to root project"
        }

        val yarnRootExtension = this.extensions.create(YarnRootExtension.YARN, YarnRootExtension::class.java, this)
        val nodeJs = NodeJsRootPlugin.apply(this)

        val setupTask = registerTask<YarnSetupTask>(YarnSetupTask.NAME) {
            it.dependsOn(nodeJs.nodeJsSetupTaskProvider)

            it.configuration = provider {
                this.project.configurations.detachedConfiguration(this.project.dependencies.create(it.ivyDependency))
                    .also { conf -> conf.isTransitive = false }
            }
        }

        val rootClean = project.rootProject.tasks.named(BasePlugin.CLEAN_TASK_NAME)

        val rootPackageJson = tasks.register(RootPackageJsonTask.NAME, RootPackageJsonTask::class.java) { task ->
            task.group = NodeJsRootPlugin.TASKS_GROUP_NAME
            task.description = "Create root package.json"

            task.mustRunAfter(rootClean)
        }

        val kotlinNpmInstall = tasks.named(KotlinNpmInstallTask.NAME)
        kotlinNpmInstall.configure {
            it.dependsOn(rootPackageJson)
            it.dependsOn(setupTask)
        }

        tasks.register("yarn" + CleanDataTask.NAME_SUFFIX, CleanDataTask::class.java) {
            it.cleanableStoreProvider = provider { yarnRootExtension.requireConfigured().cleanableStore }
            it.description = "Clean unused local yarn version"
        }

        val packageJsonUmbrella = nodeJs
            .packageJsonUmbrellaTaskProvider

        yarnRootExtension.rootPackageJsonTaskProvider.configure {
            it.dependsOn(packageJsonUmbrella)
        }

        project.allprojects
            .forEach {
                val fn: (Project) -> Unit = {
                    it.tasks.implementing(RequiresNpmDependencies::class).all {}
                }
                if (it.state.executed) {
                    fn(it)
                } else {
                    it.afterEvaluate {
                        fn(it)
                    }
                }
            }

        val storeYarnLock = tasks.register("kotlinStoreYarnLock") {
            it.dependsOn(kotlinNpmInstall)
            val yarnLock = nodeJs.rootPackageDir.resolve("yarn.lock")

            it.doLast {
                copy { copy ->
                    copy.from(yarnLock)
                    copy.rename { yarnRootExtension.lockFileName }
                    copy.into(yarnRootExtension.lockFileDirectory)
                }
            }

            it.inputs.file(yarnLock).withPropertyName("inputFile")
            it.outputs.file(yarnRootExtension.lockFileDirectory.resolve(yarnRootExtension.lockFileName)).withPropertyName("outputFile")
        }

        val restoreYarnLock = tasks.register("kotlinRestoreYarnLock") {
            val lockFile = yarnRootExtension.lockFileDirectory.resolve(yarnRootExtension.lockFileName)
            it.onlyIf {
                lockFile.exists()
            }
            it.doLast {
                copy { copy ->
                    copy.from(lockFile)
                    copy.rename { "yarn.lock" }
                    copy.into(nodeJs.rootPackageDir)
                }
            }

            it.inputs.file(lockFile).withPropertyName("inputFile")
            it.outputs.file(nodeJs.rootPackageDir.resolve("yarn.lock")).withPropertyName("outputFile")
        }

        kotlinNpmInstall.configure {
            it.dependsOn(restoreYarnLock)
            it.finalizedBy(storeYarnLock)
        }
    }

    companion object {
        fun apply(project: Project): YarnRootExtension {
            val rootProject = project.rootProject
            rootProject.plugins.apply(YarnPlugin::class.java)
            return rootProject.extensions.getByName(YarnRootExtension.YARN) as YarnRootExtension
        }
    }
}
