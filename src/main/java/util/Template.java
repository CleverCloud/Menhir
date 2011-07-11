package util;

import groovy.text.SimpleTemplateEngine;
import util.tags.Field;
import util.tags.ListTag;
import util.tags.Tags;

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

   //TODO: Test escaping in weird deep cases
   private String template;
   private boolean computed;
   private boolean compiled;
   private String parent;
   private boolean isLastChild;
   private Map<String, Object> extraArgs;
   private EnumSet<Tags> builtinTags;

   /* Those fields are here because of java sillyness */
   private int i;
   private char c;
   private StringBuilder sb;
   private int nestLevel;
   private Map<String, Object> tagArgs;
   private ListTag listTag;
   private Field field;
   private StringBuilder body;
   private List<String> tags;
   private String lastTag;
   private int lastTagIndex;
   private boolean builtin;
   private boolean special; // if, ifnot elseif, else, /*

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
    * The basic Template constructor. isLastChild defaults to false for complex tags body
    *
    * @param tpl       The template as a String
    * @param extraArgs Default args + args given in #{set}
    * @throws IOException                If there is an error when reading the file
    * @throws MalformedTemplateException If the template is malformed
    */
   public Template(String tpl, Map<String, Object> extraArgs) throws IOException, MalformedTemplateException {
      this(tpl, false, extraArgs);
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
      builtinTags = EnumSet.allOf(Tags.class);
      builtinTags.remove(Tags.OTHER);
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
         if (!m.matches()) {
            m = p2.matcher(template);
            if (!m.matches())
               break;
         }
         String include = m.group(2);
         if (included.contains(include))
            throw new MalformedTemplateException("Recursive include detected: " + include);
         template = m.group(1) + new FileToString().doJob(Config.PATH + include) + m.group(3);
         included.add(include);
      }
      template = template.replace("__LOOSE__INTERNAL__NEWLINE__", "\n").replace("__LOOSE__INTERNAL__ESCAPE__", "\\");
   }

   private void popTag(String tag) throws MalformedTemplateException {
      if (!lastTag.equals(tag))
         throw new MalformedTemplateException("Unexpected #{/" + tag + "}, did you forget #{/" + lastTag + "}");
      tags.remove(lastTagIndex--);
      lastTag = tags.isEmpty() ? "" : tags.get(lastTagIndex);
   }

   private void pushTag(String tag) {
      tags.add(tag);
      lastTag = tags.get(++lastTagIndex);
   }

   private void handleBody() throws MalformedTemplateException {
      for (; i < template.length(); ++i) {
         c = template.charAt(i);
         if (c == '*') {
            if (++i == template.length())
               throw new MalformedTemplateException("Unexpected EOF in " + lastTag);
            if ((c = template.charAt(i)) == '{')
               handleComment();
            else
               body.append('*').append(c);
         } else if (c != '#')
            body.append(c);
         else
            break;
      }
   }

   private void handleComment() throws MalformedTemplateException {
      do {
         for (; i < template.length() && template.charAt(i) != '}'; ++i) ;
         if (i == template.length())
            throw new MalformedTemplateException("Unexpected EOF in comment (missing } ?)");
      } while (++i < template.length() && template.charAt(i) != '*');
      if (i == template.length())
         throw new MalformedTemplateException("Unexpected EOF in comment (missing * ?)");
   }

   private void handleVar(Map<String, Object> args) {
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

   private void handleJavaCode() throws MalformedTemplateException {
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
   }

   private void slashIf() throws MalformedTemplateException {
      popTag("if");
      sb.append("<% } %>");
   }

   private void slashList() throws MalformedTemplateException {
      popTag("list");
      listTag.compute(body.toString(), extraArgs);
      sb.append(listTag.toString());
      body = null;
      listTag = null;
      tagArgs = new HashMap<String, Object>();
   }

   private void slashField() throws MalformedTemplateException {
      popTag("field");
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
   }

   private void slashSet(Map<String, Object> args) throws MalformedTemplateException {
      popTag("set");
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
   }

   private void slashHtmlTag(String tag) throws MalformedTemplateException {
      popTag(tag);
      sb.append("</" + tag + ">");
   }

   private void IF() throws MalformedTemplateException {
      pushTag("if");
      sb.append("<% if (");
      for (++i; i < template.length() && (c = template.charAt(i)) != '}'; ++i)
         sb.append(c);
      if (template.charAt(i - 1) == '/')
         throw new MalformedTemplateException("#{if /} is not allowed");
      sb.append(") { %>");
   }

   private void ifNot() throws MalformedTemplateException {
      pushTag("if");
      sb.append("<% } if (!(");
      for (++i; i < template.length() && (c = template.charAt(i)) != '}'; ++i)
         sb.append(c);
      if (template.charAt(i - 1) == '/')
         throw new MalformedTemplateException("#{ifnot /} is not allowed");
      sb.append(")) { %>");
   }

   private void elseIf() throws MalformedTemplateException {
      if (!lastTag.equals("if"))
         throw new MalformedTemplateException("Unexpected elseif, did you forgot #{/" + lastTag + "}");
      sb.append("<% } else if (");
      for (++i; i < template.length() && (c = template.charAt(i)) != '}'; ++i)
         sb.append(c);
      sb.append(") { %>");
   }

   private void ELSE() throws MalformedTemplateException {
      if (!lastTag.equals("if"))
         throw new MalformedTemplateException("Unexpected else, did you forgot #{/" + lastTag + "}");
      sb.append("<% } else { %>");
   }

   private void beginHandleTag(Tags tv, Map<String, Object> args, String ts) throws MalformedTemplateException {
      switch (tv) {
         case SLASHIF:
            slashIf();
            break;
         case SLASHLIST:
            slashList();
            break;
         case SLASHFIELD:
            slashField();
            break;
         case SLASHSET:
            slashSet(args);
            break;
         case SLASHFORM:
         case SLASHSCRIPT:
         case SLASHA:
            slashHtmlTag(ts.substring(1));
            break;
         case IF:
            IF();
            break;
         case IFNOT:
            ifNot();
            break;
         case ELSEIF:
            elseIf();
            break;
         case ELSE:
            ELSE();
            break;
         default:
            if (ts.startsWith("/")) {
               ts = ts.substring(1);
               popTag(ts);
               sb.append(runTemplate(Config.PATH + "tags/" + ts + ".tag", body.toString(), "doBody", tagArgs, extraArgs));
               tagArgs = new HashMap<String, Object>();
               body = null;
            } else {
               builtin = builtinTags.contains(Tags.fromString(ts));
               special = false;
               if (builtin)
                  pushTag(ts);
               if (ts.endsWith("/")) {
                  --i;
                  c = ' ';
                  ts = ts.substring(0, ts.length() - 1); /* TODO: check if that needs to be dispatched */
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
      }
   }

   private void handleTag(Map<String, Object> args) throws MalformedTemplateException {
      StringBuilder tag = new StringBuilder();
      for (++i; i < template.length() && (c = template.charAt(i)) != ' ' && c != '}'; ++i)
         tag.append(c);
      String ts = tag.toString();
      if (i == template.length())
         throw new MalformedTemplateException("Unexpected EOF while reading tag: " + ts);
      special = true;
      builtin = false;
      if (body != null && (!(ts.startsWith("/") && lastTag.equals(ts.substring(1))) || nestLevel > 0)) {
         if (ts.equals(lastTag))
            ++nestLevel;
         else if (lastTag.equals(ts.substring(1)))
            --nestLevel;
         body.append("#{").append(ts).append(c);
         return;
      }
      Tags tv = Tags.fromString(ts);
      beginHandleTag(tv, args, ts);
      boolean simpleTag = false;
      if (!special) {
         if (builtin) {
            switch (tv) {
               case LIST:
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
                  break;
               case FIELD:
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
                  break;
               case SET:
                  body = new StringBuilder();
                  for (String key : tagArgs.keySet()) {
                     if (!"_arg".equals(key))
                        extraArgs.put(key.substring(1), tagArgs.get(key));
                  }
                  break;
               case FORM:
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
                  tagArgs = new HashMap<String, Object>();
                  break;
               case SCRIPT:
                  if (!tagArgs.containsKey("_src"))
                     throw new MalformedTemplateException("Missing src in #{script}");
                  sb.append("<script type=\"text/javascript\" src=\"").append(tagArgs.get("_src")).append("\" charset=\"").append(tagArgs.containsKey("_charset") ? tagArgs.get("_charset") : "utf-8").append("\"");
                  if (tagArgs.containsKey("_id"))
                     sb.append(" id=\"").append(tagArgs.get("_id")).append("\"");
                  sb.append(">");
                  tagArgs = new HashMap<String, Object>();
                  break;
               case A:
                  if (!tagArgs.containsKey("_arg"))
                     throw new MalformedTemplateException("Argument missing in #{a}");
                  sb.append("<a href=\"").append(tagArgs.get("_arg")).append("\">");
                  tagArgs = new HashMap<String, Object>();
                  break;
               case STYLESHEET:
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
                  tagArgs = new HashMap<String, Object>();
                  break;
               case EXTENDS:
                  Object tmp = tagArgs.get("_arg");
                  if (tmp == null)
                     throw new MalformedTemplateException("No parent given to #{extends/}");
                  if (hasParent())
                     throw new MalformedTemplateException("Only one #{extends/} allowed per template");
                  parent = tmp.toString();
                  tagArgs = new HashMap<String, Object>();
                  break;
               case GET:
                  Object tmp2 = tagArgs.get("_arg");
                  if (tmp2 == null)
                     throw new MalformedTemplateException("You didn't specify a name in #{get /}");
                  String key = tmp2.toString();
                  if (!extraArgs.containsKey(key))
                     throw new MalformedTemplateException("Could not found " + key + " for #{get /}. Did you forget to #{set} it ?");
                  sb.append(extraArgs.get(key));
                  tagArgs = new HashMap<String, Object>();
                  break;
            }
            if (c == '/') {
               switch (tv) {
                  case SCRIPT:
                     sb.append("</").append(ts).append(">");
                  case STYLESHEET:
                  case EXTENDS:
                  case SET:
                  case GET:
                     popTag(ts);
                     if (Tags.SET.equals(tv))
                        body = null;
                     break;
                  default:
                     throw new MalformedTemplateException("#{" + ts + " /} is not allowed");
               }
            } else {
               switch (tv) {
                  case STYLESHEET:
                  case EXTENDS:
                  case GET:
                     throw new MalformedTemplateException("Missing / in " + ts + " tag");
               }
            }
         } else {
            if (c != '/') {
               for (; i < template.length() && (c = template.charAt(i)) != '}' && c != '/'; ++i) ;
               if (c != '/') {
                  pushTag(ts);
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
      tags = new ArrayList<String>();
      sb = new StringBuilder("<% use(util.extensions.JavaExtensions) { %>");
      tagArgs = new HashMap<String, Object>();
      listTag = null;
      body = null;
      field = null;
      nestLevel = 0;
      lastTag = "";
      lastTagIndex = -1;
      for (i = 0; i < template.length(); ++i) {
         c = template.charAt(i);
         if (body != null) {
            handleBody();
            c = template.charAt(i);
         }
         switch (c) {
            case '#':
               if (++i == template.length()) {
                  if (body != null)
                     throw new MalformedTemplateException("Unexpected EOF while reading " + lastTag + " body");
                  sb.append('#');
               } else if ((c = template.charAt(i)) == '{')
                  handleTag(args);
               else if (body == null)
                  sb.append('#').append(c);
               else
                  body.append('#').append(c);
               break;
            case '$':
               if (++i == template.length())
                  sb.append('$');
               else
                  handleVar(args);
               break;
            case '&':
               // TODO: handle i18n
               sb.append('&');
               break;
            case '%':
               if (++i == template.length())
                  sb.append('%');
               else if ((c = template.charAt(i)) == '{')
                  handleJavaCode();
               else
                  sb.append('%').append(c);
               break;
            case '*':
               if (++i == template.length())
                  sb.append('*');
               else if ((c = template.charAt(i)) == '{')
                  handleComment();
               else
                  sb.append('*').append(c);
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
         throw new MalformedTemplateException("Unexpected EOF, maybe you forgot #{/" + lastTag + "} ?");
      sb.append("<% } %>");
      if (hasParent()) {
         Map<String, Object> parentArgs = new HashMap<String, Object>();
         template = runTemplate(Config.PATH + parent, "__LOOSE__INTERNAL__DOLAYOUT__", "doLayout", parentArgs, extraArgs).replace("__LOOSE__INTERNAL__DOLAYOUT__", sb.toString());
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

   public String runTemplate(String fileName, String body, String tagToReplace, Map<String, Object> args, Map<String, Object> extraArgs) throws MalformedTemplateException {
      try {
         Template tpl = new Template(fileName, body, tagToReplace, false, extraArgs);
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

   @Override
   public String toString() {
      return template;
   }
}
