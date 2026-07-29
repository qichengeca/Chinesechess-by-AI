package com.chinesechess.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import java.util.Locale;

public class LocaleHelper {
    private static final String PREFS = "locale_prefs";
    private static final String KEY = "lang";
    private static final String ZH = "zh";
    private static final String EN = "en";

    public static void setLocale(Context context, String lang) {
        Locale locale = new Locale(lang);
        Locale.setDefault(locale);
        Resources res = context.getResources();
        Configuration config = res.getConfiguration();
        config.setLocale(locale);
        res.updateConfiguration(config, res.getDisplayMetrics());
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY, lang).apply();
    }

    public static String getSavedLang(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY, ZH);
    }

    public static void toggle(Context context) {
        String current = getSavedLang(context);
        setLocale(context, current.equals(ZH) ? EN : ZH);
    }

    public static boolean isZh(Context context) {
        return getSavedLang(context).equals(ZH);
    }

    public static void applySavedLocale(Context context) {
        setLocale(context, getSavedLang(context));
    }
}