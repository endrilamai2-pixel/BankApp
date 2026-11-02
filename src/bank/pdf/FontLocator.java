package bank.pdf;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Εντοπίζει στον υπολογιστή μια γραμματοσειρά με ελληνικά (Arial, Segoe UI, Liberation, DejaVu κ.λπ.). */
public final class FontLocator {

    private FontLocator() { }

    public static TtfFont regular() {
        return TtfFont.loadFirstWithGreek(candidates(false));
    }

    public static TtfFont bold() {
        return TtfFont.loadFirstWithGreek(candidates(true));
    }

    private static List<Path> candidates(boolean bold) {
        List<Path> list = new ArrayList<>();
        // Δική σου γραμματοσειρά: -Dbank.pdf.font=C:\path\font.ttf (και -Dbank.pdf.fontBold=...)
        String custom = System.getProperty(bold ? "bank.pdf.fontBold" : "bank.pdf.font");
        if (custom != null && !custom.isBlank()) list.add(Path.of(custom));
        // Φάκελος fonts δίπλα στην εφαρμογή
        list.add(Path.of("fonts", bold ? "font-bold.ttf" : "font.ttf"));

        String win = System.getenv("WINDIR");
        String winFonts = (win != null ? win : "C:\\Windows") + "\\Fonts\\";
        String[] windows = bold
                ? new String[]{"arialbd.ttf", "segoeuib.ttf", "calibrib.ttf", "tahomabd.ttf", "verdanab.ttf"}
                : new String[]{"arial.ttf", "segoeui.ttf", "calibri.ttf", "tahoma.ttf", "verdana.ttf"};
        for (String f : windows) list.add(Path.of(winFonts + f));

        // macOS
        for (String f : bold ? new String[]{"Arial Bold.ttf"} : new String[]{"Arial.ttf"}) {
            list.add(Path.of("/Library/Fonts/" + f));
            list.add(Path.of("/System/Library/Fonts/Supplemental/" + f));
        }
        // Linux
        list.add(Path.of(bold ? "/usr/share/fonts/truetype/liberation/LiberationSans-Bold.ttf"
                              : "/usr/share/fonts/truetype/liberation/LiberationSans-Regular.ttf"));
        list.add(Path.of(bold ? "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
                              : "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"));
        return list;
    }
}
