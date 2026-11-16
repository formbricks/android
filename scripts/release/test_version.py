import subprocess
import sys
import unittest
from pathlib import Path

from version import version_from_tag


class VersionFromTagTest(unittest.TestCase):
    def test_a_bare_stable_tag_is_the_version(self):
        self.assertEqual(version_from_tag("2.4.0", prerelease=False), "2.4.0")

    def test_a_leading_v_is_rejected_because_release_tags_are_plain_semver(self):
        for tag in ["v2.4.0", "V2.4.0", "v2.4.0-rc.1"]:
            with self.subTest(tag=tag), self.assertRaisesRegex(ValueError, "without a leading v"):
                version_from_tag(tag, prerelease=tag.endswith("rc.1"))

    def test_a_prerelease_keeps_its_suffix(self):
        self.assertEqual(version_from_tag("2.4.0-beta.1", prerelease=True), "2.4.0-beta.1")

    def test_tags_that_are_not_semver_are_rejected(self):
        for tag in [
            "2.4",            # missing patch
            "2.4.0.1",        # four parts
            "02.4.0",         # leading zero
            "2.4.0-",         # empty prerelease
            "2.4.0-beta..1",  # empty identifier
            "2.4.0-01",       # numeric identifier with a leading zero
            "release-2.4.0",
            "vv2.4.0",
            "2.4.0 ",
            "",
        ]:
            with self.subTest(tag=tag), self.assertRaises(ValueError):
                version_from_tag(tag, prerelease=False)

    def test_build_metadata_is_rejected_because_maven_would_publish_it_as_part_of_the_version(self):
        with self.assertRaises(ValueError):
            version_from_tag("2.4.0+build.5", prerelease=False)

    def test_a_release_marked_prerelease_needs_a_suffixed_tag(self):
        # Otherwise a release ticked "pre-release" on GitHub would ship as a stable version.
        with self.assertRaisesRegex(ValueError, "pre-release"):
            version_from_tag("2.4.0", prerelease=True)

    def test_a_suffixed_tag_must_be_marked_prerelease(self):
        with self.assertRaisesRegex(ValueError, "pre-release"):
            version_from_tag("2.4.0-rc.1", prerelease=False)


class CommandLineTest(unittest.TestCase):
    SCRIPT = Path(__file__).with_name("version.py")

    def run_script(self, *args):
        return subprocess.run(
            [sys.executable, str(self.SCRIPT), *args], capture_output=True, text=True
        )

    def test_prints_the_version_for_the_release_workflow_to_pass_on(self):
        result = self.run_script("--tag", "2.4.0-beta.1", "--prerelease", "true")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(result.stdout, "2.4.0-beta.1\n")

    def test_a_bad_tag_fails_and_prints_no_version(self):
        result = self.run_script("--tag", "2.4", "--prerelease", "false")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("not a semver version", result.stderr)
        self.assertEqual(result.stdout, "")

    def test_the_build_script_is_no_longer_written(self):
        # The workflow hands the version to Gradle as a property; nothing edits the checkout.
        result = self.run_script("--tag", "2.4.0", "--prerelease", "false", "--gradle-file", "x")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("unrecognized arguments", result.stderr)


if __name__ == "__main__":
    unittest.main()
