Args4c
====

[![Build Status](https://travis-ci.org/aaronp/args4c.svg?branch=master)](https://travis-ci.org/aaronp/args4c)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.github.aaronp/args4c_2.13/badge.png)](https://maven-badges.herokuapp.com/maven-central/com.github.aaronp/args4c_2.13)
[![Coverage Status](https://coveralls.io/repos/github/aaronp/args4c/badge.svg?branch=master)](https://coveralls.io/github/aaronp/args4c?branch=master)
[![Scaladoc](https://javadoc-badge.appspot.com/com.github.aaronp/args4c_2.13.svg?label=scaladoc)](https://javadoc-badge.appspot.com/com.github.aaronp/args4c_2.13)

An "arguments for config" zero-dependency* utility to aid in producing a [lightbend (typesafe) config](https://github.com/lightbend/config) from command-line arguments, as well as some convenience methods for working with configurations such as:

 * get the unique paths, export a config as a json string, filter/modify configs
 * provide 'pretty' config summaries
 * encrypting/decrypt sensitive config entries
 * allow overrides from environment variables (e.g. FOO_BAR=123 overrides property foo.bar)

The minisite can be found [here](https://aaronp.github.io/args4c/index.html)

\* It is zero-dependency as it declares a 'provided' dependency on the lightbend (typesafe) config so not to conflict with the explicit version used by your project   