package util.tags;

import util.Template;

/**
 * Created by IntelliJ IDEA.
 * User: keruspe
 * Date: 12/05/11
 * Time: 15:39
 * To change this template use File | Settings | File Templates.
 */
public class ListTag extends Template {
   private StringBuilder tpl;

   public ListTag(String as) {
      super();
      tpl = new StringBuilder("%{ for (Object ").append(as).append(" : _items) { }%");
   }

   public void addBody(String body) {
      template = tpl.append(body).append(" %{ } }%").toString();
   }
}
