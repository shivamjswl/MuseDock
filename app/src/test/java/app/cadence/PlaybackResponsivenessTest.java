package app.cadence;
import android.app.*;
import android.content.ComponentName;
import android.view.*;
import android.widget.*;
import androidx.media3.exoplayer.ExoPlayer;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
@RunWith(RobolectricTestRunner.class) @Config(sdk={29,37},application=MuseDockApp.class) @LooperMode(LooperMode.Mode.PAUSED)
public class PlaybackResponsivenessTest {
    private MuseDockApp app;private ExoPlayer player;
    private final Track song=new Track("responsive1","Test song","Test singer","",180);
    @Before public void setup(){app=(MuseDockApp)RuntimeEnvironment.getApplication();Shadows.shadowOf(app).grantPermissions(app.getPackageName()+".DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION");Shadows.shadowOf(app).declareComponentUnbindable(new ComponentName(app,PlaybackService.class));app.catalog=(q,p,m)->new MusicCatalog.Result(List.of(),List.of(),null,q);app.playlists=new PlaylistSource(){public List<Suggestion> suggest(String q){return List.of();}public Tracks playlist(String q,org.schabi.newpipe.extractor.Page p){return new Tracks("",List.of(),null);}};player=new ExoPlayer.Builder(app).build();}
    @After public void close(){player.release();app.library.close();}
    private Object field(MainActivity a,String name)throws Exception{var f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);return f.get(a);}
    private void call(MainActivity a,String name)throws Exception{var m=MainActivity.class.getDeclaredMethod(name);m.setAccessible(true);m.invoke(a);}
    @Test public void playerEventsReuseMiniPlayerInsteadOfRebuildingArtwork()throws Exception{try(var controller=Robolectric.buildActivity(MainActivity.class).setup()){MainActivity a=controller.get();player.setMediaItem(song.mediaItem(null));a.attachPlayer(player);LinearLayout mini=(LinearLayout)field(a,"mini");View original=mini.getChildAt(0);player.setShuffleModeEnabled(true);player.setRepeatMode(androidx.media3.common.Player.REPEAT_MODE_ALL);call(a,"updateMini");assertSame(original,mini.getChildAt(0));}}
    @Test public void progressTickerDoesNotWaitForTheLibraryDatabase()throws Exception{try(var controller=Robolectric.buildActivity(MainActivity.class).setup()){MainActivity a=controller.get();player.setMediaItem(song.mediaItem(null));a.attachPlayer(player);call(a,"showNowPlaying");underDatabaseLock(()->call(a,"updatePosition"));}}
    @Test public void pressingPlayDoesNotWriteOrReadEveryQueueItemOnUiThread()throws Exception{try(var controller=Robolectric.buildActivity(MainActivity.class).setup()){MainActivity a=controller.get();a.attachPlayer(player);List<Track> queue=new ArrayList<>();for(int i=0;i<100;i++)queue.add(new Track(String.format(Locale.ROOT,"perf%07d",i),"Test "+i,"Artist","",180));underDatabaseLock(()->{var m=MainActivity.class.getDeclaredMethod("play",List.class,int.class,boolean.class);m.setAccessible(true);m.invoke(a,queue,0,false);});assertEquals(100,player.getMediaItemCount());}}
    @Test public void playerOpenDefersDiscoveryNetworkRequests()throws Exception{var requests=new java.util.concurrent.atomic.AtomicInteger();app.catalog=(q,p,m)->{requests.incrementAndGet();return new MusicCatalog.Result(List.of(),List.of(),null,q);};try(var controller=Robolectric.buildActivity(MainActivity.class).setup()){MainActivity a=controller.get();player.setMediaItem(song.mediaItem(null));a.attachPlayer(player);call(a,"showNowPlaying");int before=requests.get();((Runnable)field(a,"tasteRefresh")).run();assertEquals(before,requests.get());}}
    private interface Checked{void run()throws Exception;}
    private void underDatabaseLock(Checked operation)throws Exception{CountDownLatch acquired=new CountDownLatch(1),release=new CountDownLatch(1);AtomicBoolean held=new AtomicBoolean();Thread holder=new Thread(()->{synchronized(app.library){held.set(true);acquired.countDown();try{release.await(3,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}finally{held.set(false);}}});holder.start();try{assertTrue(acquired.await(2,TimeUnit.SECONDS));operation.run();assertTrue("UI operation waited for the database lock",held.get());}finally{release.countDown();holder.join(4000);}}
}
