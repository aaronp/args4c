## Usage

The goal for this library is to enable command-line arguments to affect the parsed typesafe config.

## Running

e.g., you just write your application against a 'Config' instead of 'Array\[String\]', and then run it like this: 

```bash
  java -cp myApp.jar my.App foo.x=bar /app/config/filesystem-config.conf classpathConfig.conf
```

## In Code

The 'MyApp' would then just use the args4c.implicits (or extend a ConfigApp) to be able to get the configuration: 

```scala
  import args4c.implicits._
  
  object MyApp {
     override def main(args : Array[String]) : Unit = {
       val config = args.asConfig()
       println("Starting MyApp with config:")

       // let's "log" our app's config on startup:
       val summary : String = config.withPaths("myapp").summary()
       println(summary)
       
       startMyApplication(config)
     }
     
     def startMyApplication(conf : Config) = ...
  }
```

This shows how 'args.asConfig()' can get a configuration from the user arguments, and then use a 'summary' to produce a sensible, flat config summary
with sensitive entries obscured, as well as a source comment for some sanity as to where each entry comes from:

```bash
myapp.foo : bar # command-line
myapp.password : **** obscured **** # command-line
myapp.saveTo : afile # file:/opt/etc/myapp/test.conf@3
```

## Sensitive (Encrypted) Configurations

args4c also provides a 'args4c.ConfigApp' trait which provides some additional functionality to configuration-based
applications:

```scala
object MyApp extends args4c.ConfigApp with StrictLogging {

  override def run(config : Config) : Unit = {
     logger.info(s"Running with ${config.withPaths("myapp").summary()}")
      ...
  }
}
```

## Setting up sensitive config entries

Aside from brining in the implicit configurations, it adds argument parsing support to run your application with "--setup" in so sensitive configuration
settings can be supplied by the user using standard input.


The user will be prompted to indicate where the encrypted config should be created (and with what permissions), followed by prompts
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

or, you can supply the default config location via e.g. the --secret=path/to/password.conf argument:

```bash
> java -cp app.jar MyApp --setup --secret=path/to/secret.conf
Save secret config to (path/to/secret.conf):
...
```

## Running with an encrypted config

Then, to run the application with this config taken into account, launch it with the '--secret=path/to/your/secret.conf' 
(or just --secret to have it look in the default location). The application will then prompt for a config password from standard input.

```bash
> java -cp app.jar MyApp --secret=path/to/secret.conf -other.settings=true uat.conf
Config Password:_
```
or

```bash
> java -cp app.jar MyApp --secret
Config Password:_
```

Note: The --secret argument is in addition to any other configuration params:

```bash
> java -cp app.jar MyApp --secret -show=foo.bar foo.x=true uat.conf
Config Password:_
```


To avoid having to provide the password from standard input you can:
 * set the 'CONFIG_SECRET' environment variable
 * set the 'CONFIG_SECRET' system property to the JVM
 * override the 'readConfigPassword' function in your application

(I'm not recommending you expose your passwords via system properties or env variables -- the option's just there if you need it)
 
```bash
> java -DCONFIG_SECRET=confPassword -cp app.jar MyApp --secret show=foo
foo.bar : some value # reference.conf
> export CONFIG_SECRET=confPassword
> java -cp app.jar MyApp --secret show=foo
foo.bar : some value # reference.conf
```