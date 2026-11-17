#!/usr/bin/env bash
# package-check: build the SDK exactly as Maven Central receives it, then install it into a
# scratch consumer app. Run from anywhere: scripts/package-check.sh
#
#   1. `publishToMavenLocal` builds the AAR, POM, Gradle module file, sources and javadoc jars
#      and signs them, the same tasks the release runs before uploading.
#   2. check-kotlin-baseline.py reads the published POM and AAR (see that script for why).
#   3. scripts/package-check/consumer resolves com.formbricks:android from mavenLocal only and
#      builds a minified release APK against it.
#
# Signing needs a key. Without ORG_GRADLE_PROJECT_signingInMemoryKey in the environment this
# script makes a throwaway one, so the artifacts are signed but nothing is trusted.
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
cd "$root"

if [[ -z "${ORG_GRADLE_PROJECT_signingInMemoryKey:-}" ]]; then
  gnupg_home="$(mktemp -d)"
  trap 'rm -rf "$gnupg_home"' EXIT
  export GNUPGHOME="$gnupg_home"
  gpg --batch --quiet --passphrase '' --quick-gen-key 'package-check <package-check@invalid>' rsa3072 sign never
  ORG_GRADLE_PROJECT_signingInMemoryKey="$(gpg --batch --armor --export-secret-keys)"
  export ORG_GRADLE_PROJECT_signingInMemoryKey
  export ORG_GRADLE_PROJECT_signingInMemoryKeyPassword=''
fi

version="$(./gradlew --quiet :android:properties --property version | sed -n 's/^version: //p')"
if [[ -z "$version" ]]; then
  echo "Could not read the SDK version from Gradle." >&2
  exit 1
fi
echo "package-check: com.formbricks:android:$version"

./gradlew :android:publishToMavenLocal --no-configuration-cache

published="${HOME}/.m2/repository/com/formbricks/android/$version"
python3 scripts/check-kotlin-baseline.py "$published/android-$version.pom" "$published/android-$version.aar"

./gradlew -p scripts/package-check/consumer assembleRelease -PsdkVersion="$version"
