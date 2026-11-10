package com.logan.flowbusapp.testutil;

// 直接调用平台 API，避免压缩后的目标 APK 裁掉仅被测试引用的 Kotlin 扩展类。
final class SuppressedExceptions {
    private SuppressedExceptions() {}

    static void add(Throwable original, Throwable suppressed) {
        original.addSuppressed(suppressed);
    }

    static Throwable[] get(Throwable error) {
        return error.getSuppressed();
    }
}
