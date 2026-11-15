package com.formbricks.android.model.error

import androidx.annotation.Keep

@Keep
public object SDKError {
    // Errors related to SDK initialization and configuration
    public val sdkIsNotInitialized: RuntimeException =
        RuntimeException("Formbricks SDK is not initialized")
    public val sdkIsAlreadyInitialized: RuntimeException =
        RuntimeException("Formbricks SDK is already initialized")
    public val fragmentManagerIsNotSet: RuntimeException =
        RuntimeException("The fragment manager is not set.")
    public val fragmentManagerIsDestroyed: RuntimeException = RuntimeException(
        "The fragment manager belongs to a destroyed Activity, so the survey cannot be " +
            "shown. Call Formbricks.setFragmentManager(supportFragmentManager) from the " +
            "Activity that is currently on screen."
    )

    // Errors related to network and connectivity
    public val connectionIsNotAvailable: RuntimeException =
        RuntimeException("There is no connection.")
    public val unableToLoadFormbicksJs: RuntimeException =
        RuntimeException("Unable to load Formbricks Javascript package.")

    // Errors related to surveys
    public val surveyDisplayFetchError: RuntimeException =
        RuntimeException("Error: creating display: TypeError: Failure to fetch the survey data.")
    public val surveyNotDisplayedError: RuntimeException =
        RuntimeException("Survey was not displayed due to display percentage restrictions.")
    public val unableToRefreshEnvironment: RuntimeException =
        RuntimeException("Unable to refresh environment state.")
    public val missingSurveyId: RuntimeException =
        RuntimeException("Survey id is mandatory to set.")
    public val invalidDisplayOption: RuntimeException = RuntimeException("Invalid Display Option.")
    public val unableToPostResponse: RuntimeException =
        RuntimeException("Unable to post survey response.")
    public val surveyNotFoundError: RuntimeException =
        RuntimeException("No survey found matching the action class.")
    public val noUserIdSetError: RuntimeException =
        RuntimeException("No userId is set, please set a userId first using the setUserId function")

    public val couldNotCreateDisplayError: RuntimeException =
        RuntimeException("Something went wrong while creating a display. Please try again later")
    public val couldNotCreateResponseError: RuntimeException =
        RuntimeException("Something went wrong while creating a response. Please try again later")
    public val somethingWentWrongError: RuntimeException =
        RuntimeException("Something went wrong. Please try again later")
}
