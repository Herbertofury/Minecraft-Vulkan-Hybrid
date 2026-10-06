package mvhgrasscompat;

import java.util.Arrays;
import java.util.function.LongToDoubleFunction;

/** Reuses only the exact stable permutation for the same input sequence and camera bits.
 * The caller's distance function must be the same pure function of section key and camera.
 * No world, mesh, frustum, shader, rebuild or resource result is retained here.
 */
public final class MemoizedDistanceSort {
    private static final int RETAIN_LIMIT = 65536;
    private final DistanceSort sorter = new DistanceSort();
    private long[] input = new long[0], output = new long[0];
    private long cameraX, cameraY, cameraZ;
    private int size;
    private boolean valid;

    public int retainedCapacity() { return input.length; }

    public void sort(long[] values, int count, double x, double y, double z,
                     LongToDoubleFunction distance) {
        if (count < 0 || count > values.length) throw new IndexOutOfBoundsException();
        if (count < 2 || count > RETAIN_LIMIT) {
            valid = false;
            sorter.sort(values, count, distance);
            return;
        }
        long bx = Double.doubleToRawLongBits(x), by = Double.doubleToRawLongBits(y),
             bz = Double.doubleToRawLongBits(z);
        if (valid && count == size && bx == cameraX && by == cameraY && bz == cameraZ
                && Arrays.equals(values, 0, count, input, 0, count)) {
            System.arraycopy(output, 0, values, 0, count);
            return;
        }
        // A failed distance evaluation cannot make a new input hit an older result.
        valid = false;
        if (input.length < count) {
            int capacity = Math.min(RETAIN_LIMIT, Math.max(count, Math.max(16, input.length * 2)));
            input = new long[capacity];
            output = new long[capacity];
        }
        System.arraycopy(values, 0, input, 0, count);
        sorter.sort(values, count, distance);
        System.arraycopy(values, 0, output, 0, count);
        size = count;
        cameraX = bx; cameraY = by; cameraZ = bz;
        valid = true;
    }
}
