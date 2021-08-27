# NodeExtensionSetup
Autoinstall Node.js and modules on G-Node extension launch

## How to use
Include the [jar file](https://github.com/WiredSpast/NodeExtensionSetup/releases/latest) in your extension.zip together with your package.json and javascript files and set the command as the following:
```cmd
java -jar NodeExtensionSetup.jar -e {extensionScript} -v {minimalPythonVersion} -c {cookie} -p {port} -f {filename}
```
Example:
```cmd
java -jar NodeExtensionSetup.jar -e myExtension.js -v 3.2.0 -c {cookie} -p {port} -f {filename}
```
How to note in extension.json:
```json
"commands": {
  "default": ["java", "-jar", "NodeExtensionSetup.jar", "-e", "myExtension.js", "-v", "15.0.0", "-c", "{cookie}", "-p", "{port}", "-f", "{filename}"]
}
```

### Installing modules
If you include your package.json file in extension.zip the node modules will be automatically installed
