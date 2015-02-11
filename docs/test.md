# Notes on testing

Using the default settings, running the tests will crash SBT with a PermGen or other
memory error. This is presumably because spinning up an instance of the ReST server
(which most tests do) requires too many resources and leaks somewhere.

As a fix, the folling `_JAVA_OPTIONS` can be set as an env var which SBT will pick up.
These increase various JVM mem limits.

```bash
export _JAVA_OPTIONS="-Xms64m -Xmx1024m -Xss2m -XX:+CMSClassUnloadingEnabled -XX:MaxPermSize=256M -Dconfig.file=conf/test.conf"
```

It should be then possible to just run `play test`.

## Running a single test

In Play's default test framework (Spec2) a single test is called an example, e.g:

```scala
package mytests

class SomeSpec extends Specification {
    "something" should {
        "do something" in {
            // some text code
            ...
        }
    }
}
```

To run just the "do something" example here, it's best to load the Play shell with `play` (or `activator` as it's also known) and run:

```
play> testOnly mytests.SomeSpec -- ex "do something"
```
