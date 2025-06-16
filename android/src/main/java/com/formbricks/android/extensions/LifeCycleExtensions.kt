package com.formbricks.android.extensions

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.withResumed
import kotlinx.coroutines.launch

fun LifecycleOwner.launchWhenResumed(callback: () -> Unit) {
    lifecycleScope.launch {
        lifecycle.withResumed(callback)
    }
}