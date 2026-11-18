#pragma once

#include <atomic>
#include <cstddef>

namespace tv {

// Lock-free single-producer / single-consumer ring buffer.
template <typename T, size_t N>
class SpscRing {
    static_assert((N & (N - 1)) == 0, "capacity must be a power of two");

public:
    bool push(const T& v) {
        const size_t h = head_.load(std::memory_order_relaxed);
        const size_t t = tail_.load(std::memory_order_acquire);
        if (h - t >= N) return false;
        buf_[h & (N - 1)] = v;
        head_.store(h + 1, std::memory_order_release);
        return true;
    }
    bool pop(T& v) {
        const size_t t = tail_.load(std::memory_order_relaxed);
        const size_t h = head_.load(std::memory_order_acquire);
        if (t == h) return false;
        v = buf_[t & (N - 1)];
        tail_.store(t + 1, std::memory_order_release);
        return true;
    }

private:
    T buf_[N];
    std::atomic<size_t> head_{0};
    std::atomic<size_t> tail_{0};
};

}  // namespace tv
