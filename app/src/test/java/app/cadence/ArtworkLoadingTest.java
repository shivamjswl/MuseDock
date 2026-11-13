package app.cadence;
import android.content.ComponentName;
import android.graphics.*;
import android.graphics.drawable.BitmapDrawable;
import android.widget.ImageView;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk={29,37},application=MuseDockApp.class) @LooperMode(LooperMode.Mode.PAUSED)
public class ArtworkLoadingTest {
    private MuseDockApp app;private final List<String> calls=new CopyOnWriteArrayList<>();
    @Before public void setup(){app=(MuseDockApp)RuntimeEnvironment.getApplication();Shadows.shadowOf(app).grantPermissions(app.getPackageName()+".DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION");Shadows.shadowOf(app).declareComponentUnbindable(new ComponentName(app,PlaybackService.class));app.catalog=(q,p,m)->new MusicCatalog.Result(List.of(),List.of(),null,q);app.playlists=new PlaylistSource(){public List<Suggestion> suggest(String q){return List.of();}public Tracks playlist(String u,org.schabi.newpipe.extractor.Page p){return new Tracks("",List.of(),null);}};}
    @After public void close(){app.library.close();}
    private byte[] png(int width){try(InputStream in=getClass().getResourceAsStream("/artwork-"+width+".png")){assertNotNull(in);return in.readAllBytes();}catch(IOException e){throw new AssertionError(e);}}
    private void client(boolean failLarge){byte[] large=png(1280),small=png(640);app.artworkClient=new okhttp3.OkHttpClient.Builder().addInterceptor(chain->{String url=chain.request().url().toString();calls.add(url);boolean max=url.contains("maxresdefault");return new okhttp3.Response.Builder().request(chain.request()).protocol(okhttp3.Protocol.HTTP_1_1).code(max&&failLarge?404:200).message("fixture").body(okhttp3.ResponseBody.create(max?large:small,okhttp3.MediaType.get("image/png"))).build();}).build();}
    private void load(MainActivity activity,ImageView image,int pixels)throws Exception{var method=MainActivity.class.getDeclaredMethod("loadImage",ImageView.class,String.class,int.class);method.setAccessible(true);method.invoke(activity,image,"https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg",pixels);}
    private void await(ImageView image)throws Exception{long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);while(!(image.getDrawable() instanceof BitmapDrawable)&&System.nanoTime()<deadline){Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();Thread.sleep(10);}assertTrue(image.getDrawable() instanceof BitmapDrawable);}
    @Test public void largePlayerArtworkDoesNotReuseSmallListBitmap()throws Exception{client(false);try(var controller=Robolectric.buildActivity(MainActivity.class).setup()){ImageView row=new ImageView(controller.get()),player=new ImageView(controller.get());load(controller.get(),row,160);await(row);load(controller.get(),player,1080);await(player);assertEquals(320,((BitmapDrawable)row.getDrawable()).getBitmap().getWidth());assertEquals(calls.toString()+" cache="+app.artworkCache.snapshot().keySet(),1280,((BitmapDrawable)player.getDrawable()).getBitmap().getWidth());assertTrue(calls.stream().anyMatch(s->s.endsWith("maxresdefault.jpg")));}}
    @Test public void missingLargeImageFallsBackAndConcurrentRequestsShareFetch()throws Exception{client(true);try(var controller=Robolectric.buildActivity(MainActivity.class).setup()){ImageView one=new ImageView(controller.get()),two=new ImageView(controller.get());load(controller.get(),one,1080);load(controller.get(),two,1080);await(one);await(two);assertEquals(640,((BitmapDrawable)one.getDrawable()).getBitmap().getWidth());assertSame(((BitmapDrawable)one.getDrawable()).getBitmap(),((BitmapDrawable)two.getDrawable()).getBitmap());assertEquals(1,calls.stream().filter(s->s.endsWith("maxresdefault.jpg")).count());assertEquals(1,calls.stream().filter(s->s.endsWith("sddefault.jpg")).count());}}
    @Test public void smallProviderPlaceholderIsRejectedEvenWithHttpSuccess()throws Exception{byte[] placeholder=png(120),cover=png(640);app.artworkClient=new okhttp3.OkHttpClient.Builder().addInterceptor(chain->{String url=chain.request().url().toString();calls.add(url);return new okhttp3.Response.Builder().request(chain.request()).protocol(okhttp3.Protocol.HTTP_1_1).code(200).message("fixture").body(okhttp3.ResponseBody.create(url.endsWith("hqdefault.jpg")?cover:placeholder,okhttp3.MediaType.get("image/png"))).build();}).build();try(var controller=Robolectric.buildActivity(MainActivity.class).setup()){ImageView image=new ImageView(controller.get());load(controller.get(),image,1080);await(image);assertEquals(640,((BitmapDrawable)image.getDrawable()).getBitmap().getWidth());assertEquals(3,calls.size());}}
}
