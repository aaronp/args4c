
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
