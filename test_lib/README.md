The `jersey-server-1.9-repack.jar` and `asm-3.1-repack.jar` files
are repackaged in order to avoid a dependency clash between asm 3.1
and asm 4.0, the latter of which is used by the Pegdown markdown
parser. 

These jars are only needed on the classpath in the test configuration.
TODO: Work out how to configure SBT to ignore them when not in a test
scope.
