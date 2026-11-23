# Native build scripts

These scripts are a small native wrapper around the repository's checked-in Gradle wrapper. They build the current Forge 1.12.2 distribution module and place its distributable JAR in the repository-level `dist/` directory.

## Prerequisites

- A local JDK 8. Set `JAVA_HOME` to that JDK when more than one Java installation is present. The project and its Gradle 4.9/ForgeGradle 3 toolchain target Java 8 (`sourceCompatibility = 1.8`); this pipeline was validated with Eclipse Temurin JDK 8.0.472.8.
- Internet access may be needed the first time the Gradle wrapper resolves its declared Gradle distribution and project dependencies. The scripts do not install Java or modify global toolchain settings.

## Build

From any directory:

```bash
bash /path/to/storage-tweaks/build-scripts/linux-build.sh
```

```powershell
& 'C:\path\to\storage-tweaks\build-scripts\windows-build.ps1'
```

Each script calls the checked-in wrapper with `build`, stages only the Forge distribution JAR, and prints the completed path. The expected output is named like `dist/firebadnofire-storage-tweaks-<version>-mc1.12.2-forge.jar`. Source JARs, test JARs, the embedded `common` module JAR, and intermediate reobfuscation outputs are intentionally excluded.

## Clean

```bash
bash /path/to/storage-tweaks/build-scripts/linux-clean.sh
```

```powershell
& 'C:\path\to\storage-tweaks\build-scripts\windows-clean.ps1'
```

The clean scripts run Gradle's `clean` task and remove only the Storage Tweaks distribution JARs and script staging directories in `dist/`. They are safe to rerun and leave unrelated files in `dist/` untouched.

## Limitations

The scripts intentionally use the current repository build graph and do not launch Minecraft or perform gameplay validation. They build the presently implemented Forge 1.12.2 target only; adding future distribution modules requires adding their legitimate release artifact naming/build tasks to this pipeline rather than copying development JARs blindly.
