package app.cadence;

import android.app.*;
import android.content.*;
import android.content.res.Configuration;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import androidx.media3.exoplayer.ExoPlayer;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

@RunWith(RobolectricTestRunner.class)
@Config(sdk={29,37},application=MuseDockApp.class,qualifiers="w411dp-h914dp-420dpi")
@LooperMode(LooperMode.Mode.PAUSED)
public final class AppearanceTest {
    private MuseDockApp app;private volatile String requested="";private final Track song=new Track("testtone001","Morning light","MuseDock test artist","",120);
    @Before public void setup(){
        app=(MuseDockApp)RuntimeEnvironment.getApplication();Bitmap cover=Bitmap.createBitmap(1280,1280,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(cover);Paint paint=new Paint(3);paint.setShader(new LinearGradient(0,0,1280,1280,0xffb7597b,0xff263475,Shader.TileMode.CLAMP));canvas.drawRect(0,0,1280,1280,paint);paint.setShader(null);paint.setColor(0x887bd9cf);canvas.drawCircle(900,360,410,paint);paint.setColor(0xfff8e9d7);paint.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));paint.setTextSize(116);canvas.drawText("MORNING",80,900,paint);canvas.drawText("LIGHT",80,1030,paint);paint.setTextSize(28);canvas.drawText("MUSEDOCK · ORIGINAL TEST ARTWORK",80,1140,paint);app.artworkCache.put("https://i.ytimg.com/vi/testtone001/maxresdefault.jpg",cover);app.artworkCache.put("https://i.ytimg.com/vi/testtone001/sddefault.jpg",cover);app.getSharedPreferences("musedock.preferences",0).edit().clear().commit();Shadows.shadowOf(app).grantPermissions(app.getPackageName()+".DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION");Shadows.shadowOf(app).declareComponentUnbindable(new ComponentName(app,PlaybackService.class));
        app.playlists=new PlaylistSource(){public List<Suggestion> suggest(String q){return List.of();}public Tracks playlist(String q,org.schabi.newpipe.extractor.Page next){return new Tracks("",List.of(),null);}};
        app.catalog=(q,next,mode)->{if(q.endsWith("acoustic songs"))requested=q;return new MusicCatalog.Result(List.of(song),List.of(),next==null?new org.schabi.newpipe.extractor.Page("test-next"):null,q);};
    }
    @After public void cleanup(){app.library.close();app.equalizer.release();}
    @Test public void libraryUsesSmallerTypeAndMuseDockBranding(){
        String id=app.library.createPlaylist("Everyday favorites");app.library.add(id,song);
        try(var controller=Robolectric.buildActivity(MainActivity.class).setup()){
            View root=controller.get().getWindow().getDecorView();click(root,"Library");assertNotNull(find(root,"Your Library"));float scale=app.getResources().getDisplayMetrics().scaledDensity;assertTrue(find(root,"Your Library").getTextSize()/scale<=26.1f);assertTrue(find(root,"Everyday favorites").getTextSize()/scale<=15.1f);assertNull(find(root,"Lyrics"));assertNull(find(root,"Equalizer"));assertBranding(root);
        }
    }
    @Test public void equalizerLivesInOverflowAndPlayerHasNoLargeExtras(){
        ExoPlayer player=new ExoPlayer.Builder(app).build();
        try(var controller=Robolectric.buildActivity(MainActivity.class).setup()){
            player.setMediaItem(song.mediaItem(null));controller.get().attachPlayer(player);View root=controller.get().getWindow().getDecorView();description(root,"Open now playing").performClick();idle();Dialog now=org.robolectric.shadows.ShadowDialog.getLatestDialog();View page=now.getWindow().getDecorView();assertNull(find(page,"Lyrics"));assertNull(find(page,"Equalizer"));View more=description(page,"Playback options");assertNotNull(more);more.performClick();idle();AlertDialog menu=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();ListView list=menu.getListView();int index=-1;for(int i=0;i<list.getCount();i++)if(list.getAdapter().getItem(i).toString().equals("Equalizer"))index=i;assertTrue(index>=0);list.performItemClick(null,index,index);idle();assertNotNull(find(org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog().getWindow().getDecorView(),"Equalizer enabled"));
        }finally{player.release();}
    }
    @Test public void languageSelectionPersistsAndAppliesAcrossSearchPages()throws Exception{
        try(var controller=Robolectric.buildActivity(MainActivity.class).setup()){
            View root=controller.get().getWindow().getDecorView();click(root,"Search");EditText input=input(root);input.setText("acoustic songs");input.onEditorAction(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);awaitQuery("acoustic songs");
            description(root,"Select music language").performClick();AlertDialog dialog=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();dialog.getListView().performItemClick(null,MusicLanguage.PUNJABI.ordinal(),MusicLanguage.PUNJABI.ordinal());idle();awaitQuery("Punjabi acoustic songs");assertEquals(MusicLanguage.PUNJABI,MusicLanguage.selected(app));assertEquals("acoustic songs",input(root).getText().toString());awaitText(root,"Load more songs");click(root,"Load more songs");awaitQuery("Punjabi acoustic songs");
        }
        try(var controller=Robolectric.buildActivity(MainActivity.class).setup()){assertNotNull(find(controller.get().getWindow().getDecorView(),"Music language · Punjabi"));}
    }
    @Test public void appearancePickerPersistsSelectionAndFreshActivityUsesIt(){
        try(var controller=Robolectric.buildActivity(MainActivity.class).setup()){
            View root=controller.get().getWindow().getDecorView();description(root,"MuseDock options").performClick();org.robolectric.shadows.ShadowPopupMenu.getLatestPopupMenu().getMenu().performIdentifierAction(3,0);idle();root=controller.get().getWindow().getDecorView();assertNotNull(find(root,"Appearance"));description(root,"Use Light theme").performClick();idle();assertEquals(ThemePalette.Mode.LIGHT,ThemePalette.selected(app));
        }
        try(var controller=Robolectric.buildActivity(MainActivity.class).setup()){View root=controller.get().getWindow().getDecorView();assertEquals(ThemePalette.current(app).foreground(),find(root,"MuseDock").getCurrentTextColor());assertTrue(ThemePalette.current(app).light());}
    }
    @Test public void materialFollowsSystemNightModeAndPalettesRemainReadable(){
        Configuration configuration=new Configuration(app.getResources().getConfiguration());configuration.uiMode=(configuration.uiMode&~Configuration.UI_MODE_NIGHT_MASK)|Configuration.UI_MODE_NIGHT_YES;Context night=app.createConfigurationContext(configuration);assertFalse(ThemePalette.forMode(night,ThemePalette.Mode.MATERIAL).light());configuration.uiMode=(configuration.uiMode&~Configuration.UI_MODE_NIGHT_MASK)|Configuration.UI_MODE_NIGHT_NO;Context day=app.createConfigurationContext(configuration);assertTrue(ThemePalette.forMode(day,ThemePalette.Mode.MATERIAL).light());
        for(ThemePalette.Mode mode:ThemePalette.Mode.values()){ThemePalette p=ThemePalette.forMode(app,mode);assertTrue(mode.label+" foreground contrast",contrast(p.foreground(),p.background())>=4.5);assertTrue(mode.label+" muted contrast",contrast(p.muted(),p.background())>=4.5);}
    }
    @Test @Config(sdk=37) @GraphicsMode(GraphicsMode.Mode.NATIVE)
    public void libraryAndPlayerRenderInAllFourThemes()throws Exception{
        for(String name:List.of("Daily favorites","Late night drive","Punjabi essentials")){String id=app.library.createPlaylist(name);app.library.add(id,song);}
        for(ThemePalette.Mode mode:ThemePalette.Mode.values()){
            ThemePalette.save(app,mode);ExoPlayer player=new ExoPlayer.Builder(app).build();
            try(var controller=Robolectric.buildActivity(MainActivity.class).setup()){
                View root=controller.get().getWindow().getDecorView();click(root,"Library");capture(root,"library-"+mode.name().toLowerCase(java.util.Locale.ROOT)+"-framework.png");assertBranding(root);player.setMediaItem(song.mediaItem(null));controller.get().attachPlayer(player);description(root,"Open now playing").performClick();idle();Dialog now=org.robolectric.shadows.ShadowDialog.getLatestDialog();capture(now.getWindow().getDecorView(),"player-"+mode.name().toLowerCase(java.util.Locale.ROOT)+"-framework.png");assertNull(find(now.getWindow().getDecorView(),"Lyrics"));now.dismiss();EqualizerPanel eq=new EqualizerPanel(controller.get(),app.equalizer);eq.show();idle();capture(org.robolectric.shadows.ShadowDialog.getLatestDialog().getWindow().getDecorView(),"equalizer-"+mode.name().toLowerCase(java.util.Locale.ROOT)+"-framework.png");eq.dismiss();
            }finally{player.release();}
        }
    }
    private double luminance(int color){double result=0;double[] weights={.2126,.7152,.0722};int[] channels={Color.red(color),Color.green(color),Color.blue(color)};for(int i=0;i<3;i++){double value=channels[i]/255.0;result+=weights[i]*(value<=.04045?value/12.92:Math.pow((value+.055)/1.055,2.4));}return result;}
    private double contrast(int a,int b){double x=luminance(a),y=luminance(b);return (Math.max(x,y)+.05)/(Math.min(x,y)+.05);}
    private void assertBranding(View root){if(root instanceof TextView t){String text=t.getText().toString().toLowerCase(java.util.Locale.ROOT);assertFalse(text.contains("cadence"));assertFalse(text.contains("soundzy"));}if(root instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++)assertBranding(group.getChildAt(i));}
    private void capture(View root,String name)throws Exception{root.measure(View.MeasureSpec.makeMeasureSpec(1080,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(2400,View.MeasureSpec.EXACTLY));root.layout(0,0,1080,2400);Bitmap bitmap=Bitmap.createBitmap(1080,2400,Bitmap.Config.ARGB_8888);root.draw(new Canvas(bitmap));File dir=new File(System.getProperty("musedock.validation.dir"));assertTrue(dir.isDirectory()||dir.mkdirs());try(FileOutputStream out=new FileOutputStream(new File(dir,name))){assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,out));}finally{bitmap.recycle();}}
    private void idle(){Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();}
    private void awaitQuery(String expected)throws Exception{long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(6);do{idle();if(expected.equals(requested))return;Thread.sleep(10);}while(System.nanoTime()<deadline);assertEquals(expected,requested);}
    private void awaitText(View root,String text)throws Exception{long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(6);do{idle();if(find(root,text)!=null)return;Thread.sleep(10);}while(System.nanoTime()<deadline);assertNotNull(text,find(root,text));}
    private TextView find(View root,String value){if(root instanceof TextView t&&t.getText().toString().equals(value))return t;if(root instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){TextView v=find(group.getChildAt(i),value);if(v!=null)return v;}return null;}
    private EditText input(View root){if(root instanceof EditText e)return e;if(root instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){EditText v=input(group.getChildAt(i));if(v!=null)return v;}return null;}
    private View description(View root,String value){if(value.contentEquals(root.getContentDescription()==null?"":root.getContentDescription()))return root;if(root instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){View v=description(group.getChildAt(i),value);if(v!=null)return v;}return null;}
    private void click(View root,String value){View v=find(root,value);assertNotNull(value,v);while(!v.isClickable()&&v.getParent() instanceof View parent)v=parent;v.performClick();idle();}
}
