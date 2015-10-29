# gbif-httputils

The gbif-httputils shared library provides:
 * Common HTTP utilities built on top of Apache [HttpClient](https://hc.apache.org/httpcomponents-client-ga/index.html)
   * downloadIfChanged
   * downloadIfModifiedSince
   * verifyHost


## To build the project
```
mvn clean install
```

## Policies
 * Built as Java 6 artifact until the [IPT](https://github.com/gbif/ipt) upgrades its minimal Java version (see https://github.com/gbif/ipt/issues/1222).