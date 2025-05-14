#!/bin/bash

# Set Java 17 in PATH
export JAVA_HOME="/opt/homebrew/opt/openjdk@17"
export PATH="$JAVA_HOME/bin:$PATH"

# First check if a device is connected or emulator is running
DEVICE_COUNT=$(adb devices | grep -v "List" | grep -v "^$" | wc -l | xargs)

if [ "$DEVICE_COUNT" -eq 0 ]; then
    echo "No devices/emulators found. Please connect a device or start an emulator."
    exit 1
fi

echo "Found $DEVICE_COUNT device(s). Running instrumented tests with coverage..."

# Run the instrumented tests with coverage enabled
./gradlew :android:connectedDebugAndroidTest

# Create a tools directory if it doesn't exist
mkdir -p tools

# Download JaCoCo CLI jar if not already present
if [ ! -f tools/jacoco-cli.jar ]; then
    echo "Downloading JaCoCo CLI..."
    curl -L "https://repo1.maven.org/maven2/org/jacoco/org.jacoco.cli/0.8.10/org.jacoco.cli-0.8.10-nodeps.jar" -o tools/jacoco-cli.jar
fi

# Create report directory
mkdir -p android/build/reports/jacoco-android/html

# Check for coverage data (EC files)
COVERAGE_DATA=$(find android/build -name "*.ec" | head -n 1)

if [ -z "$COVERAGE_DATA" ]; then
    echo "No coverage data (*.ec files) found. Cannot generate report."
    exit 1
fi

echo "Found coverage data: $COVERAGE_DATA"

# Generate HTML report
java -jar tools/jacoco-cli.jar report "$COVERAGE_DATA" \
    --classfiles android/build/tmp/kotlin-classes/debug \
    --sourcefiles android/src/main/java \
    --html android/build/reports/jacoco-android/html

echo "JaCoCo instrumented test coverage report generated at: android/build/reports/jacoco-android/html/index.html" 