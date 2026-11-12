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
public class ListeningFeedbackTest {
    private MuseDockApp app;private ExoPlayer player;private ListeningFeedback feedback;private long now;private int skips;
    private final Track first=new Track("feedback001","First","Artist","",180),second=new Track("feedback002","Second","Other","",180);
    @Before public void setup(){app=(MuseDockApp)RuntimeEnvironment.getApplication();player=new ExoPlayer.Builder(app).build();feedback=new ListeningFeedback(player,app.library,()->now,()->{},()->skips++);player.addListener(feedback);player.setMediaItems(List.of(first.mediaItem(null),second.mediaItem(null)));}
    @After public void close(){player.release();app.library.close();}
    @Test public void actualMedia3NextTransitionRecordsSkipAndRequestsMixUpdate(){feedback.onIsPlayingChanged(true);now=7000;player.seekToNextMediaItem();assertEquals(1,app.library.feedback().size());assertEquals(first.id(),app.library.feedback().get(0).id());assertEquals(1,skips);assertTrue(app.library.taste().isEmpty());}
    @Test public void sameTrackSeekingAndPauseDoNotCreateSkipsOrExtraPlays(){feedback.onIsPlayingChanged(true);now=6000;player.seekTo(60000);assertTrue(app.library.feedback().isEmpty());feedback.onIsPlayingChanged(false);now=90000;feedback.tick();assertTrue(app.library.taste().isEmpty());feedback.onIsPlayingChanged(true);now=114000;feedback.tick();assertEquals(1,app.library.taste().get(0).plays());feedback.onIsPlayingChanged(false);feedback.onIsPlayingChanged(true);now=120000;feedback.tick();assertEquals(1,app.library.taste().get(0).plays());player.seekToNextMediaItem();assertEquals(0,skips);}
    @Test public void playbackFailureAndUnplayedTransitionsDoNotPenalize(){player.seekToNextMediaItem();assertTrue(app.library.feedback().isEmpty());feedback.onIsPlayingChanged(true);now=5000;feedback.onPlayerError(new PlaybackException("network",null,PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED));player.seekToPreviousMediaItem();assertTrue(app.library.feedback().isEmpty());assertEquals(0,skips);}
}
