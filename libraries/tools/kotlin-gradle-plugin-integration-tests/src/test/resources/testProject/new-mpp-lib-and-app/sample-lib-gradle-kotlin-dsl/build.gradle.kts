plugins {
	id("org.jetbrains.kotlin.multiplatform").version("<pluginMarkerVersion>")
	id("maven-publish")
}

group = "com.example"
version = "1.0"

repositories {
    mavenLocal()
    mavenCentral()
}

kotlin {
    val jvm = jvm("jvm6")
    val js = js("nodeJs")
    linuxX64("linux64")
    wasm()

    targets.all {
        mavenPublication(Action<MavenPublication> {
            pom.withXml(Action<XmlProvider> {
                asNode().appendNode("name", "Sample MPP library")
            })
        })
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(kotlin("stdlib-common"))
            }
        }
        jvm.compilations["main"].defaultSourceSet {
            dependencies {
                api(kotlin("stdlib"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:0.23.4")
            }
        }
        js.compilations["main"].defaultSourceSet {
        	dependencies {
                api(kotlin("stdlib-js"))
        	}
        }
    }
}

publishing {
	repositories {
		maven { setUrl("file://${projectDir.absolutePath.replace('\\', '/')}/repo") }
	}
}

// Check that a compilation may be created after project evaluation, KT-28896:
afterEvaluate {
    kotlin {
        jvm("jvm6").compilations.create("benchmark") {
            defaultSourceSet.dependsOn(sourceSets["jvm6Main"])
            tasks["assemble"].dependsOn(compileKotlinTask)
        }
    }
}