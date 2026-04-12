# SeaCard — правила для R8 (release)

# Читаемые стектрейсы в Play Console / Firebase (деобфускация по mapping.txt)
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Аннотации (Room, Compose и др.)
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations,AnnotationDefault

# --- ZXing ---
-keep class com.google.zxing.** { *; }

# --- Kotlin / корутины ---
-dontwarn kotlinx.coroutines.**

# Виджет: провайдер и сервис объявлены в манифесте; фабрика создаётся из кода сервиса
-keep class ru.merrcurys.seacard.widget.SeaCardAppWidgetProvider { <init>(); }
-keep class ru.merrcurys.seacard.widget.SeaCardWidgetService { <init>(); }
