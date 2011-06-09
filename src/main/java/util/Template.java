package util;

import groovy.text.SimpleTemplateEngine;
import util.tags.Field;
import util.tags.ListTag;

import java.io.IOException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Marc-Antoine Perennou<Marc-Antoine@Perennou.com>
 */
public class Template {

   //TODO: Check if isLastChild is really used as expected all the time
   private String template;
   private boolean computed;
   private boolean compiled;
   private String parent;
   private boolean isLastChild;
   private Map<String, Object> extraArgs;
   private List<String> builtinTags;

   private void registerBuiltinTags() {
      builtinTags = new ArrayList<String>();
      builtinTags.add("a");
      builtinTags.add("extends");
      builtinTags.add("field");
      builtinTags.add("form");
      builtinTags.add("get");
      builtinTags.add("list");
      builtinTags.add("script");
      builtinTags.add("set");
      builtinTags.add("stylesheet");
   }

   /**
    * The complete Template constructor
    *
    * @param fileName     The name of the file containing the template
    * @param body         The body to substitute (for template inclusions)
    * @param tagToReplace The tag to replace by the body (template inclusion)
    * @param isLastChild  Define if we're the original template when manipulating multiple ones
    * @param extraArgs    Default args + args given in #{set}
    * @throws IOException                If there is an error when reading the file
    * @throws MalformedTemplateException If the template is malformed
    */
   public Template(String fileName, String body, String tagToReplace, boolean isLastChild, Map<String, Object> extraArgs) throws IOException, MalformedTemplateException {
      this(
         (body == null) ?
            new FileToString().doJob(fileName) :
            new FileToString().doJob(fileName).replaceAll("#\\{" + tagToReplace + " */\\}", body.replaceAll("\\$", "\\\\\\$"))
         , isLastChild
         , extraArgs
      );
   }

   /**
    * Bootstrap for Controller with default values
    *
    * @param fileName The name of the file containing the template
    * @throws IOException                If there is an error when reading the file
    * @throws MalformedTemplateException If the template is malformed
    */
   public Template(String fileName) throws IOException, MalformedTemplateException {
      this(new FileToString().doJob(fileName), true, new HashMap<String, Object>());
   }

   /**
    * The basic Template constructor. isLastChild defaults to true for complex tags body
    *
    * @param tpl       The template as a String
    * @param extraArgs Default args + args given in #{set}
    * @throws IOException                If there is an error when reading the file
    * @throws MalformedTemplateException If the template is malformed
    */
   public Template(String tpl, Map<String, Object> extraArgs) throws IOException, MalformedTemplateException {
      this(tpl, true, extraArgs);
   }

   /**
    * The basic Template constructor, called by every other one
    *
    * @param tpl         The template as a String
    * @param isLastChild Define if we're the original template when manipulating multiple ones
    * @param extraArgs   Default args + args given in #{set}
    * @throws IOException                If there is an error when reading the file
    * @throws MalformedTemplateException If the template is malformed
    */
   public Template(String tpl, boolean isLastChild, Map<String, Object> extraArgs) throws IOException, MalformedTemplateException {
      registerBuiltinTags();
      computed = false;
      compiled = false;
      parent = null;
      this.isLastChild = isLastChild;
      this.extraArgs = extraArgs;
      Pattern p1 = Pattern.compile("(.*)#\\{include *'(.+)' */\\}(.*)");
      Pattern p2 = Pattern.compile("(.*)#\\{include *\"(.+)\" */\\}(.*)");
      Matcher m;
      List<String> included = new ArrayList<String>();
      template = tpl.replace("\n", "__LOOSE__INTERNAL__NEWLINE__");
      for (; ; ) {
         m = p1.matcher(template);
         if (!m.matches())
            m = p2.matcher(template);
         if (!m.matches())
            break;
         String include = m.group(2);
         if (included.contains(include))
            throw new MalformedTemplateException("Recursive include detected: " + include);
         template = m.group(1) + new FileToString().doJob(Config.PATH + include) + m.group(3);
         included.add(include);
      }
      template = template.replace("__LOOSE__INTERNAL__NEWLINE__", "\n").replace("__LOOSE__INTERNAL__ESCAPE__", "\\");
   }

   /**
    * Compute the template, parse it and make it a Groovy one
    *
    * @param args The args you want to pass to the template
    * @throws MalformedTemplateException If the template is malformed
    */
   public void compute(Map<String, Object> args) throws MalformedTemplateException {
      if (computed)
         return;
      computed = true;
      List<String> tags = new ArrayList<String>();
      StringBuilder sb = new StringBuilder("<% use(util.extensions.JavaExtensions) { %>");
      Map<String, Object> tagArgs = new HashMap<String, Object>();
      ListTag listTag = null;
      StringBuilder body = null;
      Field field = null;
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
                  boolean special = true; // if, ifnot elseif, else, /*
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
                     listTag.compute(body.toString(), extraArgs);
                     sb.append(listTag.toString());
                     body = null;
                     listTag = null;
                     tagArgs = new HashMap<String, Object>();
                  } else if ("/field".equals(ts)) {
                     if (!lastTag.equals("field"))
                        throw new MalformedTemplateException("Unexpected /field, did you forget #{/" + lastTag + "}");
                     tags.remove(lastTagIndex);
                     tagArgs = new HashMap<String, Object>();
                     tagArgs.put("field", field);
                     try {
                        Template f = new Template(body.toString(), extraArgs);
                        sb.append(f.compile(tagArgs));
                     } catch (Exception ex) {
                        Logger.getLogger(Template.class.getName()).log(Level.SEVERE, null, ex);
                        throw new MalformedTemplateException("Failed to execute #{field} -> \n" + body.toString() + " -> \n" + ex.getMessage());
                     }
                     body = null;
                     field = null;
                     tagArgs = new HashMap<String, Object>();
                  } else if ("/set".equals(ts)) {
                     if (!lastTag.equals("set"))
                        throw new MalformedTemplateException("Unexpected /set, did you forget #{/" + lastTag + "}");
                     tags.remove(lastTagIndex);
                     Object tmp = tagArgs.get("_arg");
                     if (tmp == null)
                        throw new MalformedTemplateException("No name given for #{set}#{/set} value.");
                     try {
                        Template value = new Template(body.toString(), extraArgs);
                        extraArgs.put(tmp.toString(), value.compile(args));
                     } catch (Exception ex) {
                        Logger.getLogger(Template.class.getName()).log(Level.SEVERE, null, ex);
                        throw new MalformedTemplateException("Failed to evaluate #{set} " + tmp + " value");
                     }
                     body = null;
                  } else if ("/form".equals(ts) || "/script".equals(ts) || "/a".equals(ts)) {
                     if (!lastTag.equals(ts.substring(1)))
                        throw new MalformedTemplateException("Unexpected " + ts + ", did you forget #{/" + lastTag + "}");
                     tags.remove(lastTagIndex);
                     sb.append("<" + ts + ">");
                  } else if (ts.startsWith("/")) {
                     if (!lastTag.equals(ts.substring(1)))
                        throw new MalformedTemplateException("Unexpected /" + ts + ", did you forget #{/" + lastTag + "}");
                     tags.remove(lastTagIndex);
                     ts = ts.substring(1);
                     sb.append(runTemplate(Config.PATH + "tags/" + ts + ".tag", body.toString(), "doBody", tagArgs, extraArgs));
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
                     builtin = builtinTags.contains(ts);
                     special = false;
                     if (builtin)
                        tags.add(ts);
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
                                 Object value = null;
                                 if (argNs.equals(obj))
                                    value = args.get(obj);
                                 else {
                                    try {
                                       value = new SimpleTemplateEngine().createTemplate("${" + argNs + "}").make(args);
                                    } catch (Exception ex) {
                                       Logger.getLogger(Template.class.getName()).log(Level.SEVERE, null, ex);
                                    }
                                 }
                                 tagArgs.put("_arg", value);
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
                           Object value = null;
                           if (argVs.equals(obj))
                              value = args.get(obj);
                           else {
                              try {
                                 value = new SimpleTemplateEngine().createTemplate("${" + argVs + "}").make(args);
                              } catch (Exception ex) {
                                 Logger.getLogger(Template.class.getName()).log(Level.SEVERE, null, ex);
                              }
                           }
                           tagArgs.put("_" + argNs, value);
                        }
                        if (c == ',') {
                           if (++i == template.length())
                              throw new MalformedTemplateException("Unexpected EOF after , in tag " + ts);
                        }
                     }
                  }
                  boolean simpleTag = false;
                  if (!special) {
                     if (builtin) {
                        if ("list".equals(ts)) {
                           if (!tagArgs.containsKey("_as"))
                              tagArgs.put("_as", "_");
                           if (!tagArgs.containsKey("_items")) {
                              Object items = tagArgs.get("_arg");
                              if (items == null)
                                 throw new MalformedTemplateException("You forgot the \"items\" argument in a #{list} tag");
                              tagArgs.put("_items", items);
                           }
                           listTag = new ListTag(tagArgs);
                           body = new StringBuilder();
                        } else if ("field".equals(ts)) {
                           Object fieldName = tagArgs.get("_arg");
                           if (fieldName == null)
                              throw new MalformedTemplateException("You forgot the argument of #{field} tag");
                           field = new Field();
                           field.name = fieldName.toString();
                           field.id = field.name.replace(".", "_");
                           String obj = field.name.split("\\?")[0].split("\\.")[0];
                           //TODO: field.error stuff
                           if (field.name.equals(obj))
                              field.value = args.get(obj).toString();
                           else {
                              try {
                                 field.value = new SimpleTemplateEngine().createTemplate("${" + field.name + "}").make(args).toString();
                              } catch (Exception ex) {
                                 Logger.getLogger(Template.class.getName()).log(Level.SEVERE, null, ex);
                              }
                           }
                           body = new StringBuilder();
                        } else if ("set".equals(ts)) {
                           body = new StringBuilder();
                           for (String key : tagArgs.keySet()) {
                              if (!"_arg".equals(key))
                                 extraArgs.put(key.substring(1), tagArgs.get(key));
                           }
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
                           } else if ("extends".equals(ts)) {
                              Object tmp = tagArgs.get("_arg");
                              if (tmp == null)
                                 throw new MalformedTemplateException("No parent given to #{extends/}");
                              if (hasParent())
                                 throw new MalformedTemplateException("Only one #{extends/} allowed per template");
                              parent = tmp.toString();
                           } else if ("get".equals(ts)) {
                              Object tmp = tagArgs.get("_arg");
                              if (tmp == null)
                                 throw new MalformedTemplateException("You didn't specify a name in #{get /}");
                              String key = tmp.toString();
                              if (!extraArgs.containsKey(key))
                                 throw new MalformedTemplateException("Could not found " + key + " for #{get /}. Did you forget to #{set} it ?");
                              sb.append(extraArgs.get(key));
                           }
                           tagArgs = new HashMap<String, Object>();
                        }
                        if (c == '/') {
                           if ("script".equals(ts) || "stylesheet".equals(ts) || "extends".equals(ts) || "set".equals(ts) || "get".equals(ts)) {
                              if (!tags.remove(tags.size() - 1).equals(ts))
                                 throw new RuntimeException("Anything went wrong, we should never get there");
                              if ("script".equals(ts))
                                 sb.append("</").append(ts).append(">");
                              else if ("set".equals(ts))
                                 body = null;
                           } else
                              throw new MalformedTemplateException("#{" + ts + " /} is not allowed");
                        } else if ("stylesheet".equals(ts) || "extends".equals(ts) || "get".equals(ts)) {
                           throw new MalformedTemplateException("Missing / in " + ts + " tag");
                        }
                     } else {
                        if (c != '/') {
                           for (; i < template.length() && (c = template.charAt(i)) != '}' && c != '/'; ++i) ;
                           if (c != '/') {
                              tags.add(ts);
                              body = new StringBuilder();
                           }
                        }
                        simpleTag = (c == '/');
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
                     sb.append(runTemplate(Config.PATH + "tags/" + ts + ".tag", null, null, tagArgs, extraArgs));
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
               sb.append("__LOOSE__INTERNAL__ESCAPE__").append(template.charAt(i));
               break;
            default:
               sb.append(c);
         }
      }
      if (!tags.isEmpty())
         throw new MalformedTemplateException("Unexpected EOF, maybe you forgot #{/" + tags.get(tags.size() - 1) + "} ?");
      sb.append("<% } %>");
      if (hasParent()) {
         Map<String, Object> parentArgs = new HashMap<String, Object>();
         template = runTemplate(Config.PATH + parent, "__LOOSE__INTERNAL__DOLAYOUT__", "doLayout", parentArgs, false, extraArgs).replace("__LOOSE__INTERNAL__DOLAYOUT__", sb.toString());
         args.putAll(parentArgs); // TODO: how do we handle conflict here ?
      } else
         template = sb.toString();
      if (isLastChild)
         template = template.replace("__LOOSE__INTERNAL__ESCAPE__", "");
   }

   /**
    * Compile the Template
    *
    * @param args The args you want to pass to the template
    * @return The compiled template
    * @throws ClassNotFoundException     If we're using anything we shouldn't in the template
    * @throws IOException                If we fail to read a Tag or Template file
    * @throws MalformedTemplateException If the template is malformed
    */
   public String compile(Map<String, Object> args) throws ClassNotFoundException, IOException, MalformedTemplateException {
      if (!compiled) { // TODO: Check in cache
         compute(args);
         template = new SimpleTemplateEngine().createTemplate(template).make(args).toString(); // TODO: Cache instead of doing that
         compiled = true;
      }
      return template;
   }

   /**
    * Are we extending a parent template ?
    *
    * @return true if we are
    */
   public boolean hasParent() {
      return (parent != null);
   }

   public String runTemplate(String fileName, String body, String tagToReplace, Map<String, Object> args, boolean isLastChild, Map<String, Object> extraArgs) throws MalformedTemplateException {
      try {
         Template tpl = new Template(fileName, body, tagToReplace, isLastChild, extraArgs);
         return tpl.compile(args);
      } catch (MalformedTemplateException ex) {
         throw ex;
      } catch (IOException ex) {
         Logger.getLogger(Template.class.getName()).log(Level.SEVERE, null, ex);
         throw new MalformedTemplateException("Error: " + fileName + " not found.");
      } catch (Exception ex) {
         Logger.getLogger(Template.class.getName()).log(Level.SEVERE, null, ex);
         throw new MalformedTemplateException("Unknown error: " + ex.getMessage());
      }
   }

   public String runTemplate(String fileName, String body, String tagToReplace, Map<String, Object> args, Map<String, Object> extraArgs) throws MalformedTemplateException {
      return runTemplate(fileName, body, tagToReplace, args, true, extraArgs);
   }

   @Override
   public String toString() {
      return template;
   }
}
