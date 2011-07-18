package app.models;

import base.Model;

/**
 * @author Marc-Antoine Perennou<Marc-Antoine@Perennou.com>
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
