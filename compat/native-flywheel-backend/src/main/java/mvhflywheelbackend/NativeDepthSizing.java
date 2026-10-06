package mvhflywheelbackend;
import net.minecraft.util.Mth;
/** Original MIT depth pyramid dimensions; no rendering feature substitute. */
public final class NativeDepthSizing {
	public static int mip0Size(int screenSize) {
		return Integer.highestOneBit(screenSize);
	}
	public static int getImageMipLevels(int width, int height) {
		int result = 1;

		while (width > 1 && height > 1) {
			result++;
			width >>= 1;
			height >>= 1;
		}

		return result;
	}
}
