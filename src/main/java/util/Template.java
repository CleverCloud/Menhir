package util;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
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

   public void compute() throws MalformedTemplateException {
      StringBuilder sb = new StringBuilder();
      char c;
      for (int i = 0; i < template.length(); ++i) {
         switch ((c = template.charAt(i))) {
            case '#':
               if (++i == template.length())
                  throw new MalformedTemplateException();
               c = template.charAt(i);
               if (c == '{') {
                  StringBuilder tag = new StringBuilder();

                  // TODO: handle # tags
               } else {
                  sb.append('#').append(c);
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
               c = template.charAt(++i);
               if (c == '{') {
                  do {
                     do {
                        if (++i == template.length())
                           throw new MalformedTemplateException();
                     } while ((c = template.charAt(i)) != '}');
                     if (++i == template.length())
                        throw new MalformedTemplateException();
                  } while ((c = template.charAt(i)) != '*');
               } else {
                  sb.append('*').append(c);
               }
               break;
            case '"':
            case '\'':
               sb.append(c);
               char delimit = c;
               while (++i != template.length() && (c = template.charAt(i)) != delimit) {
                  sb.append(c);
               }
               if (i == template.length())
                  throw new MalformedTemplateException();
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
