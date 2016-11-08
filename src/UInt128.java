
class UInt128 {

    long lo;
    long hi;

    UInt128(long lo, long hi) {
        this.lo = lo;
        this.hi = hi;
    }

    UInt128(UInt128 v) {
        this.lo = v.lo;
        this.hi = v.hi;
    }

    UInt128 add(UInt128 v) {
        boolean sx = this.lo < 0;
        boolean sy = v.lo < 0;
        this.lo += v.lo;
        this.hi += v.hi;
        // intuitively I believe there should be something more beautiful than this,
        // maybe: if (this.lo < v.lo) then carry...
        // Please let me know, if you find out
        if (sx && sy || (this.lo > 0 && (sx || sy))) {
            this.hi++;
        }
        return this;
    }

    /**
     * @param n Number of bits to shift (0..31)
     */
    UInt128 clearHighBits(int n) {
        this.hi <<= n;
        this.hi >>>= n;
        return this;
    }

    static UInt128 umul(long x, long y) {
        long y0  = y & 0xFFFFFFFFL;
        long y1  = y >>> 32;
        long x0  = x & 0xFFFFFFFFL;
        long x1  = x >>> 32;

        // The upper 64 bits of the output is a combination of several factors
        long high = y1 * x1;

        long p01 = x0 * y1;
        long p10 = x1 * y0;
        long p00 = x0 * y0;

        // Add the high parts directly in.
        high += (p01 >>> 32);
        high += (p10 >>> 32);

        // Account for the possible carry from the low parts.
        long p2 = (p00 >>> 32) + (p01 & 0xFFFFFFFFL) + (p10 & 0xFFFFFFFFL);
        high += (p2 >>> 32);

        return new UInt128(x * y, high);
    }

    void mula(long x) {
        UInt128 result = UInt128.umul(x, this.hi).add(new UInt128(this.lo, 0));
        this.hi = result.hi;
        this.lo = result.lo;
    }
}