
- Build using _installShadowDist_ Gradle task. This will generate a fat jar containing all the required libraries.
  The app can then be started with: 
```
    $ java -jar tekstitv-0.1.0-all.jar
```

- Log level is defined by `Log.level` (in file `Log.kt`).

- Tested with Oracle JRE 1.8.0_181.

- Example Gnome desktop shortcut file:

```
    #!/usr/bin/env xdg-open
    [Desktop Entry]
    Version=1.0
    Terminal=false
    Type=Application
    Name=Teksti-TV
    Path=/install-path/tekstitv-shadow
    Icon=/icon-path/tekstitv.svg
    Exec=/java-home/bin/java -jar lib/tekstitv-0.1.0-all.jar
    StartupWMClass=fi-reuna-tekstitv-MainKt
```
