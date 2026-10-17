#!/usr/bin/env bash
set -euo pipefail

cd "$(git rev-parse --show-toplevel)"

git diff --check

sensitive_paths='(^|/)(local\.properties|gradle\.properties|keystore\.properties|google-services\.json|agconnect-services\.json|[^/]+\.(jks|keystore|p12))$|(^|/)(ratogramx_deploy_key)(\.pub)?$'
tracked_sensitive="$(git ls-files | grep -E "$sensitive_paths" || true)"
if [[ -n "$tracked_sensitive" ]]; then
  echo "Refusing to publish tracked sensitive files:" >&2
  echo "$tracked_sensitive" >&2
  exit 1
fi

for required_file in README.md LICENSE NOTICE.md CONTRIBUTING.md SECURITY.md local.properties.sample gradle.properties.sample; do
  if [[ ! -s "$required_file" ]]; then
    echo "Missing or empty public repository file: $required_file" >&2
    exit 1
  fi
done

if git ls-files | grep -E '(^|/)(app|build)/build/|(^|/)\.cxx/|\.apk$' >/dev/null; then
  echo "Generated build output is tracked by Git." >&2
  exit 1
fi

echo "Public repository hygiene checks passed."
