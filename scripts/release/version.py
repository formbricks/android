#!/usr/bin/env python3
"""Turn a release tag into the SDK version and write it into the build script.

main keeps `version = "0.0.0-dev"` in android/build.gradle.kts. The release workflow runs this
on its own checkout only, so the real version never lands on main and no bump PR is needed.

Usage: version.py --tag <tag> --prerelease <true|false> --gradle-file <build.gradle.kts>
Prints the version on success; exits non-zero without touching the file otherwise.
"""
import argparse
import re
import sys
from pathlib import Path

# SemVer 2.0.0 (https://semver.org) without build metadata: Maven has no notion of build
# metadata, so a "+build" part would ship as part of the version string.
_NUMBER = r"0|[1-9]\d*"
_IDENTIFIER = rf"(?:{_NUMBER}|\d*[a-zA-Z-][0-9a-zA-Z-]*)"
_SEMVER = re.compile(
    rf"(?P<core>(?:{_NUMBER})\.(?:{_NUMBER})\.(?:{_NUMBER}))"
    rf"(?:-(?P<prerelease>{_IDENTIFIER}(?:\.{_IDENTIFIER})*))?"
)


def version_from_tag(tag, prerelease):
    version = tag[1:] if tag.startswith("v") else tag
    match = _SEMVER.fullmatch(version)
    if not match:
        raise ValueError(f"tag {tag!r} is not a semver version such as 2.4.0 or 2.4.0-beta.1")
    has_suffix = match.group("prerelease") is not None
    if prerelease and not has_suffix:
        raise ValueError(
            f"the release is marked pre-release but {tag!r} has no suffix such as -beta.1, "
            "so it would ship as a stable version"
        )
    if has_suffix and not prerelease:
        raise ValueError(
            f"{tag!r} has a pre-release suffix, so tick \"Set as a pre-release\" on the release"
        )
    return version


PLACEHOLDER = "0.0.0-dev"
_PLACEHOLDER_LINE = re.compile(rf'^version = "{re.escape(PLACEHOLDER)}"$', re.MULTILINE)


def write_version(gradle_text, version):
    """Return the build script with its placeholder version replaced by `version`."""
    found = len(_PLACEHOLDER_LINE.findall(gradle_text))
    if found != 1:
        raise ValueError(
            f'expected exactly one `version = "{PLACEHOLDER}"` line in the build script, '
            f"found {found}; main must keep the placeholder"
        )
    return _PLACEHOLDER_LINE.sub(f'version = "{version}"', gradle_text)


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--tag", required=True)
    parser.add_argument("--prerelease", required=True, choices=["true", "false"])
    parser.add_argument("--gradle-file", required=True, type=Path)
    args = parser.parse_args(argv)
    try:
        version = version_from_tag(args.tag, prerelease=args.prerelease == "true")
        updated = write_version(args.gradle_file.read_text(), version)
    except ValueError as error:
        print(f"error: {error}", file=sys.stderr)
        return 1
    args.gradle_file.write_text(updated)
    print(version)
    return 0


if __name__ == "__main__":
    sys.exit(main())
