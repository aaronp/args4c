Args4c
====

Args4c is a utility for producing a typesafe config from user arguments (e.g. Array[String]),
as well as some convenience methods for a config such as filtering, formatting, getting intersections, etc.

The dependency on the typesafe config is 'provided', so should work with the typesafe config
already on your classpath (or, if there isn't one, you can bring one in)

## Release

Current version is 0.0.3 available on maven central, built for 2.11 and 2.12

Maven:
```xml
<dependency>
    <groupId>args4c</groupId>
    <artifactId>args4c_2.12</artifactId>
    <version>0.0.4</version>
</dependency>
```

If using SBT then you want:
```scala
libraryDependencies += "args4c" %% "args4c" % "0.0.4"
```

## Usage

```scala
import args4c.implicits._
import com.typesafe.config._

object YourApp {
  def main(args : Array[String]): Unit = {
    val config = ConfigFactory.load().withUserArgs(args) // or just args4c.configForArgs(args)
    run(config)
  }
  def run(config : Config) = {
    println(config.summary())
  
  }
}
```

## License
```
This software is licensed under the Apache 2 license, quoted below.

Copyright 2014 Stephen Samuel

Licensed under the Apache License, Version 2.0 (the "License"); you may not
use this file except in compliance with the License. You may obtain a copy of
the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
License for the specific language governing permissions and limitations under
the License.
```
