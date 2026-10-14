# The AAR loads its bundled wake word model and native bridge by class name.
-keep class com.openwakeword.** { *; }
-keep class ai.onnxruntime.** { *; }
-keepclasseswithmembernames class * { native <methods>; }
