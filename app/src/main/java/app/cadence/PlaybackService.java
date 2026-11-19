package app.cadence;

import android.net.Uri;
import androidx.media3.common.*;
import androidx.media3.datasource.*;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.session.*;

@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
public final class PlaybackService extends MediaSessionService {
    private ExoPlayer player;
    private MediaSession session;
    private final android.os.Handler handler=new android.os.Handler(android.os.Looper.getMainLooper());
    private final java.util.concurrent.ExecutorService recommendations=java.util.concurrent.Executors.newSingleThreadExecutor();
    private java.util.concurrent.Future<?> refresh;private int generation;private boolean destroyed;
    private ListeningFeedback feedback;
    private boolean mixRefreshPending;
    private void refreshMixWhenReady(){
        if(mixRefreshPending&&player.getPlaybackState()==Player.STATE_READY){
            mixRefreshPending=false;
            adaptMix();
        }
    }
    private final java.util.concurrent.ExecutorService feedbackIO=java.util.concurrent.Executors.newSingleThreadExecutor();
    private void changed(){sendBroadcast(new android.content.Intent("app.musedock.TASTE_CHANGED").setPackage(getPackageName()));}
    private final Runnable listenTick=new Runnable(){public void run(){if(destroyed)return;feedback.tick();handler.postDelayed(this,5000);}};
    private void adaptMix(){
        AdaptiveMix mix=AdaptiveMix.capture(player);if(mix==null)return;MediaItem current=player.getCurrentMediaItem();
        if(refresh!=null)refresh.cancel(true);int version=++generation;MuseDockApp app=MuseDockApp.of(this);
        MusicLanguage language=MusicLanguage.selected(this);
        refresh=recommendations.submit(()->{try{
            Recommendations.Profile profile=Recommendations.profile(app.library,language,0).withCurrent(Track.from(current));
            var found=Recommendations.rank(profile,mix.mood,Recommendations.catalogCandidates(profile,mix.mood,app.catalog),12);
            handler.post(()->{if(destroyed||version!=generation||player.getCurrentMediaItem()==null||!current.mediaId.equals(player.getCurrentMediaItem().mediaId))return;
                mix.apply(player,found,profile);
            });
        }catch(Exception ignored){/* Keep the remaining queue on network failure. */}});
    }
    static MediaItem autoMixItem(Track t,String local,String mood){MediaItem item=t.mediaItem(local);android.os.Bundle extras=new android.os.Bundle(item.mediaMetadata.extras);extras.putString("musedock.autoMix",mood);return item.buildUpon().setMediaMetadata(item.mediaMetadata.buildUpon().setExtras(extras).build()).build();}

    @Override public void onCreate(){
        super.onCreate();
        MuseDockApp app=MuseDockApp.of(this);
        DefaultHttpDataSource.Factory http=new DefaultHttpDataSource.Factory()
            .setUserAgent(YouTubeRepository.USER_AGENT).setConnectTimeoutMs(20000).setReadTimeoutMs(30000)
            .setDefaultRequestProperties(java.util.Map.of("Referer","https://www.youtube.com/"));
        ResolvingDataSource.Factory resolving=new ResolvingDataSource.Factory(new DefaultDataSource.Factory(this,http),spec->{
            String id=VideoIds.parse(spec.uri.toString());
            if(id==null)return spec;
            String local=app.library.offline(id);
            return spec.withUri(local==null?Uri.parse(app.youtube.resolve(id)):Uri.fromFile(new java.io.File(local)));
        });
        androidx.media3.exoplayer.DefaultRenderersFactory renderers=new androidx.media3.exoplayer.DefaultRenderersFactory(this){
            @Override protected androidx.media3.exoplayer.audio.AudioSink buildAudioSink(android.content.Context context,boolean floating,boolean playbackParams){return new androidx.media3.exoplayer.audio.DefaultAudioSink.Builder(context).setEnableFloatOutput(false).setEnableAudioTrackPlaybackParams(playbackParams).setAudioProcessors(new androidx.media3.common.audio.AudioProcessor[]{new StereoBalanceProcessor(()->app.equalizer.enabled()?app.equalizer.balance():0)}).build();}
        };
        player=new ExoPlayer.Builder(this,renderers).setMediaSourceFactory(new DefaultMediaSourceFactory(resolving)).build();
        player.setAudioAttributes(new AudioAttributes.Builder().setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).setUsage(C.USAGE_MEDIA).build(),true);
        player.setHandleAudioBecomingNoisy(true);
        player.setWakeMode(C.WAKE_MODE_LOCAL);app.equalizer.setGainListener(gain->player.setVolume(gain));
        player.addListener(new Player.Listener(){
            @Override public void onAudioSessionIdChanged(int id){app.equalizer.attach(id);}
            @Override public void onPlaybackStateChanged(int state){refreshMixWhenReady();}
            @Override public void onMediaItemTransition(MediaItem item,int reason){mixRefreshPending=false;generation++;if(refresh!=null)refresh.cancel(true);}
            @Override public void onPlayerError(PlaybackException error){MediaItem current=player.getCurrentMediaItem();if(current!=null)app.youtube.invalidate(current.mediaId);}
        });
        feedback=new ListeningFeedback(player,(track,skipped)->feedbackIO.execute(()->{
            try{
                app.library.listening(track,skipped);
                changed();
            }catch(android.database.SQLException error){
                android.util.Log.w("MuseDock","Could not store listening feedback",error);
            }
        }),android.os.SystemClock::elapsedRealtime,this::changed,()->{
            int transition=generation;
            handler.postDelayed(()->{
                if(!destroyed&&transition==generation){
                    mixRefreshPending=true;
                    refreshMixWhenReady();
                }
            },1000);
        });
        player.addListener(feedback);
        handler.post(listenTick);
        session=new MediaSession.Builder(this,player).setCallback(new MediaSession.Callback(){
            @Override public MediaSession.ConnectionResult onConnect(MediaSession s,MediaSession.ControllerInfo controller){
                if(!controller.getPackageName().equals(getPackageName())&&!s.isMediaNotificationController(controller)&&!controller.isTrusted())return MediaSession.ConnectionResult.reject();
                return MediaSession.Callback.super.onConnect(s,controller);
            }
        }).build();
        session.setSessionActivity(android.app.PendingIntent.getActivity(this,0,new android.content.Intent(this,MainActivity.class),android.app.PendingIntent.FLAG_IMMUTABLE));
    }
    @Override public MediaSession onGetSession(MediaSession.ControllerInfo controller){return session;}
    @Override public void onTaskRemoved(android.content.Intent intent){
        if(!player.getPlayWhenReady()||player.getMediaItemCount()==0)stopSelf();
    }
    @Override public void onDestroy(){destroyed=true;generation++;handler.removeCallbacksAndMessages(null);recommendations.shutdownNow();feedbackIO.shutdown();MuseDockApp.of(this).equalizer.setGainListener(null);if(session!=null)session.release();if(player!=null)player.release();MuseDockApp.of(this).equalizer.release();super.onDestroy();}
}
