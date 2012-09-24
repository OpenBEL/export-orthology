@echo off

if not defined JAVA_OPTS (
    set JAVA_OPTS=-Xmx1024m -Dderby.stream.error.field=org.openbel.framework.common.enums.DatabaseType.NULL_OUTPUT_STREAM
) else (
    set JAVA_OPTS=%JAVA_OPTS% -Dderby.stream.error.field=org.openbel.framework.common.enums.DatabaseType.NULL_OUTPUT_STREAM
)

set CMD_HOME=%~dp0
java %JAVA_OPTS% -jar %CMD_HOME%\export-orthology-${project.version}.jar %*
