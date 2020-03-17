
- Build using _installShadowDist_ Gradle task. This will generate a fat jar containing all the required libraries.
  The app can then be started with: 
```
$ java -jar tekstitv-0.3.0-all.jar
```

- Log level is defined by `Log.level` (in file `Log.kt`).

- Tested with AdoptOpenJDK 1.8.0_222-b10.

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
Exec=/java-home/bin/java -jar lib/tekstitv-0.3.0-all.jar
StartupWMClass=fi-reuna-tekstitv-MainKt
```

- Default location for the configuration file is `$HOME/.tekstitv/configuration.hjson`.
  A custom location can be specified with the `cfg` system property (e.g. `-Dcfg=/dir/cfg.hjson`).
  
- The configuration file must specify at least `baseUrl` and `apiKey`. Other fields are optional.

``` 
{
    baseUrl = https://external.api.yle.fi
    appId = <yourIdHere>
    appKey = <yourKeyHere>
    backgroundColor: 141414  # RRGGBB
    startPage: 100
    autoRefreshInterval: 60  # Seconds
    fontFamily: Fira Mono
    margin: 8
}
```
