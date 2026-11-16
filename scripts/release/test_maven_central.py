import unittest

import base64
import contextlib
import io
import json
import tempfile
from pathlib import Path

from maven_central import (
    CentralError,
    already_published,
    deployment_ids_in_log,
    main,
    wait_until_live,
)

PORTAL_URL = "https://central.sonatype.com/api/v1/publisher/published"
POM_URL = "https://repo1.maven.org/maven2/com/formbricks/android/2.4.0/android-2.4.0.pom"
STATUS_URL = "https://central.sonatype.com/api/v1/publisher/status"
CREDENTIALS = ("user", "secret")


class FakeCentral:
    """Stands in for HTTP: maps a URL to (status, body) and records the headers it was sent."""

    def __init__(self, responses):
        self.responses = responses
        self.requests = []

    def __call__(self, url, headers, method="GET"):
        self.requests.append((method, url, headers))
        return self.responses.get(url.split("?")[0], (404, ""))


class AlreadyPublishedTest(unittest.TestCase):
    def test_true_when_the_repository_serves_the_pom(self):
        central = FakeCentral({POM_URL: (200, "<project/>")})
        self.assertTrue(already_published("2.4.0", central))

    def test_false_when_the_repository_does_not_have_it(self):
        self.assertFalse(already_published("2.4.0", FakeCentral({})))

    def test_an_unexpected_answer_from_the_repository_is_an_error_not_a_no(self):
        # Treating a 503 as "absent" would re-upload; treating it as "present" would skip a release.
        with self.assertRaises(CentralError):
            already_published("2.4.0", FakeCentral({POM_URL: (503, "")}))

    def test_true_when_the_portal_has_published_it_but_the_repository_has_not_synced_yet(self):
        # The gap after a successful upload: a re-run must not upload the same version again.
        central = FakeCentral({PORTAL_URL: (200, json.dumps({"published": True}))})
        self.assertTrue(already_published("2.4.0", central, credentials=("user", "secret")))

    def test_asks_the_portal_about_this_exact_version_with_the_publishing_token(self):
        central = FakeCentral({PORTAL_URL: (200, json.dumps({"published": False}))})
        self.assertFalse(already_published("2.4.0-beta.1", central, credentials=("user", "secret")))
        _, url, headers = central.requests[-1]
        self.assertEqual(
            url, PORTAL_URL + "?namespace=com.formbricks&name=android&version=2.4.0-beta.1"
        )
        token = base64.b64encode(b"user:secret").decode()
        self.assertEqual(headers, {"Authorization": f"Bearer {token}"})

    def test_a_portal_answer_without_a_published_flag_is_an_error(self):
        central = FakeCentral({PORTAL_URL: (401, json.dumps({"error": {"message": "Invalid auth"}}))})
        with self.assertRaisesRegex(CentralError, "401"):
            already_published("2.4.0", central, credentials=("user", "wrong"))


NOT_YET_PUBLISHED = (200, json.dumps({"published": False}))


def deployment(state):
    return (200, json.dumps({"deploymentId": "dep-1", "deploymentState": state}))


def portal_with(status_answer):
    """Not in the repository, not published per the Portal, and one recorded deployment."""
    return FakeCentral({PORTAL_URL: NOT_YET_PUBLISHED, STATUS_URL: status_answer})


class EarlierDeploymentTest(unittest.TestCase):
    """A re-run must not upload again while an earlier attempt's deployment is still going through."""

    def test_a_deployment_that_is_going_through_or_done_counts_as_published(self):
        for state in ["PENDING", "VALIDATING", "VALIDATED", "PUBLISHING", "PUBLISHED"]:
            with self.subTest(state=state):
                central = portal_with(deployment(state))
                self.assertTrue(
                    already_published("2.4.0", central, CREDENTIALS, deployment_ids=["dep-1"])
                )

    def test_a_failed_deployment_does_not_hold_the_version_so_it_is_uploaded_again(self):
        central = portal_with(deployment("FAILED"))
        self.assertFalse(already_published("2.4.0", central, CREDENTIALS, deployment_ids=["dep-1"]))

    def test_any_live_deployment_among_several_attempts_counts(self):
        answers_by_id = {"dep-1": deployment("FAILED"), "dep-2": deployment("PUBLISHING")}

        def central(url, headers, method="GET"):
            if url.startswith(STATUS_URL):
                return answers_by_id[url.split("id=")[1]]
            if url.startswith(PORTAL_URL):
                return NOT_YET_PUBLISHED
            return (404, "")

        self.assertTrue(
            already_published("2.4.0", central, CREDENTIALS, deployment_ids=["dep-1", "dep-2"])
        )

    def test_asks_the_portal_for_the_deployment_status_with_the_publishing_token(self):
        central = portal_with(deployment("PUBLISHING"))
        already_published("2.4.0", central, CREDENTIALS, deployment_ids=["dep-1"])
        method, url, headers = central.requests[-1]
        self.assertEqual((method, url), ("POST", STATUS_URL + "?id=dep-1"))
        token = base64.b64encode(b"user:secret").decode()
        self.assertEqual(headers, {"Authorization": f"Bearer {token}"})

    def test_an_unknown_state_is_an_error_not_a_no(self):
        for answer in [deployment("SOMETHING_NEW"), (500, ""), (200, "not json")]:
            with self.subTest(answer=answer), self.assertRaises(CentralError):
                already_published(
                    "2.4.0", portal_with(answer), CREDENTIALS, deployment_ids=["dep-1"]
                )

    def test_a_deployment_cannot_be_checked_without_credentials(self):
        with self.assertRaisesRegex(CentralError, "MAVEN_CENTRAL_USERNAME"):
            already_published("2.4.0", FakeCentral({}), None, deployment_ids=["dep-1"])


GRADLE_LOG = """> Task :android:publishMavenPublicationToMavenCentralRepository
Uploaded bundle to Central Portal as AUTOMATIC, deployment id: 2b6e4f0a-1c3d-4e5f-8a9b-0c1d2e3f4a5b
Deployment has been validated successfully
BUILD SUCCESSFUL in 1m 2s
"""


class DeploymentIdsInLogTest(unittest.TestCase):
    def test_reads_the_deployment_id_the_maven_publish_plugin_logs_after_the_upload(self):
        self.assertEqual(
            deployment_ids_in_log(GRADLE_LOG), ["2b6e4f0a-1c3d-4e5f-8a9b-0c1d2e3f4a5b"]
        )

    def test_a_build_that_never_uploaded_has_none(self):
        self.assertEqual(deployment_ids_in_log("BUILD FAILED in 3s\n"), [])


class FakeClock:
    def __init__(self):
        self.now = 0.0
        self.sleeps = []

    def time(self):
        return self.now

    def sleep(self, seconds):
        self.sleeps.append(seconds)
        self.now += seconds


def answers(*results):
    """A check that returns (or raises) each result in turn."""
    pending = list(results)

    def check():
        result = pending.pop(0)
        if isinstance(result, Exception):
            raise result
        return result

    return check


class WaitUntilLiveTest(unittest.TestCase):
    def test_returns_once_the_version_appears(self):
        clock = FakeClock()
        live = wait_until_live(answers(False, False, True), timeout=2700, interval=60,
                               clock=clock.time, sleep=clock.sleep, log=lambda _: None)
        self.assertTrue(live)
        self.assertEqual(clock.sleeps, [60, 60])

    def test_gives_up_at_the_timeout(self):
        clock = FakeClock()
        live = wait_until_live(lambda: False, timeout=2700, interval=60,
                               clock=clock.time, sleep=clock.sleep, log=lambda _: None)
        self.assertFalse(live)
        self.assertEqual(clock.now, 2700)

    def test_does_not_sleep_past_the_timeout(self):
        clock = FakeClock()
        wait_until_live(lambda: False, timeout=150, interval=60,
                        clock=clock.time, sleep=clock.sleep, log=lambda _: None)
        self.assertEqual(clock.sleeps, [60, 60, 30])

    def test_a_failed_check_is_retried_rather_than_failing_the_wait(self):
        clock = FakeClock()
        logged = []
        live = wait_until_live(answers(CentralError("HTTP 503"), True), timeout=2700, interval=60,
                               clock=clock.time, sleep=clock.sleep, log=logged.append)
        self.assertTrue(live)
        self.assertTrue(any("HTTP 503" in line for line in logged))


class CommandLineTest(unittest.TestCase):
    def run_main(self, argv, central, env=None, clock=None):
        clock = clock or FakeClock()
        out, err = io.StringIO(), io.StringIO()
        with contextlib.redirect_stdout(out), contextlib.redirect_stderr(err):
            code = main(argv, fetch=central, env=env or {}, clock=clock.time, sleep=clock.sleep)
        return code, out.getvalue(), err.getvalue()

    def test_exists_prints_true_for_a_published_version(self):
        code, out, _ = self.run_main(["exists", "2.4.0"], FakeCentral({POM_URL: (200, "")}))
        self.assertEqual((code, out), (0, "true\n"))

    def test_exists_prints_false_for_a_new_version(self):
        code, out, _ = self.run_main(["exists", "2.4.0"], FakeCentral({}))
        self.assertEqual((code, out), (0, "false\n"))

    def test_exists_asks_the_portal_with_the_publishing_credentials_from_the_environment(self):
        central = FakeCentral({PORTAL_URL: (200, json.dumps({"published": True}))})
        env = {"MAVEN_CENTRAL_USERNAME": "user", "MAVEN_CENTRAL_PASSWORD": "secret"}
        code, out, _ = self.run_main(["exists", "2.4.0"], central, env=env)
        self.assertEqual((code, out), (0, "true\n"))

    def test_exists_fails_instead_of_guessing(self):
        code, out, err = self.run_main(["exists", "2.4.0"], FakeCentral({POM_URL: (500, "")}))
        self.assertEqual((code, out), (1, ""))
        self.assertIn("HTTP 500", err)

    def test_exists_counts_a_deployment_an_earlier_attempt_recorded(self):
        central = portal_with(deployment("VALIDATING"))
        env = {"MAVEN_CENTRAL_USERNAME": "user", "MAVEN_CENTRAL_PASSWORD": "secret"}
        with tempfile.TemporaryDirectory() as tmp:
            ids = Path(tmp, "deployment-ids.txt")
            ids.write_text("dep-1\n\n")
            code, out, _ = self.run_main(
                ["exists", "2.4.0", "--deployment-ids-file", str(ids)], central, env=env
            )
        self.assertEqual((code, out), (0, "true\n"))
        self.assertIn(("POST", STATUS_URL + "?id=dep-1"), [r[:2] for r in central.requests])

    def test_exists_without_a_recorded_deployment_file_only_asks_central(self):
        code, out, _ = self.run_main(
            ["exists", "2.4.0", "--deployment-ids-file", "/nonexistent/deployment-ids.txt"],
            FakeCentral({}),
        )
        self.assertEqual((code, out), (0, "false\n"))

    def test_deployment_ids_prints_one_id_per_line_from_the_gradle_log(self):
        with tempfile.TemporaryDirectory() as tmp:
            log = Path(tmp, "publish.log")
            log.write_text(GRADLE_LOG)
            code, out, _ = self.run_main(["deployment-ids", str(log)], FakeCentral({}))
        self.assertEqual((code, out), (0, "2b6e4f0a-1c3d-4e5f-8a9b-0c1d2e3f4a5b\n"))

    def test_wait_succeeds_once_apps_can_resolve_the_version(self):
        code, _, _ = self.run_main(["wait", "2.4.0"], FakeCentral({POM_URL: (200, "")}))
        self.assertEqual(code, 0)

    def test_wait_fails_after_the_timeout(self):
        clock = FakeClock()
        code, _, err = self.run_main(
            ["wait", "2.4.0", "--timeout-minutes", "45"], FakeCentral({}), clock=clock
        )
        self.assertEqual(code, 1)
        self.assertEqual(clock.now, 45 * 60)
        self.assertIn("2.4.0", err)


if __name__ == "__main__":
    unittest.main()
