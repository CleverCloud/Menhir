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

   public void compute() {
      StringBuilder sb = new StringBuilder();
      char c;
      for (int i = 0; i < template.length(); ++i) {
         switch ((c = template.charAt(i))) {
            case '#':
               char n1 = template.charAt(++i);
               if (n1 == '{') {
                  // TODO: handle # tags
               } else {
                  sb.append('#').append(n1);
               }
               break;
            case '$':
               // TODO: handle vars checking
               sb.append('$');
               break;
            case '&':
               // TODO: handle i18n
               sb.append('&');
               break;
            case '%':
               // TODO: handle java code
               sb.append('%');
               break;
            case '*':
               char n5 = template.charAt(++i);
               if (n5 == '{') {
                  do {
                     while ((c = template.charAt(++i)) != '}');
                  } while ((c = template.charAt(++i)) != '*');
               } else {
                  sb.append('*').append(n5);
               }
               break;
            case '"':
            case '\'':
               sb.append(c);
               char n6;
               while ((n6 = template.charAt(++i)) != c) {
                  sb.append(n6);
               }
               sb.append(c);
               break;
            default:
               sb.append(c);
         }
      }
      template = sb.toString();
   }

   @Override
   public String toString() {
      return template;
   }
}
