package dev.engine_room.flywheel.backend.compile;
import mvhflywheelbackend.NativeShaderSources;
/** Original material-index invalidation hook invalidates real native shader/pipeline resources. */
public final class PipelineCompiler { public static void deleteAll(){NativeShaderSources.invalidate();} }
