# 靶场测试工具：关闭混淆，保留全部符号便于审计源码
-dontobfuscate
-keep class com.example.keylogger.** { *; }
