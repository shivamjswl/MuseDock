package app.cadence;

import androidx.media3.common.C;
import androidx.media3.common.audio.AudioProcessor.AudioFormat;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import java.nio.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=29)
public final class StereoBalanceTest {
    @Test public void pcm16AttenuatesTheCorrectChannelAndCenterIsUnchanged()throws Exception{int[] balance={100};StereoBalanceProcessor processor=new StereoBalanceProcessor(()->balance[0]);processor.configure(new AudioFormat(44100,2,C.ENCODING_PCM_16BIT));processor.flush();ByteBuffer input=ByteBuffer.allocateDirect(4).order(ByteOrder.LITTLE_ENDIAN);input.putShort((short)10000).putShort((short)-10000).flip();processor.queueInput(input);ByteBuffer out=processor.getOutput().order(ByteOrder.LITTLE_ENDIAN);assertEquals(0,out.getShort());assertEquals(-10000,out.getShort());balance[0]=-100;input.clear();input.putShort((short)10000).putShort((short)-10000).flip();processor.queueInput(input);out=processor.getOutput().order(ByteOrder.LITTLE_ENDIAN);assertEquals(10000,out.getShort());assertEquals(0,out.getShort());balance[0]=0;input.clear();input.putShort((short)12345).putShort((short)-23456).flip();processor.queueInput(input);out=processor.getOutput().order(ByteOrder.LITTLE_ENDIAN);assertEquals(12345,out.getShort());assertEquals(-23456,out.getShort());}
    @Test public void floatSamplesKeepTheirEncodingAndMonoBypasses()throws Exception{StereoBalanceProcessor processor=new StereoBalanceProcessor(()->50);assertEquals(AudioFormat.NOT_SET,processor.configure(new AudioFormat(48000,1,C.ENCODING_PCM_16BIT)));processor.configure(new AudioFormat(48000,2,C.ENCODING_PCM_FLOAT));processor.flush();ByteBuffer input=ByteBuffer.allocateDirect(8).order(ByteOrder.LITTLE_ENDIAN);input.putFloat(.5f).putFloat(-.75f).flip();processor.queueInput(input);ByteBuffer out=processor.getOutput().order(ByteOrder.LITTLE_ENDIAN);assertEquals(.25f,out.getFloat(),.00001f);assertEquals(-.75f,out.getFloat(),.00001f);}
}
