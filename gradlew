#!/bin/sh

# Attempt to use existing gradle in PATH
if command -v gradle >/dev/null 2>&1; then
    exec gradle "$@"
fi

# Fallback wrapper
APP_BASE_NAME=`basename "$0"`
APP_HOME="`cd "\`dirname "$0"\`" >/dev/null 2>&1 && pwd`"
CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar

if [ -f "$CLASSPATH" ]; then
    exec java -jar "$CLASSPATH" "$@"
else
    echo "gradle command not found in PATH and wrapper jar missing. Please run with gradle directly." >&2
    exit 1
fi
