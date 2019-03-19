## About

The typesafe config itself is marked as a 'provided' dependency, so to use this library, you'll still need to include the typesafe config.

The core is simply to convert an Array\[String\] to a Config where the arguments are either paths to configuration resources or simple key=value pairs.

Left-most arguments take precedence. In this example, we assume 'prod.conf' is a resource on the classpath:
