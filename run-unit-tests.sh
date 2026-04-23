#!/bin/bash

# Script to run unit tests without IntelliJ Platform
# This bypasses the JBR crash issue

set -e

echo "=== Running Unit Tests ==="
echo ""

# Compile test sources
echo "Compiling test sources..."
./gradlew compileTestKotlin --quiet

# Find and run tests with kotlinc directly
TEST_DIR="build/classes/kotlin/test"

if [ ! -d "$TEST_DIR" ]; then
    echo "Test classes not found. Run './gradlew compileTestKotlin' first."
    exit 1
fi

echo ""
echo "Running PathNormalizerTest..."
java -cp "$TEST_DIR:$(./gradlew -q dependencies testRuntimeClasspath --format=json | grep -o '"files":\[[^]]*\]' | tr -d '["files:]' | tr ',' ':')" \
    org.junit.platform.console.ConsoleLauncher \
    --select-class azuredevops.util.PathNormalizerTest \
    --details=tree

echo ""
echo "Running ModelExtensionsTest..."
java -cp "$TEST_DIR:$(./gradlew -q dependencies testRuntimeClasspath --format=json | grep -o '"files":\[[^]]*\]' | tr -d '["files:]' | tr ',' ':')" \
    org.junit.platform.console.ConsoleLauncher \
    --select-class azuredevops.model.ModelExtensionsTest \
    --details=tree

echo ""
echo "Running DefaultPullRequestApiTest..."
java -cp "$TEST_DIR:$(./gradlew -q dependencies testRuntimeClasspath --format=json | grep -o '"files":\[[^]]*\]' | tr -d '["files:]' | tr ',' ':')" \
    org.junit.platform.console.ConsoleLauncher \
    --select-class azuredevops.api.impl.DefaultPullRequestApiTest \
    --details=tree

echo ""
echo "=== All Unit Tests Completed ==="
