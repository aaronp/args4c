Args4c
====

[![Build](https://github.com/aaronp/args4c/actions/workflows/scala.yml/badge.svg)](https://github.com/aaronp/args4c/actions/workflows/scala.yml)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.github.aaronp/args4c_3/badge.svg?style=flat)](https://maven-badges.herokuapp.com/maven-central/com.github.aaronp/args4c_3)
[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

An "arguments for config" zero-dependency* utility to aid in producing a [lightbend (typesafe) config](https://github.com/lightbend/config) from command-line arguments, as well as some convenience methods for working with configurations such as:

 * get the unique paths, export a config as a json string, filter/modify configs
 * provide 'pretty' config summaries
 * encrypting/decrypt sensitive config entries
 * allow overrides from environment variables (e.g. FOO_BAR=123 overrides property foo.bar)

The minisite can be found [here](https://aaronp.github.io/args4c/index.html)

** It is zero-dependency as it declares a 'provided' dependency on the lightbend (typesafe) config so not to conflict with the explicit version used by your project   