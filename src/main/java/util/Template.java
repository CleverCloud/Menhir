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
   private Boolean computed;

   public Template(String fileName) {
      FileReader fr = null;
      StringBuilder sb = new StringBuilder();
      try {
         fr = new FileReader(fileName);
         BufferedReader br = new BufferedReader(fr);
         String line;
         while ((line = br.readLine()) != null) {
            sb.append(line).append('\n');
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
      computed = Boolean.FALSE;
   }

   public void compute() throws MalformedTemplateException {
      if (computed)
         return;
      computed = Boolean.TRUE;
      StringBuilder sb = new StringBuilder();
      char c;
      for (int i = 0; i < template.length(); ++i) {
         switch ((c = template.charAt(i))) {
            case '#':
               if (++i == template.length())
                  sb.append('#');
               else if ((c = template.charAt(i)) == '{') {
                  StringBuilder tag = new StringBuilder();
                  for (++i; i < template.length() && (c = template.charAt(i)) != ' ' && c != '}'; ++i)
                     tag.append(c);
                  if (i == template.length())
                     throw new MalformedTemplateException();
                  if ("if".equals(tag.toString())) {
                     // TODO: handle #if tags
                     sb.append("__IF__");
                  } else if ("/if".equals(tag.toString())) {
                     // TODO: handle #/if tags
                     sb.append("__/IF__");
                  } else if ("else".equals(tag.toString())) {
                     // TODO: handle #else tags
                     sb.append("__ELSE__");
                  } else if ("/else".equals(tag.toString())) {
                     // TODO: handle #/else tags
                     sb.append("__/ELSE__");
                  } else {
                     // TODO: handle other # tags
                     sb.append("__OTHER(").append(tag).append(")__");
                  }
                  /* TMP */
                  if (c != '}') {
                     for (++i; i < template.length() && (c = template.charAt(i)) != '}'; ++i) ;
                     if (i == template.length())
                        throw new MalformedTemplateException();
                  }
                  /* /TMP */
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
                  sb.append('*');
               else if ((c = template.charAt(i)) == '{') {
                  for (++i; i < template.length() && (c = template.charAt(i)) != '*'; ++i) {
                     for (++i; i < template.length() && (c = template.charAt(i)) != '}'; ++i) ;
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
               for (++i; i < template.length() && (c = template.charAt(i)) != delimit; ++i)
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
