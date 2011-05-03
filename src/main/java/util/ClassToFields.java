package util;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author keruspe
 */
public class ClassToFields {

   public static Map<String, Object> getMap(Object o) {
      HashMap<String, Object> map = new HashMap<String, Object>();
      for (Field f : o.getClass().getDeclaredFields()) {
         try {
            Boolean access = f.isAccessible();
            f.setAccessible(true);
            map.put(f.getName(), f.get(o));
            f.setAccessible(access);
         } catch (IllegalArgumentException ex) {
            Logger.getLogger(ClassToFields.class.getName()).log(Level.SEVERE, null, ex);
         } catch (IllegalAccessException ex) {
            Logger.getLogger(ClassToFields.class.getName()).log(Level.SEVERE, null, ex);
         }
      }
      return map;
   }

}
