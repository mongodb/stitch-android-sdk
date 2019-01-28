# MongoDB and Stitch
-keep class com.mongodb.client.model.** { *; }
-keep class com.mongodb.embedded.capi.internal.* { *; }
-keep class com.mongodb.embedded.client.MongoEmbeddedSettings { *; }
-keep class com.mongodb.stitch.android.** { *; }
-keep class com.mongodb.stitch.core.auth.internal.models.** { *; }
-keep class com.mongodb.stitch.core.internal.net.** { *; }

# Sun JNA Package
-keep class com.sun.jna.** { *; }

# Jackson
-keep class com.fasterxml.jackson.databind.ObjectMapper {
    public <methods>;
    protected <methods>;
}
-keep class com.fasterxml.jackson.databind.ObjectWriter {
    public ** writeValueAsString(**);
}
-keepnames class com.fasterxml.jackson.** { *; }
-dontwarn com.fasterxml.jackson.databind.**

# SLF4J Logger
-keep class org.slf4j.** { *; }
-dontwarn org.slf4j.**

# OkHttp Dependencies
-keep class org.codehaus.mojo.animal_sniffer.** { *; }
-keep class org.conscrypt.** { *; }
-dontwarn org.codehaus.mojo.animal_sniffer.**
-dontwarn org.conscrypt.**

# MongoDB Driver Features not required for Embedded MongoDB
-dontwarn io.netty.** # Connecting to clusters over network
-dontwarn java.awt.** # Abstract Window Toolkit
-dontwarn java.lang.management.** # JMX Monitoring
-dontwarn javax.naming.** # DNS resolution
-dontwarn javax.management.** # JMX Monitoring
-dontwarn javax.security.auth.callback.** # PLAIN auth
-dontwarn javax.security.sasl.**
-dontwarn jnr.unixsocket.**
-dontwarn org.ietf.jgss.** # Kerberos
-dontwarn org.xerial.snappy.** # Wire protocol compression
