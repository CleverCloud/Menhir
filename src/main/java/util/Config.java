package util;

import base.Controller;

/**
 * @author Marc-Antoine Perennou<Marc-Antoine@Perennou.com>
 */
public class Config {
   public final static String PATH;

   static {
      PATH = Controller.class.getClassLoader().getResource("app/views/").getPath();
   }
}
