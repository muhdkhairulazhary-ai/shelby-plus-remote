# Test entry points must survive emulator-only shrinking so instrumentation invokes the actual helpers.
# Intentionally does not keep apksig schemas: production rules must protect their reflective fields.
-keep class dev.tvremote.setup.update.** { *; }
