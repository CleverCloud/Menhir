package util.tags;

import util.MalformedTemplateException;
import util.Template;

import java.util.Iterator;
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

   public void compute(String body, Map<String, Object> extraArgs) throws MalformedTemplateException {
      Integer index = 0;
      boolean first = true;
      Iterator it = items.iterator();
      boolean notLast = it.hasNext();
      while (notLast) {
         Object o = it.next();
         notLast = it.hasNext();
         args.put(as, o);
         args.put(as + "_index", ++index);
         args.put(as + "_parity", (index % 2 == 0) ? "even" : "odd");
         args.put(as + "_isLast", !notLast);
         args.put(as + "_isFirst", first);
         if (first)
            first = false;
         try {
            Template b = new ListBody(body, extraArgs);
            b.compute(args);
            tpl.append(b.compile(args));
         } catch (Exception ex) {
            Logger.getLogger(Template.class.getName()).log(Level.SEVERE, null, ex);
            throw new MalformedTemplateException("Failed to execute #{list}");
         }
      }
   }

   @Override
   public String toString() {
      return tpl.toString();
   }
}
