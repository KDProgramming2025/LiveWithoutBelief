# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# --- LWB logging strip (release) ---
# Remove all calls to logging methods (bodies are trivial; safe to strip) in release builds.
# We only emit logs in debug anyway, but this guarantees dead code removal.
-assumenosideeffects class info.lwb.app.logging.AndroidLogger {
	public void d(...);
	public void i(...);
	public void w(...);
	public void e(...);
	public void v(...);
}