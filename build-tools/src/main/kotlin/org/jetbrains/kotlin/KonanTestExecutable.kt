package org.jetbrains.kotlin

import org.gradle.api.Action
import org.gradle.api.Task

interface KonanTestExecutable : Task {
    val executable: String
    var doBeforeRun: Action<in Task>?
    var doBeforeBuild: Action<in Task>?
}