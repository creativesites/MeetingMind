# Preserve readable stack traces for local CrashLog / Play Console symbols
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# MeetingMind JNI & native bindings
-keep class com.k2fsa.sherpa.onnx.** { *; }
# sherpa-onnx's JNI finds the diarization progress callback by name: invoke(IIJ)Ljava/lang/Integer;
-keep class com.craftflowtechnologies.meetingmind.ai.diarization.DiarizationProgressCallback {
    java.lang.Integer invoke(int, int, long);
}
-keep class app.rive.** { *; }
-keep class com.google.mediapipe.** { *; }
-dontwarn com.google.mediapipe.**
-dontwarn app.rive.**

# Room persistence & TypeConverters
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao interface *
-keepclassmembers class * extends androidx.room.RoomDatabase {
    <init>();
}

# RevenueCat Play Billing
-keep class com.revenuecat.purchases.** { *; }
-dontwarn com.revenuecat.purchases.**

# Firebase Auth, Firestore, Messaging, App Check
-keepattributes Signature
-keepclassmembers class * {
    @com.google.firebase.firestore.PropertyName <fields>;
    @com.google.firebase.firestore.PropertyName <methods>;
}
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# Coroutines, OkHttp, Commons Compress, ML Kit, Media3
-dontwarn kotlinx.coroutines.**
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.apache.commons.compress.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Data models reflected via Gson/JSON or Android Parcelable/Serializable
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    !static !transient <fields>;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# R4 document reading: PdfBox-Android loads its fonts and CMaps reflectively; keep its packages whole.
-keep class com.tom_roush.pdfbox.** { *; }
-keep class com.tom_roush.fontbox.** { *; }
-keep class com.tom_roush.harmony.awt.** { *; }
-dontwarn com.tom_roush.**
# Unbundled ML Kit text recognition is called through Play services; keep the client API it binds to.
-keep class com.google.mlkit.vision.text.** { *; }
-dontwarn com.google.mlkit.**
