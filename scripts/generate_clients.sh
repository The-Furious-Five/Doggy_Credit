#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
version=7.25.0
jar="${TMPDIR:-/tmp}/doggycredit-openapi-generator-$version.jar"
if [ ! -f "$jar" ]; then
  curl --fail --location --silent --show-error "https://repo.maven.apache.org/maven2/org/openapitools/openapi-generator-cli/$version/openapi-generator-cli-$version.jar" -o "$jar"
fi
java -jar "$jar" validate -i contracts/openapi/doggycredit-api.v1.yaml
java -jar "$jar" generate -i contracts/openapi/doggycredit-api.v1.yaml -g typescript-angular -c contracts/openapi/angular-generator.json -o web/src/app/api
