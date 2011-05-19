package util.tags;

import groovy.text.SimpleTemplateEngine;
import util.MalformedTemplateException;
import util.Template;

import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Marc-Antoine Perennou<Marc-Antoine@Perennou.com>
 */
public class ListTag {
   private StringBuilder tpl;
   private String as;
   private Iterable items;
   private Map<String, Object> args;

   public ListTag(Map<String, Object> args) {
      as = args.get("_as").toString();
      items = (Iterable) args.get("_items");
      this.args = args;
      tpl = new StringBuilder();
   }

   public void compute(String body) throws MalformedTemplateException {
      for (Object o : items) {
         try {
            args.put(as, o);
            Template b = new ListBody(body);
            SimpleTemplateEngine engine = new SimpleTemplateEngine();
            b.compute(args);
            tpl.append(engine.createTemplate(b.toString()).make(args));
         } catch (Exception ex) { //TODO: throw
            Logger.getLogger(Template.class.getName()).log(Level.SEVERE, null, ex);
         }
      }
   }

   @Override
   public String toString() {
      return tpl.toString();
   }
}
