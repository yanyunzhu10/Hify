#!/bin/sh
# Maven Wrapper script — downloads Maven 3.9.x if not cached, then delegates.

set -e

MAVEN_WRAPPER_JAR=".mvn/wrapper/maven-wrapper.jar"
MAVEN_WRAPPER_PROPERTIES=".mvn/wrapper/maven-wrapper.properties"

# Resolve JAVA_HOME
if [ -n "$JAVA_HOME" ]; then
    JAVA_EXEC="$JAVA_HOME/bin/java"
else
    JAVA_EXEC="$(which java)"
fi
[ -x "$JAVA_EXEC" ] || { echo "ERROR: java not found. Set JAVA_HOME or add java to PATH." >&2; exit 1; }

# Read distribution URL from properties
DISTRIBUTION_URL=$(grep "^distributionUrl=" "$MAVEN_WRAPPER_PROPERTIES" | cut -d= -f2-)
MAVEN_USER_HOME="${MAVEN_USER_HOME:-$HOME/.m2/wrapper}"
MAVEN_VERSION=$(echo "$DISTRIBUTION_URL" | grep -oE 'apache-maven-[0-9]+\.[0-9]+\.[0-9]+' | head -1)
MAVEN_HOME="$MAVEN_USER_HOME/dists/$MAVEN_VERSION"

# Download Maven if not cached
if [ ! -f "$MAVEN_HOME/bin/mvn" ]; then
    echo ">>> Downloading $MAVEN_VERSION to $MAVEN_HOME ..."
    mkdir -p "$MAVEN_HOME"
    TMPFILE=$(mktemp /tmp/maven-wrapper-XXXXXX.zip)
    curl -fsSL "$DISTRIBUTION_URL" -o "$TMPFILE" \
        || { echo "ERROR: failed to download Maven from $DISTRIBUTION_URL" >&2; rm -f "$TMPFILE"; exit 1; }
    unzip -q "$TMPFILE" -d "$MAVEN_USER_HOME/dists/"
    rm -f "$TMPFILE"
    # Aliyun zip extracts as apache-maven-x.y.z/
    EXTRACTED=$(ls -d "$MAVEN_USER_HOME/dists/apache-maven-"*/  2>/dev/null | head -1)
    [ -d "$EXTRACTED" ] && mv "$EXTRACTED" "$MAVEN_HOME" 2>/dev/null || true
    echo ">>> Maven downloaded: $MAVEN_HOME"
fi

exec "$MAVEN_HOME/bin/mvn" "$@"
