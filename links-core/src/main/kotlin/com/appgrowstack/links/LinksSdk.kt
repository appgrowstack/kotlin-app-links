package com.appgrowstack.links

/** This package's name and version. A connector that talks to a server reports them, so the server can count which versions call. */
public object LinksSdk {
    /** The package name (`kotlin-app-links`). */
    public const val NAME: String = "kotlin-app-links"

    /** The released version. Must equal `version` in gradle.properties (LinksSdkTest checks it). */
    public const val VERSION: String = "0.3.0"
}
