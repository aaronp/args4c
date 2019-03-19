
## Overrides from env variables

When extracting user arguments into a configuration, an additional 'fallback' config is specified.
Typically this would just be the ConfigFactory.load() configuration, but args4c uses the 'args4c.defaultConfig',
which is essentially just the system environment variables converted from snake-caes to dotted lowercase values
first, then falling back on ConfigFactory.load().

Applications can elect to not have this behaviour and provide their own fallback configs when parsing args, but
the default provides a convenience for system environment variables to override e.g. 'foo.bar.x=default' by specifying

```
  FOO_BAR_X=override
```

as a system environment variable. Otherwise you may end up having to repeat this sort of thing all over you config:
```bash
  foo.bar=default
  foo.bar=$${?FOO_BAR}

  foo.bazz=default2
  foo.bazz=$${?FOO_BAZZ}

  ...
```
