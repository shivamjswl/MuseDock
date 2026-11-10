package app.cadence;

import androidx.media3.common.C;
import androidx.media3.common.audio.BaseAudioProcessor;
import java.nio.*;
import java.util.function.IntSupplier;

/** Attenuates one stereo channel in PCM; never boosts, folds mono, or changes channel order. */
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
public final class StereoBalanceProcessor extends BaseAudioProcessor {
    private final IntSupplier balance;
    public StereoBalanceProcessor(IntSupplier balance){this.balance=balance;}
    @Override protected AudioFormat onConfigure(AudioFormat format){return format.channelCount==2&&(format.encoding==C.ENCODING_PCM_16BIT||format.encoding==C.ENCODING_PCM_FLOAT)?format:AudioFormat.NOT_SET;}
    @Override public void queueInput(ByteBuffer input){
        ByteBuffer out=replaceOutputBuffer(input.remaining()).order(ByteOrder.LITTLE_ENDIAN);input.order(ByteOrder.LITTLE_ENDIAN);int value=Math.max(-100,Math.min(100,balance.getAsInt()));float left=value>0?1-value/100f:1,right=value<0?1+value/100f:1;
        if(inputAudioFormat.encoding==C.ENCODING_PCM_FLOAT){while(input.remaining()>=8){out.putFloat(input.getFloat()*left);out.putFloat(input.getFloat()*right);}}
        else while(input.remaining()>=4){out.putShort((short)Math.round(input.getShort()*left));out.putShort((short)Math.round(input.getShort()*right));}
        while(input.hasRemaining())out.put(input.get());out.flip();
    }
}
