# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.tunnaduong.seeli.** { *** Companion; }
-keepclasseswithmembers class com.tunnaduong.seeli.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.tunnaduong.seeli.**$$serializer { *; }
# Credential Manager / Google ID
-if class androidx.credentials.CredentialManager
-keep class androidx.credentials.playservices.** { *; }

# Keep the API models and their generated serializers intact.
-keep class com.tunnaduong.seeli.core.** { *; }
