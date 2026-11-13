package app.cadence;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.text.*;
import android.view.*;
import android.view.inputmethod.*;
import android.widget.*;
import androidx.media3.common.*;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.media3.session.*;
import androidx.media3.session.MediaController;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.*;
import java.util.concurrent.*;

public final class MainActivity extends androidx.activity.ComponentActivity {
    private ThemePalette palette;
    private int BG,SURFACE,RAISED,FG,MUTED,GREEN,INK;
    private MuseDockApp app;
    private LinearLayout root,body,mini,nav;
    private ScrollView scroll;
    private Player player;
    private ListenableFuture<MediaController> connection;
    private final ExecutorService network=Executors.newSingleThreadExecutor();
    private final ExecutorService discovery=BackgroundWork.pool(2,"MuseDock-discovery");
    private final ExecutorService files=BackgroundWork.pool(1,"MuseDock-library");
    private final ExecutorService images=BackgroundWork.pool(2,"MuseDock-artwork");
    private final Map<String,CompletableFuture<Bitmap>> artworkRequests=new ConcurrentHashMap<>();
    private LinearLayout nowPage;
    private final Map<String,Long> artworkFailures=new ConcurrentHashMap<>();
    private final Handler handler=new Handler(Looper.getMainLooper());
    private String tab="home",playlistId=null,playlistName="",query="";
    private final ArrayList<Track> results=new ArrayList<>();
    private final ArrayList<PlaylistSource.Suggestion> albumResults=new ArrayList<>();
    private MusicCatalog.Mode searchMode=MusicCatalog.Mode.SONGS;
    private EqualizerPanel equalizerPanel;
    private org.schabi.newpipe.extractor.Page nextPage;
    private boolean searching=false;
    private int searchVersion=0;
    private String searchError="";
    private String resultQuery="";
    private LinearLayout suggestionsContainer,discoveryContainer;
    private final EnumMap<Recommendations.Mood,List<Track>> shelves=new EnumMap<>(Recommendations.Mood.class);
    private final EnumMap<Recommendations.Mood,String> shelfErrors=new EnumMap<>(Recommendations.Mood.class);
    private final Set<Recommendations.Mood> shelfLoading=EnumSet.noneOf(Recommendations.Mood.class);
    private final EnumMap<Recommendations.Mood,Future<?>> discoveryJobs=new EnumMap<>(Recommendations.Mood.class);
    private Recommendations.Profile profileSnapshot=new Recommendations.Profile(List.of(),List.of(),Set.of(),MusicLanguage.ANY,0);
    private Future<?> profileJob;private boolean discoveryDirty=true;
    private final Runnable drawShelves=()->{if(this.closing||!discoveryVisible())return;drawDiscovery();if(tab.equals("mix"))render();};
    private long discoveryLoadedAt;
    private int discoveryVersion=0;private String discoveryFingerprint="";private Recommendations.Mood openedMood=Recommendations.Mood.FOR_YOU;
    private String addTargetPlaylistId=null,playlistRecId="";private final List<Track> playlistAdditions=new ArrayList<>();private boolean playlistRecLoading;private int playlistRecVersion,playlistRecRefresh;

    private final ArrayList<PlaylistSource.Suggestion> suggestions=new ArrayList<>();
    private boolean suggestionsLoading=false;
    private String suggestionsFingerprint="";
    private String suggestionsError="",suggestionsQuery="",suggestionFilter="For you";
    private int suggestionsVersion=0,remoteVersion=0;
    private Future<?> suggestionsJob,remoteJob;
    private PlaylistSource.Suggestion remotePlaylist;
    private boolean remoteAlbum;
    private final ArrayList<Track> remoteTracks=new ArrayList<>();
    private org.schabi.newpipe.extractor.Page remoteNext;
    private boolean remoteLoading=false;
    private String remoteError="",remoteBack="home";
    private Dialog nowDialog;
    private QueuePanel queuePanel;
    private GoogleAccount googleAccount;
    private volatile boolean closing=false;private boolean foreground;
    private String pendingExport;
    private static final int PICK_HISTORY=81,EXPORT_AUDIO=82;
    private TextView nowTitle,nowArtist,nowStatus,elapsed,total,miniTitle,miniArtist;
    private Track miniTrack,nowTrack;
    private String playbackStatusId="";private boolean playbackLiked,playbackOffline;
    private ImageView nowArt;
    private FrameLayout nowArtFrame;
    private SeekBar seek;
    private Icons playIcon,likeIcon,shuffleIcon,repeatIcon,miniPlay;
    private boolean dragging=false,adaptivePlayback=false;private Recommendations.Mood adaptiveMood=Recommendations.Mood.FOR_YOU;
    private void playMix(List<Track> tracks,int index,boolean shuffle){adaptivePlayback=true;adaptiveMood=tab.equals("mix")?openedMood:Recommendations.Mood.FOR_YOU;try{play(tracks,index,shuffle);}finally{adaptivePlayback=false;}}
    private final Runnable tasteRefresh=()->{discoveryDirty=true;if(!closing&&discoveryVisible())refreshDiscovery(false);};
    private final BroadcastReceiver downloadsChanged=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){if("app.musedock.TASTE_CHANGED".equals(i.getAction())){handler.removeCallbacks(tasteRefresh);handler.postDelayed(tasteRefresh,750);}else{playbackStatusId="";updateNow();if(tab.equals("offline"))render();}}};
    private final Player.Listener listener=new Player.Listener(){
        @Override public void onEvents(Player p,Player.Events events){updateMini();updateNow();}
        @Override public void onPlayerError(PlaybackException error){toast("Playback failed. Check your connection, then tap Play to retry.");}
    };
    private final Runnable ticker=new Runnable(){public void run(){updatePosition();handler.postDelayed(this,500);}};
    @Override public void onCreate(Bundle state){
        palette=ThemePalette.current(this);setTheme(palette.style());super.onCreate(state);app=MuseDockApp.of(this);
        BG=palette.background();SURFACE=palette.surface();RAISED=palette.raised();FG=palette.foreground();MUTED=palette.muted();GREEN=palette.accent();INK=palette.onAccent();
        WindowCompat.getInsetsController(getWindow(),getWindow().getDecorView()).setAppearanceLightStatusBars(palette.light());WindowCompat.getInsetsController(getWindow(),getWindow().getDecorView()).setAppearanceLightNavigationBars(palette.light());
        if(state!=null){addTargetPlaylistId=state.getString("addTarget");openedMood=Recommendations.Mood.valueOf(state.getString("mood","FOR_YOU"));searchMode="ALBUMS".equals(state.getString("searchMode"))?MusicCatalog.Mode.ALBUMS:MusicCatalog.Mode.SONGS;tab=state.getString("tab","home");query=state.getString("query","");playlistId=state.getString("playlist");playlistName=state.getString("playlistName","");pendingExport=state.getString("pendingExport");}
        if(getIntent().getBooleanExtra("offline",false))tab="offline";
        WindowCompat.setDecorFitsSystemWindows(getWindow(),false);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        render();
        connection=new MediaController.Builder(this,new SessionToken(this,new ComponentName(this,PlaybackService.class))).buildAsync();
        connection.addListener(()->{if(closing||isDestroyed())return;try{attachPlayer(connection.get());}catch(Exception e){toast("Could not connect to the audio player. Restart MuseDock to retry.");}},getMainExecutor());
        getOnBackPressedDispatcher().addCallback(this,new androidx.activity.OnBackPressedCallback(true){@Override public void handleOnBackPressed(){goBack();}});
        androidx.core.content.ContextCompat.registerReceiver(this,downloadsChanged,tasteFilter(),androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED);
        handler.post(ticker);
        handler.post(()->{loadSuggestions(recommendationQuery());refreshDiscovery(false);});
    }
    @Override protected void onStart(){super.onStart();foreground=true;handler.removeCallbacks(ticker);handler.post(ticker);if(discoveryDirty)handler.post(tasteRefresh);}
    @Override protected void onStop(){foreground=false;handler.removeCallbacks(ticker);pauseDiscovery();super.onStop();}
    @Override protected void onSaveInstanceState(Bundle s){super.onSaveInstanceState(s);s.putString("addTarget",addTargetPlaylistId);s.putString("mood",openedMood.name());s.putString("searchMode",searchMode.name());s.putString("tab",tab);s.putString("query",query);s.putString("playlist",playlistId);s.putString("playlistName",playlistName);s.putString("pendingExport",pendingExport);}
    @Override protected void onDestroy(){
        closing=true;if(equalizerPanel!=null)equalizerPanel.dismiss();if(googleAccount!=null)googleAccount.cancel();if(queuePanel!=null)queuePanel.dismiss();
        handler.removeCallbacksAndMessages(null);unregisterReceiver(downloadsChanged);network.shutdownNow();discovery.shutdownNow();files.shutdownNow();images.shutdownNow();
        if(player!=null)player.removeListener(listener);if(connection!=null)MediaController.releaseFuture(connection);if(nowDialog!=null)nowDialog.dismiss();super.onDestroy();
    }
    void attachPlayer(Player connected){if(player!=null)player.removeListener(listener);player=connected;player.addListener(listener);updateMini();handler.removeCallbacks(tasteRefresh);handler.postDelayed(tasteRefresh,750);}
    private IntentFilter tasteFilter(){IntentFilter filter=new IntentFilter(OfflineService.CHANGED);filter.addAction("app.musedock.TASTE_CHANGED");return filter;}
    private void goBack(){
        if(tab.equals("youtube-playlist")){tab=remoteBack;render();}
        else if(playlistId!=null){playlistId=null;render();}
        else if(!tab.equals("home")){tab="home";render();}
        else finish();
    }
    private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private GradientDrawable bg(int color,int radius){return palette.card(this,color,radius);}
    private GradientDrawable border(int color,int radius){GradientDrawable d=bg(color,radius);d.setStroke(dp(1),palette.outline());return d;}
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    private TextView text(String s,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setFontFeatureSettings("kern");if(bold)t.setTypeface(android.graphics.Typeface.create("sans-serif",android.graphics.Typeface.BOLD));return t;}
    private TextView label(String s,int size,int color,boolean bold){TextView t=text(s,size,color,bold);t.setMaxLines(1);t.setEllipsize(TextUtils.TruncateAt.END);return t;}
    private void space(LinearLayout l,int n){View v=new View(this);l.addView(v,new LinearLayout.LayoutParams(1,dp(n)));}
    private void add(LinearLayout parent,View v,int height){parent.addView(v,new LinearLayout.LayoutParams(-1,height<0?height:dp(height)));}
    private void inset(LinearLayout l,int n){l.setPadding(dp(n),dp(n),dp(n),dp(n));}
    private Icons icon(String name,int color,Runnable action){Icons i=new Icons(this,name,color);i.setLayoutParams(new LinearLayout.LayoutParams(dp(48),dp(48)));if(action!=null){i.setOnClickListener(v->action.run());i.setBackground(selectable());}return i;}
    private android.graphics.drawable.Drawable selectable(){android.util.TypedValue out=new android.util.TypedValue();getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless,out,true);return getDrawable(out.resourceId);}
    private TextView button(String title,boolean primary,Runnable action){TextView b=label(title,14,primary?INK:FG,true);b.setGravity(Gravity.CENTER);b.setMinHeight(dp(48));b.setPadding(dp(18),dp(10),dp(18),dp(10));b.setBackground(primary?bg(GREEN,26):border(SURFACE,26));b.setOnClickListener(v->action.run());return b;}
    private void section(String title,String action,Runnable click){LinearLayout r=row();r.addView(text(title,21,FG,true),new LinearLayout.LayoutParams(0,-2,1));if(action!=null)r.addView(button(action,false,click));add(body,r,56);}
    private void navigate(String target){if(!target.equals("home")&&!target.equals("mix"))pauseDiscovery();tab=target;playlistId=null;addTargetPlaylistId=null;hideKeyboard();render();if(target.equals("home")){refreshDiscovery(false);loadSuggestions(recommendationQuery());}}
    private void render(){
        root=column();root.setBackground(palette.backdrop());setContentView(root);
        ViewCompat.setOnApplyWindowInsetsListener(root,(v,insets)->{androidx.core.graphics.Insets sys=insets.getInsets(WindowInsetsCompat.Type.systemBars()|WindowInsetsCompat.Type.displayCutout()|WindowInsetsCompat.Type.ime());v.setPadding(sys.left,sys.top,sys.right,sys.bottom);return insets;});root.requestApplyInsets();
        LinearLayout header=row();header.setPadding(dp(16),dp(6),dp(12),dp(4));
        Icons mark=icon("wave",GREEN,null);header.addView(mark);TextView brand=text("MuseDock",22,FG,true);brand.setLetterSpacing(-.04f);header.addView(brand,new LinearLayout.LayoutParams(0,-2,1));Icons queue=icon("queue",MUTED,this::showQueue);queue.setContentDescription("View playback queue");header.addView(queue);Icons options=icon("more",MUTED,null);options.setContentDescription("MuseDock options");options.setOnClickListener(v->showOptions(options));header.addView(options);add(root,header,64);
        scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setClipToPadding(false);scroll.setVerticalScrollBarEnabled(false);
        body=column();body.setPadding(dp(22),dp(12),dp(22),dp(28));scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        switch(tab){case "search"->searchScreen();case "library"->{if(playlistId==null)libraryScreen();else playlistScreen();}case "offline"->offlineScreen();case "youtube-playlist"->remotePlaylistScreen();case "account"->accountScreen();case "settings"->settingsScreen();case "mix"->mixScreen();default->homeScreen();}
        mini=column();mini.setPadding(dp(10),0,dp(10),dp(4));add(root,mini,-2);updateMini();
        nav=row();nav.setPadding(dp(8),dp(4),dp(8),dp(6));nav.setBackgroundColor(BG);
        String[][] tabs={{"home","home","Listen"},{"search","search","Search"},{"library","library","Library"},{"offline","download","Offline"}};
        for(String[] t:tabs){LinearLayout item=column();item.setGravity(Gravity.CENTER);boolean active=t[0].equals(tab)||(t[0].equals("home")&&(tab.equals("youtube-playlist")||tab.equals("mix")));Icons i=icon(t[1],active?GREEN:MUTED,null);FrameLayout bubble=new FrameLayout(this);if(active)bubble.setBackground(bg(palette.selected(),24));bubble.addView(i,new FrameLayout.LayoutParams(dp(32),dp(30),Gravity.CENTER));item.addView(bubble,new LinearLayout.LayoutParams(dp(64),dp(30)));TextView name=label(t[2],11,active?GREEN:MUTED,active);name.setGravity(Gravity.CENTER);item.addView(name);item.setContentDescription(t[2]);item.setOnClickListener(v->navigate(t[0]));nav.addView(item,new LinearLayout.LayoutParams(0,dp(56),1));}
        add(root,nav,-2);
    }
    private void showOptions(View anchor){
        PopupMenu menu=new PopupMenu(this,anchor);menu.getMenu().add(0,1,0,"Equalizer");menu.getMenu().add(0,2,1,"Music language");menu.getMenu().add(0,3,2,"Appearance");menu.getMenu().add(0,4,3,"Account & history");menu.getMenu().add(0,5,4,"App info");
        menu.setOnMenuItemClickListener(item->{switch(item.getItemId()){case 1->showEqualizer();case 2->chooseLanguage();case 3->navigate("settings");case 4->navigate("account");case 5->showAbout();}return true;});menu.show();
    }
    private void addLanguageChip(){
        MusicLanguage language=MusicLanguage.selected(this);TextView chip=button("Music language · "+language.label,false,this::chooseLanguage);chip.setTextSize(12);chip.setContentDescription("Select music language");add(body,chip,44);
    }
    private void chooseLanguage(){
        MusicLanguage[] languages=MusicLanguage.values();String[] names=new String[languages.length];for(int i=0;i<names.length;i++)names[i]=languages[i].label;
        new AlertDialog.Builder(this).setTitle("Music language").setSingleChoiceItems(names,MusicLanguage.selected(this).ordinal(),(dialog,index)->{
            languages[index].save(this);dialog.dismiss();String currentQuery=query;++searchVersion;searching=false;results.clear();albumResults.clear();nextPage=null;
            if(tab.equals("search")&&!currentQuery.isBlank())startSearch(currentQuery,false);else render();
            refreshDiscovery(false);loadSuggestions(suggestionsQuery.isBlank()?recommendationQuery():suggestionsQuery);
        }).setNegativeButton("Cancel",null).show();
    }
    private void settingsScreen(){
        add(body,text("Make it yours",26,FG,true),-2);space(body,8);add(body,text("A look and a soundtrack that feel like you.",13,MUTED,false),-2);space(body,22);section("Appearance",null,null);
        for(ThemePalette.Mode mode:ThemePalette.Mode.values()){
            ThemePalette preview=ThemePalette.forMode(this,mode);LinearLayout card=row();inset(card,14);card.setBackground(border(mode==palette.mode()?palette.selected():SURFACE,18));
            View swatch=new View(this);swatch.setBackground(preview.card(this,preview.accent(),30));card.addView(swatch,new LinearLayout.LayoutParams(dp(30),dp(30)));LinearLayout copy=column();copy.setPadding(dp(14),0,dp(8),0);copy.addView(text(mode.label,15,FG,true));space(copy,4);
            String description=switch(mode){case MATERIAL->"Follows your phone · Dynamic colors on Android 12+";case LIGHT->"Bright surfaces · Soft green accents";case DARK->"Deep charcoal · Fresh green accents";case LIQUID->"Blue gradients · Glass-like cards";};
            copy.addView(text(description,12,MUTED,false));card.addView(copy,new LinearLayout.LayoutParams(0,-2,1));if(mode==palette.mode())card.addView(icon("check",GREEN,null));card.setContentDescription("Use "+mode.label+" theme");card.setOnClickListener(v->{if(mode!=palette.mode()){ThemePalette.save(this,mode);recreate();}});add(body,card,90);space(body,10);
        }
        space(body,14);section("Your music",null,null);addLanguageChip();space(body,8);add(body,text("Your language preference guides song, album and playlist discovery. Some results may include other languages.",12,MUTED,false),-2);
    }
    private void homeScreen(){
        LinearLayout welcome=column();welcome.setPadding(dp(20),dp(20),dp(20),dp(20));GradientDrawable welcomeBg=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{palette.hero(),ThemePalette.mix(0xffa880ec,BG,palette.light()?.86f:.78f),palette.selected()});welcomeBg.setCornerRadius(dp(24));welcome.setBackground(welcomeBg);TextView eyebrow=text("YOUR SOUND. ALWAYS EVOLVING.",10,GREEN,true);eyebrow.setLetterSpacing(.10f);welcome.addView(eyebrow);space(welcome,8);welcome.addView(text("Made for you",30,FG,true));space(welcome,8);welcome.addView(text("Follow your mood. Find your next favorite.",13,MUTED,false));add(body,welcome,-2);space(body,18);addLanguageChip();space(body,18);
        discoveryContainer=column();add(body,discoveryContainer,-2);drawDiscovery();space(body,18);suggestionsContainer=column();add(body,suggestionsContainer,-2);drawSuggestions();space(body,18);
        add(body,button("Browse albums",false,()->{searchMode=MusicCatalog.Mode.ALBUMS;startSearch(app.library.recent().isEmpty()?"popular albums":app.library.recent().get(0).artist(),false);}),48);space(body,18);section("Recently played",null,null);List<Track> recent=app.library.recent();if(recent.isEmpty())empty("Your soundtrack starts here","Search or play a few favorites to personalize your home.","search","Explore music",()->navigate("search"));else for(Track t:recent)trackRow(body,t,recent,false);
    }
    private long recommendationRotation(){return getSharedPreferences("musedock.preferences",0).getLong("recommendationRotation",0);}
    private Recommendations.Profile tasteProfile(){return profileSnapshot.withCurrent(player!=null&&player.getCurrentMediaItem()!=null?Track.from(player.getCurrentMediaItem()):null);}
    private boolean discoveryVisible(){return foreground&&(tab.equals("home")||tab.equals("mix"))&&(nowDialog==null||!nowDialog.isShowing());}
    private void pauseDiscovery(){
        discoveryDirty=true;discoveryFingerprint="";app.youtube.cancelRequestsFor("MuseDock-discovery");discoveryVersion++;suggestionsVersion++;handler.removeCallbacks(tasteRefresh);handler.removeCallbacks(drawShelves);
        if(profileJob!=null)profileJob.cancel(true);for(Future<?> job:discoveryJobs.values())job.cancel(true);discoveryJobs.clear();((ThreadPoolExecutor)discovery).purge();
        if(suggestionsJob!=null)suggestionsJob.cancel(true);suggestionsLoading=false;suggestionsFingerprint="";
    }
    private void scheduleDiscoveryDraw(){handler.removeCallbacks(drawShelves);handler.post(drawShelves);}
    private void refreshDiscovery(boolean force){
        if(closing)return;if(!discoveryVisible()){discoveryDirty=true;return;}
        if(force)getSharedPreferences("musedock.preferences",0).edit().putLong("recommendationRotation",recommendationRotation()+1).apply();
        discoveryDirty=false;int version=++discoveryVersion;Track current=player!=null&&player.getCurrentMediaItem()!=null?Track.from(player.getCurrentMediaItem()):null;
        MusicLanguage language=MusicLanguage.selected(this);long rotation=recommendationRotation();if(profileJob!=null)profileJob.cancel(true);
        profileJob=files.submit(()->{
            Recommendations.Profile profile=Recommendations.profile(app.library,language,rotation).withCurrent(current);
            EnumMap<Recommendations.Mood,List<Track>> local=new EnumMap<>(Recommendations.Mood.class);Set<Recommendations.Mood> hits=EnumSet.noneOf(Recommendations.Mood.class);
            for(var mood:Recommendations.Mood.values())try{String data=app.library.cachedDiscovery(mood.name(),Recommendations.retrievalKey(profile,mood));if(data!=null){local.put(mood,Recommendations.rank(profile,mood,Recommendations.decode(data),12));hits.add(mood);}}catch(Exception ignored){}
            local.put(Recommendations.Mood.REPEAT,Recommendations.repeat(profile));
            runOnUiThread(()->{
                if(closing||version!=discoveryVersion||!discoveryVisible())return;profileSnapshot=profile;if(!force&&profile.fingerprint().equals(discoveryFingerprint)&&System.currentTimeMillis()-discoveryLoadedAt<6*60*60*1000L){scheduleDiscoveryDraw();return;}discoveryFingerprint=profile.fingerprint();discoveryLoadedAt=System.currentTimeMillis();
                for(Future<?> job:discoveryJobs.values())job.cancel(true);discoveryJobs.clear();shelfErrors.clear();shelfLoading.clear();
                for(var mood:Recommendations.Mood.values()){
                    if(local.containsKey(mood))shelves.put(mood,local.get(mood));
                    if(mood==Recommendations.Mood.REPEAT||!force&&hits.contains(mood))continue;shelfLoading.add(mood);
                    discoveryJobs.put(mood,discovery.submit(()->{
                        try{List<Track> pool=Recommendations.catalogCandidates(profile,mood,app.catalog);List<Track> ranked=Recommendations.rank(profile,mood,pool,12);if(Thread.currentThread().isInterrupted())return;app.library.cacheDiscovery(mood.name(),Recommendations.retrievalKey(profile,mood),Recommendations.encode(pool));runOnUiThread(()->{if(closing||version!=discoveryVersion)return;shelves.put(mood,ranked);shelfLoading.remove(mood);scheduleDiscoveryDraw();});}
                        catch(Exception error){runOnUiThread(()->{if(closing||version!=discoveryVersion)return;shelfLoading.remove(mood);shelfErrors.put(mood,"Couldn't refresh this mix. Check your connection and retry.");scheduleDiscoveryDraw();});}
                    }));
                }
                scheduleDiscoveryDraw();if(tab.equals("home"))loadSuggestions(recommendationQuery());
            });
        });
    }
    private List<Track> shelfSongs(Recommendations.Mood mood){
        List<Track> tracks=shelves.getOrDefault(mood,List.of());Recommendations.Profile profile=tasteProfile();
        if(mood==Recommendations.Mood.FOR_YOU||mood==Recommendations.Mood.REPEAT)return tracks.stream().filter(t->!Recommendations.blocked(profile,t.id())).collect(java.util.stream.Collectors.toList());
        Set<String> taken=new HashSet<>();for(var other:List.of(Recommendations.Mood.FOCUS,Recommendations.Mood.CHILL,Recommendations.Mood.ENERGY,Recommendations.Mood.DISCOVERY)){if(other==mood)break;for(Track t:shelves.getOrDefault(other,List.of()))taken.add(t.id());}
        return tracks.stream().filter(t->!taken.contains(t.id())&&!Recommendations.blocked(profile,t.id())).collect(java.util.stream.Collectors.toList());
    }
    private void drawDiscovery(){
        if(discoveryContainer==null||!tab.equals("home"))return;discoveryContainer.removeAllViews();
        LinearLayout top=row();top.addView(text("Picked for you",21,FG,true),new LinearLayout.LayoutParams(0,-2,1));Icons refresh=icon("repeat",MUTED,()->{refreshDiscovery(true);loadSuggestions(recommendationQuery());});refresh.setContentDescription("Refresh personalized recommendations");top.addView(refresh);discoveryContainer.addView(top);
        var searches=profileSnapshot.searches();Track current=tasteProfile().current();String reason=current!=null?"Inspired by “"+current.title()+"” and your taste":!searches.isEmpty()?"Because you searched “"+searches.get(0).query()+"”":app.library.recent().isEmpty()?"Search, like or play a few favorites to shape your mix.":"Inspired by your listening and liked songs";discoveryContainer.addView(text(reason,12,MUTED,false));space(discoveryContainer,14);
        List<Track> picked=shelfSongs(Recommendations.Mood.FOR_YOU);if(shelfErrors.containsKey(Recommendations.Mood.FOR_YOU)){discoveryContainer.addView(text("Showing saved favorites · Connect to refresh new picks",12,MUTED,false));space(discoveryContainer,8);}if(!picked.isEmpty())songRail(discoveryContainer,picked);else discoveryContainer.addView(text(shelfLoading.contains(Recommendations.Mood.FOR_YOU)?"Finding music for your taste…":"No picks yet. Search for a favorite, then refresh.",13,MUTED,false));
        space(discoveryContainer,22);discoveryContainer.addView(text("Made for your mood",21,FG,true));space(discoveryContainer,6);discoveryContainer.addView(text("Different sounds for different moments",12,MUTED,false));space(discoveryContainer,14);
        List<Recommendations.Mood> moods=List.of(Recommendations.Mood.FOCUS,Recommendations.Mood.CHILL,Recommendations.Mood.ENERGY,Recommendations.Mood.DISCOVERY);
        int columns=getResources().getConfiguration().screenWidthDp>=600?3:2;
        for(int start=0;start<moods.size();start+=columns){LinearLayout group=row();for(int at=start;at<Math.min(start+columns,moods.size());at++){
            Recommendations.Mood mood=moods.get(at);List<Track> songs=shelfSongs(mood);LinearLayout card=column();card.setBackground(bg(SURFACE,20));card.setClipToOutline(true);
            FrameLayout picture=artwork(songs.isEmpty()?null:songs.get(0),150);picture.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{mood.color,ThemePalette.mix(mood.color,BG,.70f)}));card.addView(picture,new LinearLayout.LayoutParams(-1,dp(116)));
            LinearLayout copy=column();copy.setPadding(dp(12),dp(10),dp(12),dp(12));copy.addView(label(mood.title,14,FG,true));space(copy,4);copy.addView(label(songs.isEmpty()?(shelfLoading.contains(mood)?"Finding your mix…":"Refresh for new songs"):songs.size()+" songs · Made for this mood",11,MUTED,false));card.addView(copy);card.setContentDescription("Open mood "+mood.title);card.setOnClickListener(v->openMix(mood));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);if(at>start)lp.setMarginStart(dp(10));group.addView(card,lp);
        }if(group.getChildCount()<columns)group.addView(new View(this),new LinearLayout.LayoutParams(0,1,1));discoveryContainer.addView(group);space(discoveryContainer,12);}
        List<Track> repeated=shelfSongs(Recommendations.Mood.REPEAT);if(!repeated.isEmpty()){space(discoveryContainer,12);LinearLayout heading=row();heading.addView(text("On repeat",21,FG,true),new LinearLayout.LayoutParams(0,-2,1));heading.addView(button("See all",false,()->openMix(Recommendations.Mood.REPEAT)));discoveryContainer.addView(heading);space(discoveryContainer,10);songRail(discoveryContainer,repeated,Recommendations.Mood.REPEAT);}
    }
    private void songRail(LinearLayout parent,List<Track> tracks){songRail(parent,tracks,Recommendations.Mood.FOR_YOU);}
    private void songRail(LinearLayout parent,List<Track> tracks,Recommendations.Mood mood){
        HorizontalScrollView rail=new HorizontalScrollView(this);rail.setHorizontalScrollBarEnabled(false);LinearLayout cards=row();List<Track> queue=new ArrayList<>(tracks);
        for(int index=0;index<Math.min(10,tracks.size());index++){Track track=tracks.get(index);final int position=index;LinearLayout card=column();card.setBackground(bg(SURFACE,18));inset(card,10);card.addView(artwork(track,128),new LinearLayout.LayoutParams(-1,dp(128)));space(card,10);card.addView(label(track.title(),14,FG,true));space(card,4);LinearLayout metadata=row();metadata.addView(label(track.artist(),11,MUTED,false),new LinearLayout.LayoutParams(0,-2,1));Icons more=icon("more",MUTED,()->songMenu(track,false));more.setContentDescription("Recommendation options for "+track.title());metadata.addView(more);card.addView(metadata);card.setContentDescription("Play recommendation "+track.title());card.setOnClickListener(v->{if(mood==Recommendations.Mood.REPEAT)play(queue,position,false);else playMix(queue,position,false);});card.setOnLongClickListener(v->{songMenu(track,false);return true;});LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(154),-2);lp.setMarginEnd(dp(10));cards.addView(card,lp);}rail.addView(cards);parent.addView(rail);
    }
    private void openMix(Recommendations.Mood mood){openedMood=mood;tab="mix";playlistId=null;render();if(discoveryFingerprint.isEmpty())refreshDiscovery(false);}
    private void mixScreen(){
        LinearLayout header=row();header.addView(icon("back",FG,()->navigate("home")));header.addView(text("MADE FOR YOU",11,GREEN,true));add(body,header,48);List<Track> songs=shelfSongs(openedMood);add(body,text(openedMood.title,28,FG,true),-2);space(body,8);add(body,text(openedMood.description,13,MUTED,false),-2);space(body,18);
        if(!songs.isEmpty()){List<Track> queue=new ArrayList<>(songs);LinearLayout controls=row();controls.addView(button("▶  Play",true,()->playMix(queue,0,false)),new LinearLayout.LayoutParams(0,dp(48),1));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(48),1);lp.setMarginStart(dp(10));controls.addView(button("Shuffle",false,()->playMix(queue,0,true)),lp);add(body,controls,-2);space(body,10);add(body,button("Save mix to Library",false,()->nameDialog("Save mix",openedMood.title,name->{String id=app.library.importPlaylist(name,queue);openOwnPlaylist(id);})),48);space(body,14);for(Track track:queue)trackRow(body,track,queue,false);}
        if(shelfLoading.contains(openedMood))add(body,text("Refreshing this mood…",13,MUTED,false),-2);else if(songs.isEmpty())add(body,text(shelfErrors.getOrDefault(openedMood,"No different songs are available for this mood yet. Refresh to try another selection."),13,MUTED,false),-2);
        space(body,14);add(body,button("Refresh this mix",false,()->{refreshDiscovery(true);loadSuggestions(recommendationQuery());render();}),48);
    }
    private String recommendationQuery(){
        List<Track> recent=app.library.recent();
        return recent.isEmpty()||recent.get(0).artist().isBlank()?"popular music playlists":recent.get(0).artist()+" music playlists";
    }
    private void drawSuggestions(){
        if(suggestionsContainer==null||!tab.equals("home"))return;
        suggestionsContainer.removeAllViews();
        LinearLayout title=row();title.addView(text("Mixes for your taste",21,FG,true),new LinearLayout.LayoutParams(0,-2,1));
        Icons refresh=icon("repeat",GREEN,()->{refreshDiscovery(true);loadSuggestions(suggestionsQuery.isBlank()?recommendationQuery():suggestionsQuery);});refresh.setContentDescription("Refresh YouTube playlists");title.addView(refresh);suggestionsContainer.addView(title);
        suggestionsContainer.addView(text(suggestionFilter.equals("For you")&&!app.library.recent().isEmpty()?"From your listening, searches and likes.":"Public playlists for your next listening session.",13,MUTED,false));space(suggestionsContainer,12);
        HorizontalScrollView filters=new HorizontalScrollView(this);filters.setHorizontalScrollBarEnabled(false);LinearLayout chips=row();
        for(String name:List.of("For you","Fresh","Chill","Workout")){
            TextView chip=button(name,name.equals(suggestionFilter),()->{suggestionFilter=name;loadSuggestions(switch(name){case "Fresh"->"new music playlists";case "Chill"->"chill lofi music playlists";case "Workout"->"workout music playlists";default->recommendationQuery();});});
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,dp(48));lp.setMarginEnd(dp(8));chips.addView(chip,lp);
        }
        filters.addView(chips);suggestionsContainer.addView(filters);space(suggestionsContainer,14);
        if(suggestionsLoading){LinearLayout loading=row();ProgressBar progress=new ProgressBar(this);loading.addView(progress,new LinearLayout.LayoutParams(dp(22),dp(22)));loading.addView(text("  Finding playlists on YouTube…",13,MUTED,false));suggestionsContainer.addView(loading);return;}
        if(!suggestionsError.isEmpty()){
            suggestionsContainer.addView(text(suggestionsError,13,MUTED,false));space(suggestionsContainer,10);suggestionsContainer.addView(button("Retry playlists",false,()->loadSuggestions(suggestionsQuery)),new LinearLayout.LayoutParams(-2,dp(48)));return;
        }
        if(suggestions.isEmpty()){suggestionsContainer.addView(text("Choose a mood to find public YouTube playlists.",13,MUTED,false));return;}
        HorizontalScrollView rail=new HorizontalScrollView(this);rail.setHorizontalScrollBarEnabled(false);LinearLayout cards=row();
        for(PlaylistSource.Suggestion playlist:suggestions){
            LinearLayout card=column();card.setBackground(bg(SURFACE,18));inset(card,12);
            Track cover=new Track("",playlist.title(),playlist.author(),playlist.artwork(),0);
            card.addView(artwork(cover,142),new LinearLayout.LayoutParams(-1,dp(116)));space(card,12);
            TextView name=text(playlist.title(),15,FG,true);name.setMaxLines(2);name.setEllipsize(TextUtils.TruncateAt.END);card.addView(name,new LinearLayout.LayoutParams(-1,dp(44)));
            card.addView(label(playlist.author(),11,MUTED,false));space(card,5);card.addView(label(playlist.count()>0?playlist.count()+" tracks · YouTube":"Playlist · YouTube",11,GREEN,false));
            card.setContentDescription("Open YouTube playlist "+playlist.title());card.setOnClickListener(v->openRemotePlaylist(playlist));
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(176),dp(238));lp.setMarginEnd(dp(12));cards.addView(card,lp);
        }
        rail.addView(cards);suggestionsContainer.addView(rail);
    }
    private void loadSuggestions(String query){
        if(closing||isDestroyed()||!discoveryVisible()||discoveryFingerprint.isEmpty())return;
        String q=query.isBlank()?recommendationQuery():query;
        Recommendations.Profile profile=tasteProfile();String fingerprint=profile.fingerprint()+suggestionFilter;if(q.equals(suggestionsQuery)&&fingerprint.equals(suggestionsFingerprint)&&(suggestionsLoading||!suggestions.isEmpty()))return;suggestionsFingerprint=fingerprint;
        suggestionsQuery=q;suggestionsLoading=true;suggestionsError="";suggestions.clear();int version=++suggestionsVersion;
        if(suggestionsJob!=null)suggestionsJob.cancel(true);drawSuggestions();
        MusicLanguage language=MusicLanguage.selected(this);suggestionsJob=discovery.submit(()->{
            try{
                List<PlaylistSource.Suggestion> found=suggestionFilter.equals("For you")?Recommendations.playlists(profile,app.playlists):app.playlists.suggest(language.query(q));
                runOnUiThread(()->{if(closing||isDestroyed()||version!=suggestionsVersion)return;suggestionsLoading=false;suggestions.addAll(found);if(found.isEmpty())suggestionsError="No public playlists found. Try another mood.";drawSuggestions();});
            }catch(Exception e){runOnUiThread(()->{if(closing||isDestroyed()||version!=suggestionsVersion)return;suggestionsLoading=false;suggestionsError="Couldn't load YouTube playlists. Check your connection and retry.";drawSuggestions();});}
        });
    }
    private void openRemotePlaylist(PlaylistSource.Suggestion playlist){openRemotePlaylist(playlist,false);}
    private void openRemotePlaylist(PlaylistSource.Suggestion playlist,boolean album){
        remoteAlbum=album;
        if(!tab.equals("youtube-playlist"))remoteBack=tab;
        remotePlaylist=playlist;remoteTracks.clear();remoteNext=null;tab="youtube-playlist";hideKeyboard();loadRemoteTracks(false);
    }
    private void loadRemoteTracks(boolean more){
        if(remotePlaylist==null||more&&remoteLoading)return;
        String url=remotePlaylist.url();org.schabi.newpipe.extractor.Page page=more?remoteNext:null;
        remoteLoading=true;remoteError="";int version=++remoteVersion;if(remoteJob!=null)remoteJob.cancel(false);render();
        remoteJob=network.submit(()->{
            try{
                PlaylistSource.Tracks fetched=app.playlists.playlist(url,page);
                runOnUiThread(()->{if(closing||isDestroyed()||version!=remoteVersion)return;
                    if(!fetched.title().isBlank())remotePlaylist=new PlaylistSource.Suggestion(remotePlaylist.url(),fetched.title(),remotePlaylist.author(),remotePlaylist.artwork(),remotePlaylist.count());
                    LinkedHashMap<String,Track> unique=new LinkedHashMap<>();for(Track t:remoteTracks)unique.put(t.id(),t);for(Track t:fetched.tracks())unique.putIfAbsent(t.id(),t);remoteTracks.clear();remoteTracks.addAll(unique.values());
                    remoteNext=fetched.next();remoteLoading=false;if(remoteTracks.isEmpty()&&remoteNext==null)remoteError="This playlist has no available songs. It may be private or unavailable in your region.";
                    if(tab.equals("youtube-playlist"))render();
                });
            }catch(Exception e){runOnUiThread(()->{if(closing||isDestroyed()||version!=remoteVersion)return;remoteLoading=false;remoteError="Couldn't load this playlist. Check your connection or try another public playlist.";if(tab.equals("youtube-playlist"))render();});}
        });
    }
    private void remotePlaylistScreen(){
        if(remotePlaylist==null){add(body,text("Choose a playlist from Listen.",16,FG,false),-2);return;}
        LinearLayout top=row();Icons back=icon("back",FG,this::goBack);back.setContentDescription("Back from YouTube playlist");top.addView(back);top.addView(text(remoteAlbum?"ALBUM · YOUTUBE MUSIC":"YOUTUBE / YOUTUBE MUSIC",11,GREEN,true));add(body,top,48);space(body,12);
        add(body,text(remotePlaylist.title(),30,FG,true),-2);space(body,8);add(body,text(remotePlaylist.author()+(remoteAlbum?" · Album":" · Public playlist"),13,MUTED,false),-2);space(body,18);
        if(!remoteTracks.isEmpty()){
            List<Track> queue=new ArrayList<>(remoteTracks);LinearLayout actions=row();actions.addView(button("▶  Play",true,()->play(queue,0,false)),new LinearLayout.LayoutParams(0,dp(48),1));TextView shuffle=button("Shuffle",false,()->play(queue,0,true));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(48),1);lp.setMarginStart(dp(10));actions.addView(shuffle,lp);add(body,actions,-2);space(body,10);
            TextView save=button("Save "+queue.size()+" songs to Library",false,()->saveRemotePlaylist(queue));save.setEnabled(!remoteLoading);add(body,save,48);space(body,10);
            add(body,text(remoteNext!=null?"Showing "+queue.size()+" songs. Load more before saving to include more tracks.":queue.size()+" available songs. Saving a playlist doesn't download audio.",12,MUTED,false),-2);space(body,16);
            for(Track track:queue)trackRow(body,track,queue,false);
        }
        if(remoteLoading){ProgressBar progress=new ProgressBar(this);add(body,progress,44);add(body,text("Loading songs from YouTube…",13,MUTED,false),-2);}
        else if(!remoteError.isEmpty()){add(body,text(remoteError,13,MUTED,false),-2);space(body,12);add(body,button("Retry playlist",false,()->loadRemoteTracks(!remoteTracks.isEmpty()&&remoteNext!=null)),48);}
        else if(remoteNext!=null){space(body,14);add(body,button("Load more playlist songs",false,()->loadRemoteTracks(true)),48);}
    }
    private void saveRemotePlaylist(List<Track> tracks){
        String title=remotePlaylist.title();
        new AlertDialog.Builder(this).setTitle("Save to your library?").setMessage("Save the "+tracks.size()+" loaded songs as a local playlist. This saves song links, not offline audio.")
            .setPositiveButton("Save playlist",(d,which)->{
                try{playlistId=app.library.importPlaylist(title,tracks);playlistName=title.length()>80?title.substring(0,80):title;tab="library";render();toast("Playlist saved to Library");}
                catch(RuntimeException e){toast("Couldn't save this playlist. Please try again.");}
            }).setNegativeButton("Cancel",null).show();
    }
    private void searchScreen(){
        if(addTargetPlaylistId!=null){LinearLayout adding=row();adding.addView(text("Choose songs to add to your playlist",12,GREEN,true),new LinearLayout.LayoutParams(0,-2,1));adding.addView(button("Done",false,()->{String id=addTargetPlaylistId;addTargetPlaylistId=null;openOwnPlaylist(id);}));add(body,adding,48);space(body,10);}
        add(body,text("Find your next listen.",28,FG,true),-2);space(body,8);add(body,text("Search YouTube Music songs and albums, or import a playlist.",14,MUTED,false),-2);space(body,20);
        addLanguageChip();space(body,14);
        LinearLayout bar=row();bar.setBackground(border(SURFACE,16));bar.addView(icon("search",MUTED,null));EditText input=new EditText(this);input.setSingleLine(true);input.setTextColor(FG);input.setHintTextColor(MUTED);input.setTextSize(15);input.setHint("Songs, artists, or a music playlist link");input.setText(query);input.setBackgroundColor(android.graphics.Color.TRANSPARENT);input.setPadding(0,dp(12),0,dp(12));input.setInputType(android.text.InputType.TYPE_CLASS_TEXT);input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);bar.addView(input,new LinearLayout.LayoutParams(0,dp(56),1));Icons go=icon("next",GREEN,()->startSearch(input.getText().toString(),false));go.setContentDescription("Search YouTube Music");bar.addView(go);add(body,bar,-2);
        input.setOnEditorActionListener((v,id,event)->{if(id==EditorInfo.IME_ACTION_SEARCH){startSearch(input.getText().toString(),false);return true;}return false;});
        input.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int b,int c){query=s.toString();}public void afterTextChanged(Editable e){}});
        space(body,12);HorizontalScrollView modes=new HorizontalScrollView(this);modes.setHorizontalScrollBarEnabled(false);LinearLayout choices=row();
        for(MusicCatalog.Mode mode:MusicCatalog.Mode.values()){
            String name=switch(mode){case SONGS->"Songs";case ALBUMS->"Albums";};
            TextView choice=button(name,mode==searchMode,()->{searchMode=mode;if(query.isBlank()){results.clear();albumResults.clear();nextPage=null;searchError="";searching=false;++searchVersion;render();}else startSearch(query,false);});
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,dp(48));lp.setMarginEnd(dp(8));choices.addView(choice,lp);
        }modes.addView(choices);add(body,modes,-2);space(body,16);
        if(searching){LinearLayout loading=row();ProgressBar p=new ProgressBar(this);loading.addView(p,new LinearLayout.LayoutParams(dp(24),dp(24)));TextView status=text("  Finding your sound…",14,MUTED,false);loading.addView(status);add(body,loading,48);}
        if(!searchError.isEmpty())empty("Couldn't reach the music",searchError,"search","Try again",()->startSearch(query,false));
        if(!results.isEmpty()||!albumResults.isEmpty()){
            section(searchMode==MusicCatalog.Mode.ALBUMS&&!albumResults.isEmpty()?"Albums · YouTube Music":"Search results",null,null);
            for(PlaylistSource.Suggestion album:albumResults){LinearLayout card=row();card.setPadding(dp(8),dp(8),dp(8),dp(8));card.setBackground(bg(SURFACE,14));card.addView(artwork(new Track("albumcover0",album.title(),album.author(),album.artwork(),0),64),new LinearLayout.LayoutParams(dp(64),dp(64)));LinearLayout copy=column();copy.setPadding(dp(12),0,0,0);copy.addView(label(album.title(),17,FG,true));copy.addView(label(album.author()+" · Album",12,MUTED,false));card.addView(copy,new LinearLayout.LayoutParams(0,-2,1));card.addView(icon("chevron",GREEN,null));card.setContentDescription("Open album "+album.title());card.setOnClickListener(v->openRemotePlaylist(album,true));add(body,card,88);space(body,8);}
            for(Track t:results)trackRow(body,t,results,false);
            if(nextPage!=null&&!searching){space(body,18);add(body,button(searchMode==MusicCatalog.Mode.ALBUMS?"Load more albums":"Load more songs",false,()->startSearch(resultQuery,true)),48);}
        }else if(!searching&&searchError.isEmpty()){
            if(query.isEmpty()){
                section("Start with something you love",null,null);
                for(String suggestion:List.of("Indie discoveries","Jazz for a quiet evening","Hindi acoustic songs","Electronic essentials")){
                    LinearLayout r=row();r.addView(icon("search",MUTED,null));r.addView(text(suggestion,15,FG,false),new LinearLayout.LayoutParams(0,-2,1));r.addView(icon("chevron",MUTED,null));r.setOnClickListener(v->startSearch(suggestion,false));add(body,r,62);
                }
                space(body,18);add(body,text("Songs and Albums use YouTube Music catalog search. Availability varies by region; album results may include singles and EPs.",12,MUTED,false),-2);
            }else empty("No songs here yet","Run a search above. Try a song title, artist, or a YouTube video link.","music",null,null);
        }
    }
    private void startSearch(String value,boolean more){
        String q=value.trim();if(q.isEmpty()){navigate("search");return;}
        String playlistLink=PlaylistLinks.canonical(q);
        if(!more&&playlistLink!=null&&VideoIds.parse(q)==null){openRemotePlaylist(new PlaylistSource.Suggestion(playlistLink,"YouTube playlist","YouTube","",-1));return;}
        if(searching && more)return;
        tab="search";playlistId=null;query=q;if(!more)app.library.recordSearch(q);hideKeyboard();int version=++searchVersion;
        org.schabi.newpipe.extractor.Page page=more?nextPage:null;
        MusicCatalog.Mode mode=searchMode;MusicLanguage language=MusicLanguage.selected(this);if(!more){results.clear();albumResults.clear();nextPage=null;}searching=true;searchError="";render();
        network.execute(()->{
            try{
                var response=app.catalog.search(language.query(q),page,mode);
                runOnUiThread(()->{if(closing||isDestroyed()||version!=searchVersion)return;
                    LinkedHashMap<String,Track> all=new LinkedHashMap<>();for(Track t:results)all.put(t.id(),t);for(Track t:response.tracks())all.put(t.id(),t);results.clear();results.addAll(all.values());
                    LinkedHashMap<String,PlaylistSource.Suggestion> albums=new LinkedHashMap<>();for(var a:albumResults)albums.put(a.url(),a);for(var a:response.albums())albums.putIfAbsent(a.url(),a);albumResults.clear();albumResults.addAll(albums.values());
                    nextPage=response.next();resultQuery=q;searching=false;if(results.isEmpty()&&albumResults.isEmpty())searchError="No playable results were found. Try a different song or artist.";if(tab.equals("search"))render();});
            }catch(Exception e){runOnUiThread(()->{if(closing||isDestroyed()||version!=searchVersion)return;searching=false;searchError="Check your internet connection and try again. YouTube may be limiting requests, or the extractor may need an update.";if(tab.equals("search"))render();});}
        });
    }
    private void libraryScreen(){
        add(body,text("Your Library",26,FG,true),-2);space(body,8);add(body,text("Your playlists, all in one place.",13,MUTED,false),-2);space(body,22);
        add(body,button("+  Create playlist",true,()->editPlaylist(null,"")),50);space(body,10);TextView history=text("Import YouTube history",12,MUTED,false);history.setGravity(Gravity.CENTER);history.setMinHeight(dp(48));history.setOnClickListener(v->navigate("account"));add(body,history,48);space(body,18);
        for(Library.Playlist p:app.library.playlists()){
            LinearLayout card=row();card.setBackground(bg(SURFACE,18));card.setPadding(dp(12),dp(12),dp(8),dp(12));
            card.addView(playlistArtwork(p.id(),52),new LinearLayout.LayoutParams(dp(52),dp(52)));
            LinearLayout copy=column();copy.setPadding(dp(16),0,0,0);copy.addView(label(p.name(),15,FG,true));space(copy,5);copy.addView(text((p.pinned()?"Pinned · ":"")+p.count()+" songs · On this device",12,MUTED,false));card.addView(copy,new LinearLayout.LayoutParams(0,-2,1));card.addView(icon("chevron",MUTED,null));card.setOnClickListener(v->{playlistId=p.id();playlistName=p.name();render();});card.setMinimumHeight(dp(80));add(body,card,-2);space(body,10);
        }
        space(body,12);add(body,text("Playlists are saved locally and work without an account. Download tracks to listen without a connection.",12,MUTED,false),-2);
    }
    private void playlistScreen(){
        LinearLayout tools=row();tools.addView(icon("back",FG,()->{playlistId=null;render();}));tools.addView(text("YOUR COLLECTION",11,MUTED,true),new LinearLayout.LayoutParams(0,-2,1));if(!playlistId.equals("liked")){Icons options=icon("more",FG,this::playlistOptions);options.setContentDescription("Playlist options");tools.addView(options);}add(body,tools,48);
        space(body,18);Icons art=icon(playlistId.equals("liked")?"liked":"music",GREEN,null);art.setBackground(bg(palette.selected(),24));LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(dp(104),dp(104));ap.gravity=Gravity.CENTER_HORIZONTAL;body.addView(art,ap);space(body,22);
        add(body,text(playlistName,26,FG,true),-2);List<Track> songs=app.library.playlistTracks(playlistId);space(body,6);add(body,text(songs.size()+" songs · Made by you",13,MUTED,false),-2);if(!currentPlaylist().description().isBlank()){space(body,8);add(body,text(currentPlaylist().description(),13,MUTED,false),-2);}space(body,20);
        add(body,button("Add songs",false,()->{addTargetPlaylistId=playlistId;tab="search";query="";results.clear();albumResults.clear();render();}),48);space(body,10);
        if(!downloadedTracks().isEmpty()){add(body,button("Add downloaded songs",false,()->selectDownloadedSongs(playlistId,null)),48);space(body,12);}
        if(!songs.isEmpty()){
            LinearLayout controls=row();controls.addView(button("▶  Play",true,()->play(songs,0,false)),new LinearLayout.LayoutParams(0,dp(50),1));TextView shuffle=button("Shuffle",false,()->play(songs,0,true));LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(0,dp(50),1);bp.leftMargin=dp(10);controls.addView(shuffle,bp);add(body,controls,-2);space(body,18);for(Track t:songs)trackRow(body,t,songs,true);
        }else empty("Make room for your favorites","Add songs from Search or your downloads.","music","Find songs",()->navigate("search"));
        drawPlaylistAdditions();handler.post(()->{if(!closing&&tab.equals("library")&&playlistId!=null)loadPlaylistAdditions(false);});
    }
    private void offlineScreen(){
        add(body,text("Your offline music",28,FG,true),-2);space(body,8);add(body,text("Saved on your phone. Ready for airplane mode.",14,MUTED,false),-2);space(body,20);
        List<Library.Download> downloads=app.library.downloads();List<Track> available=new ArrayList<>();long bytes=0;
        for(var d:downloads){String path=app.library.offline(d.track().id());if(path!=null){available.add(d.track());bytes+=new java.io.File(path).length();}}
        LinearLayout summary=row();summary.setBackground(border(SURFACE,18));inset(summary,16);summary.addView(icon("download",GREEN,null));LinearLayout copy=column();copy.addView(text(available.size()+" songs offline",18,FG,true));copy.addView(text(String.format(Locale.ROOT,"%.1f MB on this device",bytes/1048576.0),12,MUTED,false));summary.addView(copy,new LinearLayout.LayoutParams(0,-2,1));add(body,summary,88);space(body,18);
        if(!available.isEmpty()){
            add(body,button("Play offline collection",true,()->play(available,0,false)),48);space(body,10);
            add(body,button("Create playlist from downloads",false,this::createOfflinePlaylist),48);space(body,10);
            add(body,text("Choose downloaded songs for your own playlist. Find it later in Library.",12,MUTED,false),-2);space(body,16);
        }
        if(downloads.isEmpty())empty("Your music, even without Wi-Fi","Find a song, open its menu, and choose Save offline. Downloads stay in the app until you remove them.","download","Find songs",()->navigate("search"));
        for(var d:downloads){
            if(app.library.offline(d.track().id())!=null&&!d.status().equals("converting")){trackRow(body,d.track(),available,false);if(!d.error().isBlank())add(body,text(d.error(),12,MUTED,false),-2);}
            else{
                LinearLayout card=column();card.setBackground(bg(SURFACE,16));inset(card,14);card.addView(label(d.track().title(),16,FG,true));space(card,5);
                boolean busy=d.status().equals("queued")||d.status().equals("downloading")||d.status().equals("converting");
                card.addView(text(busy?(d.status().equals("converting")?"Converting to MP3 on this device":d.status().equals("queued")?"Queued for download":"Downloading · See notification for progress"):(d.error().isEmpty()?"File missing. Download again to restore offline playback.":d.error()),12,MUTED,false));space(card,12);
                LinearLayout actions=row();actions.addView(button(busy?"Cancel":"Retry",!busy,()->{if(busy)OfflineService.cancel(this,d.track().id());else download(d.track());}));if(!busy){TextView remove=button("Remove",false,()->{app.library.removeDownload(d.track().id());render();});LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-2,dp(48));rp.leftMargin=dp(8);actions.addView(remove,rp);}card.addView(actions);add(body,card,-2);space(body,10);
            }
        }
    }
    private void trackRow(LinearLayout parent,Track t,List<Track> queue,boolean inPlaylist){
        LinearLayout r=row();r.setPadding(0,dp(7),0,dp(7));
        FrameLayout art=artwork(t,54);r.addView(art,new LinearLayout.LayoutParams(dp(54),dp(54)));
        LinearLayout copy=column();copy.setPadding(dp(13),0,dp(3),0);copy.addView(label(t.title(),inPlaylist?14:15,FG,true));space(copy,5);copy.addView(label((app.library.offline(t.id())!=null?"↓  ":"")+t.artist()+ (t.duration()>0?" · "+time(t.duration()*1000):""),12,MUTED,false));r.addView(copy,new LinearLayout.LayoutParams(0,-2,1));
        Icons add=icon("add",GREEN,()->{if(addTargetPlaylistId!=null){app.library.add(addTargetPlaylistId,t);toast("Added to playlist");}else choosePlaylist(t);});add.setContentDescription("Add "+t.title()+" to playlist");r.addView(add);Icons more=icon("more",MUTED,()->songMenu(t,inPlaylist));more.setContentDescription("Options for "+t.title());r.addView(more);r.setOnClickListener(v->{int index=0;for(int i=0;i<queue.size();i++)if(queue.get(i).id().equals(t.id())){index=i;break;}if(tab.equals("mix"))playMix(queue,index,false);else play(queue,index,false);});r.setContentDescription("Play "+t.title()+" by "+t.artist());parent.addView(r,new LinearLayout.LayoutParams(-1,dp(76)));
    }
    private void empty(String title,String subtitle,String symbol,String action,Runnable click){
        LinearLayout box=column();inset(box,22);box.setBackground(bg(SURFACE,20));Icons i=icon(symbol,GREEN,null);box.addView(i);space(box,12);box.addView(text(title,20,FG,true));space(box,8);TextView copy=text(subtitle,13,MUTED,false);copy.setLineSpacing(dp(3),1);box.addView(copy);if(action!=null){space(box,18);box.addView(button(action,true,click),new LinearLayout.LayoutParams(-2,dp(48)));}add(body,box,-2);
    }
    private FrameLayout artwork(Track t,int size){
        FrameLayout f=new FrameLayout(this);f.setBackground(bg(RAISED,12));f.setClipToOutline(true);f.addView(new RecordArt(this),new FrameLayout.LayoutParams(-1,-1));
        if(t!=null&&!t.artworkUrl().isEmpty()){ImageView image=new ImageView(this);image.setScaleType(ImageView.ScaleType.CENTER_CROP);image.setContentDescription("Artwork for "+t.title());f.addView(image,new FrameLayout.LayoutParams(-1,-1));loadImage(image,t.artworkUrl(),dp(size));}return f;
    }
    private void loadImage(ImageView image,String url,int pixels){
        if(closing||images.isShutdown())return;
        List<String> candidates=Artwork.candidates(url,pixels);if(candidates.isEmpty())return;int target=pixels<=256?320:pixels<=640?640:1280;String key=candidates.get(0)+"#"+target;image.setTag(key);
        Bitmap cached=app.artworkCache.get(key);if(cached==null){Bitmap original=app.artworkCache.get(candidates.get(0));if(original!=null&&Math.max(original.getWidth(),original.getHeight())<=target*2)cached=original;}if(cached!=null){displayArtwork(image,cached);return;}
        CompletableFuture<Bitmap> task=artworkRequests.get(key);
        if(task==null){task=new CompletableFuture<>();artworkRequests.put(key,task);final CompletableFuture<Bitmap> work=task;
            images.execute(()->{Bitmap found=null;try{for(String candidate:candidates){
                if(Thread.currentThread().isInterrupted())break;
                String imageKey=candidate+"#"+target;found=app.artworkCache.get(imageKey);if(found!=null)break;
                if(System.currentTimeMillis()-artworkFailures.getOrDefault(candidate,0L)<30000)continue;
                try(okhttp3.Response response=app.artworkClient.newCall(new okhttp3.Request.Builder().url(candidate).build()).execute()){
                    if(!response.isSuccessful()||response.body()==null)throw new java.io.IOException("Artwork unavailable");
                    java.io.InputStream in=response.body().byteStream();java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();byte[] chunk=new byte[8192];int n,count=0;while((n=in.read(chunk))!=-1){count+=n;if(count>6*1024*1024)throw new java.io.IOException("Artwork too large");out.write(chunk,0,n);}
                    byte[] data=out.toByteArray();BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(data,0,data.length,options);
                    if(options.outWidth<1||options.outHeight<1||(long)options.outWidth*options.outHeight>32000000L||(candidate.contains("maxresdefault")||candidate.contains("sddefault"))&&options.outWidth<320)throw new java.io.IOException("Invalid artwork");
                    options.inSampleSize=Artwork.sampleSize(options.outWidth,options.outHeight,target);options.inJustDecodeBounds=false;options.inPreferredConfig=Bitmap.Config.ARGB_8888;
                    found=BitmapFactory.decodeByteArray(data,0,data.length,options);if(found==null)throw new java.io.IOException("Invalid artwork");app.artworkCache.put(imageKey,found);break;
                }catch(Exception error){if(artworkFailures.size()>200)artworkFailures.clear();artworkFailures.put(candidate,System.currentTimeMillis());}
            }}finally{work.complete(found);artworkRequests.remove(key,work);}});
        }
        task.thenAccept(bitmap->runOnUiThread(()->{if(bitmap!=null&&!closing&&!isDestroyed()&&key.equals(image.getTag()))displayArtwork(image,bitmap);}));
    }
    private void displayArtwork(ImageView view,Bitmap bitmap){view.setImageBitmap(bitmap);if(view==nowArt&&nowPage!=null){int color=bitmap.getPixel(bitmap.getWidth()/2,bitmap.getHeight()/2);int tone=ThemePalette.mix(color,palette.background(),palette.light()?.82f:.66f);nowPage.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{tone,palette.hero(),BG}));}}
    private void play(List<Track> tracks,int index,boolean shuffle){
        if(player==null){toast("Audio player is connecting. Please try again.");return;}if(tracks.isEmpty())return;
        List<Track> copy=new ArrayList<>(tracks);List<MediaItem> items=new ArrayList<>();for(Track t:copy)items.add(adaptivePlayback?PlaybackService.autoMixItem(t,null,adaptiveMood.name()):t.mediaItem(null));
        player.setShuffleModeEnabled(shuffle);player.setMediaItems(items,index,0);player.prepare();player.play();updateMini();showNowPlaying();
    }
    private void togglePlay(){if(player==null)return;if(player.isPlaying())player.pause();else{if(player.getPlaybackState()==Player.STATE_IDLE)player.prepare();if(player.getPlaybackState()==Player.STATE_ENDED)player.seekToDefaultPosition();player.play();}}
    private void updateMini(){
        if(mini==null)return;if(player==null||player.getCurrentMediaItem()==null){mini.removeAllViews();miniTrack=null;return;}
        Track t=Track.from(player.getCurrentMediaItem());if(t.equals(miniTrack)&&mini.getChildCount()>0){miniPlay.set(player.isPlaying()?"pause":"play",GREEN);miniPlay.setContentDescription(player.isPlaying()?"Pause":"Play");return;}miniTrack=t;mini.removeAllViews();LinearLayout r=row();r.setBackground(border(palette.selected(),14));r.setPadding(dp(8),dp(8),dp(4),dp(8));r.addView(artwork(t,44),new LinearLayout.LayoutParams(dp(44),dp(44)));
        LinearLayout copy=column();copy.setPadding(dp(12),0,0,0);miniTitle=label(t.title(),13,FG,true);miniArtist=label(t.artist(),11,MUTED,false);copy.addView(miniTitle);space(copy,3);copy.addView(miniArtist);r.addView(copy,new LinearLayout.LayoutParams(0,-2,1));miniPlay=icon(player.isPlaying()?"pause":"play",GREEN,this::togglePlay);miniPlay.setContentDescription(player.isPlaying()?"Pause":"Play");r.addView(miniPlay);r.addView(icon("next",FG,()->{if(player.hasNextMediaItem())player.seekToNextMediaItem();else toast("You're at the end of the queue.");}));r.setContentDescription("Open now playing");r.setOnClickListener(v->showNowPlaying());mini.addView(r,new LinearLayout.LayoutParams(-1,dp(68)));
    }
    private void showNowPlaying(){
        if(player==null||player.getCurrentMediaItem()==null)return;if(nowDialog!=null&&nowDialog.isShowing())return;
        pauseDiscovery();nowTrack=null;playbackStatusId="";nowDialog=new Dialog(this);LinearLayout page=column();nowPage=page;page.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{palette.hero(),SURFACE,BG}));page.setPadding(dp(26),dp(18),dp(26),dp(24));
        ScrollView container=new ScrollView(this);container.setFillViewport(true);container.addView(page);nowDialog.setContentView(container);nowDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);WindowCompat.setDecorFitsSystemWindows(nowDialog.getWindow(),false);nowDialog.getWindow().setLayout(-1,-1);ViewCompat.setOnApplyWindowInsetsListener(container,(v,i)->{androidx.core.graphics.Insets s=i.getInsets(WindowInsetsCompat.Type.systemBars());v.setPadding(0,s.top,0,s.bottom);return i;});
        LinearLayout top=row();top.addView(icon("down",FG,()->nowDialog.dismiss()));TextView title=label("NOW PLAYING · MUSEDOCK",10,FG,true);title.setLetterSpacing(.12f);title.setGravity(Gravity.CENTER);top.addView(title,new LinearLayout.LayoutParams(0,-2,1));top.addView(icon("queue",FG,this::showQueue));page.addView(top);space(page,16);TextView capsule=text("YOUR MOMENT. YOUR MUSIC.",10,GREEN,true);capsule.setGravity(Gravity.CENTER);capsule.setLetterSpacing(.16f);page.addView(capsule);space(page,24);
        int artSize=Math.min(360,(int)(getResources().getDisplayMetrics().widthPixels/getResources().getDisplayMetrics().density)-64);nowArtFrame=new FrameLayout(this);nowArtFrame.setBackground(border(RAISED,28));nowArtFrame.setElevation(dp(12));nowArtFrame.setClipToOutline(true);nowArtFrame.addView(new RecordArt(this),new FrameLayout.LayoutParams(-1,-1));nowArt=new ImageView(this);nowArt.setScaleType(ImageView.ScaleType.CENTER_CROP);nowArtFrame.addView(nowArt,new FrameLayout.LayoutParams(-1,-1));LinearLayout.LayoutParams artParams=new LinearLayout.LayoutParams(dp(artSize),dp(artSize));artParams.gravity=Gravity.CENTER_HORIZONTAL;page.addView(nowArtFrame,artParams);space(page,26);
        LinearLayout name=row();LinearLayout names=column();nowTitle=label("",25,FG,true);nowArtist=label("",15,MUTED,false);names.addView(nowTitle);space(names,6);names.addView(nowArtist);name.addView(names,new LinearLayout.LayoutParams(0,-2,1));likeIcon=icon("heart",GREEN,()->{Track t=Track.from(player.getCurrentMediaItem());boolean liked=app.library.contains("liked",t.id());if(liked)app.library.remove("liked",t.id());else app.library.add("liked",t);playbackStatusId="";updateNow();refreshDiscovery(false);loadSuggestions(recommendationQuery());});likeIcon.setContentDescription("Toggle liked song");name.addView(likeIcon);page.addView(name);space(page,18);
        seek=new SeekBar(this);seek.setMax(1000);seek.setProgressTintList(android.content.res.ColorStateList.valueOf(GREEN));seek.setThumbTintList(android.content.res.ColorStateList.valueOf(GREEN));seek.setContentDescription("Playback position");page.addView(seek,new LinearLayout.LayoutParams(-1,dp(44)));seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int v,boolean user){if(user&&player.getDuration()>0)elapsed.setText(time(player.getDuration()*v/1000));}public void onStartTrackingTouch(SeekBar s){dragging=true;}public void onStopTrackingTouch(SeekBar s){if(player.getDuration()>0)player.seekTo(player.getDuration()*s.getProgress()/1000);dragging=false;}});
        LinearLayout times=row();elapsed=text("0:00",12,MUTED,false);total=text("0:00",12,MUTED,false);times.addView(elapsed,new LinearLayout.LayoutParams(0,-2,1));times.addView(total);page.addView(times);space(page,16);
        LinearLayout controls=row();controls.setGravity(Gravity.CENTER);controls.setPadding(dp(4),dp(12),dp(4),dp(12));controls.setBackground(border(palette.selected(),32));shuffleIcon=icon("shuffle",MUTED,()->player.setShuffleModeEnabled(!player.getShuffleModeEnabled()));controls.addView(shuffleIcon,new LinearLayout.LayoutParams(0,dp(56),1));Icons prev=icon("previous",FG,()->{if(player.getCurrentPosition()>3000)player.seekTo(0);else if(player.hasPreviousMediaItem())player.seekToPreviousMediaItem();else player.seekTo(0);});controls.addView(prev,new LinearLayout.LayoutParams(0,dp(60),1));playIcon=icon("play",INK,this::togglePlay);playIcon.setBackground(bg(GREEN,50));playIcon.setElevation(dp(5));controls.addView(playIcon,new LinearLayout.LayoutParams(dp(76),dp(76)));Icons next=icon("next",FG,()->{if(player.hasNextMediaItem())player.seekToNextMediaItem();else toast("You're at the end of the queue.");});controls.addView(next,new LinearLayout.LayoutParams(0,dp(60),1));repeatIcon=icon("repeat",MUTED,()->player.setRepeatMode((player.getRepeatMode()+1)%3));repeatIcon.setContentDescription("Change repeat mode");controls.addView(repeatIcon,new LinearLayout.LayoutParams(0,dp(56),1));page.addView(controls);space(page,18);
        nowStatus=text("",12,MUTED,false);nowStatus.setGravity(Gravity.CENTER);page.addView(nowStatus);space(page,14);LinearLayout actions=row();actions.setGravity(Gravity.CENTER);Icons save=icon("download",FG,()->download(Track.from(player.getCurrentMediaItem())));save.setContentDescription("Save current song offline");actions.addView(save);TextView source=text("MuseDock · Music",12,MUTED,false);source.setPadding(dp(12),0,dp(12),0);actions.addView(source);Icons menu=icon("more",FG,()->songMenu(Track.from(player.getCurrentMediaItem()),false));menu.setContentDescription("Playback options");actions.addView(menu);page.addView(actions);space(page,12);TextView feedback=text("Your mix grows with what you love. Early skips help tune the next picks.",11,MUTED,false);feedback.setGravity(Gravity.CENTER);page.addView(feedback);
        nowDialog.setOnDismissListener(d->{dragging=false;if(!closing&&(tab.equals("library")||tab.equals("home")||tab.equals("mix"))){render();if(discoveryVisible())refreshDiscovery(false);}});nowArt.setTag(null);nowDialog.show();nowDialog.getWindow().setLayout(-1,-1);WindowCompat.getInsetsController(nowDialog.getWindow(),container).setAppearanceLightStatusBars(palette.light());WindowCompat.getInsetsController(nowDialog.getWindow(),container).setAppearanceLightNavigationBars(palette.light());updateNow();
    }
    private void updateNow(){
        if(nowDialog==null||!nowDialog.isShowing()||player==null)return;
        if(player.getCurrentMediaItem()==null){nowDialog.dismiss();return;}
        Track t=Track.from(player.getCurrentMediaItem());if(!t.equals(nowTrack)){nowTrack=t;nowTitle.setText(t.title());nowArtist.setText(t.artist());}
        if(!t.id().equals(playbackStatusId)){playbackStatusId=t.id();playbackLiked=false;playbackOffline=false;files.execute(()->{boolean liked=app.library.contains("liked",t.id()),offline=app.library.offline(t.id())!=null;runOnUiThread(()->{if(closing||!t.id().equals(playbackStatusId))return;playbackLiked=liked;playbackOffline=offline;updateNow();});});}
        if(!t.id().equals(nowArtFrame.getTag())){nowArtFrame.setTag(t.id());nowArt.setImageDrawable(null);nowArt.setTag("");loadImage(nowArt,t.artworkUrl(),Math.min(1280,getResources().getDisplayMetrics().widthPixels));}
        nowArt.setContentDescription("Artwork for "+t.title());
        playIcon.set(player.isPlaying()?"pause":"play",INK);playIcon.setContentDescription(player.isPlaying()?"Pause":"Play");likeIcon.set(playbackLiked?"liked":"heart",GREEN);shuffleIcon.set("shuffle",player.getShuffleModeEnabled()?GREEN:MUTED);repeatIcon.set("repeat",player.getRepeatMode()==Player.REPEAT_MODE_OFF?MUTED:GREEN);
        updatePosition();
        String state=player.getPlayerError()!=null?"Can't play this track · Tap Play to retry":player.getPlaybackState()==Player.STATE_BUFFERING?"Finding the best audio stream…":playbackOffline?"Playing from your offline library":"Playing online";
        if(player.getRepeatMode()==Player.REPEAT_MODE_ONE)state+=" · Repeat one";else if(player.getRepeatMode()==Player.REPEAT_MODE_ALL)state+=" · Repeat all";nowStatus.setText(state);
    }
    private void updatePosition(){
        if(nowDialog==null||!nowDialog.isShowing()||player==null||seek==null)return;
        long duration=player.getDuration();if(!dragging){elapsed.setText(time(player.getCurrentPosition()));seek.setProgress(duration>0?(int)(player.getCurrentPosition()*1000/duration):0);}total.setText(duration>0?time(duration):"–:––");seek.setEnabled(duration>0);
    }
    private void showEqualizer(){if(equalizerPanel!=null)equalizerPanel.dismiss();equalizerPanel=new EqualizerPanel(this,app.equalizer);equalizerPanel.show();}
    private void showQueue(){
        if(player==null){toast("The player is connecting. Please try again.");return;}
        if(queuePanel!=null)queuePanel.dismiss();queuePanel=new QueuePanel(this,player,this::artwork);queuePanel.show();
    }
    private void songMenu(Track t,boolean inPlaylist){
        boolean liked=app.library.contains("liked",t.id()),offline=app.library.offline(t.id())!=null;
        ArrayList<String> options=new ArrayList<>(List.of(liked?"Remove from liked songs":"Like song","Add to playlist",offline?"Remove offline copy":"Save offline","Add to queue","Open in YouTube Music"));
        if(inPlaylist){options.add("Remove from this playlist");options.add("Move earlier");options.add("Move later");}
        if(offline){options.add("Export offline audio");if(!app.library.offline(t.id()).endsWith(".mp3"))options.add("Convert offline copy to MP3");}
        options.add("Equalizer");if(!inPlaylist)options.add("Hide from suggestions");
        new AlertDialog.Builder(this).setTitle(t.title()).setItems(options.toArray(new String[0]),(d,action)->{
            if(options.get(action).equals("Hide from suggestions")){app.library.hideSuggestion(t.id());refreshDiscovery(true);toast("Hidden from recommendations");return;}
            if(options.get(action).equals("Equalizer")){showEqualizer();return;}
            if(options.get(action).equals("Export offline audio")){exportAudio(t);return;}
            if(options.get(action).equals("Convert offline copy to MP3")){startDownload(t,true);return;}
            switch(action){
                case 0->{if(liked)app.library.remove("liked",t.id());else app.library.add("liked",t);toast(liked?"Removed from liked songs":"Added to liked songs");playbackStatusId="";updateNow();if(tab.equals("library"))render();if(tab.equals("home")){refreshDiscovery(false);loadSuggestions(recommendationQuery());}}
                case 1->choosePlaylist(t);
                case 2->{if(offline)new AlertDialog.Builder(this).setTitle("Remove offline copy?").setMessage("This frees device storage. The song stays in your playlists.").setPositiveButton("Remove",(dd,w)->{if(app.library.removeDownload(t.id())){toast("Offline copy removed");render();}else toast("Could not remove the file. Please try again.");}).setNegativeButton("Keep",null).show();else download(t);}
                case 3->{if(player!=null){app.library.remember(t);player.addMediaItem(t.mediaItem(app.library.offline(t.id())));toast("Added to queue");}}
                case 4->{try{startActivity(new Intent(Intent.ACTION_VIEW,android.net.Uri.parse("https://music.youtube.com/watch?v="+t.id())));}catch(ActivityNotFoundException e){toast("No browser is installed.");}}
                case 5->{app.library.remove(playlistId,t.id());render();}
                case 6->{app.library.move(playlistId,t.id(),-1);render();}
                case 7->{app.library.move(playlistId,t.id(),1);render();}
            }
        }).setNegativeButton("Close",null).show();
    }
    private void choosePlaylist(Track t){
        List<Library.Playlist> lists=app.library.playlists();ArrayList<String> names=new ArrayList<>();names.add("+ Create a new playlist");for(var p:lists)names.add(p.name());
        new AlertDialog.Builder(this).setTitle("Add to playlist").setItems(names.toArray(new String[0]),(d,index)->{if(index==0)createAndAdd(t);else{var p=lists.get(index-1);app.library.add(p.id(),t);toast("Added to "+p.name());}}).show();
    }
    private void openOwnPlaylist(String id){
        Library.Playlist saved=app.library.playlists().stream().filter(item->item.id().equals(id)).findFirst().orElseThrow();
        playlistId=id;playlistName=saved.name();tab="library";render();
    }
    private void createAndAdd(Track track){
        nameDialog("New playlist","",name->{String id=app.library.importPlaylist(name,List.of(track));openOwnPlaylist(id);toast("Playlist created · Song added");});
    }
    private void editPlaylist(String id,String old){
        nameDialog(id==null?"New playlist":"Rename playlist",old,name->{
            if(id==null){openOwnPlaylist(app.library.createPlaylist(name));toast("Your playlist is ready");}
            else{app.library.rename(id,name);openOwnPlaylist(id);}
        });
    }
    private interface NameAction{void accept(String name);}
    private void nameDialog(String title,String old,NameAction action){
        LinearLayout content=column();inset(content,22);content.addView(text("Give your playlist a name.",14,MUTED,false));space(content,10);
        EditText input=new EditText(this);input.setSingleLine(true);input.setText(old);input.setHint("Playlist name");input.setTextColor(FG);input.setHintTextColor(MUTED);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);input.setImeOptions(EditorInfo.IME_ACTION_DONE);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(80)});content.addView(input,new LinearLayout.LayoutParams(-1,dp(56)));
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(title).setView(content).setPositiveButton("Save",null).setNegativeButton("Cancel",null).create();
        dialog.show();
        Runnable save=()->{
            String name=input.getText().toString().trim();
            if(name.isEmpty()){input.setError("Enter a playlist name");input.requestFocus();return;}
            try{action.accept(name);hideKeyboard();dialog.dismiss();}
            catch(IllegalArgumentException error){input.setError(error.getMessage());}
            catch(android.database.SQLException error){input.setError("Couldn't save this playlist. Please try again.");}
        };
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->save.run());
        input.setOnEditorActionListener((v,event,key)->{if(event==EditorInfo.IME_ACTION_DONE){save.run();return true;}return false;});
        input.requestFocus();input.setSelection(input.length());
    }
    private List<Track> downloadedTracks(){
        List<Track> tracks=new ArrayList<>();for(Library.Download download:app.library.downloads())if(app.library.offline(download.track().id())!=null&&!download.status().equals("converting"))tracks.add(download.track());return tracks;
    }
    private void createOfflinePlaylist(){
        if(downloadedTracks().isEmpty()){toast("Save songs offline first, or create an empty playlist in Library.");return;}
        nameDialog("New offline playlist","",name->selectDownloadedSongs(null,name));
    }
    private void selectDownloadedSongs(String target,String newName){
        List<Track> tracks=downloadedTracks();if(tracks.isEmpty()){toast("No downloaded songs are available yet.");return;}
        String[] names=tracks.stream().map(track->track.title()+" · "+track.artist()).toArray(String[]::new);boolean[] checked=new boolean[tracks.size()];
        AlertDialog picker=new AlertDialog.Builder(this).setTitle("Choose downloaded songs").setMultiChoiceItems(names,checked,(d,index,value)->checked[index]=value)
            .setPositiveButton(target==null?"Create playlist":"Add songs",null).setNeutralButton("Select all",null).setNegativeButton("Cancel",null).create();picker.show();
        picker.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->{boolean all=true;for(boolean item:checked)all&=item;for(int i=0;i<checked.length;i++){checked[i]=!all;picker.getListView().setItemChecked(i,!all);}picker.getButton(AlertDialog.BUTTON_NEUTRAL).setText(all?"Select all":"Clear selection");});
        picker.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            List<Track> selected=new ArrayList<>();for(int i=0;i<checked.length;i++)if(checked[i])selected.add(tracks.get(i));if(selected.isEmpty()){toast("Choose at least one downloaded song");return;}
            try{String id=target;if(id==null)id=app.library.importPlaylist(newName,selected);else for(Track track:selected)app.library.add(id,track);picker.dismiss();openOwnPlaylist(id);toast("Downloaded songs added to your playlist");}
            catch(android.database.SQLException error){toast("Couldn't save these songs. Your downloads are kept.");}
        });
    }
    private Library.Playlist currentPlaylist(){return app.library.playlists().stream().filter(p->p.id().equals(playlistId)).findFirst().orElseThrow();}
    private void playlistOptions(){
        Library.Playlist item=currentPlaylist();String[] options={"Edit details","Add songs from search","Add downloaded songs","Refresh suggestions","Sort songs","Select songs","Duplicate playlist","Merge into another playlist",item.pinned()?"Unpin playlist":"Pin playlist","Delete playlist"};
        new AlertDialog.Builder(this).setTitle(playlistName).setItems(options,(dialog,index)->{switch(index){
            case 0->editPlaylistDetails(item);
            case 1->{addTargetPlaylistId=item.id();tab="search";query="";results.clear();albumResults.clear();nextPage=null;render();}
            case 2->selectDownloadedSongs(item.id(),null);
            case 3->loadPlaylistAdditions(true);
            case 4->new AlertDialog.Builder(this).setTitle("Sort playlist").setItems(new String[]{"Song title","Artist","Reverse current order"},(d,at)->{app.library.sortPlaylist(item.id(),new String[]{"title","artist","reverse"}[at]);render();}).show();
            case 5->selectPlaylistSongs(item.id());
            case 6->nameDialog("Duplicate playlist",(item.name()+" copy").substring(0,Math.min(80,item.name().length()+5)),name->{openOwnPlaylist(app.library.duplicate(item.id(),name));toast("Playlist duplicated");});
            case 7->mergePlaylist(item.id());
            case 8->{app.library.pin(item.id(),!item.pinned());render();toast(item.pinned()?"Playlist unpinned":"Playlist pinned");}
            case 9->new AlertDialog.Builder(this).setTitle("Delete this playlist?").setMessage("Downloaded audio stays in your offline library.").setPositiveButton("Delete",(d,w)->{app.library.deletePlaylist(item.id());playlistId=null;render();}).setNegativeButton("Keep",null).show();
        }}).setNegativeButton("Close",null).show();
    }
    private void editPlaylistDetails(Library.Playlist item){
        LinearLayout form=column();inset(form,20);EditText name=new EditText(this);name.setText(item.name());name.setHint("Playlist name");name.setTextColor(FG);name.setHintTextColor(MUTED);name.setSingleLine();name.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(80)});form.addView(name);
        EditText description=new EditText(this);description.setText(item.description());description.setHint("Description · optional");description.setTextColor(FG);description.setHintTextColor(MUTED);description.setMinLines(2);description.setMaxLines(5);description.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(280)});form.addView(description);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Edit playlist").setView(form).setPositiveButton("Save",null).setNegativeButton("Cancel",null).create();dialog.show();dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{if(name.getText().toString().trim().isEmpty()){name.setError("Name your playlist");return;}app.library.details(item.id(),name.getText().toString(),description.getText().toString());playlistName=name.getText().toString().trim();dialog.dismiss();render();});
    }
    private void mergePlaylist(String source){
        List<Library.Playlist> targets=app.library.playlists().stream().filter(p->!p.id().equals(source)).collect(java.util.stream.Collectors.toList());if(targets.isEmpty()){toast("Create another playlist first");return;}
        String[] names=targets.stream().map(Library.Playlist::name).toArray(String[]::new);new AlertDialog.Builder(this).setTitle("Add these songs to…").setItems(names,(d,index)->{app.library.addAll(targets.get(index).id(),app.library.playlistTracks(source));toast("Songs merged · Existing songs kept once");}).setNegativeButton("Cancel",null).show();
    }
    private void selectPlaylistSongs(String id){
        List<Track> songs=app.library.playlistTracks(id);if(songs.isEmpty()){toast("This playlist is empty");return;}boolean[] chosen=new boolean[songs.size()];String[] titles=songs.stream().map(t->t.title()+" · "+t.artist()).toArray(String[]::new);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Select songs to remove").setMultiChoiceItems(titles,chosen,(d,index,checked)->chosen[index]=checked).setNeutralButton("Select all",null).setPositiveButton("Remove",null).setNegativeButton("Cancel",null).create();dialog.show();dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->{boolean all=true;for(boolean checked:chosen)all&=checked;for(int i=0;i<chosen.length;i++){chosen[i]=!all;dialog.getListView().setItemChecked(i,!all);}});dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{List<String> ids=new ArrayList<>();for(int i=0;i<chosen.length;i++)if(chosen[i])ids.add(songs.get(i).id());if(ids.isEmpty()){toast("Choose a song first");return;}app.library.removeAll(id,ids);dialog.dismiss();render();toast("Removed from playlist · Offline files kept");});
    }
    private void loadPlaylistAdditions(boolean force){
        if(playlistId==null||closing)return;String id=playlistId;if(!force&&id.equals(playlistRecId))return;playlistRecId=id;playlistAdditions.clear();playlistRecLoading=true;int version=++playlistRecVersion;List<Track> existing=app.library.playlistTracks(id);Recommendations.Profile base=tasteProfile();List<Library.Taste> taste=existing.isEmpty()?base.taste():existing.stream().limit(12).map(t->new Library.Taste(t,1,0,false)).collect(java.util.stream.Collectors.toList());Set<String> hidden=new HashSet<>(base.hidden());for(Track song:existing)hidden.add(song.id());Recommendations.Profile profile=new Recommendations.Profile(taste,existing.isEmpty()?base.searches():List.of(),hidden,base.language(),base.rotation()+(force?++playlistRecRefresh:0),base.feedback(),null);
        discovery.submit(()->{try{List<Track> found=Recommendations.load(profile,Recommendations.Mood.FOR_YOU,q->app.catalog.search(q,null,MusicCatalog.Mode.SONGS).tracks());runOnUiThread(()->{if(closing||version!=playlistRecVersion||!id.equals(playlistId))return;playlistAdditions.addAll(found);playlistRecLoading=false;render();});}catch(Exception error){runOnUiThread(()->{if(closing||version!=playlistRecVersion||!id.equals(playlistId))return;playlistRecLoading=false;render();});}});if(tab.equals("library"))render();
    }
    private void drawPlaylistAdditions(){
        space(body,18);LinearLayout top=row();top.addView(text("Suggested additions",19,FG,true),new LinearLayout.LayoutParams(0,-2,1));Icons refresh=icon("repeat",MUTED,()->loadPlaylistAdditions(true));refresh.setContentDescription("Refresh playlist suggestions");top.addView(refresh);add(body,top,48);
        Recommendations.Profile feedback=tasteProfile();List<Track> available=playlistAdditions.stream().filter(t->!Recommendations.blocked(feedback,t.id())&&!app.library.contains(playlistId,t.id())).collect(java.util.stream.Collectors.toList());
        if(available.isEmpty())add(body,text(playlistRecLoading?"Finding songs that fit this playlist…":"Add a few favorites or refresh to find related songs.",12,MUTED,false),-2);
        else{add(body,text("Chosen around this playlist's artists. Tap + to update it.",12,MUTED,false),-2);for(Track song:available){LinearLayout row=row();row.addView(artwork(song,44),new LinearLayout.LayoutParams(dp(44),dp(44)));LinearLayout copy=column();copy.setPadding(dp(10),0,0,0);copy.addView(label(song.title(),14,FG,true));copy.addView(label(song.artist(),11,MUTED,false));row.addView(copy,new LinearLayout.LayoutParams(0,-2,1));Icons add=icon("add",GREEN,()->{app.library.add(playlistId,song);playlistAdditions.remove(song);render();toast("Added to playlist");});add.setContentDescription("Add suggested "+song.title());row.addView(add);add(body,row,66);}
            space(body,8);add(body,button("Add all suggestions",false,()->{app.library.addAll(playlistId,available);playlistAdditions.clear();render();toast("Suggested songs added");}),48);}
    }
    private FrameLayout playlistArtwork(String id,int size){
        List<Track> songs=app.library.playlistTracks(id);if(songs.isEmpty())return artwork(null,size);if(songs.size()<4)return artwork(songs.get(0),size);FrameLayout cover=new FrameLayout(this);cover.setBackground(bg(RAISED,14));cover.setClipToOutline(true);LinearLayout mosaic=column();for(int r=0;r<2;r++){LinearLayout row=row();for(int c=0;c<2;c++)row.addView(artwork(songs.get(r*2+c),size/2),new LinearLayout.LayoutParams(0,0+dp(size/2),1));mosaic.addView(row);}cover.addView(mosaic,new FrameLayout.LayoutParams(-1,-1));return cover;
    }
    private void download(Track track){
        if(app.library.offline(track.id())!=null){toast("Already available offline. Use its menu to export or convert to MP3.");return;}
        for(var d:app.library.downloads())if(d.track().id().equals(track.id())&&List.of("queued","downloading","converting").contains(d.status())){toast("This song is already being saved");return;}
        LinearLayout choices=column();inset(choices,22);choices.addView(text("Save audio you have permission to download. Mobile data may be used.",13,MUTED,false));space(choices,12);
        RadioGroup formats=new RadioGroup(this);RadioButton original=new RadioButton(this);original.setId(View.generateViewId());original.setText("Original audio · Faster, smaller");original.setTextColor(FG);formats.addView(original);
        RadioButton mp3=new RadioButton(this);mp3.setId(View.generateViewId());mp3.setText("MP3 · Convert on this device");mp3.setTextColor(FG);formats.addView(mp3);formats.check(original.getId());choices.addView(formats);space(choices,10);choices.addView(text("MP3 conversion takes extra time and storage. Interrupted transfers can continue when you retry.",12,MUTED,false));
        new AlertDialog.Builder(this).setTitle("Save offline").setView(choices).setPositiveButton("Save offline",(d,w)->startDownload(track,formats.getCheckedRadioButtonId()==mp3.getId())).setNegativeButton("Cancel",null).show();
    }
    private void startDownload(Track track,boolean mp3){
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},7);
        try{OfflineService.enqueue(this,track,mp3);toast(mp3?"Saving and converting to MP3 · Check Offline":"Download queued · Check Offline");}catch(RuntimeException e){toast("Couldn't start saving. Keep MuseDock open and retry.");}
    }
    private void exportAudio(Track track){
        String path=app.library.offline(track.id());if(path==null){toast("Download this song first.");return;}pendingExport=path;
        String extension;try{extension=AudioFiles.extension(new java.io.File(path));}catch(java.io.IOException e){toast("Couldn’t read this audio file. Download it again.");return;}String type=AudioFiles.mime(extension);
        Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT).setType(type).addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,track.title().replaceAll("[\\\\/:*?\"<>|]","_")+"."+extension);
        try{startActivityForResult(intent,EXPORT_AUDIO);}catch(ActivityNotFoundException e){toast("No file picker is available on this device.");}
    }
    private void accountScreen(){
        add(body,text("Your MuseDock.",28,FG,true),-2);space(body,8);add(body,text("Bring your favorites with you.",14,MUTED,false),-2);space(body,22);
        android.content.SharedPreferences profile=getSharedPreferences("soundzy.account",MODE_PRIVATE);String name=profile.getString("name","");
        LinearLayout account=column();account.setBackground(bg(SURFACE,20));inset(account,20);account.addView(text(name.isBlank()?"Google account":name,22,FG,true));space(account,8);
        account.addView(text(name.isBlank()?"Optional sign-in for your Google profile. Your music stays on this device.":profile.getString("email",""),13,MUTED,false));space(account,16);
        if(name.isBlank()){
            TextView signIn=button("Sign in with Google",true,this::signInGoogle);boolean ready=!getString(R.string.google_web_client_id).isBlank();signIn.setEnabled(ready);signIn.setAlpha(ready?1f:.5f);account.addView(signIn,new LinearLayout.LayoutParams(-1,dp(48)));
            if(!ready){space(account,10);account.addView(text("Google sign-in isn't available in this build yet. You can import history without signing in.",12,MUTED,false));}
        }else account.addView(button("Sign out",false,()->{
            Runnable clear=()->{if(closing)return;profile.edit().clear().apply();if(tab.equals("account"))render();};
            try{if(googleAccount==null)googleAccount=new GoogleAccount(this);googleAccount.signOut(clear);}catch(RuntimeException e){clear.run();}
        }),new LinearLayout.LayoutParams(-1,dp(48)));
        add(body,account,-2);space(body,22);section("YouTube listening history",null,null);
        add(body,text("Google doesn't let apps read YouTube watch history through sign-in. Import a Google Takeout export instead. Choose which entries are songs before adding them.",14,MUTED,false),-2);space(body,16);
        add(body,button("1. Open Google Takeout",false,()->openBrowser("https://takeout.google.com/")),48);space(body,10);
        add(body,text("In Takeout, select YouTube and YouTube Music → history. Export watch history as JSON or HTML, then unzip the downloaded export.",12,MUTED,false),-2);space(body,14);
        add(body,button("2. Choose history file",true,()->{
            Intent picker=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"application/json","text/html","text/plain","application/octet-stream"});
            try{startActivityForResult(picker,PICK_HISTORY);}catch(ActivityNotFoundException e){toast("No file picker is available on this device.");}
        }),48);space(body,16);add(body,text("Your export is read locally and is never uploaded. History can include videos as well as music. Signing out keeps your playlists and offline files.",12,MUTED,false),-2);
    }
    private void signInGoogle(){
        if(googleAccount==null)googleAccount=new GoogleAccount(this);
        googleAccount.signIn(new GoogleAccount.Callback(){public void signedIn(String name,String email){if(closing)return;getSharedPreferences("soundzy.account",MODE_PRIVATE).edit().putString("name",name).putString("email",email).apply();if(tab.equals("account"))render();}public void failed(String message){if(!closing)toast(message);}});
    }
    private void openBrowser(String url){try{startActivity(new Intent(Intent.ACTION_VIEW,android.net.Uri.parse(url)));}catch(ActivityNotFoundException e){toast("No browser is installed.");}}
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);if(intent.getBooleanExtra("offline",false))navigate("offline");}
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);if(result!=RESULT_OK||data==null||data.getData()==null)return;android.net.Uri uri=data.getData();
        if(request==PICK_HISTORY){toast("Reading history on this device…");files.submit(()->{
            try(java.io.InputStream in=getContentResolver().openInputStream(uri)){
                if(in==null)throw new java.io.IOException("Couldn't open the selected history file.");HistoryImport.Result history=HistoryImport.read(in);runOnUiThread(()->{if(!closing&&!isDestroyed())chooseHistory(history);});
            }catch(Exception e){runOnUiThread(()->{if(!closing)toast(e.getMessage()==null?"Couldn't read this history export.":e.getMessage());});}
        });}else if(request==EXPORT_AUDIO&&pendingExport!=null){String source=pendingExport;pendingExport=null;files.submit(()->{
            try(java.io.InputStream in=new java.io.FileInputStream(source);java.io.OutputStream out=getContentResolver().openOutputStream(uri,"wt")){
                if(out==null)throw new java.io.IOException("Couldn't open the selected destination.");byte[] buffer=new byte[65536];int count;while((count=in.read(buffer))!=-1)out.write(buffer,0,count);runOnUiThread(()->{if(!closing)toast("Audio exported");});
            }catch(Exception e){runOnUiThread(()->{if(!closing)toast("Export failed. Choose another destination and retry.");});}
        });}
    }
    private void chooseHistory(HistoryImport.Result history){
        if(history.entries().isEmpty()){toast("No YouTube video links were found. Choose a watch-history JSON or HTML export.");return;}
        String[] labels=history.entries().stream().map(e->e.track().title()+" · "+e.track().artist()).toArray(String[]::new);boolean[] selected=new boolean[labels.length];
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Choose songs from history"+(history.limited()?" · First 5,000 entries":""))
            .setMultiChoiceItems(labels,selected,(d,which,checked)->selected[which]=checked).setPositiveButton("Import selected",null).setNeutralButton("Select all",null).setNegativeButton("Cancel",null).create();
        dialog.setOnShowListener(d->{
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->{boolean all=true;for(boolean item:selected)all&=item;for(int i=0;i<selected.length;i++){selected[i]=!all;dialog.getListView().setItemChecked(i,!all);}dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setText(all?"Select all":"Clear selection");});
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
                ArrayList<HistoryImport.Entry> chosen=new ArrayList<>();for(int i=0;i<selected.length;i++)if(selected[i])chosen.add(history.entries().get(i));if(chosen.isEmpty()){toast("Choose at least one song to import.");return;}
                dialog.dismiss();toast("Adding selected songs…");files.submit(()->{try{String id=app.library.importHistory(chosen);runOnUiThread(()->{if(closing)return;playlistId=id;playlistName="YouTube listening history";tab="library";render();toast("History imported · "+chosen.size()+" songs");});}catch(RuntimeException e){runOnUiThread(()->{if(!closing)toast("Couldn't import these entries. Your library was kept.");});}});
            });
        });dialog.show();
    }
    private void showAbout(){
        new AlertDialog.Builder(this).setTitle("MuseDock · "+getString(R.string.app_version)).setMessage(getString(R.string.developed_by)+"\n\nYour music, in your frequency.\n\nAndroid 10+ · No account required\n\nDiscover songs, albums and playlists through YouTube Music search with the open-source NewPipe Extractor. Previously saved tracks and explicitly imported playlists remain playable. For you suggestions combine your current song, searches, artists and explicit style clues; other filters use the selected mood. Optional Google sign-in saves only your name and email locally; it does not grant YouTube history access. Takeout exports are processed locally. Offline audio and optional MP3 conversion stay on this device. Recommendations use local searches, meaningful listening, early skips and likes; derived song/artist searches go to YouTube Music to fetch suggestions. You can hide songs from suggestions through their menu. There are no in-app ads. YouTube availability and extraction can change; some videos may not play.\n\nPlaylists and downloads stay on this device. Uninstalling clears them. Audio files are kept in app-private storage. All discovery filters search YouTube Music through NewPipe; playback resolves its shared YouTube/Google media streams. General-video search and related-video recommendations are disabled. Equalizer effects depend on device audio support. Search requests and playback connect directly to YouTube/Google; thumbnail requests connect to their image servers. No analytics or developer server.\n\nOnly download content you have permission to save. This app is not affiliated with YouTube or Spotify.\n\nOpen-source: GPL-3.0-or-later. NewPipe Extractor and nanojson: GPL-3.0-or-later and Apache-2.0 respectively; AndroidX Media3: Apache-2.0; OkHttp: Apache-2.0. Full notices are included with the source.").setNeutralButton("Account & history",(d,w)->navigate("account")).setPositiveButton("Got it",null).show();
    }
    private void hideKeyboard(){View focus=getCurrentFocus();if(focus!=null)getSystemService(InputMethodManager.class).hideSoftInputFromWindow(focus.getWindowToken(),0);}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    private String time(long ms){long seconds=Math.max(0,ms/1000);return seconds>=3600?String.format(Locale.ROOT,"%d:%02d:%02d",seconds/3600,seconds/60%60,seconds%60):String.format(Locale.ROOT,"%d:%02d",seconds/60,seconds%60);}
}
