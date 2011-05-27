package util.extensions;

import java.util.List;

/**
 * @author Marc-Antoine Perennou<Marc-Antoine@Perennou.com>
 */
public class FooBarJavaExtensions extends JavaExtensions {
   public static String foobar(List<?> items) {
        return "LISTFOOBAR";
    }
}
