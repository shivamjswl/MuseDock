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
import org.schabi.newpipe.extractor.*;
import static org.junit.Assert.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

@RunWith(RobolectricTestRunner.class)
@Config(sdk={29,37},application=MuseDockApp.class,qualifiers="w411dp-h914dp-420dpi")
@LooperMode(LooperMode.Mode.PAUSED)
public final class MuseDockFeaturesTest {
    private MuseDockApp app;
    private MusicCatalog.Mode requested;
    private final Track song=new Track("testtone001","Original test song","Test artist","",120);
    @Before public void setup(){
        app=(MuseDockApp)RuntimeEnvironment.getApplication();Shadows.shadowOf(app).grantPermissions(app.getPackageName()+".DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION");Shadows.shadowOf(app).declareComponentUnbindable(new ComponentName(app,PlaybackService.class));
        app.playlists=new PlaylistSource(){public List<Suggestion> suggest(String q){return List.of();}public Tracks playlist(String url,Page next){return new Tracks("Original test album",List.of(song),null);}};
        app.catalog=(query,next,mode)->{if(query.equals("Test artist")||query.equals("popular albums"))requested=mode;return mode==MusicCatalog.Mode.ALBUMS?new MusicCatalog.Result(List.of(),next==null?List.of(album("PL_first","Original test album")):List.of(album("PL_first","Original test album"),album("PL_second","Second test album")),next==null?new Page("next"):null,query):new MusicCatalog.Result(List.of(song),List.of(),null,query);};
    }
    private PlaylistSource.Suggestion album(String id,String name){return new PlaylistSource.Suggestion("https://www.youtube.com/playlist?list="+id,name,"Test artist","",1);}
    @After public void cleanup(){app.equalizer.release();app.library.close();}
    @Test public void musicFiltersReachTheMusicExtractor()throws Exception{
        for(MusicCatalog.Mode mode:List.of(MusicCatalog.Mode.SONGS,MusicCatalog.Mode.ALBUMS)){
            var query=ServiceList.YouTube.getSearchQHFactory().fromQuery("Test artist",List.of(mode.filter),"");assertTrue(query.getUrl().startsWith("https://music.youtube.com/"));assertTrue(ServiceList.YouTube.getSearchExtractor(query).getClass().getSimpleName().contains("Music"));
        }
        var regular=ServiceList.YouTube.getSearchQHFactory().fromQuery("Test",List.of(MusicCatalog.Mode.VIDEOS.filter),"");assertTrue(regular.getUrl().startsWith("https://www.youtube.com/"));
    }
    @Test public void albumBrowsingPagesDeduplicatesOpensAndSavesTracks()throws Exception{
        try(var controller=Robolectric.buildActivity(MainActivity.class).setup()){
            View root=controller.get().getWindow().getDecorView();assertNotNull(find(root,"MuseDock"));click(root,"Browse albums");await(root,"Original test album");assertEquals(MusicCatalog.Mode.ALBUMS,requested);
            click(root,"Load more albums");await(root,"Second test album");assertEquals(1,count(root,"Original test album"));click(root,"Original test album");await(root,"Original test song");click(root,"Save 1 songs to Library");AlertDialog dialog=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();idle();Library.Playlist saved=app.library.playlists().stream().filter(p->p.name().equals("Original test album")).findFirst().orElseThrow();assertEquals(List.of(song),app.library.playlistTracks(saved.id()));
        }
    }
    @Test public void songsDefaultAndVideoFallbackUseSeparateFilters()throws Exception{
        try(var controller=Robolectric.buildActivity(MainActivity.class).setup()){
            View root=controller.get().getWindow().getDecorView();click(root,"Search");EditText input=input(root);input.setText("Test artist");input.onEditorAction(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);await(root,"Original test song");assertEquals(MusicCatalog.Mode.SONGS,requested);click(root,"YouTube videos");await(root,"Original test song");assertEquals(MusicCatalog.Mode.VIDEOS,requested);
        }
    }
    @Test public void equalizerIsDiscoverableAndPresetControlsPersist(){
        try(var controller=Robolectric.buildActivity(MainActivity.class).setup()){
            View root=controller.get().getWindow().getDecorView();View info=description(root,"MuseDock options");info.performClick();org.robolectric.shadows.ShadowPopupMenu.getLatestPopupMenu().getMenu().performIdentifierAction(1,0);idle();AlertDialog dialog=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();View eq=dialog.getWindow().getDecorView();assertNotNull(find(eq,"Equalizer enabled"));click(eq,"Bass boost");assertEquals("Bass boost",app.equalizer.preset());find(eq,"Equalizer enabled").performClick();idle();assertTrue(app.equalizer.enabled());dialog.dismiss();
        }
    }
    private void idle(){Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();}
    private void await(View root,String value)throws Exception{long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(8);do{idle();if(find(root,value)!=null)return;Thread.sleep(10);}while(System.nanoTime()<deadline);fail("Missing UI text: "+value);}
    private TextView find(View root,String value){if(root instanceof TextView t&&t.getText().toString().equals(value))return t;if(root instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){TextView v=find(group.getChildAt(i),value);if(v!=null)return v;}return null;}
    private int count(View root,String value){int count=root instanceof TextView t&&t.getText().toString().equals(value)?1:0;if(root instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++)count+=count(group.getChildAt(i),value);return count;}
    private EditText input(View root){if(root instanceof EditText e)return e;if(root instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){EditText v=input(group.getChildAt(i));if(v!=null)return v;}return null;}
    private View description(View root,String value){if(value.contentEquals(root.getContentDescription()==null?"":root.getContentDescription()))return root;if(root instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){View v=description(group.getChildAt(i),value);if(v!=null)return v;}return null;}
    private void click(View root,String value){View v=find(root,value);assertNotNull(value,v);while(!v.isClickable()&&v.getParent() instanceof View parent)v=parent;assertTrue(v.performClick());idle();}
}
