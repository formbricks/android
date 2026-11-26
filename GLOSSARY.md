# Glossary

Domain terms the code and architecture reviews use. Only terms specific to the Android SDK belong here: no general
programming concepts, and no implementation details.

## Setup

**Workspace** — The Formbricks workspace an app's surveys, action classes and settings belong to, identified by `workspaceId` in `FormbricksConfig`. _Avoid_: environment, project (`environmentId` and the `project` payload key are legacy names the SDK still accepts).

**Workspace state** — The workspace's surveys, action classes and settings, fetched from the server, cached on the device and refreshed when it expires.

**Appearance** — Whether surveys render light, dark, or follow the host app's night mode (`FormbricksAppearance`). Never sent to the server.

## Users

**User state** — What the server knows about the identified user: `userId`, `contactId`, segments, displays, responses, the last display time and the language. Cached on the device until it expires; cleared by `logout()`.

**Contact** — The server-side record for an identified user, referenced by `contactId`. A user with no `userId` is anonymous and has no contact.

**Attribute** — A string or number value stored on the contact (`setAttribute`, `setAttributes`). Its type is fixed by its first write. _Avoid_: embedded data.

**Segment** — A server-side group of contacts. A survey targets at most one; the device only learns whether that segment has filters (`hasFilters`), never the filters themselves. Anonymous users never see surveys whose segment has filters.

**Embedded data** — Key-value context the host app attaches to future responses (`setEmbeddedData`). In memory only, never persisted, and not stored on the contact. _Avoid_: attribute.

## Surveys

**Action** — A named event the host app reports with `track(name)`. Only `code` action classes can be tracked from the SDK. _Avoid_: event.

**Action class** — The workspace's definition of an action, matched by its `key`.

**Trigger** — The link from a survey to an action class: tracking that action may show the survey.

**Display** — One showing of a survey to a user, recorded with its survey ID and time.

**Response** — A user's answers to a survey. The user state keeps the IDs of the surveys the user responded to.

**Display option** — The survey's rule for how often it may show: `displayOnce`, `displayMultiple` (until the user responds), `displaySome` (up to `displayLimit` displays) or `respondMultiple`.

**Recontact days** — The minimum number of days between the user's last display and the next survey; the survey's value overrides the workspace's.

**Display percentage** — The chance that a triggered survey actually shows.

**Interaction refresh** — Per-survey flags (`onDisplay`, `onResponse`, `onFinished`) saying that interacting with the survey can change segment membership, so the SDK refreshes the user state afterwards.
