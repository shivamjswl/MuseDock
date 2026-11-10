package app.cadence;

import android.content.Context;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=29)
public final class AudioEqualizerTest {
    private android.content.SharedPreferences prefs;private Fake effect;private AudioEqualizer equalizer;
    private static final class Fake implements AudioEqualizer.Effect {
        final int[] levels=new int[3];boolean on,released;
        public int bands(){return 3;}public int frequency(int b){return new int[]{60,910,14000}[b];}public int minimum(){return -300;}public int maximum(){return 300;}
        public void level(int b,int value){levels[b]=value;}public void enabled(boolean value){on=value;}public void release(){released=true;}
    }
    @Before public void setup(){prefs=RuntimeEnvironment.getApplication().getSharedPreferences("test-eq",Context.MODE_PRIVATE);prefs.edit().clear().commit();effect=new Fake();equalizer=new AudioEqualizer(prefs,id->effect);}
    @Test public void persistsCurveAndToggleAndAppliesWithinHardwareLimits(){equalizer.preset(1);equalizer.setEnabled(true);equalizer.attach(22);assertTrue(effect.on);assertEquals(300,effect.levels[0]);assertEquals(-100,effect.levels[2]);AudioEqualizer restored=new AudioEqualizer(prefs,id->new Fake());assertTrue(restored.enabled());assertArrayEquals(equalizer.levels(),restored.levels());equalizer.setEnabled(false);assertFalse(effect.on);}
    @Test public void neverAttachesGlobalSessionAndReleasesOnSessionChange(){int[] created={0};equalizer=new AudioEqualizer(prefs,id->{created[0]++;return new Fake();});equalizer.attach(0);assertEquals(0,created[0]);equalizer.attach(10);equalizer.attach(10);assertEquals(1,created[0]);equalizer.attach(20);assertEquals(2,created[0]);equalizer.release();assertFalse(equalizer.available());}
    @Test public void unsupportedDeviceKeepsSettingsWithoutCrashing(){equalizer=new AudioEqualizer(prefs,id->{throw new UnsupportedOperationException();});equalizer.preset(2);equalizer.attach(20);assertFalse(equalizer.available());assertTrue(equalizer.status().contains("unavailable"));assertEquals("Vocal",equalizer.preset());}
    @Test public void resetAndCustomSlidersPersist(){equalizer.setBand(1,9999);assertEquals(1200,equalizer.levels()[1]);assertEquals("Custom",equalizer.preset());equalizer.preset(0);assertArrayEquals(new int[5],equalizer.levels());}
    @Test public void logarithmicInterpolationRespectsEndpoints(){int[] curve={0,200,400,600,800};assertEquals(0,AudioEqualizer.gain(20,curve));assertEquals(400,AudioEqualizer.gain(910,curve));assertEquals(800,AudioEqualizer.gain(20000,curve));int value=AudioEqualizer.gain(500,curve);assertTrue(value>200&&value<400);}
    @Test public void customPresetRestoresCurvePreampBalanceAndHeadroom(){equalizer.setBand(2,350);equalizer.setPreamp(-600);equalizer.setBalance(35);equalizer.setHeadroom(false);equalizer.saveCustom("Headphones");equalizer.reset();equalizer.loadCustom("Headphones");assertEquals(350,equalizer.levels()[2]);assertEquals(-600,equalizer.preamp());assertEquals(35,equalizer.balance());assertFalse(equalizer.headroom());AudioEqualizer restored=new AudioEqualizer(prefs,id->new Fake());assertEquals(java.util.List.of("Headphones"),restored.customPresets());equalizer.deleteCustom("Headphones");assertTrue(equalizer.customPresets().isEmpty());}
    @Test public void preampAndHeadroomSetPlayerGainAndMasterBypasses(){float[] gain={1};equalizer.setGainListener(value->gain[0]=value);equalizer.setEnabled(true);equalizer.preset(1);equalizer.attach(20);assertEquals(Math.pow(10,-500/2000d),gain[0],.0001);equalizer.setPreamp(-600);assertEquals(Math.pow(10,-1100/2000d),gain[0],.0001);equalizer.setHeadroom(false);assertEquals(Math.pow(10,-600/2000d),gain[0],.0001);equalizer.setEnabled(false);assertEquals(1,gain[0],.0001);}
}
