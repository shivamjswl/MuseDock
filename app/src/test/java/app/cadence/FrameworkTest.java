package app.cadence;

import android.view.*;
import android.widget.TextView;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;
import java.io.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=36,application=MuseDockApp.class,qualifiers="w411dp-h914dp-420dpi")
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public final class FrameworkTest {
    private MuseDockApp app;
    private final Track one=new Track("testtone001","First song","Test artist","",120);
    private final Track two=new Track("testtone002","Second song","Test artist","",180);
    private FakePlaylists source;
    @Before public void setup(){app=(MuseDockApp)RuntimeEnvironment.getApplication();app.catalog=(query,page,mode)->new MusicCatalog.Result(java.util.List.of(),java.util.List.of(),null,query);source=new FakePlaylists();app.playlists=source;}
    private final class FakePlaylists implements PlaylistSource {
        boolean fail=false;
        @Override public java.util.List<Suggestion> suggest(String query)throws Exception{
            if(fail)throw new IOException("Network unavailable");
            return java.util.List.of(new Suggestion("https://www.youtube.com/playlist?list=PL_test","Test discovery mix","Test creator","",2));
        }
        @Override public Tracks playlist(String url,org.schabi.newpipe.extractor.Page page){
            return page==null?new Tracks("Test discovery mix",java.util.List.of(one),new org.schabi.newpipe.extractor.Page("test-next")):
                new Tracks("",java.util.List.of(one,two),null);
        }
    }
    @After public void closeDatabase(){app.library.close();}
    @Test public void sqlitePersistsUniquePlaylistMembersAndOrder(){
        String id=app.library.createPlaylist("Evening");app.library.add(id,one);app.library.add(id,two);app.library.add(id,one);
        assertEquals(2,app.library.playlistTracks(id).size());app.library.move(id,two.id(),-1);
        Library reopened=new Library(app);assertEquals(two.id(),reopened.playlistTracks(id).get(0).id());reopened.close();
        app.library.rename(id,"Evening favorites");assertTrue(app.library.playlists().stream().anyMatch(p->p.name().equals("Evening favorites")));
        app.library.remove(id,one.id());assertEquals(1,app.library.playlistTracks(id).size());
    }
    @Test public void missingFilesAreNeverReportedAsOffline()throws Exception{
        File f=new File(app.getFilesDir(),"test.audio");try(FileOutputStream out=new FileOutputStream(f)){out.write(new byte[]{1,2,3});}
        app.library.download(one,"ready",f.getAbsolutePath(),"");assertEquals(f.getAbsolutePath(),app.library.offline(one.id()));
        String id=app.library.createPlaylist("Offline mix");app.library.add(id,one);app.library.deletePlaylist(id);assertEquals(f.getAbsolutePath(),app.library.offline(one.id()));
        assertTrue(f.delete());assertNull(app.library.offline(one.id()));
    }
    @Test public void processDeathMarksDownloadsForRetry(){
        app.library.download(one,"queued",null,"");app.library.download(two,"downloading",null,"");app.library.recoverDownloads();
        for(var d:app.library.downloads()){assertEquals("failed",d.status());assertTrue(d.error().contains("interrupted"));assertNull(app.library.offline(d.track().id()));}
    }
    @Test public void homeLibraryAndOfflineScreensRenderOnAndroid16(){
        // Robolectric does not host the MediaSessionService Binder; device tests exercise playback.
        Shadows.shadowOf(app).declareComponentUnbindable(new android.content.ComponentName(app,PlaybackService.class));
        String id=app.library.createPlaylist("My evening mix");app.library.add(id,one);
        try(var controller=Robolectric.buildActivity(MainActivity.class).setup()){
            MainActivity activity=controller.get();View root=activity.getWindow().getDecorView();
            assertNotNull(find(root,"Made for you"));capture(root,"home-framework.png");click(root,"Library");assertNotNull(find(root,"My evening mix"));capture(root,"library-framework.png");
            click(root,"Offline");assertNotNull(find(root,"0 songs offline"));assertNotNull(find(root,"Find songs"));
            click(root,"Search");assertNotNull(find(root,"Find your next listen."));
        }
    }
    @Test public void playlistImportPreservesOrderWithoutDuplicatingSongs(){
        String id=app.library.importPlaylist("From YouTube",java.util.List.of(one,two,one));
        assertEquals(java.util.List.of(one,two),app.library.playlistTracks(id));
        assertNull("Saving links must not mark songs as downloaded",app.library.offline(one.id()));
    }
    @Test public void failedImportRollsBackPlaylistAndMembership(){
        int before=app.library.playlists().size();
        try{app.library.importPlaylist("Broken import",java.util.List.of(one,new Track("testtone003",null,"Artist","",1)));fail("Expected invalid track to fail");}
        catch(android.database.SQLException expected){}
        assertEquals(before,app.library.playlists().size());assertNull(app.library.track(one.id()));
    }
    @Test public void suggestedPlaylistCanBeOpenedPagedAndSaved()throws Exception{
        Shadows.shadowOf(app).declareComponentUnbindable(new android.content.ComponentName(app,PlaybackService.class));
        try(var controller=Robolectric.buildActivity(MainActivity.class).setup()){
            View root=controller.get().getWindow().getDecorView();awaitText(root,"Test discovery mix");capture(root,"playlists-framework.png");
            View card=findDescription(root,"Open YouTube playlist Test discovery mix");assertNotNull(card);card.performClick();
            awaitText(root,"First song");assertNotNull(find(root,"Save 1 songs to Library"));
            click(root,"Load more playlist songs");awaitText(root,"Second song");assertNotNull(find(root,"Save 2 songs to Library"));capture(root,"youtube-playlist-framework.png");
            click(root,"Save 2 songs to Library");android.app.AlertDialog dialog=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();assertNotNull(dialog);
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick();Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            Library.Playlist saved=app.library.playlists().stream().filter(p->p.name().equals("Test discovery mix")).findFirst().orElseThrow();
            assertEquals(java.util.List.of(one,two),app.library.playlistTracks(saved.id()));assertNull(app.library.offline(one.id()));
        }
    }
    @Test public void playlistDiscoveryFailureShowsRetryAndKeepsNavigationUsable()throws Exception{
        source.fail=true;Shadows.shadowOf(app).declareComponentUnbindable(new android.content.ComponentName(app,PlaybackService.class));
        try(var controller=Robolectric.buildActivity(MainActivity.class).setup()){
            View root=controller.get().getWindow().getDecorView();awaitText(root,"Retry playlists");source.fail=false;click(root,"Retry playlists");awaitText(root,"Test discovery mix");
            click(root,"Library");assertNotNull(find(root,"Liked songs"));
        }
    }
    @Test public void infoShowsDeveloperAndCurrentVersion(){
        Shadows.shadowOf(app).declareComponentUnbindable(new android.content.ComponentName(app,PlaybackService.class));
        try(var controller=Robolectric.buildActivity(MainActivity.class).setup()){
            View root=controller.get().getWindow().getDecorView();View info=findDescription(root,"MuseDock options");assertNotNull(info);info.performClick();org.robolectric.shadows.ShadowPopupMenu.getLatestPopupMenu().getMenu().performIdentifierAction(5,0);Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            android.app.AlertDialog dialog=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();assertNotNull(dialog);
            assertTrue(((TextView)dialog.findViewById(android.R.id.message)).getText().toString().contains("Developed by Shivam Jaiswal"));dialog.dismiss();
        }
    }
    @Test public void historyImportCreatesPlaylistAndPreservesMoreRecentLocalListening(){
        app.library.played(one);long timestamp=1;
        String id=app.library.importHistory(java.util.List.of(new HistoryImport.Entry(new Track(one.id(),"Imported title","YouTube history","",0),timestamp),new HistoryImport.Entry(two,1000)));
        assertEquals(java.util.List.of(one,two),app.library.playlistTracks(id));assertEquals(one.id(),app.library.recent().get(0).id());
    }
    @Test public void accountExplainsHistoryAndUnconfiguredLogin(){
        Shadows.shadowOf(app).declareComponentUnbindable(new android.content.ComponentName(app,PlaybackService.class));
        try(var controller=Robolectric.buildActivity(MainActivity.class).setup()){
            View root=controller.get().getWindow().getDecorView();assertNotNull(find(root,"MuseDock"));click(root,"Library");click(root,"Import YouTube history");
            assertNotNull(find(root,"Your MuseDock."));assertNotNull(find(root,"2. Choose history file"));assertFalse(find(root,"Sign in with Google").isEnabled());capture(root,"account-framework.png");
        }
    }
    @Test public void userCanCreatePlaylistFromLibrary(){
        Shadows.shadowOf(app).declareComponentUnbindable(new android.content.ComponentName(app,PlaybackService.class));
        try(var controller=Robolectric.buildActivity(MainActivity.class).setup()){
            View root=controller.get().getWindow().getDecorView();click(root,"Library");click(root,"+  Create playlist");Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            android.app.AlertDialog dialog=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();assertTrue(dialog.isShowing());
            android.widget.EditText name=findInput(dialog.getWindow().getDecorView());assertNotNull(name);name.setText("My own playlist");
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick();Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            assertTrue(app.library.playlists().stream().anyMatch(item->item.name().equals("My own playlist")));assertNotNull(find(root,"My own playlist"));assertFalse(dialog.isShowing());
        }
    }
    private android.widget.EditText findInput(View view){if(view instanceof android.widget.EditText input)return input;if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){android.widget.EditText found=findInput(group.getChildAt(i));if(found!=null)return found;}return null;}
    private void awaitText(View root,String text)throws Exception{
        long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
        do{Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();if(find(root,text)!=null)return;Thread.sleep(10);}while(System.nanoTime()<deadline);
        fail("Did not render: "+text);
    }
    private View findDescription(View root,String description){
        if(description.contentEquals(root.getContentDescription()==null?"":root.getContentDescription()))return root;
        if(root instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){View found=findDescription(group.getChildAt(i),description);if(found!=null)return found;}return null;
    }
    private void capture(View root,String name){
        root.measure(View.MeasureSpec.makeMeasureSpec(1080,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(2400,View.MeasureSpec.EXACTLY));root.layout(0,0,1080,2400);
        android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(1080,2400,android.graphics.Bitmap.Config.ARGB_8888);root.draw(new android.graphics.Canvas(bitmap));
        File dir=new File(System.getProperty("musedock.validation.dir"));assertTrue(dir.isDirectory()||dir.mkdirs());
        try(FileOutputStream out=new FileOutputStream(new File(dir,name))){assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out));}catch(IOException e){throw new AssertionError(e);}finally{bitmap.recycle();}
    }
    private TextView find(View root,String text){
        if(root instanceof TextView t&&t.getText().toString().equals(text))return t;
        if(root instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){TextView found=find(group.getChildAt(i),text);if(found!=null)return found;}
        return null;
    }
    private void click(View root,String text){View view=find(root,text);assertNotNull(text,view);while(!view.isClickable()&&view.getParent() instanceof View p)view=p;assertTrue(view.performClick());}
}
