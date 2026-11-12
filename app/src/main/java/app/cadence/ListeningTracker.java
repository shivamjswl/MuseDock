package app.cadence;

/** Counts audible wall time only: buffering, pauses and seeking do not manufacture listening. */
final class ListeningTracker {
    private long accumulated,started=-1;private boolean rewarded;
    void playing(boolean playing,long now){if(started>=0){accumulated+=Math.max(0,now-started);started=-1;}if(playing)started=now;}
    long heard(long now){return accumulated+(started<0?0:Math.max(0,now-started));}
    boolean reward(long duration,long now){long threshold=duration>0?Math.min(30000,Math.max(5000,duration/2)):30000;if(!rewarded&&heard(now)>=threshold){rewarded=true;return true;}return false;}
    boolean earlySkip(long duration,long now){long heard=heard(now);return !rewarded&&heard>0&&heard<30000&&(duration<=0||heard<duration/4);}
    void reset(){accumulated=0;started=-1;rewarded=false;}
}
