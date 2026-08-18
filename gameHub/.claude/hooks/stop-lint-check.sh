#!/usr/bin/env bash
# Claude Code Stop hook: after Claude finishes a turn, format + lint any
# .java files under gameHub/ that changed (staged, unstaged, or new/untracked).
# Complements .githooks/pre-commit, which runs the same checks at commit time.
#
# Contract: on violations, prints {"decision":"block","reason":"..."} to stdout
# so Claude Code keeps the turn open and feeds the Checkstyle output back to
# Claude instead of letting the turn end silently.

set -euo pipefail

REPO_ROOT="$(git rev-parse --show-toplevel)"
cd "$REPO_ROOT"

CHANGED_JAVA_FILES="$(git status --porcelain -- 'gameHub/*.java')"

if [ -z "$CHANGED_JAVA_FILES" ]; then
    exit 0
fi

cd "$REPO_ROOT/gameHub"

# Auto-fix formatting. If this itself fails (not a formatting issue but a
# real build/plugin error), let it surface as a normal non-blocking error.
./mvnw -q spotless:apply

CHECKSTYLE_OUTPUT="$(./mvnw checkstyle:check 2>&1)" && exit 0

jq -n --arg reason "Checkstyle found violations in gameHub/ after this turn's edits. Fix them before finishing:

$CHECKSTYLE_OUTPUT" '{decision: "block", reason: $reason}'

exit 0
