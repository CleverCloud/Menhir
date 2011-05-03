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
                  for (++i ; i < template.length() && (c = template.charAt(i)) != ' '; ++i)
                     tag.append(c);
                  if (i == template.length())
                     throw new MalformedTemplateException();
                  if ("if".equals(tag)) {

                  }
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
               if (++i == template.length())
                  throw new MalformedTemplateException();
               c = template.charAt(i);
               if (c == '{') {
                  for (++i ; i < template.length() && (c = template.charAt(i)) != '*'; ++i) {
                     for (++i ; i < template.length() && (c = template.charAt(i)) != '}'; ++i) ;
                     if (i == template.length())
                        throw new MalformedTemplateException();
                  }
                  if (i == template.length())
                     throw new MalformedTemplateException();
               } else {
                  sb.append('*').append(c);
               }
               break;
            case '"':
            case '\'':
               char delimit = c;
               for (++i ; i < template.length() && (c = template.charAt(i)) != delimit; ++i)
                  sb.append(c);
               if (i == template.length())
                  throw new MalformedTemplateException();
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
