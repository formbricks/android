# Releasing the Android SDK

Publishing a GitHub release ships `com.formbricks:android` to Maven Central. The version comes from
the release tag. There is no bump PR: `android/build.gradle.kts` on `main` holds the placeholder
`0.0.0-dev`, and the release workflow writes the real version into its own checkout only.

Cross-SDK runbook (manual QA, check names, golden journey): <!-- NOTION_SDK_QA_RELEASE_URL -->

## Before you release

1. Check that CI is green on the `main` commit you are about to tag.
2. Run the manual QA: start the E2E workflow from the Actions tab and set `server_version` to the
   Formbricks server you are releasing against. Nothing automated checks that this ran.

## Release

1. On GitHub, open **Releases → Draft a new release**.
2. Create the tag on `main`. Use the bare version, as earlier releases do: `2.4.0`. A leading `v`
   (`v2.4.0`) also works. The tag must be valid semver without build metadata.
3. Click **Generate release notes**. The notes come from the PR titles.
4. For a prerelease, use a suffixed tag such as `2.4.0-beta.1` or `2.4.0-rc.1` and tick
   **Set as a pre-release**. Maven Central has no prerelease channel. A prerelease is just the
   suffixed version, which apps only get when they ask for that exact version. The workflow fails
   if the tick and the suffix disagree, so a prerelease can never ship as a stable version.
5. Click **Publish release**.

The **Release** workflow (`.github/workflows/release.yml`) then runs:

| Job       | What it does                                                                                      |
| --------- | ------------------------------------------------------------------------------------------------- |
| `build`   | Checks the tag, writes the version into the build script, builds the release AAR and POM.          |
| `publish` | Skips the version if Maven Central already has it. Otherwise it signs and publishes it.           |
| `verify`  | Polls `repo1.maven.org` until apps can resolve the version. Fails after 45 minutes.                |

## When something fails

- Use **Re-run failed jobs**. It is always safe, because `publish` skips a version that Maven
  Central already has, even before the repository has synced it.
- `verify` timed out: look at the deployment on [central.sonatype.com](https://central.sonatype.com/publishing/deployments).
  Re-run `verify` once it shows as published.
- Never delete or move a tag. Maven Central never lets a version change, so fix a broken release
  with a new patch version.

## Credentials

Repository secrets used by `publish`:

- `MAVEN_CENTRAL_USERNAME`, `MAVEN_CENTRAL_PASSWORD`: a Central Portal user token for the
  `com.formbricks` namespace.
- `SIGNING_KEY`, `SIGNING_KEY_ID`, `SIGNING_PASSWORD`: the GPG key that signs the artifacts.

## The release scripts

`scripts/release/version.py` turns the tag into the version. `scripts/release/maven_central.py`
answers "is this version out?". Their tests run with:

```sh
python3 -m unittest discover -s scripts/release
```

`./gradlew publishToMavenLocal` on `main` publishes `0.0.0-dev`. That is expected.
