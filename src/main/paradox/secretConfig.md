
## Sensitive (Secret) Configurations

Finally, args4c also provides a 'args4c.ConfigApp' trait which provides some additional functionality to configuration-based
applications:

```scala
object MyApp extends args4c.ConfigApp with StrictLogging {

  override def run(config : Config) : Unit = {
     logger.info(s"Running with ${config.withPaths("myapp").summary()}")
      ...
  }
}
```


For example, if you run your application with the argument --setup (or --setup --secret=path/to/secret.conf), then the user
will be prompted to indicate where the encrypted config should be created (and with what permissions), followed by prompts
for configuration entries:

```bash
> java -cp app.jar MyApp --setup
Save secret config to (.config/secret.conf):
Config Permissions (rwx------):
Add config path in the form <key>=<value> (leave blank when finished):example.password1=sEcr3t
Add config path in the form <key>=<value> (leave blank when finished):example.config.entry.key=password
Add config path in the form <key>=<value> (leave blank when finished):
Config Password:myapp
```

Then, to run the application with this config taken into account, launch it with the '--secret=path/to/your/secret.conf' 
(or just --secret to have it look in the default location).

NOTE: the application will then prompt for a config password from standard input. Alternatively you can:
 * set the 'CONFIG_SECRET' environment variable
 * set the 'CONFIG_SECRET' system property to the JVM
 * override the 'readConfigPassword' function in your application  