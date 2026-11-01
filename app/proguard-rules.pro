# Los métodos que el HTML llama (window.IgnisBrain, IgnisDevice, IgnisUpdate, IgnisHaptic, IgnisWeb)
# se buscan por nombre en tiempo de ejecución: R8 no debe renombrarlos ni quitarlos.
-keepclassmembers class com.ignis.studio.** {
    @android.webkit.JavascriptInterface <methods>;
}

# Motor local: los métodos nativos y el callback que llama C++ por su nombre
-keep class com.ignis.studio.LlamaNative { *; }
-keep interface com.ignis.studio.LlamaNative$TokenCallback { *; }
-keepclassmembers class * implements com.ignis.studio.LlamaNative$TokenCallback {
    boolean onToken(byte[]);
}
