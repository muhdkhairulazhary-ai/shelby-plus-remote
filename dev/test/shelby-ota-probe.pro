# Test entry points must survive emulator-only shrinking so instrumentation invokes the actual helpers.
# Intentionally does not keep apksig schemas: production rules must protect their reflective fields.
-keep class dev.tvremote.setup.update.** { *; }
# The separate instrumentation APK uses stdlib methods invisible to target-app R8 reachability.
# Retain them only in this emulator fixture; do not change production Kotlin shrinking.
-keep class kotlin.** { *; }
