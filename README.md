# mod-audit-filter

Copyright (C) 2017-2018 The Open Library Foundation

This software is distributed under the terms of the Apache License,
Version 2.0. See the file "[LICENSE](LICENSE)" for more information.

## Introduction

The audit-filter module implements Okapi PRE and POST filters to capture audit data. It then calls mod-audit to persist the data.

## Permissions

## Additional information

### Other documentation

Other [modules](https://dev.folio.org/source-code/#server-side) are described,
with further FOLIO Developer documentation at [dev.folio.org](https://dev.folio.org/)

### Issue tracker

See project [MODAUDIT](https://issues.folio.org/browse/MODAUD)
at the [FOLIO issue tracker](https://dev.folio.org/guidelines/issue-tracker).

### Quick start

Compile with `mvn clean install`

Run the local stand-alone instance:

```
java -jar target/mod-audit-filter-fat.jar \
  -Dhttp.port=8085 embed_postgres=true
```

### API documentation

There is no public API exposed by this module.

### Code analysis

[SonarQube analysis](https://sonarcloud.io/dashboard?id=org.folio.rest%3Amod-audit-filter).

### Download and configuration

The built artifacts for this module are available.
See [configuration](https://dev.folio.org/download/artifacts) for repository access,
and the [Docker image](https://hub.docker.com/r/folioorg/mod-audit-filter/).

