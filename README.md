<!-- PROJECT LOGO -->
<br/>
<div align="center">

<a href="https://github.com/AllayPlus/AllayPlus">
    <img src="docs/assets/logo/allayplus.png" alt="Logo" width="200" height="200">
</a>
<h3 align="center">AllayPlus</h3>

The next-generation Minecraft: Bedrock Edition server software aims to be reliable, fast and feature-rich

<a href="https://github.com/AllayPlus/AllayPlus/actions"><img src="https://github.com/AllayPlus/AllayPlus/actions/workflows/gradle.yml/badge.svg" alt="Build"/></a>
<a href="https://docs.allaymc.org"><img src="https://readthedocs.org/projects/allaymc/badge/?version=latest" alt="Documentation Status"></a>
[![javadoc](https://javadoc.io/badge2/io.github.allayplus/api/javadoc.svg)](https://javadoc.io/doc/io.github.allayplus/api)
[![api](https://img.shields.io/maven-central/v/io.github.allayplus/api?label=api)](https://central.sonatype.com/artifact/io.github.allayplus/api)
[![server](https://img.shields.io/maven-central/v/io.github.allayplus/server?label=server)](https://central.sonatype.com/artifact/io.github.allayplus/server)
[![codecov](https://codecov.io/gh/AllayPlus/AllayPlus/graph/badge.svg?token=TQVZ3XBJXC)](https://codecov.io/gh/AllayPlus/AllayPlus)
[![Codacy Badge](https://app.codacy.com/project/badge/Grade/87c025f71e9549b9a4f2ab36a56b5cf9)](https://app.codacy.com/gh/AllayPlus/AllayPlus/dashboard?utm_source=gh&utm_medium=referral&utm_content=&utm_campaign=Badge_grade)
<a href="https://discord.gg/ngkkE4hPTU"><img src="https://img.shields.io/discord/1147136608290750526?label=discord&color=7289DA&logo=discord" alt="Discord" /></a>
<a href="https://feedback.minecraft.net/hc/en-us/sections/360001186971-Release-Changelogs"><img src="https://img.shields.io/badge/minecraft-1.21.80 ~ 1.21.130%20(Bedrock)-green" /></a>
[![GitHub License](https://img.shields.io/github/license/AllayPlus/AllayPlus)](LICENSE)
[![Ko-Fi](https://img.shields.io/badge/Buy_Me_a_Coffee-f37574?logo=kofi&logoColor=white)](https://ko-fi.com/AllayMC)

</div>

## 📖 Introduction

AllayPlus is a third-party server software for Minecraft: Bedrock Edition written from scratch, with the goal of being
reliable, fast and feature-rich.

## 🎯 Why AllayPlus?

- **Fast**: AllayPlus is very fast. Compared to almost all other server software, AllayPlus is far more performant in almost all aspects. The written from
scratch codebase allows us to re-examine performance critical points and solve problems in the most efficient way.
- **Reliable**: AllayPlus is designed to be reliable. To achieve this goal, we have written a lot of unit tests to ensure the stability of the project.
- **Hassle-free**: AllayPlus is designed to be user-friendly and is ready to use right out of the box. We also have complete documentation to guide you 
in your deployment. 
- **Developer-friendly**: Our api is seperated from the implementation and is well-documented. No more wasting time looking at the implementation.
- **Feature-rich**: One of our goal is to be feature-rich. Thanks to the architecture of AllayPlus, adding new features is very easy for us.
- **Cross-platform**: Benefit from JVM, AllayPlus can run on almost any platform.
- **Multi-version**: AllayPlus has multi-version support. No longer worry about losing your players due to version updates.

## 🛠️ Getting Started

AllayPlus is written and running in java 21, so you need to install java 21. There are
several version of java, and we recommend you to use [GraalVM](https://www.graalvm.org/) for the best performance.
[OpenJDK](https://adoptopenjdk.net/) is also a good choice if you want to have a stable experience.

After you have installed java 21, you can install AllayPlus manually. Just
download the `allayplus-server-*-shaded.jar` file from our release page. Move it to the folder you want and run:

```shell
java -jar allayplus-server-*-shaded.jar
```

## 🌟 Plugins

AllayPlus supports running plugins written in Java, Kotlin, Scala (JVM based language).

### Use Plugin Template

The quickest way to create a new plugin is to use our plugin template!
You can check out the following plugin templates:

- [JavaPluginTemplate](https://github.com/AllayMC/JavaPluginTemplate)
- [KotlinPluginTemplate](https://github.com/MineBuilders/allaymc-kotlin-plugin-template)
- [ScalaPluginTemplate](https://github.com/AllayMC/ScalaPluginTemplate)

### Use AllayPlus Gradle Plugin

AllayPlus provides a Gradle plugin to make it much easier to develop AllayPlus plugin. Check out
[AllayGradle](https://github.com/AllayMC/AllayGradle) for more information about how to use it.

### Use AllayPlus in Existing Project

AllayPlus API is published to the Maven Central. If you have an existing project and want to use AllayPlus in it, simply
add the following dependencies to your project:

```kts
repositories {
    mavenCentral()
}

dependencies {
    compileOnly(group = "io.github.allayplus", name = "api", version = "<version>")
}
```

You can also use the snapshot version that is under development. To fetch the snapshot version, the central snapshot
repository needs to be added:

```kts
repositories {
    mavenCentral()
    // Add the central snapshot repository
    maven("https://central.sonatype.com/repository/maven-snapshots/")
}

dependencies {
    // Add `-SNAPSHOT` suffix to the version to use the snapshot version, an example is `0.19.0-SNAPSHOT`
    compileOnly(group = "io.github.allayplus", name = "api", version = "<version>-SNAPSHOT")
}
```

## 🙌 Contributing

Contributions are welcomed! And please read [CONTRIBUTING.md](CONTRIBUTING.md) before contributing.
There are several ways you can contribute:

### Submitting a Pull Request

We appreciate code contributions. If you've fixed a bug or implemented a new feature, please submit
a pull request! Please ensure your code follows our coding standards and include tests where possible.

This project exists thanks to the participation of the following developers:

![contributors](https://contrib.rocks/image?repo=AllayPlus/AllayPlus)

### Feedback

Your feedback can make this project better. If you find a problem/have a new idea, feel free to raise it in
the [issues page](https://github.com/AllayPlus/AllayPlus/issues). Ensure to include a detailed description of the 
bug and steps to reproduce it.

## 🎫 License

Copyright **© 2026 AllayPlus**, all rights reserved. If not otherwise specified, project content is open source under
the LGPL-3.0 license. The contents of the following folders are open-sourced under the MIT license:

- data
- codegen

## ❤️ Special Thanks

- [Endstone](https://github.com/EndstoneMC/Endstone): Endstone's build-in DevTools is essential for AllayPlus to dump key data from BDS
- [Protocol](https://github.com/CloudburstMC/Protocol): The protocol library and implementation of RakNet used by AllayPlus
- [df-mc](https://github.com/df-mc): References for some key API designs
- [gophertunnel](https://github.com/Sandertv/gophertunnel): The protocol library for reference which is well-documented
- [pmmp](https://github.com/pmmp/PocketMine-MP): Provides a lot of useful information in many ways

## 👀 Stargazers over time

[![Stargazers over time](https://starchart.cc/AllayPlus/AllayPlus.svg?variant=adaptive)](https://starchart.cc/AllayPlus/AllayPlus)

[//]: # (AllayPlus is the cutest software in the world!)
