# Releasing the Android SDK

Publishing a GitHub release ships `com.formbricks:android` to Maven Central. The version comes from
the release tag. There is no bump PR: `android/build.gradle.kts` on `main` holds the placeholder
`0.0.0-dev`, and the release workflow passes the real version to Gradle as the `releaseVersion`
property.

Cross-SDK runbook (manual QA, check names, golden journey):
https://app.notion.com/p/419e5de84a5881cd8091c30193db8607

## Before you release

1. Check that CI is green on the `main` commit you are about to tag.
2. Run the manual QA: start the **E2E** workflow (`e2e.yml`) from the Actions tab and set
   `server_version` to the Formbricks server you are releasing against. Nothing automated checks that this ran.

## Release

1. On GitHub, open **Releases → Draft a new release**.
2. Create the tag on `main`. Use the bare version: `2.4.0`. The workflow rejects a leading `v`
   (`v2.4.0`) and anything that is not semver, and build metadata (`+…`) too.
3. Click **Generate release notes**. The notes come from the PR titles.
4. For a prerelease, use a suffixed tag such as `2.4.0-beta.1` or `2.4.0-rc.1` and tick
   **Set as a pre-release**. Maven Central has no prerelease channel. A prerelease is just the
   suffixed version, which apps only get when they ask for that exact version. The workflow fails
   if the tick and the suffix disagree, so a prerelease can never ship as a stable version.
5. Click **Publish release**.

The **Release** workflow (`.github/workflows/release.yml`) then runs:

| Job       | What it does                                                                                      |
| --------- | ------------------------------------------------------------------------------------------------- |
| `build`   | Checks the tag and turns it into the version, then builds the release AAR and POM with it.        |
| `publish` | Skips the version if Maven Central has it or an earlier attempt's upload is still going through. Otherwise it rebuilds, signs and publishes it. |
| `verify`  | Polls `repo1.maven.org` until apps can resolve the version. Fails after 45 minutes.                |

Every job checks out the tag, so the scripts and the build come from the tagged commit.

## When something fails

- Use **Re-run failed jobs**. It is always safe: `publish` skips a version that Maven Central
  already has, even before the repository has synced it. Each upload also records its Central
  Portal deployment id in the run's `maven-central-deployments` artifact, and a re-run skips the
  upload while that deployment is pending, validating, validated, publishing or published. Only a
  `FAILED` deployment is uploaded again.
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
answers "is this version out, or on its way?" and waits until it is. Their tests run in the CI
`unit-tests` job, and locally with:

```sh
python3 -m unittest discover -s scripts/release
```

`./gradlew publishToMavenLocal` on `main` publishes `0.0.0-dev`. That is expected.
