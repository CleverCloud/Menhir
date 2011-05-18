package util;

import groovy.text.SimpleTemplateEngine;
import util.tags.ListTag;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author keruspe
 */
//TODO: #include in #list
//TODO: forward parent context to complex tags ?
public class Template {

   protected String template;
   private Boolean computed;

   public Template(String fileName, String body) throws IOException {
      FileToString fts = new FileToString();
      template = fts.doJob(fileName);
      if (body != null)
         template = template.replaceAll("#\\{doBody */\\}", body.replaceAll("\\$", "\\\\\\$"));
      Pattern p1 = Pattern.compile("(.*)#\\{include *'(.+)' */\\}(.*)");
      Pattern p2 = Pattern.compile("(.*)#\\{include *\"(.+)\" */\\}(.*)");
      Matcher m;
      for (; ; ) {
         m = p1.matcher(template);
         if (!m.matches())
            m = p2.matcher(template);
         if (!m.matches())
            break;
         template = m.group(1) + fts.doJob(Config.PATH + m.group(2)) + m.group(3);
      }
      template = template.replace("__LOOSE__INTERNAL__NEWLINE__", "\n");
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
      int nestLevel = 0;
      char c;
      for (int i = 0; i < template.length(); ++i) {
         c = template.charAt(i);
         if (body != null) {
            if (c == '*') {
               if (++i == template.length())
                  throw new MalformedTemplateException("Unexpected EOF in " + tags.get(tags.size() - 1));
               if ((c = template.charAt(i)) == '{') {
                  do {
                     for (; i < template.length() && (c = template.charAt(i)) != '}'; ++i) ;
                     if (i == template.length())
                        throw new MalformedTemplateException("Unexpected EOF in comment (missing } ?)");
                  } while (++i < template.length() && (c = template.charAt(i)) != '*');
                  if (i == template.length())
                     throw new MalformedTemplateException("Unexpected EOF in comment (missing * ?)");
               } else {
                  body.append('*').append(c);
               }
               continue;
            } else if (c != '#') {
               body.append(c);
               continue;
            }
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
                  boolean builtin = false;
                  int lastTagIndex = tags.size() - 1;
                  String lastTag = tags.isEmpty() ? "" : tags.get(lastTagIndex);
                  if (body != null && (!(ts.startsWith("/") && lastTag.equals(ts.substring(1))) || nestLevel > 0)) {
                     if (ts.equals(lastTag))
                        ++nestLevel;
                     else if (lastTag.equals(ts.substring(1)))
                        --nestLevel;
                     body.append("#{").append(ts).append(c);
                     break;
                  }
                  if ("/if".equals(ts) || "/elseif".equals(ts) || "/else".equals(ts)) { //TODO: is it really necessary to support those?
                     if (!lastTag.equals("if"))
                        throw new MalformedTemplateException("Unexpected " + ts + ", did you forget #{/" + lastTag + "}");
                     tags.remove(lastTagIndex);
                     sb.append("<% } %>");
                  } else if ("/list".equals(ts)) {
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
                     tagArgs = new HashMap<String, Object>();
                  } else if ("/form".equals(ts) || "/script".equals(ts) || "/a".equals(ts)) {
                     if (!lastTag.equals(ts.substring(1)))
                        throw new MalformedTemplateException("Unexpected " + ts + ", did you forget #{/" + lastTag + "}");
                     tags.remove(lastTagIndex);
                     sb.append("<" + ts + ">");
                  } else if (ts.startsWith("/")) {
                     if (!lastTag.equals(ts.substring(1)))
                        throw new MalformedTemplateException("Unexpected /" + ts + ", did you forget #{/" + lastTag + "}");
                     tags.remove(lastTagIndex);
                     try {
                        ts = ts.substring(1);
                        Template tagImpl = new Template(Config.PATH + "tags/" + ts + ".tag", body.toString());
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
                     if (template.charAt(i - 1) == '/')
                        throw new MalformedTemplateException("#{if /} is not allowed");
                     sb.append(") { %>");
                  } else if ("ifnot".equals(ts)) {
                     tags.add("if");
                     sb.append("<% } if (!(");
                     for (++i; i < template.length() && (c = template.charAt(i)) != '}'; ++i)
                        sb.append(c);
                     if (template.charAt(i - 1) == '/')
                        throw new MalformedTemplateException("#{ifnot /} is not allowed");
                     sb.append(")) { %>");
                  } else if ("elseif".equals(ts)) {
                     if (!lastTag.equals("if"))
                        throw new MalformedTemplateException("Unexpected elseif, did you forgot #{/" + lastTag + "}");
                     sb.append("<% } else if (");
                     for (++i; i < template.length() && (c = template.charAt(i)) != '}'; ++i)
                        sb.append(c);
                     sb.append(") { %>");
                  } else if ("else".equals(ts)) {
                     if (!lastTag.equals("if"))
                        throw new MalformedTemplateException("Unexpected else, did you forgot #{/" + lastTag + "}");
                     sb.append("<% } else { %>");
                  } else {
                     // TODO: handle other play # tags
                     // set, get, doLayout, extends, field, verbatim, ...
                     if ("list".equals(ts) || "form".equals(ts) || "script".equals(ts) || "a".equals(ts) || "stylesheet".equals(ts)) {
                        tags.add(ts);
                        builtin = true;
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
                        char delim = template.charAt(i);
                        boolean isString = (delim == '\'' || delim == '\"');
                        if (isString) {
                           if (++i == template.length())
                              throw new MalformedTemplateException("Error while parsing tag " + ts + " (Missing data + delimiter " + delim + ")");
                           for (; i < template.length() && (c = template.charAt(i)) != delim; ++i)
                              argName.append(c);
                           if (++i == template.length())
                              throw new MalformedTemplateException("Error while parsing tag " + ts + " (Missing delimiter " + delim + ")");
                           c = template.charAt(i);
                        } else {
                           for (; i < template.length() && (c = template.charAt(i)) != ':' && c != '/' && c != ' ' && c != '}' && c != ','; ++i)
                              argName.append(c);
                        }
                        String argNs = argName.toString();
                        if (c != ':') {
                           int argNl = argName.length();
                           if (argNl > 0) {
                              if (isString)
                                 tagArgs.put("_arg", argNs);
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
                                          throw new MalformedTemplateException("Failed to evaluate: " + argNs + " in tag " + ts + " (maybe your forgot to quote it ?)");
                                       }
                                    }
                                 } else
                                    tagArgs.put("_arg", null);
                              }
                           }
                           for (; i < template.length() && (c = template.charAt(i)) != '/' && c != '}' && c != ','; ++i) {
                              if (c != ' ')
                                 throw new MalformedTemplateException("Unexpected character (" + c + ") found while parsing tag " + ts + " with anonymous argument.");
                           }
                           if (c == ',') {
                              if (++i == template.length())
                                 throw new MalformedTemplateException("Error while parsing tag " + ts + " nothing found after ','");
                              continue;
                           }
                           break;
                        }
                        if (++i == template.length())
                           throw new MalformedTemplateException("Unexpected EOF while parsing argument " + argNs + " for tag" + ts);
                        for (; i < template.length() && template.charAt(i) == ' '; ++i) ;
                        if (c == '/' || c == '}') {
                           throw new MalformedTemplateException("Error while parsing argument " + argNs + " for tag " + ts);
                        }
                        delim = template.charAt(i);
                        isString = (delim == '\'' || delim == '\"');
                        if (isString) {
                           if (++i == template.length())
                              throw new MalformedTemplateException("Error while parsing argument " + argNs + " for tag " + ts + " (Missing data + delimiter " + delim + ")");
                           for (; i < template.length() && (c = template.charAt(i)) != delim; ++i)
                              argValue.append(c);
                           if (++i == template.length())
                              throw new MalformedTemplateException("Error while parsing argument " + argNs + " for tag " + ts + " (Missing delimiter " + delim + ")");
                           c = template.charAt(i);
                        } else {
                           for (; i < template.length() && (c = template.charAt(i)) != '}' && c != ',' && c != '/'; ++i)
                              argValue.append(c);
                        }
                        if (c != '}' && c != '/') {
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
                  } else if (builtin) {
                     if ("list".equals(ts)) {
                        if (tagArgs.size() != 2 || !tagArgs.containsKey("_as") || !tagArgs.containsKey("_items"))
                           throw new MalformedTemplateException("You forgot either the \"as\" argument or the \"items\" one in a #{list} tag");
                        listTag = new ListTag(tagArgs.get("_as").toString());
                        body = new StringBuilder();
                     } else {
                        if ("form".equals(ts)) {
                           String action;
                           if (!tagArgs.containsKey("_action")) {
                              Object tmp = tagArgs.get("_arg");
                              if (tmp == null)
                                 throw new MalformedTemplateException("No action given in form tag");
                              action = tmp.toString();
                           } else
                              action = tagArgs.get("_action").toString();
                           sb.append("<form action=\"").append(action).append("\" accept-charset=\"utf-8\" enctype=\"").append(tagArgs.containsKey("_enctype") ? tagArgs.get("_enctype") : "application/x-www-form-urlencoded").append("\"");
                           if (tagArgs.containsKey("_id"))
                              sb.append(" id=\"").append(tagArgs.get("_id")).append("\"");
                           if (tagArgs.containsKey("_method"))
                              sb.append(" method=\"").append(tagArgs.get("_method")).append("\"");
                           sb.append(">");
                        } else if ("script".equals(ts)) {
                           if (!tagArgs.containsKey("_src"))
                              throw new MalformedTemplateException("Missing src in #{script}");
                           sb.append("<script type=\"text/javascript\" src=\"").append(tagArgs.get("_src")).append("\" charset=\"").append(tagArgs.containsKey("_charset") ? tagArgs.get("_charset") : "utf-8").append("\"");
                           if (tagArgs.containsKey("_id"))
                              sb.append(" id=\"").append(tagArgs.get("_id")).append("\"");
                           sb.append(">");
                        } else if ("a".equals(ts)) {
                           if (!tagArgs.containsKey("_arg"))
                              throw new MalformedTemplateException("Argument missing in #{a}");
                           sb.append("<a href=\"").append(tagArgs.get("_arg")).append("\">");
                        } else if ("stylesheet".equals(ts)) {
                           String src;
                           if (!tagArgs.containsKey("_src")) {
                              Object tmp = tagArgs.get("_arg");
                              if (tmp == null)
                                 throw new MalformedTemplateException("No src given in form stylesheet");
                              src = tmp.toString();
                           } else
                              src = tagArgs.get("_src").toString();
                           sb.append("<link rel=\"stylesheet\" type=\"text/css\" href=\"").append(src).append("\"");
                           if (tagArgs.containsKey("_id"))
                              sb.append(" id=\"").append(tagArgs.get("_id")).append("\"");
                           if (tagArgs.containsKey("_media"))
                              sb.append(" media=\"").append(tagArgs.get("_media")).append("\"");
                           if (tagArgs.containsKey("_title"))
                              sb.append(" title=\"").append(tagArgs.get("_title")).append("\"");
                           sb.append(" />");
                        }
                        tagArgs = new HashMap<String, Object>();
                     }
                     if (c == '/') {
                        if ("script".equals(ts) || "stylesheet".equals(ts)) {
                           if (!tags.remove(tags.size() - 1).equals(ts))
                              throw new RuntimeException("Anything went wrong, we should never get there");
                           if ("script".equals(ts))
                              sb.append("</").append(ts).append(">");
                        } else
                           throw new MalformedTemplateException("#{" + ts + " /} is not allowed");
                     } else if ("stylesheet".equals(ts)) {
                        throw new MalformedTemplateException("Missing / in " + ts + " tag");
                     }
                  }
                  if (c != '}') {
                     for (++i; i < template.length() && (c = template.charAt(i)) != '}'; ++i) {
                        if (c != ' ')
                           throw new MalformedTemplateException("Unexpected character (" + c + ") before " + ts + " ending");
                     }
                     if (i == template.length())
                        throw new MalformedTemplateException("You forgot to close your tag " + ts + " (missing })");
                  }
                  if (simpleTag) {
                     try {
                        Template tagImpl = new Template(Config.PATH + "tags/" + ts + ".tag", null);
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
                  do {
                     for (; i < template.length() && (c = template.charAt(i)) != '}'; ++i) ;
                     if (i == template.length())
                        throw new MalformedTemplateException("Unexpected EOF in comment (missing } ?)");
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
