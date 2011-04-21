package util;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author keruspe
 */
public class Template {
   
   private String template;
   
   public Template(String fileName) {
      FileReader fr = null;
      StringBuilder sb = new StringBuilder();
      try {
         fr = new FileReader(fileName);
         BufferedReader br = new BufferedReader(fr);
         String line;
         while ((line = br.readLine()) != null) {
            sb.append(line);
         }
      } catch (IOException ex) {
         Logger.getLogger(Template.class.getName()).log(Level.SEVERE, null, ex);
      } finally {
         try {
            fr.close();
         } catch (IOException ex) {
            Logger.getLogger(Template.class.getName()).log(Level.SEVERE, null, ex);
         }
      }
      template = sb.toString();
   }
   
   @Override
   public String toString() {
      return template;
   }
   
}
