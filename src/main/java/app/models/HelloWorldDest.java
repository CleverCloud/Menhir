package app.models;

import base.Model;

/**
 * Created by IntelliJ IDEA.
 * User: keruspe
 * Date: 10/05/11
 * Time: 16:20
 * To change this template use File | Settings | File Templates.
 */
public class HelloWorldDest extends Model {
   private String dest;
   public HelloWorldDest(String dest) {
      this.dest = dest;
   }
   public String getDest() {
      return dest;
   }
}
