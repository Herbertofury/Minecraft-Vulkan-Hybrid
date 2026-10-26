package mvhglcompat;
import java.util.concurrent.CompletableFuture;
import java.util.function.*;
/** Keeps a render-owned lookup on its owner; preserves asynchronous failure semantics. */
public final class TextureLookup {
 private TextureLookup(){}
 public static <T> CompletableFuture<T> submit(boolean ownsRenderContext,Supplier<T> work,Function<Supplier<T>,CompletableFuture<T>> ordinarySubmit){
  if(!ownsRenderContext)return ordinarySubmit.apply(work);
  try{return CompletableFuture.completedFuture(work.get());}catch(Throwable failure){return CompletableFuture.failedFuture(failure);}
 }
}
