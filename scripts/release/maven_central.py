#!/usr/bin/env python3
"""Ask Maven Central whether a version of com.formbricks:android is out.

  maven_central.py exists <version>   prints true or false; publishing skips a version that exists
  maven_central.py wait <version>     polls until apps can resolve the version, fails at the timeout

`exists` checks the repository apps resolve from (repo1.maven.org) and, when
MAVEN_CENTRAL_USERNAME and MAVEN_CENTRAL_PASSWORD are set, the Central Portal, which reports a
version as published before the repository has synced it. Any answer that is not a clear yes or
no exits 1, because guessing either way is wrong: "no" re-uploads, "yes" skips a release.

`wait` polls the repository rather than search.maven.org: the search index does not list this
artifact at all, so polling it would always time out.
"""
import argparse
import base64
import json
import os
import sys
import time
import urllib.error
import urllib.request
from urllib.parse import urlencode

GROUP = "com.formbricks"
ARTIFACT = "android"
REPOSITORY = "https://repo1.maven.org/maven2"
PORTAL = "https://central.sonatype.com/api/v1/publisher/published"


class CentralError(Exception):
    """Maven Central gave an answer that is neither a clear yes nor a clear no."""


def pom_url(version):
    return f"{REPOSITORY}/{GROUP.replace('.', '/')}/{ARTIFACT}/{version}/{ARTIFACT}-{version}.pom"


def in_repository(version, fetch):
    """Whether apps can resolve the version: the POM is served by the Central repository."""
    url = pom_url(version)
    status, _ = fetch(url, {})
    if status == 200:
        return True
    if status == 404:
        return False
    raise CentralError(f"{url} answered HTTP {status}")


def in_portal(version, fetch, credentials):
    """Whether the Central Portal has published the version, which it knows before the repository."""
    query = urlencode({"namespace": GROUP, "name": ARTIFACT, "version": version})
    token = base64.b64encode(":".join(credentials).encode()).decode()
    status, body = fetch(f"{PORTAL}?{query}", {"Authorization": f"Bearer {token}"})
    try:
        published = json.loads(body)["published"] if status == 200 else None
    except (ValueError, KeyError, TypeError):
        published = None
    if not isinstance(published, bool):
        raise CentralError(f"the Central Portal answered HTTP {status}: {body[:200]}")
    return published


def already_published(version, fetch, credentials=None):
    if in_repository(version, fetch):
        return True
    return credentials is not None and in_portal(version, fetch, credentials)


def wait_until_live(check, timeout, interval, clock, sleep, log):
    """Run `check` every `interval` seconds until it is true or `timeout` seconds have passed.

    A check that raises CentralError counts as "not yet": Central has brief outages, and the
    deadline is what decides failure.
    """
    deadline = clock() + timeout
    while True:
        try:
            if check():
                return True
            log("not live yet")
        except CentralError as error:
            log(f"check failed, will retry: {error}")
        remaining = deadline - clock()
        if remaining <= 0:
            return False
        sleep(min(interval, remaining))


def http_fetch(url, headers):
    request = urllib.request.Request(url, headers={"User-Agent": "formbricks-android-release", **headers})
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            return response.status, response.read().decode("utf-8", "replace")
    except urllib.error.HTTPError as error:
        return error.code, error.read().decode("utf-8", "replace")
    except (urllib.error.URLError, TimeoutError) as error:
        raise CentralError(f"{url} could not be reached: {error}") from error


def main(argv=None, fetch=http_fetch, env=os.environ, clock=time.monotonic, sleep=time.sleep):
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    commands = parser.add_subparsers(dest="command", required=True)
    exists = commands.add_parser("exists")
    exists.add_argument("version")
    wait = commands.add_parser("wait")
    wait.add_argument("version")
    wait.add_argument("--timeout-minutes", type=float, default=45)
    wait.add_argument("--interval-seconds", type=float, default=60)
    args = parser.parse_args(argv)

    if args.command == "exists":
        username = env.get("MAVEN_CENTRAL_USERNAME")
        password = env.get("MAVEN_CENTRAL_PASSWORD")
        credentials = (username, password) if username and password else None
        try:
            print("true" if already_published(args.version, fetch, credentials) else "false")
        except CentralError as error:
            print(f"error: {error}", file=sys.stderr)
            return 1
        return 0

    def log(message):
        print(f"{GROUP}:{ARTIFACT}:{args.version}: {message}", file=sys.stderr, flush=True)

    live = wait_until_live(
        lambda: in_repository(args.version, fetch),
        timeout=args.timeout_minutes * 60,
        interval=args.interval_seconds,
        clock=clock,
        sleep=sleep,
        log=log,
    )
    if live:
        print(f"{pom_url(args.version)} is live")
        return 0
    log(f"still not on Maven Central after {args.timeout_minutes:g} minutes")
    return 1


if __name__ == "__main__":
    sys.exit(main())
