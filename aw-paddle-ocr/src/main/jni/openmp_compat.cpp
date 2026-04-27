// OpenCV 的 OpenMP backend 可能引用 Intel OMP runtime 的符號（例如 __kmpc_dispatch_deinit），
// 但 Android NDK 的 libomp.so 不一定提供同名符號。
// 這裡提供一個兼容 shim 以通過連結；在大多數情況下 deinit 是清理階段，可安全降級為 no-op。

extern "C" {
struct ident_t;

// Intel OpenMP runtime signature (commonly used by OpenCV builds)
void __kmpc_dispatch_deinit(ident_t*, int, int) {}
}

