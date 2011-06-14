package util.tags;

import javax.validation.constraints.Null;

/**
 * @author Marc-Antoine Perennou<Marc-Antoine@Perennou.com>
 */
// TODO: remove that when switching to java7
public enum Tags {
   A("a"),
   ELSE("else"),
   ELSEIF("elseif"),
   EXTENDS("extends"),
   FIELD("field"),
   FORM("form"),
   GET("get"),
   IF("if"),
   IFNOT("ifnot"),
   LIST("list"),
   SCRIPT("script"),
   SET("set"),
   SLASHA("/a"),
   SLASHFIELD("/field"),
   SLASHFORM("/form"),
   SLASHIF("/if"),
   SLASHLIST("/list"),
   SLASHSCRIPT("/script"),
   SLASHSET("/set"),
   STYLESHEET("stylesheet");

   private String name;

   private Tags(String name) {
      this.name = name;
   }

   public static Tags fromString(String name) {
      if ("a".equals(name))
         return A;
      if ("else".equals(name))
         return ELSE;
      if ("elseif".equals(name))
         return ELSEIF;
      if ("extends".equals(name))
         return EXTENDS;
      if ("field".equals(name))
         return FIELD;
      if ("form".equals(name))
         return FORM;
      if ("get".equals(name))
         return GET;
      if ("if".equals(name))
         return IF;
      if ("ifnot".equals(name))
         return IFNOT;
      if ("list".equals(name))
         return LIST;
      if ("script".equals(name))
         return SCRIPT;
      if ("set".equals(name))
         return SET;
      if ("/a".equals(name))
         return SLASHA;
      if ("/field".equals(name))
         return SLASHFIELD;
      if ("/form".equals(name))
         return SLASHFORM;
      if ("/if".equals(name))
         return SLASHIF;
      if ("/list".equals(name))
         return SLASHLIST;
      if ("/script".equals(name))
         return SLASHSCRIPT;
      if ("/set".equals(name))
         return SLASHSET;
      if ("stylesheet".equals(name))
         return STYLESHEET;
      return null;
   }

   public String toString() {
      return this.name;
   }
}
