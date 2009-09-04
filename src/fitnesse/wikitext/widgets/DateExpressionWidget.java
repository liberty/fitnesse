/*
 * Copyright (c) 2006 Sabre Holdings. All Rights Reserved.
 */
package fitnesse.wikitext.widgets;

import java.text.*;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.regex.*;

import fitnesse.html.HtmlUtil;

public class DateExpressionWidget extends ParentWidget {
	// An amount followed by an optional units of measure
   enum SET_QUALIFIER {NONE, FUTURE, FUTURE_EQUALS, PAST, PAST_EQUALS};
   enum DMY {DAY, MONTH, YEAR;
      public static DMY convert(String value) {
         if ((value == null) || value.equals("")) {
            return DMY.DAY;
         } else if (value.toLowerCase().startsWith("d")) {
            return DMY.DAY;
         } else if (value.toLowerCase().startsWith("m")) {
            return DMY.MONTH;
         } else if (value.toLowerCase().startsWith("y")) {
            return DMY.YEAR;
         } else {
            throw new RuntimeException("Invalid unit of measure: " + value);
         }
      }
   };
	private static final String CAPTURING_GROUP_START = "(?:(?<=[^\\\\])|^)\\((?!\\?)"; // find "(" excluding "\\(", "(?"
	private static final String setUnitStr = "((?:day)|(?:month)|(?:year))?\\s*=\\s*([1-9][0-9]*)\\s*,?\\s*";
   private static final Pattern setUnitPattern = Pattern.compile(setUnitStr, Pattern.DOTALL + Pattern.MULTILINE + Pattern.CASE_INSENSITIVE);
	private static final String setStr = "(set(?:(?:Future)|(?:FutureOrToday)|(?:Past)|(?:PastOrToday))?)\\s*\\[\\s*((?:" + setUnitStr.replaceAll(CAPTURING_GROUP_START, "(?:") + ")+)?\\s*\\]\\s*";
   private static final Pattern setPattern = Pattern.compile(setStr, Pattern.DOTALL + Pattern.MULTILINE + Pattern.CASE_INSENSITIVE);
	private static final String amountUnitStr = "([+-]\\s*[1-9][0-9]*)\\s*((?:days?)|(?:months?)|(?:years?))?\\s*";
   private static final Pattern amountUnitPattern = Pattern.compile(amountUnitStr, Pattern.DOTALL + Pattern.MULTILINE + Pattern.CASE_INSENSITIVE);
	private static final String amountUnitSetStr = "((?:" + amountUnitStr.replaceAll(CAPTURING_GROUP_START, "(?:") + ")|(?:" + setStr.replaceAll(CAPTURING_GROUP_START, "(?:") + "))";
   private static final Pattern amountUnitSetPattern = Pattern.compile(amountUnitSetStr, Pattern.DOTALL + Pattern.MULTILINE + Pattern.CASE_INSENSITIVE);
   // The whole pattern matching the entire amount/uom string - replace all capturing groups within the amount/uom pattern as non-capturing
   private static final String patternStr = "!date\\{(?:\\s*today\\s*((?:" + amountUnitSetStr.replaceAll(CAPTURING_GROUP_START, "(?:") + ")+)?\\s*?(,[^\\}]+)?)?\\}";
   private static final Pattern pattern = Pattern.compile(patternStr, Pattern.DOTALL + Pattern.MULTILINE + Pattern.CASE_INSENSITIVE);
   // Build the pattern for FitNesse replacing all capturing groups as non-capturing
   public static final String REGEXP = patternStr.replaceAll(CAPTURING_GROUP_START, "(?:"); // Make all groups non-capturing for fitnesse
   private static final DateFormat format = new SimpleDateFormat("ddMMM");

   private List actions = new ArrayList();
   private String renderedText;
   private String origText;
   private boolean rendered;
   private DateFormat specifiedFormat;
   private Throwable exception;

   public DateExpressionWidget(ParentWidget parent, String text) throws Exception {
      super(parent);
      origText = text;
      Matcher match = pattern.matcher(text);
      if (match.find()) {
   		specifiedFormat = format;
   		// Match the amounts and units
   		String matchText = match.group(1);
   		if ((matchText != null) && !matchText.equals("")) {
      		Matcher amountUnitSetMatcher = amountUnitSetPattern.matcher(matchText);
      		while (amountUnitSetMatcher.find()) {
      			String amtOrSetStr = amountUnitSetMatcher.group(1).trim();
      			if (amtOrSetStr.startsWith("set")) {
      				// Explicit setting
      				Matcher setMatcher = setPattern.matcher(amtOrSetStr);
      				setMatcher.find();
      				String type = setMatcher.group(1);
      				Matcher setUnitMatcher = setUnitPattern.matcher(setMatcher.group(2));
                  SetDateAction newAction = new SetDateAction();
                  actions.add(newAction);
                  if (type.equalsIgnoreCase("set")) {
                     newAction.qualifier = SET_QUALIFIER.NONE;
                  } else if (type.equalsIgnoreCase("setFuture")) {
                     newAction.qualifier = SET_QUALIFIER.FUTURE;
                  } else if (type.equalsIgnoreCase("setFutureOrToday")) {
                     newAction.qualifier = SET_QUALIFIER.FUTURE_EQUALS;
                  } else if (type.equalsIgnoreCase("setPast")) {
                     newAction.qualifier = SET_QUALIFIER.PAST;
                  } else if (type.equalsIgnoreCase("setPastOrToday")) {
                     newAction.qualifier = SET_QUALIFIER.PAST_EQUALS;
                  }
                  newAction.actions = new ArrayList<ModifyDateAction>();
      				while (setUnitMatcher.find()) {
                     ModifyDateAction modAction = new ModifyDateAction();
                     newAction.actions.add(modAction);
      					modAction.dmy = DMY.convert(setUnitMatcher.group(1));
      					modAction.amount = Integer.parseInt(setUnitMatcher.group(2));
      				}
      			} else {
      				Matcher amountUnitMatcher = amountUnitPattern.matcher(amtOrSetStr);
      				amountUnitMatcher.find();
                  ModifyDateAction newAction = new ModifyDateAction();

                  actions.add(newAction);
      				String amtStr = amountUnitMatcher.group(1);
                  newAction.amount = Integer.parseInt(amtStr.replaceAll(" ", "").replaceAll("\\+", ""));
      	   		matchText = amountUnitMatcher.group(2);
                  newAction.dmy = DMY.convert(matchText);
      			}
      		}
   		}
   		// Match the format
   		matchText = match.group(2);
   		if ((matchText != null) && !matchText.equals("")) {
   			try {
   				specifiedFormat = new SimpleDateFormat(matchText.substring(1).trim());
   			} catch (Exception ex) {
   				exception = ex;
   			}
   		}
      } else {
      	exception = new RuntimeException("Invalid format");
      }
   }

   public String render() throws Exception {
      if (!rendered)
         doRender();
      return renderedText;
   }

   private void doRender() throws Exception {
   	if (exception != null) {
   		renderedText = HtmlUtil.metaText("invalid date expression: " + origText + ", error: " + exception.getMessage());
   		return;
   	}
      String value = parseDate();
      if (value != null) {
         addChildWidgets(value);
         renderedText = childHtml();
      }
      else
         renderedText = makeInvalidExpression();
      rendered = true;
   }

   private String parseDate() {
      Calendar cal = Calendar.getInstance();
      for (Object action : actions) {
         if (action instanceof ModifyDateAction) {
            ModifyDateAction modAction = (ModifyDateAction)action;
            switch (modAction.dmy) {
               case DAY:
                  cal.add(Calendar.DATE, modAction.amount);
                  break;
               case MONTH:
                  cal.add(Calendar.MONTH, modAction.amount);
                  break;
               case YEAR:
                  cal.add(Calendar.YEAR, modAction.amount);
                  break;
            }
         }
         if (action instanceof SetDateAction) {
            DMY highestLevel = DMY.DAY;
            Date now = cal.getTime();
            for (ModifyDateAction modAction : ((SetDateAction)action).actions) {
               switch (modAction.dmy) {
                  case DAY:
                     cal.set(Calendar.DATE, modAction.amount);
                     break;
                  case MONTH:
                     cal.set(Calendar.MONTH, modAction.amount - 1);
                     if (highestLevel.compareTo(DMY.MONTH) < 0) {
                        highestLevel = DMY.MONTH;
                     }
                     break;
                  case YEAR:
                     cal.set(Calendar.YEAR, modAction.amount);
                     if (highestLevel.compareTo(DMY.YEAR) < 0) {
                        highestLevel = DMY.YEAR;
                     }
                     break;
               }
            }
            if (highestLevel != DMY.YEAR) {
               // Can't move date if the year is specified
               switch (((SetDateAction)action).qualifier) {
                  case NONE:
                     break;
                  case FUTURE:
                     while (cal.getTime().compareTo(now) <= 0) {
                        if (highestLevel == DMY.MONTH) {
                           cal.add(Calendar.YEAR, 1);
                        } else {
                           cal.add(Calendar.MONTH, 1);
                        }
                     }
                     break;
                  case FUTURE_EQUALS:
                     while (cal.getTime().compareTo(now) < 0) {
                        if (highestLevel == DMY.MONTH) {
                           cal.add(Calendar.YEAR, 1);
                        } else {
                           cal.add(Calendar.MONTH, 1);
                        }
                     }
                     break;
                  case PAST:
                     while (cal.getTime().compareTo(now) >= 0) {
                        if (highestLevel == DMY.MONTH) {
                           cal.add(Calendar.YEAR, -1);
                        } else {
                           cal.add(Calendar.MONTH, -1);
                        }
                     }
                     break;
                  case PAST_EQUALS:
                     while (cal.getTime().compareTo(now) > 0) {
                        if (highestLevel == DMY.MONTH) {
                           cal.add(Calendar.YEAR, -1);
                        } else {
                           cal.add(Calendar.MONTH, -1);
                        }
                     }
                     break;
               }
            }
         }
      }
      return specifiedFormat.format(cal.getTime()).toUpperCase();
   }

   private String makeInvalidExpression() throws Exception {
      return HtmlUtil.metaText("invalid date expression: " + origText);
   }

   public String asWikiText() throws Exception {
      return "!date{" + origText + "}";
   }

   abstract class DateAction {

   }
   class SetDateAction extends DateAction {
      SET_QUALIFIER qualifier;
      List<ModifyDateAction> actions;
   }

   class ModifyDateAction {
      int amount;
      DMY dmy;
   }
}
