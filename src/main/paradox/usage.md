## Usage

The goal for this library is to enable command-line arguments to affect the parsed typesafe config.

## Running

e.g., you just write your application against a 'Config' instead of 'Array\[String\]', and then run it like this: 

```bash
  java -cp app.jar MyApp foo.x=bar foo.x=ignored /opt/etc/overrides.conf prod.conf
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
