package util;

import groovy.text.SimpleTemplateEngine;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author keruspe
 */

//TODO: #{foo}#{foo}#{/foo}#{/foo}
//TODO: check #{list/} #{form/}
public class Template {

   protected String template;
   private Boolean computed;

   public Template(String fileName, String body) throws IOException {
      StringBuilder sb = new StringBuilder();
      FileReader fr = new FileReader(fileName);
      BufferedReader br = new BufferedReader(fr);
      String line;
      while ((line = br.readLine()) != null) {
         sb.append(line).append('\n');
      }
      fr.close();
      template = (body == null) ? sb.toString() : sb.toString().replaceAll("#\\{doBody */\\}", body.replaceAll("\\$", "\\\\\\$"));
      computed = Boolean.FALSE;
   }

   protected Template() {
      computed = Boolean.FALSE;
   }

   public void compute(Map<String, Object> args) throws MalformedTemplateException {
      if (computed)
         return;
      List<String> tags = new ArrayList<String>();
      computed = Boolean.TRUE;
      StringBuilder sb = new StringBuilder();
      Map<String, Object> tagArgs = new HashMap<String, Object>();
      ListTag listTag = null;
      StringBuilder body = null;
      char c;
      for (int i = 0; i < template.length(); ++i) {
         if ((c = template.charAt(i)) != '#' && body != null) {
            body.append(c);
            continue;
         }
         switch (c) {
            case '#':
               if (++i == template.length()) {
                  if (body != null)
                     throw new MalformedTemplateException("Unexpected EOF while reading " + tags.get(tags.size() - 1) + " body");
                  sb.append('#');
               } else if ((c = template.charAt(i)) == '{') {
                  StringBuilder tag = new StringBuilder();
                  for (++i; i < template.length() && (c = template.charAt(i)) != ' ' && c != '}'; ++i)
                     tag.append(c);
                  String ts = tag.toString();
                  if (i == template.length())
                     throw new MalformedTemplateException("Unexpected EOF while reading tag: " + ts);
                  boolean custom = false;
                  boolean builtinComplexTag = false;
                  if (body != null && !(ts.startsWith("/") && tags.get(tags.size() - 1).equals(ts.substring(1)))) {
                     body.append("#{").append(ts).append(c);
                     break;
                  }
                  if ("/if".equals(ts) || "/elseif".equals(ts) || "/else".equals(ts)) { //TODO: is it really necessary to support those?
                     int lastTagIndex = tags.size() - 1;
                     String lastTag = tags.isEmpty() ? "" : tags.get(lastTagIndex);
                     if (!lastTag.equals("if"))
                        throw new MalformedTemplateException("Unexpected " + ts + ", did you forget #{/" + lastTag + "}");
                     tags.remove(lastTagIndex);
                     sb.append("<% } %>");
                  } else if ("/list".equals(ts)) {
                     int lastTagIndex = tags.size() - 1;
                     String lastTag = tags.isEmpty() ? "" : tags.get(lastTagIndex);
                     if (!lastTag.equals("list"))
                        throw new MalformedTemplateException("Unexpected /list, did you forget #{/" + lastTag + "}");
                     tags.remove(lastTagIndex);
                     listTag.addBody(body.toString());
                     try {
                        SimpleTemplateEngine engine = new SimpleTemplateEngine();
                        listTag.compute(tagArgs);
                        sb.append(engine.createTemplate(listTag.toString()).make(tagArgs));
                     } catch (ClassNotFoundException ex) {
                        Logger.getLogger(Template.class.getName()).log(Level.SEVERE, null, ex);
                        sb.append("__OTHER(").append(ts).append(")__");
                     } catch (IOException ex) {
                        Logger.getLogger(Template.class.getName()).log(Level.SEVERE, null, ex);
                        sb.append("__OTHER(").append(ts).append(")__");
                     }
                     body = null;
                     listTag = null;
                  } else if ("/form".equals(ts)) {
                     int lastTagIndex = tags.size() - 1;
                     String lastTag = tags.isEmpty() ? "" : tags.get(lastTagIndex);
                     if (!lastTag.equals("form"))
                        throw new MalformedTemplateException("Unexpected /form, did you forget #{/" + lastTag + "}");
                     tags.remove(lastTagIndex);
                     sb.append("</form>");
                  } else if (ts.startsWith("/")) {
                     int lastTagIndex = tags.size() - 1;
                     String lastTag = tags.isEmpty() ? "" : tags.get(lastTagIndex);
                     if (!lastTag.equals(ts.substring(1)))
                        throw new MalformedTemplateException("Unexpected /" + ts + ", did you forget #{/" + lastTag + "}");
                     tags.remove(lastTagIndex);
                     try {
                        ts = ts.substring(1);
                        Template tagImpl = new Template("/home/keruspe/Clever Cloud/Loose/src/main/java/app/views/tags/" + ts + ".tag", body.toString());
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
                     tagArgs = new HashMap<String, Object>();
                     body = null;
                  } else if ("if".equals(ts)) {
                     tags.add("if");
                     sb.append("<% if (");
                     for (++i; i < template.length() && (c = template.charAt(i)) != '}'; ++i)
                        sb.append(c);
                     sb.append(") { %>");
                  } else if ("ifnot".equals(ts)) {
                     tags.add("if");
                     sb.append("<% } if (!(");
                     for (++i; i < template.length() && (c = template.charAt(i)) != '}'; ++i)
                        sb.append(c);
                     sb.append(")) { %>");
                  } else if ("elseif".equals(ts)) {
                     String lastTag = tags.isEmpty() ? "" : tags.get(tags.size() - 1);
                     if (!lastTag.equals("if"))
                        throw new MalformedTemplateException("Unexpected elseif, did you forgot #{/" + lastTag + "}");
                     sb.append("<% } else if (");
                     for (++i; i < template.length() && (c = template.charAt(i)) != '}'; ++i)
                        sb.append(c);
                     sb.append(") { %>");
                  } else if ("else".equals(ts)) {
                     String lastTag = tags.isEmpty() ? "" : tags.get(tags.size() - 1);
                     if (!lastTag.equals("if"))
                        throw new MalformedTemplateException("Unexpected else, did you forgot #{/" + lastTag + "}");
                     sb.append("<% } else { %>");
                  } else {
                     // TODO: handle other play # tags
                     // set, get, doLayout, extends, script, field, verbatim, form
                     if ("list".equals(ts)) {
                        tags.add("list");
                        builtinComplexTag = true;
                     } else if ("form".equals(ts)) {
                        tags.add("form");
                        builtinComplexTag = true;
                     } else
                        custom = true;
                     if (ts.endsWith("/")) {
                        --i;
                        c = ' ';
                        tag = new StringBuilder(tag.substring(0, tag.length() - 1));
                        ts = tag.toString();
                     }
                     while (c != '/' && c != '}') {
                        for (; i < template.length() && template.charAt(i) == ' '; ++i) ;
                        StringBuilder argName = new StringBuilder();
                        StringBuilder argValue = new StringBuilder();
                        for (; i < template.length() && (c = template.charAt(i)) != ':' && c != '/' && c != ' ' && c != '}'; ++i)
                           argName.append(c);
                        String argNs = argName.toString();
                        if (c != ':') {
                           int argNl = argName.length();
                           if (argNl > 0) {
                              char delim = argName.charAt(0);
                              if (argNl > 2 && (delim == '\'' || delim == '\"') && delim == argName.charAt(argNl - 1))
                                 tagArgs.put("_arg", argNs.substring(1, argNl - 1));
                              else {
                                 String obj = argNs.split("\\?")[0].split("\\.")[0];
                                 if (args.containsKey(obj)) {
                                    if (argNs.equals(obj))
                                       tagArgs.put("_arg", args.get(obj));
                                    else {
                                       try {
                                          tagArgs.put("_arg", new SimpleTemplateEngine().createTemplate("${" + argNs + "}").make(args));
                                       } catch (Exception ex) {
                                          Logger.getLogger(Template.class.getName()).log(Level.SEVERE, null, ex);
                                          throw new MalformedTemplateException("Failed to evaluate: " + argNs.substring(1, argNl - 1) + " in tag " + ts + " (maybe your forgot to quote it ?)");
                                       }
                                    }
                                 } else
                                    tagArgs.put("_arg", null);
                              }
                           }
                           for (; i < template.length() && (c = template.charAt(i)) != '/' && c != '}'; ++i) {
                              if (c != ' ')
                                 throw new MalformedTemplateException("Unexpected character (" + c + ") found while parsing tag " + ts + " with anonymous argument.");
                           }
                           break;
                        }
                        if (++i == template.length())
                           throw new MalformedTemplateException("Unexpected EOF while parsing argument " + argNs + " for tag" + ts);
                        for (; i < template.length() && template.charAt(i) == ' '; ++i) ;
                        if (c == '/' || c == '}') {
                           throw new MalformedTemplateException("Error while parsing argument " + argNs + " for tag " + ts);
                        }
                        char delim = template.charAt(i);
                        boolean isString = (delim == '\'' || delim == '\"');
                        if (isString) {
                           if (++i == template.length())
                              throw new MalformedTemplateException("Error while parsing argument " + argNs + " for tag " + ts + " (Missing data + delimiter " + delim + ")");
                        } else
                           delim = ' ';
                        for (; i < template.length() && (c = template.charAt(i)) != delim && c != '}' && c != ',' && !(delim == ' ' && c == '/'); ++i)
                           argValue.append(c);
                        if (c == '/') {
                           if (delim != ' ')
                              throw new MalformedTemplateException("Error while parsing argument " + argNs + " for tag " + ts + " (Missing delimiter " + delim + ")");
                        } else if (c != '}') {
                           if (++i == template.length())
                              throw new MalformedTemplateException("Unexpected EOF while parsing tag " + ts);
                        }
                        for (; i < template.length() && (c = template.charAt(i)) == ' '; ++i) ;
                        String argVs = argValue.toString();
                        if (isString)
                           tagArgs.put("_" + argNs, argVs);
                        else {
                           String obj = argVs.split("\\?")[0].split("\\.")[0];
                           if (args.containsKey(obj)) {
                              if (argVs.equals(obj))
                                 tagArgs.put("_" + argNs, args.get(obj));
                              else {
                                 try {
                                    tagArgs.put("_" + argNs, new SimpleTemplateEngine().createTemplate("${" + argVs + "}").make(args));
                                 } catch (Exception ex) {
                                    Logger.getLogger(Template.class.getName()).log(Level.SEVERE, null, ex);
                                    throw new MalformedTemplateException("Failed to evaluate " + argNs + " value: " + argVs + " in tag " + ts + " (maybe your forgot to quote it ?)");
                                 }
                              }
                           } else
                              tagArgs.put("_" + argNs, null);
                        }
                        if (c == ',') {
                           if (++i == template.length())
                              throw new MalformedTemplateException("Unexpected EOF after , in tag " + ts);
                        }
                     }
                  }
                  boolean simpleTag = false;
                  if (custom) {
                     if (c != '/') {
                        for (; i < template.length() && (c = template.charAt(i)) != '}' && c != '/'; ++i) ;
                        if (c != '/') {
                           tags.add(ts);
                           body = new StringBuilder();
                        }
                     }
                     simpleTag = (c == '/');
                  } else if (builtinComplexTag) {
                     if ("list".equals(ts)) {
                        if (tagArgs.size() != 2 || !tagArgs.containsKey("_as") || !tagArgs.containsKey("_items"))
                           throw new MalformedTemplateException("You forgot either the \"as\" argument or the \"items\" one in a #{list} tag");
                        listTag = new ListTag(tagArgs.get("_as").toString());
                        body = new StringBuilder();
                     } else if ("form".equals(ts)) {
                        if (!tagArgs.containsKey("_action")) //TODO: supposed to be _arg in play
                           throw new MalformedTemplateException("No action given in form tag");
                        sb.append("<form action=\"").append(tagArgs.get("_action")).append("\" method=\"").append(tagArgs.containsKey("_method") ? tagArgs.get("_method") : "POST").append("\" accept-charset=\"utf-8\"");
                        if (tagArgs.containsKey("_id"))
                           sb.append(" id=\"").append(tagArgs.get("_id")).append("\"");
                        if (tagArgs.containsKey("_enctype"))
                           sb.append(" enctype=\"").append(tagArgs.get("_enctype")).append("\"");
                        sb.append(">");
                     }
                  }
                  if (c != '}') {
                     for (++i; i < template.length() && (c = template.charAt(i)) != '}'; ++i) ;
                     if (i == template.length())
                        throw new MalformedTemplateException("You forgot to close your tag " + ts + " (missing })");
                  }
                  if (simpleTag) {
                     try {
                        Template tagImpl = new Template("/home/keruspe/Clever Cloud/Loose/src/main/java/app/views/tags/" + ts + ".tag", null);
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
                     tagArgs = new HashMap<String, Object>();
                  }
               } else if (body == null)
                  sb.append('#').append(c);
               else
                  body.append('#').append(c);
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
                        throw new MalformedTemplateException("Unexpected EOF in code section (missing } ?)");
                     toAppend = '}';
                  } while (++i < template.length() && (c = template.charAt(i)) != '%');
                  if (i == template.length())
                     throw new MalformedTemplateException("Unexpected EOF in code section (missing % ?)");
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
                        throw new MalformedTemplateException("Unexpected EOF in comment (missing } ?)");
                     toAppend = '}';
                  } while (++i < template.length() && (c = template.charAt(i)) != '*');
                  if (i == template.length())
                     throw new MalformedTemplateException("Unexpected EOF in comment (missing * ?)");
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
                  throw new MalformedTemplateException("Unexpected EOF escaping");
               sb.append(template.charAt(i));
               break;
            default:
               sb.append(c);
         }
      }
      if (!tags.isEmpty())
         throw new MalformedTemplateException("Unexpected EOF, maybe you forgot #{/" + tags.get(tags.size() - 1) + "} ?");
      template = sb.toString();
   }

   @Override
   public String toString() {
      return template;
   }
}
