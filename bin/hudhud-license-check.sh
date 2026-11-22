#!/usr/bin/env bash
# Added by HudHud Maps, 2026-11-21 (AGPL). Fails when our patch breaks the license rules of RFC lakehouse-access/33:
#  1. nothing under enterprise/, premium_features/ or the license files changes;
#  2. no paid-feature gate is added, removed or edited (`:feature`, premium-features, defenterprise);
#  3. every changed upstream file carries a "Modified by HudHud Maps" notice (AGPL section 5a);
#  4. every new file carries an "Added by HudHud Maps" notice;
#  5. with a jar given, it contains no metabase_enterprise code (built with MB_EDITION=oss).
# Usage: bin/hudhud-license-check.sh [UPSTREAM_REF] [path/to/metabase.jar]
set -euo pipefail
base="${1:-v0.64.8}"
jar="${2:-}"
fail=0
err() { echo "LICENSE CHECK FAILED: $*" >&2; fail=1; }

changed=$(git diff --name-only "$base" -- . ; git ls-files --others --exclude-standard)
added=$(git diff --name-only --diff-filter=A "$base" -- . ; git ls-files --others --exclude-standard)
modified=$(git diff --name-only --diff-filter=M "$base" -- .)

while IFS= read -r f; do
  [ -z "$f" ] && continue
  case "$f" in
    enterprise/*|*/premium_features/*|LICENSE*) err "$f is off limits";;
  esac
done <<< "$changed"

if git diff "$base" -- . ':!bin/hudhud-license-check.sh' | grep -E '^[+-][^+-]' | grep -nE ':feature[[:space:]]|premium-features|defenterprise|define-premium-feature' ; then
  err "the diff touches a paid-feature gate (lines above)"
fi

while IFS= read -r f; do
  [ -z "$f" ] && continue
  case "$f" in .clj-kondo/*) continue;; esac
  head -3 "$f" | grep -q "Modified by HudHud Maps" || err "$f is modified without a 'Modified by HudHud Maps' notice"
done <<< "$modified"

while IFS= read -r f; do
  [ -z "$f" ] && continue
  head -3 "$f" | grep -q "HudHud Maps" || err "$f is new without an 'Added by HudHud Maps' notice"
done <<< "$added"

if [ -n "$jar" ]; then
  if unzip -Z1 "$jar" | grep -m1 -E '^metabase_enterprise/'; then
    err "$jar contains metabase_enterprise code; build with MB_EDITION=oss"
  fi
fi

[ "$fail" = 0 ] && echo "license check passed ($(echo "$changed" | grep -c . ) files differ from $base)"
exit "$fail"
