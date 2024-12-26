# Juice Plugin

A GoLand plugin for MyBatis-style SQL mapping support.

## Features

1. SQL Syntax Highlighting
   - Highlights SQL syntax in XML mapper files
   - Supports nested tags

2. Resource Navigation
   - Navigate between Go interfaces and XML mapper files
   - Line markers for easy navigation

3. Resource Reference Check
   - Validates XML mapper references
   - Shows errors for invalid references

## Building

Linux/macOS:
```bash
./gradlew build
```

Windows:
```cmd
gradlew.bat build
```

The plugin JAR will be generated in `build/libs/juice-plugin-1.0-SNAPSHOT.jar`

## Installation

1. Download the latest release
2. In GoLand: Settings -> Plugins -> Install Plugin from Disk
3. Select the downloaded JAR file
4. Restart GoLand

## License

MIT License
