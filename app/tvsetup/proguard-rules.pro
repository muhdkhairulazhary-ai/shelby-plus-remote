# apksig parses PKCS#7/X.509 through ASN.1 annotations and getDeclaredFields().
# Keeping names alone is insufficient: R8 otherwise removes unused mandatory schema fields.
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault,Signature,InnerClasses,EnclosingMethod
-keep @interface com.android.apksig.internal.asn1.Asn1Class { *; }
-keep @interface com.android.apksig.internal.asn1.Asn1Field { *; }
-keep @com.android.apksig.internal.asn1.Asn1Class class com.android.apksig.** { *; }
-keep enum com.android.apksig.internal.asn1.* { *; }
-keep class com.android.apksig.internal.asn1.Asn1OpaqueObject { *; }
