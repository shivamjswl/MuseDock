package app.cadence;
import org.junit.Test;
import static org.junit.Assert.*;
public class ListeningTrackerTest {
    @Test public void pausesAndBufferingDoNotCount(){ListeningTracker t=new ListeningTracker();t.playing(true,0);t.playing(false,5000);assertEquals(5000,t.heard(100000));assertTrue(t.earlySkip(180000,100000));assertFalse(t.reward(180000,100000));t.playing(true,100000);assertTrue(t.reward(180000,125000));assertFalse(t.reward(180000,126000));assertFalse(t.earlySkip(180000,126000));}
    @Test public void skippedBeforeAudioStartsIsNotNegativeFeedback(){ListeningTracker t=new ListeningTracker();assertFalse(t.earlySkip(180000,10000));t.playing(true,10000);assertFalse(t.earlySkip(180000,10000));t.reset();assertEquals(0,t.heard(20000));}
    @Test public void shortSongsAndUnknownDurationsHaveMeaningfulThresholds(){ListeningTracker t=new ListeningTracker();t.playing(true,0);assertTrue(t.earlySkip(20000,4000));assertFalse(t.earlySkip(20000,6000));assertTrue(t.reward(20000,10000));t.reset();t.playing(true,0);assertFalse(t.reward(-1,29000));assertTrue(t.reward(-1,30000));}
}
