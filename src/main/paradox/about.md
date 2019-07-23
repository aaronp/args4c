## About

The typesafe config itself is marked as a 'provided' dependency, so to use this library, you'll still need to include the typesafe config.

The core is simply to convert an Array\[String\] to a Config where the arguments are either paths to configuration resources or simple key=value pairs.


So, consider this application:

```scala
package acme

import args4c.ConfigApp
import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging

object MyMain extends ConfigApp with StrictLogging {
  override type Result = String
  override def run(config : Config) = {
    logger.info("Running MyMain with:\n" + config.getConfig("acme").summary())
    
    val message = "Hello ${config.getString("acme.name")}"
    logger.info(message)
    
    // let's return our message, so our main class can actually act as a function from Config => String
    message
  }
}
```

which also has these resources:

src/main/resources/reference.conf
```
acme.name : "default"
acme.password : ""
```

and 


src/main/resources/acme-test.conf
```
acme.name : "testing"
```

Let's also assume there is an '/app/config/prod.conf' file which contains
```
acme.name : "production"
acme.password : "S3creT"
```

When we run the application, we can observe the left-most arguments take presedence.

### No args (e.g. using our reference.conf)

```scala
java -jar myapp.jar
Running MyMain with:
name : default # reference.conf
password : **** obscured **** # reference.conf
```

### Using the acme-test.conf resource file:
```scala
java -jar myapp.jar acme-test.conf
Running MyMain with:
name : testing # acme-test.conf
password : **** obscured **** # reference.conf
```
Note: If you ran it with e.g. 
```
java -cp /opt/config:/opt/lib/myapp.jar acme.MyMain fileInOptConfig.conf 
```
It would find and use /opt/config/fileInOptConfig.conf 

### Specifying an absolute path to prod.conf
```scala
java -jar myapp.jar /app/config/prod.conf
Running MyMain with:
name : production # /app/config/prod.conf
password : **** obscured **** # /app/config/prod.conf
```

### Our prod.conf file, overridden on the command-line
```scala
java -jar myapp.jar acme.name=foo /app/config/prod.conf
Running MyMain with:
name : foo # command-line
password : **** obscured **** # reference.conf
```

