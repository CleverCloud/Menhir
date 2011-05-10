package util;

import groovy.text.SimpleTemplateEngine;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author keruspe
 */
public class Template {

   protected String template;
   protected Boolean computed;

   public Template(String fileName) throws IOException {
      StringBuilder sb = new StringBuilder();
      FileReader fr = new FileReader(fileName);
      BufferedReader br = new BufferedReader(fr);
      String line;
      while ((line = br.readLine()) != null) {
         sb.append(line).append('\n');
      }
      fr.close();
      template = sb.toString();
      computed = Boolean.FALSE;
   }

   public void compute(Map<String, Object> args) throws MalformedTemplateException {
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
                  String ts = tag.toString();
                  if ("if".equals(ts)) {
                     sb.append("<% if (");
                     for (++i; i < template.length() && (c = template.charAt(i)) != '}'; ++i)
                        sb.append(c);
                     sb.append(") { %>");
                  } else if ("ifnot".equals(ts)) {
                     sb.append("<% } if (!(");
                     for (++i; i < template.length() && (c = template.charAt(i)) != '}'; ++i)
                        sb.append(c);
                     sb.append(")) { %>");
                  } else if ("elseif".equals(ts)) {
                     sb.append("<% } else if (");
                     for (++i; i < template.length() && (c = template.charAt(i)) != '}'; ++i)
                        sb.append(c);
                     sb.append(") { %>");
                  } else if ("else".equals(ts)) {
                     sb.append("<% } else { %>");
                  } else if ("/if".equals(ts)) {
                     sb.append("<% } %>");
                  } else {
                     // TODO: handle other # tags, and complex ones (eg #{foo}#{/foo})
                     // set, get, doLayout, extends, script, list, verbatim, form
                     Map<String, Object> tagArgs = new HashMap<String, Object>();
                     if (c != '}') {
                        while (c != '}') {
                           for (; i < template.length() && template.charAt(i) == ' '; ++i) ;
                           StringBuilder argName = new StringBuilder();
                           StringBuilder argValue = new StringBuilder();
                           for (; i < template.length() && (c = template.charAt(i)) != ':' && c != '}' && c != ' '; ++i)
                              argName.append(c);
                           if (c != ':') {
                              if (argName.length() > 2) {
                                 if (argName.charAt(0) == argName.charAt(argName.length() - 1))
                                    tagArgs.put("_arg", argName.toString().substring(1, argName.length() - 1));
                                 else
                                    throw new MalformedTemplateException();
                              }
                              for (; i < template.length() && (c = template.charAt(i)) != '}'; ++i) {
                                 if (c != ' ' && c != '/') // TODO: be stricter about simple tags terminaison
                                    throw new MalformedTemplateException();
                              }
                              break;
                           }
                           if (++i == template.length())
                              throw new MalformedTemplateException();
                           for (; i < template.length() && template.charAt(i) == ' '; ++i) ;
                           if (c == '}') {
                              tagArgs.put(argName.toString(), null);
                              break;
                           }
                           char delim = template.charAt(i);
                           if (++i == template.length())
                              throw new MalformedTemplateException(); // TODO: "Anything special for non-strings ? Control delimiter ?"
                           for (; i < template.length() && (c = template.charAt(i)) != delim && c != '}'; ++i)
                              argValue.append(c);
                           if (c == '}')
                              throw new MalformedTemplateException();
                           if (++i == template.length())
                              throw new MalformedTemplateException();
                           for (; i < template.length() && (c = template.charAt(i)) == ' ' && c != ',' && c != '}'; ++i)
                              ;
                           tagArgs.put(argName.toString(), argValue.toString());
                           if (c == ',') {
                              if (++i == template.length())
                                 throw new MalformedTemplateException();
                           }
                        }
                     }
                     try {
                        Template tagImpl = new Template("/home/keruspe/Clever Cloud/Loose/src/main/java/app/views/tags/" + ts + ".tag");
                        SimpleTemplateEngine engine = new SimpleTemplateEngine();
                        tagImpl.compute(tagArgs);
                        sb.append(engine.createTemplate(tagImpl.toString()).make(tagArgs));
                     } catch (ClassNotFoundException ex) {
                        Logger.getLogger(Template.class.getName()).log(Level.SEVERE, null, ex);
                        sb.append("__OTHER(").append(tag.toString()).append(")__");
                     } catch (IOException ex) {
                        Logger.getLogger(Template.class.getName()).log(Level.SEVERE, null, ex);
                        sb.append("__OTHER(").append(tag.toString()).append(")__");//.append(System.getProperty("user.dir")).append("__");
                     }
                  }
                  if (c != '}') {
                     for (++i; i < template.length() && (c = template.charAt(i)) != '}'; ++i) ;
                     if (i == template.length())
                        throw new MalformedTemplateException();
                  }
               } else {
                  sb.append('#').append(c);
               }
               break;
            case '$':
               if (++i == template.length())
                  sb.append('$');
               else {
                  StringBuilder varName = new StringBuilder();
                  char delimit;
                  if ((c = template.charAt(i)) == '{')
                     delimit = '}';
                  else {
                     delimit = ' ';
                     varName.append(c);
                  }
                  for (++i; i < template.length() && ((c = template.charAt(i)) != delimit); ++i)
                     varName.append(c);
                  String objName = varName.toString().split("\\?")[0].split("\\.")[0];
                  if (!args.containsKey(objName))
                     args.put(objName, null);
                  sb.append("${").append(varName.toString()).append('}');
               }
               break;
            case '&':
               // TODO: handle i18n
               sb.append('&');
               break;
            case '%':
               if (++i == template.length())
                  sb.append('%');
               else if ((c = template.charAt(i)) == '{') {
                  sb.append("<% ");
                  Character toAppend = null;
                  do {
                     if (toAppend != null)
                        sb.append(toAppend);
                     for (++i; i < template.length() && (c = template.charAt(i)) != '}'; ++i)
                        sb.append(c);
                     if (i == template.length())
                        throw new MalformedTemplateException();
                     toAppend = '}';
                  } while (++i < template.length() && (c = template.charAt(i)) != '%');
                  if (i == template.length())
                     throw new MalformedTemplateException();
                  sb.append(" %>");
               } else {
                  sb.append('%').append(c);
               }
               break;
            case '*':
               if (++i == template.length())
                  sb.append('*');
               else if ((c = template.charAt(i)) == '{') {
                  Character toAppend = null;
                  do {
                     if (toAppend != null)
                        sb.append(toAppend);
                     for (++i; i < template.length() && (c = template.charAt(i)) != '}'; ++i) ;
                     if (i == template.length())
                        throw new MalformedTemplateException();
                     toAppend = '}';
                  } while (++i < template.length() && (c = template.charAt(i)) != '*');
                  if (i == template.length())
                     throw new MalformedTemplateException();
               } else {
                  sb.append('*').append(c);
               }
               break;
            case '@':
               // TODO: handle links
               sb.append('@');
               break;
            case '\\':
               if (++i == template.length())
                  throw new MalformedTemplateException();
               sb.append(template.charAt(i));
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
