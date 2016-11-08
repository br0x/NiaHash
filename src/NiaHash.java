
public class NiaHash {

    /* IOS 1.13.3 */
    private static final long MAGIC_TABLE[] = {
            0x95C05F4D1512959EL, 0xE4F3C46EEF0DCF07L,
            0x6238DC228F980AD2L, 0x53F3E3BC49607092L,
            0x4E7BE7069078D625L, 0x1016D709D1AD25FCL,
            0x044E89B8AC76E045L, 0xE0B684DDA364BFA1L,
            0x90C533B835E89E5FL, 0x3DAF462A74FA874FL,
            0xFEA54965DD3EF5A0L, 0x287A5D7CCB31B970L,
            0xAE681046800752F8L, 0x121C2D6EAF66EC6EL,
            0xEE8F8CA7E090FB20L, 0xCE1AE25F48FE0A52L
    };
    private static final UInt128 ROUND_MAGIC = new UInt128(0x14C983660183C0AEL, 0x78F32468CD48D6DEL);
    private static final long FINAL_MAGIC0 = 0xBDB31B10864F3F87L;
    private static final long FINAL_MAGIC1 = 0x5B7E9E828A9B8ABDL;

    public static long compute_hash(byte[] in, int len) {
        int num_chunks = len >> 7;

        // copy tail, pad with zeroes
        // TODO: try to avoid memcopy (work in place)
        byte[] tail = new byte[128];
        int tail_size = len & 0x7F;
        System.arraycopy(in, len - tail_size, tail, 0, tail_size);

        UInt128 hash;
        if (num_chunks != 0) {
            hash = hash_chunk(in, 128, 0); // Hash the first 128 bytes
        } else {
            hash = hash_chunk(tail, tail_size, 0); // Hash the tail
        }

        hash = hash.add(ROUND_MAGIC);
        int in_offset = 0;
        if (num_chunks != 0) {
            while (--num_chunks != 0) {
                in_offset += 128;
                hash = hash_muladd(hash, ROUND_MAGIC, hash_chunk(in, 128, in_offset));
            }
            if (tail_size != 0) {
                hash = hash_muladd(hash, ROUND_MAGIC, hash_chunk(tail, tail_size, 0));
            }
        }

        // Finalize the hash
        hash.add(new UInt128(0, tail_size * 8));
        UInt128 tmp = new UInt128(hash);
        tmp.add(new UInt128(1L, 0L));
        if (tmp.hi < 0) {
            hash = tmp;
        }
        hash.clearHighBits(1);

        long hash_high = hash.hi;
        long hash_low = hash.lo;
        long X = hash_high + (hash_low >>> 32);
        X = ((X + (X >>> 32) + 1L) >>> 32) + hash_high;
        long Y = (X << 32) + hash_low;

        long A = X + FINAL_MAGIC0;
        if (unsignedCompare(A, X)) {
            A += 0x101L;
        }

        long B = Y + FINAL_MAGIC1;
        if (unsignedCompare(B, Y)) {
            B += 0x101L;
        }

        UInt128 H = UInt128.umul(A, B);
        H.mula(0x101L);
        H.mula(0x101L);
        if (H.hi != 0L) {
            H.add(new UInt128(0x101L, 0));
        }
        if (unsignedCompare(0xFFFFFFFFFFFFFEFEL, H.lo)) {
            H.add(new UInt128(0x101L, 0));
        }
        return H.lo;
    }

    private static UInt128 hash_chunk(byte[] chunk, int size, int masterOffset) {
        UInt128 hash = new UInt128(0L, 0L);
        for (int i = 0; i < 8; i++) {
            int offset = i * 16;
            if (offset >= size) {
                break;
            }
            long a = read_int64(chunk, masterOffset + offset);
            long b = read_int64(chunk, masterOffset + offset + 8);
            long even = a + (MAGIC_TABLE[i * 2]);
            long odd = b + (MAGIC_TABLE[i * 2 + 1]);
            UInt128 mul = UInt128.umul(even, odd);
            hash.add(mul);
        }
        return hash.clearHighBits(2);
    }

    private static UInt128 hash_muladd(UInt128 hash, UInt128 mul, UInt128 add) {
        long a0 = add.lo & 0xffffffffL, a1 = add.lo >>> 32, a23 = add.hi;
        long m0 = mul.lo & 0xffffffffL, m1 = mul.lo >>> 32;
        long m2 = mul.hi & 0xffffffffL,  m3 = mul.hi >>> 32;
        long h0 = hash.lo & 0xffffffffL, h1 = hash.lo >>> 32;
        long h2 = hash.hi & 0xffffffffL, h3 = hash.hi >>> 32;

	    /* Column sums, before carry */
        long c0 = (h0 * m0);
        long c1 = (h0 * m1) + (h1 * m0);
        long c2 = (h0 * m2) + (h1 * m1) + (h2 * m0);
        long c3 = (h0 * m3) + (h1 * m2) + (h2 * m1) + (h3 * m0);
        long c4 = (h1 * m3) + (h2 * m2) + (h3 * m1);
        long c5 = (h2 * m3) + (h3 * m2);
        long c6 = (h3 * m3);

	    /* Combine, add, and carry (bugs included) */
        long r2 = c2 + (c6 << 1) + a23;
        long r3 = c3                   + (r2 >>> 32);

        long r0 = c0 + (c4 << 1) + a0  + (r3 >>> 31);
        long r1 = c1 + (c5 << 1) + a1  + (r0 >>> 32);

	    /* Return as uint128_t */
        // no carry during addition as bit63 = 0
        return new UInt128((r1 << 32) | (r0 & 0xffffffffL), ((r3 << 33 >>> 1) | (r2 & 0xffffffffL)) + (r1 >>> 32));
    }

    private static long read_int64(byte[] p, int offset) { // 01, 02, 03, 04, 05, 06, 07, 08 -> 0x0807060504030201
        // endian-safe read 64-bit integer
        long v = 0;
        for (int i = 7; i >= 0; i--) {
            v = (v << 8) | (p[offset + i] & 0xff);
        }
        return v;
    }

    private static boolean unsignedCompare(long i, long j) {
        return  (i < j) ^ (i < 0) ^ (j < 0);
    }
}
