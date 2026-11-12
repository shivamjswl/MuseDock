package app.cadence;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.*;
import android.os.Build;

/** Shared colors for every app surface, including dialogs and the queue. */
public record ThemePalette(Mode mode, boolean light, int background, int surface, int raised,
        int foreground, int muted, int accent, int onAccent, int outline, int selected, int hero) {
    public enum Mode { MATERIAL("Material"), LIGHT("Light"), DARK("Dark"), LIQUID("Liquid");
        public final String label; Mode(String label){this.label=label;}
    }
    public static Mode selected(Context context){try{return Mode.valueOf(context.getSharedPreferences("musedock.preferences",0).getString("theme","DARK"));}catch(IllegalArgumentException e){return Mode.DARK;}}
    public static void save(Context context,Mode mode){context.getSharedPreferences("musedock.preferences",0).edit().putString("theme",mode.name()).apply();}
    public static ThemePalette current(Context context){return forMode(context,selected(context));}
    public static ThemePalette forMode(Context context,Mode mode){
        boolean light=mode==Mode.LIGHT||mode==Mode.MATERIAL&&(context.getResources().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK)!=Configuration.UI_MODE_NIGHT_YES;
        if(mode==Mode.LIQUID)return new ThemePalette(mode,false,0xff111a30,0x99202e49,0xb02e405c,0xfff4f8ff,0xffbac8e0,0xffa9caff,0xff102746,0x807d9dbe,0xa8344e6d,0xff243d65);
        int accent=light?0xff146c43:0xff43df86;
        if(mode==Mode.MATERIAL){accent=light?0xff6750a4:0xffd0bcff;if(Build.VERSION.SDK_INT>=31)accent=context.getColor(light?android.R.color.system_accent1_600:android.R.color.system_accent1_200);}
        return light?new ThemePalette(mode,true,0xfff5f8f6,0xffffffff,0xffe7eee9,0xff17251d,0xff52655a,accent,0xffffffff,0xffd3dfd7,mix(accent,0xffffffff,.87f),mix(accent,0xfff5f8f6,.88f)):
            new ThemePalette(mode,false,0xff0e1210,0xff191f1b,0xff252f28,0xfff2f7f3,0xffa5b4aa,accent,0xff092414,0xff334239,mix(accent,0xff0e1210,.82f),mix(accent,0xff0e1210,.89f));
    }
    public int style(){return switch(mode){case MATERIAL->light?R.style.AppThemeMaterialLight:R.style.AppThemeMaterialDark;case LIGHT->R.style.AppThemeLight;case LIQUID->R.style.AppThemeLiquid;default->R.style.AppTheme;};}
    public Drawable backdrop(){return mode==Mode.LIQUID?new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0xff352452,background,0xff10505a}):new ColorDrawable(background);}
    public GradientDrawable card(Context context,int color,int radius){
        GradientDrawable shape=mode==Mode.LIQUID&&(color==surface||color==raised)?new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{(mix(color,0xff8099bb,.12f)&0x00ffffff)|(Color.alpha(color)<<24),color}):new GradientDrawable();
        if(mode!=Mode.LIQUID||color!=surface&&color!=raised)shape.setColor(color);
        float density=context.getResources().getDisplayMetrics().density;shape.setCornerRadius(radius*density);
        if(mode==Mode.LIQUID&&(color==surface||color==raised))shape.setStroke(Math.max(1,Math.round(density)),outline);
        return shape;
    }
    public static int mix(int a,int b,float weight){return Color.rgb(Math.round(Color.red(a)*(1-weight)+Color.red(b)*weight),Math.round(Color.green(a)*(1-weight)+Color.green(b)*weight),Math.round(Color.blue(a)*(1-weight)+Color.blue(b)*weight));}
}
