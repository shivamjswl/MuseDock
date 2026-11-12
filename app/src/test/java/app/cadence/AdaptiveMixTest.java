package app.cadence;
import androidx.media3.common.*;
import androidx.media3.exoplayer.ExoPlayer;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
import java.util.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk={29,37},application=MuseDockApp.class)
public class AdaptiveMixTest {
    private MuseDockApp app;private ExoPlayer player;
    private Track track(int id){return new Track(String.format(Locale.ROOT,"mix%08d",id),"Song "+id,"Artist "+id,"",180);}
    @Before public void setup(){app=(MuseDockApp)RuntimeEnvironment.getApplication();player=new ExoPlayer.Builder(app).build();player.setMediaItems(List.of(item(1),item(2),item(3)),1,0);}
    private MediaItem item(int id){return PlaybackService.autoMixItem(track(id),null,"FOR_YOU");}
    @After public void close(){player.release();app.library.close();}
    @Test public void refreshedTailPreservesCurrentSongAndHistoryAndFiltersFeedback(){AdaptiveMix captured=AdaptiveMix.capture(player);app.library.listening(track(5),true);assertTrue(captured.apply(player,List.of(track(1),track(2),track(4),track(4),track(5)),app.library));assertEquals(3,player.getMediaItemCount());assertEquals(track(1).id(),player.getMediaItemAt(0).mediaId);assertEquals(track(2).id(),player.getCurrentMediaItem().mediaId);assertEquals(track(4).id(),player.getMediaItemAt(2).mediaId);}
    @Test public void userQueueEditInvalidatesPendingRefresh(){AdaptiveMix captured=AdaptiveMix.capture(player);player.addMediaItem(item(8));assertFalse(captured.apply(player,List.of(track(4)),app.library));assertEquals(4,player.getMediaItemCount());}
    @Test public void ordinaryQueuesAndShuffleAreNotRewritten(){player.setMediaItem(track(1).mediaItem(null));assertNull(AdaptiveMix.capture(player));player.setMediaItem(item(1));player.setShuffleModeEnabled(true);assertNull(AdaptiveMix.capture(player));}
    @Test public void emptyResultsOrTrackChangeKeepTheQueue(){AdaptiveMix captured=AdaptiveMix.capture(player);assertFalse(captured.apply(player,List.of(),app.library));player.seekToNextMediaItem();assertFalse(captured.apply(player,List.of(track(4)),app.library));assertEquals(3,player.getMediaItemCount());}
}
