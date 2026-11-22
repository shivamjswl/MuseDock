package app.cadence;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

/** Network extraction for one download never holds a lock needed by another song. */
final class AudioSourceCache {
    interface Loader { AudioSource load(String id) throws IOException; }
    private record Entry(AudioSource source,long expires){}
    private final Map<String,Entry> cached=new LinkedHashMap<>(32,.75f,true);
    private final Map<String,CompletableFuture<AudioSource>> pending=new HashMap<>();
    AudioSource get(String id,boolean refresh,Loader loader)throws IOException {
        CompletableFuture<AudioSource> future;boolean owner;
        synchronized(this){if(refresh)cached.remove(id);Entry hit=cached.get(id);if(hit!=null&&hit.expires()>System.currentTimeMillis())return hit.source();future=pending.get(id);owner=future==null;if(owner){future=new CompletableFuture<>();pending.put(id,future);}}
        if(owner){try{AudioSource source=loader.load(id);synchronized(this){if(pending.get(id)==future){cached.put(id,new Entry(source,System.currentTimeMillis()+15*60*1000));while(cached.size()>64)cached.remove(cached.keySet().iterator().next());}}future.complete(source);}catch(Exception e){future.completeExceptionally(e);}finally{synchronized(this){pending.remove(id,future);}}}
        try{return future.get();}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IOException("Audio request interrupted",e);}catch(ExecutionException e){throw e.getCause() instanceof IOException io?io:new IOException("Audio extraction failed",e.getCause());}
    }
    synchronized void invalidate(String id){cached.remove(id);pending.remove(id);}
}
