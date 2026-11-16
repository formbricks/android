#!/usr/bin/env python3
"""Turn a release tag into the SDK version.

main keeps the placeholder `0.0.0-dev` in android/build.gradle.kts. The release workflow runs this
once, then hands the version to Gradle as the `releaseVersion` property, so the real version never
lands on main and no bump PR is needed.

Usage: version.py --tag <tag> --prerelease <true|false>
Prints the version on success; prints nothing and exits non-zero otherwise.
"""
import argparse
import re
import sys

# SemVer 2.0.0 (https://semver.org) without build metadata: Maven has no notion of build
# metadata, so a "+build" part would ship as part of the version string.
_NUMBER = r"0|[1-9]\d*"
_IDENTIFIER = rf"(?:{_NUMBER}|\d*[a-zA-Z-][0-9a-zA-Z-]*)"
_SEMVER = re.compile(
    rf"(?P<core>(?:{_NUMBER})\.(?:{_NUMBER})\.(?:{_NUMBER}))"
    rf"(?:-(?P<prerelease>{_IDENTIFIER}(?:\.{_IDENTIFIER})*))?"
)


def version_from_tag(tag, prerelease):
    """The tag is the version itself: plain semver, so `v2.4.0` is rejected like in every SDK."""
    if tag[:1] in ("v", "V"):
        raise ValueError(f"tag {tag!r} must be the bare version without a leading v, such as 2.4.0")
    match = _SEMVER.fullmatch(tag)
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
    return tag


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--tag", required=True)
    parser.add_argument("--prerelease", required=True, choices=["true", "false"])
    args = parser.parse_args(argv)
    try:
        version = version_from_tag(args.tag, prerelease=args.prerelease == "true")
    except ValueError as error:
        print(f"error: {error}", file=sys.stderr)
        return 1
    print(version)
    return 0


if __name__ == "__main__":
    sys.exit(main())
