import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

from version import version_from_tag, write_version


class VersionFromTagTest(unittest.TestCase):
    def test_a_bare_stable_tag_is_the_version(self):
        self.assertEqual(version_from_tag("2.4.0", prerelease=False), "2.4.0")

    def test_a_leading_v_is_dropped_like_the_old_v1_0_2_tag(self):
        self.assertEqual(version_from_tag("v1.0.2", prerelease=False), "1.0.2")

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


GRADLE = """plugins {
    id("com.android.library")
}

version = "0.0.0-dev"
val groupId = "com.formbricks"
"""


class WriteVersionTest(unittest.TestCase):
    def test_replaces_the_placeholder_with_the_release_version(self):
        self.assertEqual(
            write_version(GRADLE, "2.4.0-beta.1"),
            GRADLE.replace('version = "0.0.0-dev"', 'version = "2.4.0-beta.1"'),
        )

    def test_fails_when_the_placeholder_is_missing(self):
        # A build file that already holds a real version means someone bumped it by hand.
        with self.assertRaisesRegex(ValueError, "0.0.0-dev"):
            write_version(GRADLE.replace("0.0.0-dev", "2.3.1"), "2.4.0")

    def test_fails_when_the_placeholder_appears_twice(self):
        with self.assertRaises(ValueError):
            write_version(GRADLE + 'version = "0.0.0-dev"\n', "2.4.0")


class CommandLineTest(unittest.TestCase):
    SCRIPT = Path(__file__).with_name("version.py")

    def run_script(self, tag, prerelease, gradle_file):
        return subprocess.run(
            [sys.executable, str(self.SCRIPT), "--tag", tag, "--prerelease", prerelease,
             "--gradle-file", str(gradle_file)],
            capture_output=True, text=True,
        )

    def test_prints_the_version_and_writes_it_into_the_build_script(self):
        with tempfile.TemporaryDirectory() as tmp:
            gradle = Path(tmp, "build.gradle.kts")
            gradle.write_text(GRADLE)
            result = self.run_script("v2.4.0-beta.1", "true", gradle)
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertEqual(result.stdout, "2.4.0-beta.1\n")
            self.assertIn('version = "2.4.0-beta.1"', gradle.read_text())

    def test_a_bad_tag_fails_and_leaves_the_build_script_alone(self):
        with tempfile.TemporaryDirectory() as tmp:
            gradle = Path(tmp, "build.gradle.kts")
            gradle.write_text(GRADLE)
            result = self.run_script("2.4", "false", gradle)
            self.assertNotEqual(result.returncode, 0)
            self.assertIn("not a semver version", result.stderr)
            self.assertEqual(result.stdout, "")
            self.assertEqual(gradle.read_text(), GRADLE)


if __name__ == "__main__":
    unittest.main()
