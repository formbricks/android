#!/usr/bin/env python3
"""Ask Maven Central whether a version of com.formbricks:android is out.

  maven_central.py exists <version> [--deployment-ids-file <file>]
      prints true or false; publishing skips a version that exists
  maven_central.py deployment-ids <gradle-log>
      prints the Portal deployment ids the upload logged, one per line
  maven_central.py wait <version>
      polls until apps can resolve the version, fails at the timeout

`exists` checks the repository apps resolve from (repo1.maven.org) and, when
MAVEN_CENTRAL_USERNAME and MAVEN_CENTRAL_PASSWORD are set, the Central Portal, which reports a
version as published before the repository has synced it. Neither knows about an upload that is
still being validated or published, so the release workflow also records each deployment id an
attempt uploads, and `exists` counts any of them that the Portal has not failed. The Portal has no
endpoint to look deployments up by version, so the recorded id is the only way to find one.
Any answer that is not a clear yes or no exits 1, because guessing either way is wrong: "no"
uploads twice, "yes" skips a release.

`wait` polls the repository rather than search.maven.org: the search index does not list this
artifact at all, so polling it would always time out.
"""
import argparse
import base64
import json
import os
import re
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path
from urllib.parse import urlencode

GROUP = "com.formbricks"
ARTIFACT = "android"
REPOSITORY = "https://repo1.maven.org/maven2"
PORTAL = "https://central.sonatype.com/api/v1/publisher/published"
PORTAL_STATUS = "https://central.sonatype.com/api/v1/publisher/status"

# Deployment states (https://central.sonatype.org/publish/publish-portal-api/). Every state but
# FAILED means the upload is through or on its way, so uploading again would upload twice.
LIVE_STATES = {"PENDING", "VALIDATING", "VALIDATED", "PUBLISHING", "PUBLISHED"}
FAILED_STATE = "FAILED"

# Logged at lifecycle level by com.vanniktech.maven.publish after each upload.
_DEPLOYMENT_ID = re.compile(r"Uploaded bundle to Central Portal as \w+, deployment id: (\S+)")


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


def _authorization(credentials):
    token = base64.b64encode(":".join(credentials).encode()).decode()
    return {"Authorization": f"Bearer {token}"}


def in_portal(version, fetch, credentials):
    """Whether the Central Portal has published the version, which it knows before the repository."""
    query = urlencode({"namespace": GROUP, "name": ARTIFACT, "version": version})
    status, body = fetch(f"{PORTAL}?{query}", _authorization(credentials))
    try:
        published = json.loads(body)["published"] if status == 200 else None
    except (ValueError, KeyError, TypeError):
        published = None
    if not isinstance(published, bool):
        raise CentralError(f"the Central Portal answered HTTP {status}: {body[:200]}")
    return published


def deployment_live(deployment_id, fetch, credentials):
    """Whether an uploaded deployment is through or still on its way, rather than FAILED."""
    query = urlencode({"id": deployment_id})
    status, body = fetch(f"{PORTAL_STATUS}?{query}", _authorization(credentials), method="POST")
    try:
        state = json.loads(body)["deploymentState"] if status == 200 else None
    except (ValueError, KeyError, TypeError):
        state = None
    if state in LIVE_STATES:
        return True
    if state == FAILED_STATE:
        return False
    raise CentralError(
        f"the Central Portal answered HTTP {status} for deployment {deployment_id}: {body[:200]}"
    )


def already_published(version, fetch, credentials=None, deployment_ids=()):
    if in_repository(version, fetch):
        return True
    if deployment_ids and credentials is None:
        raise CentralError(
            "checking a recorded deployment needs MAVEN_CENTRAL_USERNAME and MAVEN_CENTRAL_PASSWORD"
        )
    if credentials is None:
        return False
    if in_portal(version, fetch, credentials):
        return True
    return any(deployment_live(id_, fetch, credentials) for id_ in deployment_ids)


def deployment_ids_in_log(log):
    """The deployment ids the Maven Central plugin logged while uploading."""
    return _DEPLOYMENT_ID.findall(log)


def read_deployment_ids(path):
    """The ids recorded by earlier attempts, one per line; a missing file means there are none."""
    try:
        text = path.read_text()
    except FileNotFoundError:
        return []
    return [line.strip() for line in text.splitlines() if line.strip()]


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


def http_fetch(url, headers, method="GET"):
    request = urllib.request.Request(
        url, headers={"User-Agent": "formbricks-android-release", **headers}, method=method
    )
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
    exists.add_argument("--deployment-ids-file", type=Path)
    ids = commands.add_parser("deployment-ids")
    ids.add_argument("gradle_log", type=Path)
    wait = commands.add_parser("wait")
    wait.add_argument("version")
    wait.add_argument("--timeout-minutes", type=float, default=45)
    wait.add_argument("--interval-seconds", type=float, default=60)
    args = parser.parse_args(argv)

    if args.command == "exists":
        username = env.get("MAVEN_CENTRAL_USERNAME")
        password = env.get("MAVEN_CENTRAL_PASSWORD")
        credentials = (username, password) if username and password else None
        ids_file = args.deployment_ids_file
        deployment_ids = read_deployment_ids(ids_file) if ids_file else []
        try:
            present = already_published(args.version, fetch, credentials, deployment_ids)
            print("true" if present else "false")
        except CentralError as error:
            print(f"error: {error}", file=sys.stderr)
            return 1
        return 0

    if args.command == "deployment-ids":
        for deployment_id in deployment_ids_in_log(args.gradle_log.read_text(errors="replace")):
            print(deployment_id)
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
