# Los métodos que el HTML llama (window.IgnisBrain, IgnisDevice, IgnisUpdate, IgnisHaptic, IgnisWeb)
# se buscan por nombre en tiempo de ejecución: R8 no debe renombrarlos ni quitarlos.
-keepclassmembers class com.ignis.studio.** {
    @android.webkit.JavascriptInterface <methods>;
}
