#!/bin/sh
# Gradle start up script for POSIX systems
SCRIPT_DIR=$(dirname "$0")
exec "$SCRIPT_DIR/gradle/wrapper/gradlew.sh" "$@" 2>/dev/null || \
  exec gradle "$@"
