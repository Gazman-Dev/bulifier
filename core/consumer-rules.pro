# Preserve JSR 305 annotations for nullability information.
-dontwarn javax.annotation.**

# Keep the PublicSuffixDatabase class as it loads resources with a relative path.
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# Ignore warnings related to Animal Sniffer, used for API compatibility checks.
-dontwarn org.codehaus.mojo.animal_sniffer.*

# Ignore warnings about Conscrypt, an optional security provider.
-dontwarn okhttp3.internal.platform.ConscryptPlatform