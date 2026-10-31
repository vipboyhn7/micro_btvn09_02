#!/usr/bin/env bash
set -euo pipefail
project_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
repo_dir="$project_dir/.local/config-repo"
mkdir -p "$repo_dir"
if [[ ! -d "$repo_dir/.git" ]]; then
  git init -b main "$repo_dir"
fi
# Only this demo file is staged; existing local configuration is never overwritten.
if [[ ! -f "$repo_dir/pharmacy-service.properties" ]]; then
  cp "$project_dir/config-repo/pharmacy-service.properties" "$repo_dir/"
fi
# Upgrade repositories created by the previous exercise without changing an existing VAT rate.
if ! grep -Eq '^[[:space:]]*pharmacy[.]vat-rate[[:space:]]*[:=]' "$repo_dir/pharmacy-service.properties"; then
  printf '\n' >> "$repo_dir/pharmacy-service.properties"
  grep '^pharmacy.vat-rate=' "$project_dir/config-repo/pharmacy-service.properties" >> "$repo_dir/pharmacy-service.properties"
fi
git -C "$repo_dir" add pharmacy-service.properties
if ! git -C "$repo_dir" diff --cached --quiet; then
  git -C "$repo_dir" -c user.name='Config Demo' -c user.email='config-demo@example.invalid' \
    -c commit.gpgsign=false commit -m 'Configure pharmacy service'
fi
echo "Config Git repository: $repo_dir"
