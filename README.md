Args4c
====

[![Build Status](https://travis-ci.org/aaronp/args4c.svg?branch=master)](https://travis-ci.org/aaronp/args4c)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.github.aaronp/args4c_2.12/badge.png)](https://maven-badges.herokuapp.com/maven-central/com.github.aaronp/args4c_2.12/badge.png)
[![Coverage Status](https://coveralls.io/repos/github/aaronp/args4c/badge.svg?branch=master)](https://coveralls.io/github/aaronp/args4c?branch=master)
[![Scaladoc](https://javadoc-badge.appspot.com/com.github.aaronp/args4c_2.12.svg?label=scaladoc)](https://javadoc-badge.appspot.com/com.github.aaronp/args4c_2.12)

a zero-dependency* utility for producing a [lightbend (typesafe) config](https://github.com/lightbend/config) from command-line arguments, as well as some convenience methods for working with configurations.

It is intended to be "arguments for config" library which provides a means to interpret command-line arguments as configuration key/value pairs or configuration files.

It also adds some conveniences to:

 * get the unique paths, export a config as a json string, filter/modify configs
 * provide 'pretty' config summaries
 * encrypting/decrypt sensitive config entries
 * expose convenient overrides by default from environment variables

Check out the documentation [here](https://aaronp.github.io/args4c/index.html) or the latest scaladocs [here](https://aaronp.github.io/args4c/api/latest/args4c/index.html)

\* It is zero-dependency as it declares a 'provided' dependency on the lightbend (typesafe) config so not to conflict with the explicit version used by your project   