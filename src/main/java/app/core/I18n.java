package app.core;

import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

public final class I18n {

    private static final String BASE = "i18n.messages";

    private static Locale locale = Locale.forLanguageTag("ru");
    private static ResourceBundle bundle = loadBundle(locale);

    private I18n() {}

    public static void initFromSettings() {
        String lang = AppSettings.lang();
        setLang(lang);
    }

    public static void setLang(String langCode) {
        if (langCode == null || langCode.isBlank()) langCode = "ru";

        Locale next = Locale.forLanguageTag(langCode);
        locale = next;
        bundle = loadBundle(next);
    }

    public static String lang() {
        return locale.toLanguageTag();
    }

    public static ResourceBundle bundle() {
        return bundle;
    }

    public static String t(String key) {
        if (key == null || key.isBlank()) return "";
        try {
            return bundle.getString(key);
        } catch (MissingResourceException e) {
            return "!" + key + "!";
        }
    }

    private static ResourceBundle loadBundle(Locale loc) {
        try {
            return ResourceBundle.getBundle(BASE, loc);
        } catch (Exception e) {
            return ResourceBundle.getBundle(BASE, Locale.forLanguageTag("ru"));
        }
    }
}
