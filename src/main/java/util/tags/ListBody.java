package util.tags;

import util.MalformedTemplateException;
import util.Template;

import java.io.IOException;

/**
 * @author Marc-Antoine Perennou<Marc-Antoine@Perennou.com>
 */
public class ListBody extends Template {
   public ListBody(String body) throws IOException, MalformedTemplateException {
      super(body);
   }
}
