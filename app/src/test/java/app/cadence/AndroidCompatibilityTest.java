package app.cadence;

import android.app.AlertDialog;
import android.content.ComponentName;
import android.view.*;
import android.widget.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;
import java.io.*;
import java.util.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk={29,30,31,32,33,34,35,36,37},application=MuseDockApp.class,qualifiers="w411dp-h914dp-420dpi")
@LooperMode(LooperMode.Mode.PAUSED)
public final class AndroidCompatibilityTest {
    private MuseDockApp app;
    private final Track first=new Track("testtone001","Downloaded song one","Test artist","",120),second=new Track("testtone002","Downloaded song two","Test artist","",180);
    @Before public void setup(){
        app=(MuseDockApp)RuntimeEnvironment.getApplication();app.catalog=(query,page,mode)->new MusicCatalog.Result(java.util.List.of(),java.util.List.of(),null,query);
        app.playlists=new PlaylistSource(){public List<Suggestion> suggest(String query){return List.of();}public Tracks playlist(String url,org.schabi.newpipe.extractor.Page page){return new Tracks("",List.of(),null);}};
        // Android grants the app its declared signature permission at install; simulate that grant in Robolectric.
        Shadows.shadowOf(app).grantPermissions(app.getPackageName()+".DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION");
        Shadows.shadowOf(app).declareComponentUnbindable(new ComponentName(app,PlaybackService.class));
    }
    @After public void cleanup(){app.library.close();}
    private void download(Track track)throws Exception{File dir=new File(app.getFilesDir(),"audio");assertTrue(dir.isDirectory()||dir.mkdirs());File file=new File(dir,track.id()+".audio");try(FileOutputStream out=new FileOutputStream(file)){out.write(new byte[]{1,2,3});}app.library.download(track,"ready",file.getAbsolutePath(),"");}
    @Test public void createsOwnPlaylistImmediatelyAndPersistsIt(){
        try(var activity=Robolectric.buildActivity(MainActivity.class).setup()){
            View root=activity.get().getWindow().getDecorView();click(root,"Library");click(root,"+  Create playlist");AlertDialog dialog=latest();EditText name=input(dialog.getWindow().getDecorView());assertNotNull(name);name.setText("  My own music  ");
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();idle();assertFalse(dialog.isShowing());
            Library.Playlist saved=app.library.playlists().stream().filter(p->p.name().equals("My own music")).findFirst().orElseThrow();assertNotNull(text(root,"My own music"));
            Library reopened=new Library(app);assertTrue(reopened.playlists().stream().anyMatch(p->p.id().equals(saved.id())));reopened.close();
        }
    }
    @Test public void createsCustomPlaylistFromDownloadsWithoutSearching()throws Exception{
        download(first);download(second);
        try(var activity=Robolectric.buildActivity(MainActivity.class).setup()){
            View root=activity.get().getWindow().getDecorView();click(root,"Offline");click(root,"Create playlist from downloads");AlertDialog name=latest();input(name.getWindow().getDecorView()).setText("Travel music");name.getButton(AlertDialog.BUTTON_POSITIVE).performClick();idle();
            AlertDialog picker=latest();assertNotSame(name,picker);picker.getButton(AlertDialog.BUTTON_NEUTRAL).performClick();picker.getButton(AlertDialog.BUTTON_POSITIVE).performClick();idle();
            Library.Playlist saved=app.library.playlists().stream().filter(p->p.name().equals("Travel music")).findFirst().orElseThrow();assertEquals(2,app.library.playlistTracks(saved.id()).size());assertTrue(app.library.contains(saved.id(),first.id()));assertTrue(app.library.contains(saved.id(),second.id()));
            assertNotNull(app.library.offline(first.id()));assertNotNull(app.library.offline(second.id()));assertNotNull(text(root,"Travel music"));
        }
    }
    @Test public void addsDownloadsToAnExistingEmptyPlaylist()throws Exception{
        download(first);String id=app.library.createPlaylist("Road trip");
        try(var activity=Robolectric.buildActivity(MainActivity.class).setup()){
            View root=activity.get().getWindow().getDecorView();click(root,"Library");click(root,"Road trip");click(root,"Add downloaded songs");AlertDialog picker=latest();picker.getButton(AlertDialog.BUTTON_NEUTRAL).performClick();picker.getButton(AlertDialog.BUTTON_POSITIVE).performClick();idle();assertEquals(List.of(first),app.library.playlistTracks(id));
        }
    }
    @Test public void blankNameKeepsDialogOpenAndKeyboardDoneSaves(){
        try(var activity=Robolectric.buildActivity(MainActivity.class).setup()){
            View root=activity.get().getWindow().getDecorView();click(root,"Library");click(root,"+  Create playlist");AlertDialog dialog=latest();EditText name=input(dialog.getWindow().getDecorView());name.setText("   ");dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();assertTrue(dialog.isShowing());assertNotNull(name.getError());assertEquals(1,app.library.playlists().size());
            name.setText("Study music");name.onEditorAction(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);idle();assertFalse(dialog.isShowing());assertTrue(app.library.playlists().stream().anyMatch(p->p.name().equals("Study music")));
        }
    }
    @Test public void downloadedSongsExposeAddToPlaylistWithoutOpeningAMenu()throws Exception{
        download(first);String id=app.library.createPlaylist("Favorites from downloads");
        try(var activity=Robolectric.buildActivity(MainActivity.class).setup()){
            View root=activity.get().getWindow().getDecorView();click(root,"Offline");View add=description(root,"Add Downloaded song one to playlist");assertNotNull(add);add.performClick();idle();
            AlertDialog chooser=latest();chooser.getListView().performItemClick(null,2,2);idle();assertTrue(app.library.contains(id,first.id()));
        }
    }
    private void idle(){Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();}
    private AlertDialog latest(){idle();AlertDialog dialog=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();assertNotNull(dialog);assertTrue(dialog.isShowing());return dialog;}
    private TextView text(View view,String value){if(view instanceof TextView t&&t.getText().toString().equals(value))return t;if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){TextView found=text(group.getChildAt(i),value);if(found!=null)return found;}return null;}
    private EditText input(View view){if(view instanceof EditText t)return t;if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){EditText found=input(group.getChildAt(i));if(found!=null)return found;}return null;}
    private View description(View view,String value){if(value.contentEquals(view.getContentDescription()==null?"":view.getContentDescription()))return view;if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){View found=description(group.getChildAt(i),value);if(found!=null)return found;}return null;}
    private void click(View root,String value){View view=text(root,value);assertNotNull(value,view);while(!view.isClickable()&&view.getParent() instanceof View parent)view=parent;assertTrue(view.performClick());idle();}
}
