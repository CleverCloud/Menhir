package util.tags;

import util.MalformedTemplateException;
import util.Template;

import java.io.IOException;

/**
 * Created by IntelliJ IDEA.
 * User: keruspe
 * Date: 18/05/11
 * Time: 14:33
 * To change this template use File | Settings | File Templates.
 */
public class ListBody extends Template {
   public ListBody(String body) throws IOException, MalformedTemplateException {
      super(body);
   }
}
