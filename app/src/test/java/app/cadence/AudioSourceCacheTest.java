package app.cadence;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.io.IOException;
public class AudioSourceCacheTest {
    private AudioSource source(String name){return new AudioSource("https://example.test/"+name,"audio/mp4","test",1);}
    @Test public void slowDownloadCannotBlockDifferentPlaybackTrack()throws Exception{var cache=new AudioSourceCache();var pool=Executors.newFixedThreadPool(2);var started=new CountDownLatch(1);var release=new CountDownLatch(1);try{var slow=pool.submit(()->cache.get("download",false,id->{started.countDown();try{release.await(3,TimeUnit.SECONDS);}catch(InterruptedException e){throw new IOException(e);}return source(id);}));assertTrue(started.await(2,TimeUnit.SECONDS));var play=pool.submit(()->cache.get("playback",false,this::source));assertEquals(source("playback"),play.get(1,TimeUnit.SECONDS));assertFalse(slow.isDone());release.countDown();slow.get(2,TimeUnit.SECONDS);}finally{release.countDown();pool.shutdownNow();}}
    @Test public void repeatedTrackUsesOneCachedExtractionAndRefreshLoadsAgain()throws Exception{var cache=new AudioSourceCache();var calls=new AtomicInteger();AudioSourceCache.Loader loader=id->{calls.incrementAndGet();return source(id);};assertEquals(cache.get("song",false,loader),cache.get("song",false,loader));assertEquals(1,calls.get());cache.get("song",true,loader);assertEquals(2,calls.get());}
    @Test public void failedExtractionDoesNotPoisonLaterRetry()throws Exception{var cache=new AudioSourceCache();try{cache.get("song",false,id->{throw new IOException("temporary");});fail();}catch(IOException expected){}assertEquals(source("song"),cache.get("song",false,this::source));}
    @Test public void invalidatedInflightResultCannotOverwriteFreshCache()throws Exception{var cache=new AudioSourceCache();var pool=Executors.newSingleThreadExecutor();var started=new CountDownLatch(1);var release=new CountDownLatch(1);try{var old=pool.submit(()->cache.get("same",false,id->{started.countDown();try{release.await(3,TimeUnit.SECONDS);}catch(InterruptedException e){throw new IOException(e);}return source("old");}));assertTrue(started.await(2,TimeUnit.SECONDS));cache.invalidate("same");assertEquals(source("new"),cache.get("same",false,id->source("new")));release.countDown();old.get(2,TimeUnit.SECONDS);assertEquals(source("new"),cache.get("same",false,id->{throw new IOException("must stay cached");}));}finally{release.countDown();pool.shutdownNow();}}
}
