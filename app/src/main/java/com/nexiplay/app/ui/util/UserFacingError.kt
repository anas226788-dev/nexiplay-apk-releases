package com.nexiplay.app.ui.util

/** Keeps backend and SDK implementation details out of user-visible messages. */
object UserFacingError {
    fun from(error: Throwable, fallback: String = "Something went wrong. Please try again."): String {
        val details = generateSequence(error) { it.cause }
            .mapNotNull { it.message }
            .joinToString(" ")
            .lowercase()

        return when {
            details.contains("invalid_code") -> "That referral code isn't valid. Check it and try again."
            details.contains("own_code") -> "You can't redeem your own referral code."
            details.contains("already_redeemed") -> "You've already redeemed a referral code."
            details.contains("invalid login credentials") || details.contains("invalid password") ->
                "That email and password don't match. Check them and try again."
            details.contains("user already registered") || details.contains("already exists") ->
                "An account with this email already exists. Try signing in instead."
            details.contains("email not confirmed") ->
                "Confirm your email from the link we sent, then try again."
            details.contains("password should") || details.contains("password must") ->
                "Choose a stronger password and try again."
            details.contains("rate limit") || details.contains("too many requests") ->
                "Too many attempts. Please wait a little and try again."
            details.contains("network") || details.contains("unable to resolve host") ||
                details.contains("no address associated") || details.contains("connection") ||
                details.contains("timeout") || details.contains("timed out") ->
                "Couldn't connect. Check your internet connection and try again."
            details.contains("401") || details.contains("jwt expired") || details.contains("session expired") ->
                "Your session expired. Sign in again to continue."
            details.contains("403") || details.contains("row-level security") || details.contains("permission denied") ->
                "You don't have permission to do that. Sign in and try again."
            details.contains("404") || details.contains("not found") ->
                "We couldn't find that item. It may have been removed."
            details.contains("invalid email") || details.contains("email address") ->
                "Enter a valid email address and try again."
            else -> fallback
        }
    }
}
