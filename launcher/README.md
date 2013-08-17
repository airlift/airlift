Launcher script for Java applications, compatible with
[LSB init scripts](http://refspecs.linuxbase.org/LSB_4.1.0/LSB-Core-generic/LSB-Core-generic/iniscrptact.html).

Assumes the following layout:

    /bin/<scripts>
    /etc/<config>
    /lib/*.jar
    /plugin/<plugins>
    /README.txt

See [launcher](src/main/scripts/bin/launcher) for details.

The [airbase](https://github.com/airlift/airbase) base POM can be used to
build a tarball that contains and is compatible with this launcher.
