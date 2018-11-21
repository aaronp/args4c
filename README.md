Args4c
====

Args4c is a utility for producing a typesafe config from user argments (e.g. Array[String])

The dependency on the typesafe config is 'provided', so should work with the typesafe config
already on your classpath (or, if there isn't one, you can bring one in)


## Release

Current version is 0.0.0 available on maven central, built for 2.10 and 2.11

Maven:
```xml
<dependency>
    <groupId>args4c</groupId>
    <artifactId>args4c_2.12</artifactId>
    <version>0.0.0</version>
</dependency>
```

If using SBT then you want:
```scala
libraryDependencies += "args4c" %% "args4c" % "0.0.4"
```

## Usage

```scala
import
object YourApp {

  def main(args : Array[String]) {

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
