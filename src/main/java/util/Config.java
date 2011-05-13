package util;

import base.Controller;

import java.awt.geom.Path2D;

/**
 * Created by IntelliJ IDEA.
 * User: keruspe
 * Date: 13/05/11
 * Time: 14:11
 * To change this template use File | Settings | File Templates.
 */
public class Config {
   public final static String PATH;

   static {
      PATH = Controller.class.getClassLoader().getResource("app/views/").getPath();
   }
}
