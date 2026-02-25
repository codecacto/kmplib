# KmpLib ProGuard/R8 Rules
#
# USAGE: Copy these rules to your app's proguard-rules.pro file
# OR add this file to your app's build.gradle:
#
# android {
#     buildTypes {
#         release {
#             proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'),
#                          'proguard-rules.pro'
#         }
#     }
# }

# ========================
# Firebase
# ========================
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# Firebase Firestore
-keepclassmembers class * {
    @com.google.firebase.firestore.PropertyName <methods>;
    @com.google.firebase.firestore.PropertyName <fields>;
}

# ========================
# Kotlinx Serialization
# ========================
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

# Keep Serializers
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep @Serializable classes
-keep,includedescriptorclasses class br.com.codecacto.kmplib.**$$serializer { *; }
-keepclassmembers class br.com.codecacto.kmplib.** {
    *** Companion;
}
-keepclasseswithmembers class br.com.codecacto.kmplib.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ========================
# Kotlin Coroutines
# ========================
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.**

# ========================
# AndroidX Biometric
# ========================
-keep class androidx.biometric.** { *; }
-dontwarn androidx.biometric.**

# ========================
# Compose
# ========================
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# ========================
# KmpLib Public API
# ========================
-keep class br.com.codecacto.kmplib.KmpLib { *; }
-keep class br.com.codecacto.kmplib.KmpLibInit { *; }

# Keep all validators
-keep class br.com.codecacto.kmplib.validation.** { *; }

# Keep all masks
-keep class br.com.codecacto.kmplib.mask.** { *; }

# Keep Firebase classes
-keep class br.com.codecacto.kmplib.firebase.** { *; }

# Keep platform interfaces
-keep class br.com.codecacto.kmplib.platform.** { *; }

# Keep Brazilian data
-keep class br.com.codecacto.kmplib.brdata.** { *; }

# ========================
# General
# ========================
# Keep line numbers for debugging stack traces
-keepattributes SourceFile,LineNumberTable

# Rename source file attribute to hide source file names
-renamesourcefileattribute SourceFile
