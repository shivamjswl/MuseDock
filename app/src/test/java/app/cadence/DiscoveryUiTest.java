package app.cadence;

import android.app.*;
import android.content.ComponentName;
import android.graphics.*;
import android.graphics.drawable.BitmapDrawable;
import android.view.*;
import android.widget.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk={29,37},application=MuseDockApp.class,qualifiers="w411dp-h914dp-420dpi")
@LooperMode(LooperMode.Mode.PAUSED)
public final class DiscoveryUiTest {
    private MuseDockApp app;private final Queue<String> queries=new ConcurrentLinkedQueue<>();
    private Track track(int id,String title,String artist){String cover="https://fixture.invalid/cover/"+id;Bitmap bitmap=Bitmap.createBitmap(128,128,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(bitmap);Paint paint=new Paint(3);paint.setShader(new LinearGradient(0,0,128,128,0xff315e80+(id%12)*0x030400,0xff9b7b6b,Shader.TileMode.CLAMP));canvas.drawRect(0,0,128,128,paint);paint.setShader(null);paint.setColor(0xccffffff);paint.setTextSize(15);canvas.drawText("TEST MIX",16,65,paint);app.artworkCache.put(cover,bitmap);return new Track(String.format(Locale.ROOT,"mix%08d",id),title,artist,cover,180);}
    @Before public void setup(){
        app=(MuseDockApp)RuntimeEnvironment.getApplication();Shadows.shadowOf(app).grantPermissions(app.getPackageName()+".DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION");Shadows.shadowOf(app).declareComponentUnbindable(new ComponentName(app,PlaybackService.class));
        app.catalog=(query,page,mode)->{queries.add(query);String mood=query.contains("instrumental")?"Focus":query.contains("mellow")?"Chill":query.contains("upbeat")?"Energy":query.contains("emerging")?"Discovery":"Personal";int offset=switch(mood){case "Focus"->100;case "Chill"->200;case "Energy"->300;case "Discovery"->400;default->500;};List<Track> songs=new ArrayList<>();for(int i=0;i<8;i++)songs.add(track(offset+i,mood+" song "+i,"Test artist "+i));return new MusicCatalog.Result(songs,List.of(),null,query);};
        app.playlists=new PlaylistSource(){public List<Suggestion> suggest(String q){int hash=Math.floorMod(q.hashCode(),90000);Track cover=track(hash,"Test playlist","Test curator");return List.of(new Suggestion("https://www.youtube.com/playlist?list=PL_"+hash,"Mix · "+q,"Test curator",cover.artwork(),8));}public Tracks playlist(String url,org.schabi.newpipe.extractor.Page page){return new Tracks("Test playlist",List.of(track(700,"Test playlist song","Test artist")),null);}};
    }
    @After public void cleanup(){app.library.close();app.equalizer.release();}
    @Test public void moodTilesShowBitmapArtworkAndOpenDifferentSongs()throws Exception{
        app.library.played(track(1,"Favorite song","Seed artist"));app.library.recordSearch("electric guitar");
        try(var controller=Robolectric.buildActivity(MainActivity.class).setup()){
            View root=controller.get().getWindow().getDecorView();await(()->queries.stream().anyMatch(q->q.contains("electric guitar")));await(()->hasBitmap(description(root,"Open mood Deep focus"))&&hasBitmap(description(root,"Open mood Turn it up")));description(root,"Open mood Deep focus").performClick();idle();awaitText(root,"Focus song 0");assertNull(find(root,"Energy song 0"));click(root,"Listen");description(root,"Open mood Turn it up").performClick();idle();awaitText(root,"Energy song 0");assertNull(find(root,"Focus song 0"));
        }
    }
    @Test public void aNewSearchChangesTheHomeTasteAndPlaylistQueries()throws Exception{
        app.library.played(track(1,"Favorite","Seed artist"));app.library.add("liked",track(2,"Liked song","Other artist"));
        try(var controller=Robolectric.buildActivity(MainActivity.class).setup()){
            View root=controller.get().getWindow().getDecorView();click(root,"Search");EditText input=input(root);input.setText("new acoustic artist");input.onEditorAction(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);awaitText(root,"Personal song 0");click(root,"Listen");awaitText(root,"Because you searched “new acoustic artist”");await(()->queries.stream().anyMatch(q->q.equals("new acoustic artist")));assertEquals("new acoustic artist",app.library.searches().get(0).query());
        }
    }
    @Test public void playlistDetailsPinDuplicateAndBulkRemovalWorkThroughUi()throws Exception{
        String id=app.library.createPlaylist("Weekend");app.library.addAll(id,List.of(track(1,"One","First"),track(2,"Two","Second")));
        try(var controller=Robolectric.buildActivity(MainActivity.class).setup()){
            View root=controller.get().getWindow().getDecorView();click(root,"Library");click(root,"Weekend");description(root,"Playlist options").performClick();menu("Edit details");AlertDialog form=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();List<EditText> fields=new ArrayList<>();inputs(form.getWindow().getDecorView(),fields);fields.get(0).setText("Road trip");fields.get(1).setText("For the open road");form.getButton(AlertDialog.BUTTON_POSITIVE).performClick();idle();assertNotNull(find(root,"For the open road"));description(root,"Playlist options").performClick();menu("Pin playlist");assertTrue(app.library.playlists().stream().filter(p->p.id().equals(id)).findFirst().orElseThrow().pinned());description(root,"Playlist options").performClick();menu("Duplicate playlist");form=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();input(form.getWindow().getDecorView()).setText("Road trip copy");form.getButton(AlertDialog.BUTTON_POSITIVE).performClick();idle();String copy=app.library.playlists().stream().filter(p->p.name().equals("Road trip copy")).findFirst().orElseThrow().id();assertEquals(2,app.library.playlistTracks(copy).size());description(root,"Playlist options").performClick();menu("Select songs");form=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();form.getButton(AlertDialog.BUTTON_NEUTRAL).performClick();form.getButton(AlertDialog.BUTTON_POSITIVE).performClick();idle();assertTrue(app.library.playlistTracks(copy).isEmpty());assertEquals(2,app.library.playlistTracks(id).size());
        }
    }
    @Test public void playlistSuggestionsCanBeAddedWithoutDuplicateMembers()throws Exception{
        String id=app.library.createPlaylist("Favorites");app.library.add(id,track(1,"Existing song","Seed artist"));
        try(var controller=Robolectric.buildActivity(MainActivity.class).setup()){
            View root=controller.get().getWindow().getDecorView();click(root,"Library");click(root,"Favorites");awaitText(root,"Add all suggestions");click(root,"Add all suggestions");assertTrue(app.library.playlistTracks(id).size()>1);assertEquals(app.library.playlistTracks(id).size(),app.library.playlistTracks(id).stream().map(Track::id).distinct().count());assertTrue(app.library.contains(id,"mix00000001"));
        }
    }
    @Test public void advancedSoundControlsSaveAndReloadNamedPresets(){
        try(var controller=Robolectric.buildActivity(MainActivity.class).setup()){
            View root=controller.get().getWindow().getDecorView();description(root,"MuseDock options").performClick();org.robolectric.shadows.ShadowPopupMenu.getLatestPopupMenu().getMenu().performIdentifierAction(1,0);idle();View sound=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog().getWindow().getDecorView();assertNotNull(description(sound,"Stereo balance"));assertNotNull(description(sound,"Preamp attenuation"));assertNotNull(find(sound,"Auto headroom"));click(sound,"Rock");click(sound,"Save as preset");AlertDialog save=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();input(save.getWindow().getDecorView()).setText("My headphones");save.getButton(AlertDialog.BUTTON_POSITIVE).performClick();idle();assertTrue(app.equalizer.customPresets().contains("My headphones"));click(sound,"Reset sound settings");click(sound,"Saved presets");menu("My headphones");assertEquals("My headphones",app.equalizer.preset());
        }
    }
    @Test @Config(sdk=37) @GraphicsMode(GraphicsMode.Mode.NATIVE)
    public void personalizedHomeMixPlaylistAndSoundRender()throws Exception{
        app.library.played(track(1,"A familiar favorite","Seed artist"));app.library.recordSearch("acoustic guitar");String id=app.library.createPlaylist("Golden hour drive");app.library.addAll(id,List.of(track(2,"Early sunlight","Seed artist"),track(3,"Wide open roads","Other artist"),track(4,"A little room to breathe","Third artist"),track(5,"Back to the coast","Fourth artist")));
        try(var controller=Robolectric.buildActivity(MainActivity.class).setup()){
            View root=controller.get().getWindow().getDecorView();await(()->hasBitmap(description(root,"Open mood Deep focus"))&&hasBitmap(description(root,"Open mood Turn it up")));capture(root,"personalized-home-framework.png");description(root,"Open mood Deep focus").performClick();idle();awaitText(root,"Focus song 0");capture(root,"focus-mix-framework.png");click(root,"Library");click(root,"Golden hour drive");awaitText(root,"Add all suggestions");capture(root,"playlist-updates-framework.png");description(root,"MuseDock options").performClick();org.robolectric.shadows.ShadowPopupMenu.getLatestPopupMenu().getMenu().performIdentifierAction(1,0);idle();View sound=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog().getWindow().getDecorView();capture(sound,"sound-studio-framework.png");View ancestor=description(sound,"Preamp attenuation");while(!(ancestor instanceof ScrollView))ancestor=(View)ancestor.getParent();ScrollView scroll=(ScrollView)ancestor;scroll.scrollTo(0,scroll.getChildAt(0).getHeight());assertTrue(scroll.getScrollY()>0);sound.invalidate();capture(sound,"sound-studio-advanced-framework.png");
        }
    }
    private boolean hasBitmap(View root){if(root==null)return false;if(root instanceof ImageView image&&image.getDrawable() instanceof BitmapDrawable)return true;if(root instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++)if(hasBitmap(group.getChildAt(i)))return true;return false;}
    private interface Condition{boolean satisfied();}
    private void await(Condition condition)throws Exception{long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);do{idle();if(condition.satisfied())return;Thread.sleep(10);}while(System.nanoTime()<deadline);assertTrue(condition.satisfied());}
    private void awaitText(View root,String text)throws Exception{await(()->find(root,text)!=null);}
    private void menu(String title){AlertDialog dialog=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();ListView list=dialog.getListView();int index=-1;for(int i=0;i<list.getCount();i++)if(title.equals(list.getAdapter().getItem(i).toString()))index=i;assertTrue(title,index>=0);list.performItemClick(null,index,index);idle();}
    private void idle(){Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();}
    private TextView find(View root,String value){if(root instanceof TextView t&&t.getText().toString().equals(value))return t;if(root instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){TextView v=find(group.getChildAt(i),value);if(v!=null)return v;}return null;}
    private void inputs(View root,List<EditText> out){if(root instanceof EditText e)out.add(e);if(root instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++)inputs(group.getChildAt(i),out);}
    private EditText input(View root){List<EditText> out=new ArrayList<>();inputs(root,out);return out.isEmpty()?null:out.get(0);}
    private ScrollView scroll(View root){if(root instanceof ScrollView s)return s;if(root instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){ScrollView v=scroll(group.getChildAt(i));if(v!=null)return v;}return null;}
    private View description(View root,String value){if(value.contentEquals(root.getContentDescription()==null?"":root.getContentDescription()))return root;if(root instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){View v=description(group.getChildAt(i),value);if(v!=null)return v;}return null;}
    private void click(View root,String value){View v=find(root,value);assertNotNull(value,v);while(!v.isClickable()&&v.getParent() instanceof View parent)v=parent;v.performClick();idle();}
    private void capture(View root,String name)throws Exception{root.measure(View.MeasureSpec.makeMeasureSpec(1080,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(2400,View.MeasureSpec.EXACTLY));root.layout(0,0,1080,2400);Bitmap bitmap=Bitmap.createBitmap(1080,2400,Bitmap.Config.ARGB_8888);root.draw(new Canvas(bitmap));File dir=new File(System.getProperty("musedock.validation.dir"));assertTrue(dir.isDirectory()||dir.mkdirs());try(FileOutputStream out=new FileOutputStream(new File(dir,name))){assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,out));}finally{bitmap.recycle();}}
}
