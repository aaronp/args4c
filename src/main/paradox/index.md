Args4c
======

Args4c (arguments for configuration) is intended to add some helpers and utilities for obtaining a typesafe configuration from user arguments.

## About

The typesafe config itself is marked as a 'provided' dependency, so to use this library, you'll still need to include the typesafe config.

The core is simply to convert an Array\[String\] to a Config where the arguments are either paths to configuration resources or simple key=value pairs.

Left-most arguments take precedence. In this example, we assume 'prod.conf' is a resource on the classpath:

## Usage

```bash
  java -cp app.jar MyApp foo.x=bar foo.x=ignored /opt/etc/overrides.conf prod.conf
```

The library can be used like this:
```scala
  import args4c.implicits._
  
  object MyApp {
     override def main(args : Array[String]) : Unit = {
       val config = args.asConfig()
       println("Starting MyApp with config:")

       // let's "log" our app's config on startup:
       val summary : String = config.withPaths("myapp").summary()
       println(summary)
     }
  }
```

Where the 'summary' will produce sorted [[args4c.StringEntry]] values with potentially sensitive entries (e.g. passwords)
obscured and a source comment for some sanity as to where each entry comes from:

```bash
myapp.foo : bar # command-line
myapp.password : **** obscured **** # command-line
myapp.saveTo : afile # file:/opt/etc/myapp/test.conf@3
```

## Auto-overriding entries using environment variables

When extracting user arguments into a configuration, an additional 'fallback' config is specified.
Typically this would just be the ConfigFactory.load() configuration, but args4c uses the 'args4c.defaultConfig',
which is essentially just the system environment variables converted from snake-caes to dotted lowercase values
first, then falling back on ConfigFactory.load().

Applications can elect to not have this behaviour and provide their own fallback configs when parsing args, but
the default provides a convenience for system environment variables to override e.g. 'foo.bar.x=default' by specifying

```
  FOO_BAR_X=override
```

as a system environment variable. Otherwise you may end up having to repeat this sort of thing all over you config:
```bash
  foo.bar=default
  foo.bar=$${?FOO_BAR}

  foo.bazz=default2
  foo.bazz=$${?FOO_BAZZ}

  ...
```

## Sensitive (Secret) Configurations

Finally, args4c also provides a 'args4c.ConfigApp' trait which provides some additional functionality to configuration-based
applications:

```scala
object MyApp extends args4c.ConfigApp with StrictLogging {

  override def run(config : Config) : Unit = {
     logger.info(s"Running with ${config.withPaths("myapp").summary().mkString("\n")}")
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

## ScalaDocs

Consult the [ScalaDocs](https://aaronp.github.io/args4c/api/index.html) for more.

