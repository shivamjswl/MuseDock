package app.cadence;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.audiofx.Equalizer;

/** Owns an effect on this player's audio session, never the global output mix. */
public final class AudioEqualizer {
    public static final int[] FREQUENCIES = {60, 230, 910, 3600, 14000};
    public static final String[] PRESETS = {"Flat", "Bass boost", "Vocal", "Treble boost", "Pop", "Rock", "Electronic", "Acoustic", "Jazz", "Podcast"};
    private static final int[][] CURVES = {{0,0,0,0,0}, {500,350,0,-100,-100}, {-150,0,350,300,0}, {-100,-100,0,300,500}, {-100,200,350,150,-100}, {450,250,-150,250,450}, {550,250,0,250,450}, {200,100,200,300,150}, {250,100,-100,150,300}, {-300,-150,400,300,-200}};
    interface Effect {
        int bands(); int frequency(int band); int minimum(); int maximum();
        void level(int band, int value); void enabled(boolean value); void release();
    }
    interface Factory { Effect create(int session); }
    private final SharedPreferences prefs;
    private final Factory factory;
    private Effect effect;
    private java.util.function.Consumer<Float> gainListener;
    private int session;
    private String status = "Start a song to connect the equalizer to its audio output.";
    public AudioEqualizer(Context context) { this(context.getSharedPreferences("musedock.equalizer",Context.MODE_PRIVATE), AudioEqualizer::androidEffect); }
    AudioEqualizer(SharedPreferences prefs, Factory factory) { this.prefs=prefs; this.factory=factory; }
    private static Effect androidEffect(int session) {
        Equalizer eq=new Equalizer(0,session);
        return new Effect() {
            public int bands(){return eq.getNumberOfBands();}
            public int frequency(int band){return eq.getCenterFreq((short)band)/1000;}
            public int minimum(){return eq.getBandLevelRange()[0];}
            public int maximum(){return eq.getBandLevelRange()[1];}
            public void level(int band,int value){if(!eq.hasControl())throw new IllegalStateException("Equalizer has no control");eq.setBandLevel((short)band,(short)value);}
            public void enabled(boolean value){if(!eq.hasControl()||eq.setEnabled(value)!=android.media.audiofx.AudioEffect.SUCCESS)throw new IllegalStateException("Equalizer has no control");}
            public void release(){eq.release();}
        };
    }
    public synchronized void attach(int id) {
        if(id==session&&effect!=null)return;
        release(); if(id<=0)return;
        try { effect=factory.create(id);session=id;apply(); }
        catch(RuntimeException error){unavailable();}
    }
    public synchronized void release() {
        if(effect!=null){try{effect.release();}catch(RuntimeException ignored){} effect=null;}
        session=0;applyGain();status="Start a song to connect the equalizer to its audio output.";
    }
    private void unavailable(){release();status="Tone bands unavailable on this output. Preamp and stereo balance can still be used.";}
    public synchronized boolean available(){return effect!=null;}
    public synchronized String status(){return status;}
    public boolean enabled(){return prefs.getBoolean("enabled",false);}
    public int[] levels(){int[] values=new int[5];for(int i=0;i<5;i++)values[i]=Math.max(-1200,Math.min(1200,prefs.getInt("band"+i,0)));return values;}
    public synchronized void setEnabled(boolean value){prefs.edit().putBoolean("enabled",value).apply();apply();}
    public synchronized void setBand(int band,int value){if(band<0||band>=5)throw new IllegalArgumentException("Invalid equalizer band");prefs.edit().putInt("band"+band,Math.max(-1200,Math.min(1200,value))).putString("preset","Custom").apply();apply();}
    public synchronized void preset(int index){if(index<0||index>=CURVES.length)throw new IllegalArgumentException("Invalid preset");SharedPreferences.Editor edit=prefs.edit().putString("preset",PRESETS[index]);for(int i=0;i<5;i++)edit.putInt("band"+i,CURVES[index][i]);edit.apply();apply();}
    public String preset(){return prefs.getString("preset","Flat");}
    public int preamp(){return Math.max(-1200,Math.min(0,prefs.getInt("preamp",0)));}
    public int balance(){return Math.max(-100,Math.min(100,prefs.getInt("balance",0)));}
    public boolean headroom(){return prefs.getBoolean("headroom",true);}
    public synchronized void setPreamp(int value){prefs.edit().putInt("preamp",Math.max(-1200,Math.min(0,value))).apply();apply();}
    public synchronized void setBalance(int value){prefs.edit().putInt("balance",Math.max(-100,Math.min(100,value))).apply();}
    public synchronized void setHeadroom(boolean value){prefs.edit().putBoolean("headroom",value).apply();apply();}
    public synchronized void setGainListener(java.util.function.Consumer<Float> listener){gainListener=listener;applyGain();}
    public synchronized float outputGain(){if(!enabled())return 1f;int boost=0;if(headroom()&&effect!=null)for(int level:levels())boost=Math.max(boost,level);return (float)Math.pow(10,(preamp()-boost)/2000d);}
    private void applyGain(){if(gainListener!=null)gainListener.accept(outputGain());}
    public java.util.List<String> customPresets(){java.util.List<String> out=new java.util.ArrayList<>();for(String key:prefs.getAll().keySet())if(key.startsWith("custom."))out.add(key.substring(7));out.sort(String.CASE_INSENSITIVE_ORDER);return out;}
    public synchronized void saveCustom(String name){name=name.trim();if(name.isEmpty()||name.length()>30)throw new IllegalArgumentException("Use a name between 1 and 30 characters");if(customPresets().size()>=20&&!prefs.contains("custom."+name))throw new IllegalArgumentException("Keep up to 20 custom presets");StringBuilder value=new StringBuilder();for(int level:levels())value.append(level).append(',');value.append(preamp()).append(',').append(balance()).append(',').append(headroom());prefs.edit().putString("custom."+name,value.toString()).putString("preset",name).apply();}
    public synchronized void loadCustom(String name){String value=prefs.getString("custom."+name,"");String[] fields=value.split(",");if(fields.length!=8)throw new IllegalArgumentException("This preset is unavailable");try{int[] values=new int[7];for(int i=0;i<7;i++)values[i]=Integer.parseInt(fields[i]);android.content.SharedPreferences.Editor edit=prefs.edit();for(int i=0;i<5;i++)edit.putInt("band"+i,Math.max(-1200,Math.min(1200,values[i])));edit.putInt("preamp",Math.max(-1200,Math.min(0,values[5]))).putInt("balance",Math.max(-100,Math.min(100,values[6]))).putBoolean("headroom",Boolean.parseBoolean(fields[7])).putString("preset",name).apply();apply();}catch(NumberFormatException e){throw new IllegalArgumentException("This preset is unavailable");}}
    public synchronized void deleteCustom(String name){prefs.edit().remove("custom."+name).apply();}
    public synchronized void reset(){android.content.SharedPreferences.Editor edit=prefs.edit().putInt("preamp",0).putInt("balance",0).putBoolean("headroom",true).putString("preset","Flat");for(int i=0;i<5;i++)edit.putInt("band"+i,0);edit.apply();apply();}
    /** Interpolate on logarithmic frequency so devices with different band counts share the curve. */
    static int gain(int hz,int[] values){
        if(hz<=FREQUENCIES[0])return values[0];
        for(int i=1;i<5;i++)if(hz<=FREQUENCIES[i]){double fraction=Math.log((double)hz/FREQUENCIES[i-1])/Math.log((double)FREQUENCIES[i]/FREQUENCIES[i-1]);return (int)Math.round(values[i-1]+fraction*(values[i]-values[i-1]));}
        return values[4];
    }
    private void apply(){
        if(effect==null){applyGain();return;}
        try { if(enabled())applyGain();int[] values=levels();for(int b=0;b<effect.bands();b++)effect.level(b,Math.max(effect.minimum(),Math.min(effect.maximum(),gain(effect.frequency(b),values))));effect.enabled(enabled());status=enabled()?"Active on this player's audio output":"Off · Original sound"; }
        catch(RuntimeException error){unavailable();}
        applyGain();
    }
}
