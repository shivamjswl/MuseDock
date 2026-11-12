package app.cadence;

import androidx.media3.common.*;
import java.util.function.LongSupplier;

/** Shared by in-app, headset and notification controls through the Media3 listener. */
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
final class ListeningFeedback implements Player.Listener {
    private final Player player;private final Library library;private final Runnable changed,skipped;
    private final LongSupplier clock;private final ListeningTracker tracker=new ListeningTracker();
    private Track track;private boolean pendingSkip;
    ListeningFeedback(Player player,Library library,LongSupplier clock,Runnable changed,Runnable skipped){this.player=player;this.library=library;this.clock=clock;this.changed=changed;this.skipped=skipped;}
    void tick(){if(track!=null&&tracker.reward(track.duration()*1000,clock.getAsLong())){library.listening(track,false);changed.run();}}
    @Override public void onIsPlayingChanged(boolean playing){
        if(playing&&track==null&&player.getCurrentMediaItem()!=null){track=Track.from(player.getCurrentMediaItem());changed.run();}
        tracker.playing(playing,clock.getAsLong());tick();
    }
    @Override public void onPositionDiscontinuity(Player.PositionInfo old,Player.PositionInfo next,int reason){
        boolean different=old.mediaItem!=null&&next.mediaItem!=null&&(old.mediaItemIndex!=next.mediaItemIndex||!old.mediaItem.mediaId.equals(next.mediaItem.mediaId));
        if(different&&reason==Player.DISCONTINUITY_REASON_SEEK&&player.getPlayerError()==null&&track!=null&&track.id().equals(old.mediaItem.mediaId)&&tracker.earlySkip(track.duration()*1000,clock.getAsLong())){
            library.listening(track,true);pendingSkip=true;changed.run();
        }
    }
    @Override public void onMediaItemTransition(MediaItem item,int reason){
        // Credit the outgoing track if the threshold was crossed between timer ticks.
        tick();tracker.reset();track=item==null?null:Track.from(item);tracker.playing(player.isPlaying(),clock.getAsLong());changed.run();
        if(pendingSkip){pendingSkip=false;skipped.run();}
    }
    @Override public void onPlayerError(PlaybackException error){tracker.reset();track=null;pendingSkip=false;}
}
