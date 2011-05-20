package util.tags;

import util.MalformedTemplateException;
import util.Template;

import java.io.IOException;
import java.util.Map;

/**
 * @author Marc-Antoine Perennou<Marc-Antoine@Perennou.com>
 */
public class ListBody extends Template {
   public ListBody(String body, Map<String, Object> extraArgs) throws IOException, MalformedTemplateException {
      super(body, extraArgs);
   }
}
