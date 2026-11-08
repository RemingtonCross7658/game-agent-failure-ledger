#!/usr/bin/env sh
set -eu
BUILD_DIR="${TMPDIR:-/tmp}/game-agent-failure-ledger-classes"
mkdir -p "$BUILD_DIR"
find src/main/java src/test/java -name '*.java' -print > "$BUILD_DIR/sources.txt"
javac -d "$BUILD_DIR" @"$BUILD_DIR/sources.txt"
java -cp "$BUILD_DIR" dev.infrai.game.domain.ModerationQueueServiceTest
