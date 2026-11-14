# Regras do R8 para o NTPlayone.
# Media3, Coil, Compose e AndroidX já trazem regras de consumidor.
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# zxing: apenas QRCodeWriter é usado; o resto pode ser removido.
-keep class com.google.zxing.qrcode.** { *; }
-keep class com.google.zxing.common.** { *; }
-keep class com.google.zxing.BarcodeFormat { *; }
-keep class com.google.zxing.EncodeHintType { *; }
-keep class com.google.zxing.WriterException { *; }

# nomes legíveis em stack traces
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# devolve nomes de classes e métodos que aparecem em stack traces de crash
-keep class com.ntplayone.tv.** { *; }
