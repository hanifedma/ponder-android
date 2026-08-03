# Ponder — R8 rules.
#
# Firestore documents are read/written field-by-field (getString/getTimestamp),
# never via reflective POJO mapping, so no model classes need keeping.
# Firebase and AndroidX ship their own consumer rules; these cover the rest.

# Credential Manager loads its Play Services provider reflectively.
-keep class androidx.credentials.playservices.** { *; }
-if class androidx.credentials.CredentialManager
-keep class androidx.credentials.playservices.CredentialProviderPlayServicesImpl

# Keep the Google ID token request/response types used by Credential Manager.
-keep class com.google.android.libraries.identity.googleid.** { *; }

# Silence warnings for optional transitive deps that are not on the runtime path.
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
