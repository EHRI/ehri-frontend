The `jersey-server-1.9-repack.jar` and `asm-3.1-repack.jar` files
are repackaged in order to avoid a dependency clash between asm 3.1
and asm 4.0, the latter of which is used by the Pegdown markdown
parser. 

The repackaged Jars were created using instructions as given in
[this blog post](http://nyatekniken.blogspot.co.uk/2012/10/making-jersey-work-with-google-app.html).

The `jarjar` rules used were (in a file called `rules.txt`):

    rule org.objectweb.asm.**  org.objectweb.asm3.@1

The commands used were:

    java -jar jarjar-1.4.jar process rules.txt asm-3.1.jar asm-3.1-repack.jar
    java -jar jarjar-1.4.jar process rules.txt jersey-server-1.9.jar jersey-server-1.9-repack.jar

The output jar files were then stuck in this `test_lib` directory and the
SBT build file adjusted to put it on the classpath during test execution.