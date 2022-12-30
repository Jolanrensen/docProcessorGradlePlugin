package com.example.plugin

import org.gradle.api.Plugin
import org.gradle.api.Project

class KdocIncludePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.tasks.register("processKdocInclude", ProcessKdocIncludeTask::class.java)
    }
}