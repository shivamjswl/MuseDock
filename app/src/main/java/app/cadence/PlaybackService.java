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
            private boolean counted;
            private void recordListening(){MediaItem item=player.getCurrentMediaItem();if(!counted&&player.isPlaying()&&item!=null){Track track=app.library.track(item.mediaId);if(track!=null){app.library.played(track);counted=true;sendBroadcast(new android.content.Intent("app.musedock.TASTE_CHANGED").setPackage(getPackageName()));}}}
            @Override public void onIsPlayingChanged(boolean playing){recordListening();}
            @Override public void onAudioSessionIdChanged(int id){app.equalizer.attach(id);}
            @Override public void onMediaItemTransition(MediaItem item,int reason){
                counted=false;recordListening();
            }
            @Override public void onPlayerError(PlaybackException error){
                MediaItem current=player.getCurrentMediaItem();if(current!=null)app.youtube.invalidate(current.mediaId);
            }
        });
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
    @Override public void onDestroy(){MuseDockApp.of(this).equalizer.setGainListener(null);if(session!=null)session.release();if(player!=null)player.release();MuseDockApp.of(this).equalizer.release();super.onDestroy();}
}
