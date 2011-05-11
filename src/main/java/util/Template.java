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
         boolean needsSlash = false;
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
                     if (ts.endsWith("/")) {
                        --i;
                        c = ' ';
                        tag = new StringBuilder(tag.substring(0, tag.length() - 1));
                        ts = tag.toString();
                     } else
                        needsSlash = true;
                     Map<String, Object> tagArgs = new HashMap<String, Object>();
                     while (c != '/') {
                        for (; i < template.length() && template.charAt(i) == ' '; ++i) ;
                        StringBuilder argName = new StringBuilder();
                        StringBuilder argValue = new StringBuilder();
                        for (; i < template.length() && (c = template.charAt(i)) != ':' && c != '/' && c != ' '; ++i)
                           argName.append(c);
                        String argNs = argName.toString();
                        if (c != ':') {
                           int argNl = argName.length();
                           if (argNl > 2) {
                              char delim = argName.charAt(0);
                              if ((delim == '\'' || delim == '\"') && delim == argName.charAt(argNl - 1))
                                 tagArgs.put("_arg", argNs.substring(1, argNl - 1));
                              else {
                                 String obj = argNs.split("\\?")[0].split("\\.")[0];
                                 if (args.containsKey(obj)) {
                                    if (argNs.equals(obj))
                                       tagArgs.put("_arg", args.get(obj));
                                    else {
                                       try {
                                          tagArgs.put("_arg", new SimpleTemplateEngine().createTemplate("${" + argNs.substring(1, argNl - 1) + "}").make(args));
                                       } catch (Exception ex) {
                                          Logger.getLogger(Template.class.getName()).log(Level.SEVERE, null, ex);
                                          throw new MalformedTemplateException();
                                       }
                                    }
                                 } else
                                    throw new MalformedTemplateException();
                              }
                           }
                           for (; i < template.length() && (c = template.charAt(i)) != '/'; ++i) {
                              if (c != ' ')
                                 throw new MalformedTemplateException();
                           }
                           break;
                        }
                        if (++i == template.length())
                           throw new MalformedTemplateException();
                        for (; i < template.length() && template.charAt(i) == ' '; ++i) ;
                        if (c == '}') {
                           tagArgs.put(argNs, null);
                           break;
                        }
                        char delim = template.charAt(i);
                        boolean isString = (delim == '\'' || delim == '\"');
                        if (isString) {
                           if (++i == template.length())
                              throw new MalformedTemplateException();
                        } else
                           delim = ' ';
                        for (; i < template.length() && (c = template.charAt(i)) != delim && c != '/'; ++i)
                           argValue.append(c);
                        if (c == '}') {
                           if (delim != ' ')
                              throw new MalformedTemplateException();
                        } else if (++i == template.length())
                           throw new MalformedTemplateException();
                        for (; i < template.length() && template.charAt(i) == ' '; ++i) ;
                        String argVs = argValue.toString();
                        if (isString)
                           tagArgs.put(argNs, argVs);
                        else {
                           String obj = argVs.split("\\?")[0].split("\\.")[0];
                           if (args.containsKey(obj)) {
                              if (argVs.equals(obj))
                                 tagArgs.put(argNs, args.get(obj));
                              else {
                                 try {
                                    tagArgs.put(argNs, new SimpleTemplateEngine().createTemplate("${" + argVs + "}").make(args));
                                 } catch (Exception ex) {
                                    Logger.getLogger(Template.class.getName()).log(Level.SEVERE, null, ex);
                                    throw new MalformedTemplateException();
                                 }
                              }
                           } else
                              throw new MalformedTemplateException();
                        }
                        if (c == ',') {
                           if (++i == template.length())
                              throw new MalformedTemplateException();
                        }
                     }
                     try {
                        Template tagImpl = new Template("/home/keruspe/Clever Cloud/Loose/src/main/java/app/views/tags/" + ts + ".tag");
                        SimpleTemplateEngine engine = new SimpleTemplateEngine();
                        tagImpl.compute(tagArgs);
                        sb.append(engine.createTemplate(tagImpl.toString()).make(tagArgs));
                     } catch (ClassNotFoundException ex) {
                        Logger.getLogger(Template.class.getName()).log(Level.SEVERE, null, ex);
                        sb.append("__OTHER(").append(ts).append(")__");
                     } catch (IOException ex) {
                        Logger.getLogger(Template.class.getName()).log(Level.SEVERE, null, ex);
                        sb.append("__OTHER(").append(ts).append(")__");
                     }
                  }
                  //TODO: clean this part
                  if (needsSlash) {
                     if (c != '/') {
                        for (++i; i < template.length() && (c = template.charAt(i)) != '/'; ++i) ;
                        if (i == template.length())
                           throw new MalformedTemplateException();
                     }
                     if (++i == template.length() || template.charAt(i) != '}')
                        throw new MalformedTemplateException();
                  } else if (c != '}') {
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
