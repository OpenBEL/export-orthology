Export Orthology
================

BEL Framework command-line tool to export an orthologized KAM to XGMML or KAM format.

Building
--------

This command is built in Java |trade| using `Apache Maven`_.

To build Whistle with `Apache Maven`_ type::

  mvn package assembly:single

The export orthology zip file is then located at::

  target/export-orthology-0.1.0-distribution.zip

Running
-------

This command ships with a default BEL Framework configuration that should work for most use cases.  If you want to reuse an existing installation of the BEL Framework then set the appropriate environment variable.

Linux or OS X::

  export BELFRAMEWORK_HOME=/path/to/bel/framework

Windows::

  set BELFRAMEWORK_HOME=c:\path\to\bel\framework

To run this command extract the distribution and run:

Linux or OS X::

  ./export-orthology.sh --help

Windows::

  export-orthology.cmd --help
