#!/bin/sh
set -e -x -u

# FIXME: use docker image with JDK 1.6 and 1.7 configured.
echo "kotlin.build.isObsoleteJdkOverrideEnabled=true" > local.properties

./gradlew tasks
