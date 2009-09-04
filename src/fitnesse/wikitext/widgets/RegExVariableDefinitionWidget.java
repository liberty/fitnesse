/*
 * Copyright (c) 2006 Sabre Holdings. All Rights Reserved.
 */

package fitnesse.wikitext.widgets;

import fitnesse.html.HtmlUtil;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class RegExVariableDefinitionWidget extends ParentWidget
{
   public static final String REGEXP = "(?:^!defineregex \\w+ (?:(?:\\{[^\r\n]*\\})$|(?:\\([^\r\n]*\\))$))";
   private static final Pattern pattern =
           Pattern.compile("^!defineregex (\\w+) ([\\{\\(])(.*)[\\}\\)]",
                   Pattern.DOTALL + Pattern.MULTILINE);

   private static final Pattern VARIABLE_REF = Pattern.compile("\\$\\{(\\w+)\\}",
           Pattern.MULTILINE + Pattern.DOTALL);

   public String name;
   public String value;
   public String opener;

   public RegExVariableDefinitionWidget(ParentWidget parent, String text) throws Exception
   {
      super(parent);
      Matcher match = pattern.matcher(text);
      if(match.find())
      {
         name = match.group(1);
         opener = match.group(2);
         value = match.group(3);
      }
   }

   public String render() throws Exception
   {
      this.parent.addVariable(name, value);
      try
      {
         Matcher matcher = VARIABLE_REF.matcher(value);
         while (matcher.find())
         {
            String ref = matcher.group(0);
            VariableWidget variable = new VariableWidget(parent, ref);
            String val = variable.render();
            value = value.replace(ref, val);
         }
         Pattern.compile(value);

         return HtmlUtil.metaText("variable defined: " + name + "=" + value);
      }
      catch(PatternSyntaxException e)
      {
         return metaErrorText("variable defined with invalid regular expression: " + name + "=" + value +
                              ": " + e.getMessage());
      }
   }

   private String metaErrorText(String text)
   {
      return "<span class=\"meta\"><span class=\"error\">" + text + "</span></span>";
   }

   public String asWikiText() throws Exception
   {
      String text = "!defineregex " + name + " ";
      if(opener.equals("{"))
         text += "{" + value + "}";
      else
         text += "(" + value + ")";
      return text;
   }

}
