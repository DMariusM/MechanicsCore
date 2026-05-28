plugins {
    `java-library`
}

dependencies {
    compileOnly(project(":mechanicscore-core"))
    compileOnly(libs.paper)
    compileOnly(libs.worldedit) {
        exclude(group = "com.google.guava", module = "guava")
        exclude(group = "com.google.code.gson", module = "gson")
        exclude(group = "it.unimi.dsi", module = "fastutil")
    }
    compileOnly(libs.worldguard) {
        exclude(group = "com.google.guava", module = "guava")
        exclude(group = "com.google.code.gson", module = "gson")
        exclude(group = "it.unimi.dsi", module = "fastutil")
    }
}
