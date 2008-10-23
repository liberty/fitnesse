package fitnesse.responders.run.slimResponder;

import static fitnesse.responders.run.slimResponder.SlimTable.Disgracer.disgraceMethodName;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;

public class DecisionTable extends SlimTable {
  private static final String instancePrefix = "decisionTable";
  private Map<String, Integer> vars = new HashMap<String, Integer>();
  private Map<String, Integer> funcs = new HashMap<String, Integer>();
  private int headerColumns;

  public DecisionTable(Table table, String id) {
    super(table, id);
  }

  public DecisionTable(Table table, String id, SlimTestContext context) {
    super(table, id, context);
  }

  protected String getTableType() {
    return instancePrefix;
  }

  public void appendInstructions() {
    if (table.getRowCount() < 3)
      throw new SyntaxError("DecisionTables should have at least three rows.");
    constructFixture();
    invokeRows();
  }

  protected void evaluateReturnValues(Map<String, Object> returnValues) {
  }

  private void invokeRows() {
    parseColumnHeader();
    for (int row = 2; row < table.getRowCount(); row++)
      invokeRow(row);
  }

  private void parseColumnHeader() {
    headerColumns = table.getColumnCountInRow(1);
    for (int col = 0; col < headerColumns; col++) {
      String cell = table.getCellContents(col, 1);
      if (cell.endsWith("?"))
        funcs.put(cell.substring(0, cell.length() - 1), col);
      else
        vars.put(cell, col);
    }
  }

  private void invokeRow(int row) {
    checkRow(row);
    setVariables(row);
    callFunction(getTableName(), "execute");
    callFunctions(row);
  }

  private void checkRow(int row) {
    int columns = table.getColumnCountInRow(row);
    if (columns < headerColumns)
      throw new SyntaxError(
        String.format("Table has %d header column(s), but row %d only has %d column(s).",
          headerColumns, row, columns
        )
      );
  }

  private void callFunctions(int row) {
    Set<String> funcKeys = funcs.keySet();
    for (String functionName : funcKeys) {
      callFunctionInRow(functionName, row);
    }
  }

  private void callFunctionInRow(String functionName, int row) {
    int col = funcs.get(functionName);
    Matcher matcher = cellMatchesVariableAssignment(row, col);
    if (matcher.matches()) {
      String symbolName = matcher.group(1);
      addExpectation(new SymbolAssignmentExpectation(symbolName, getInstructionNumber(), col, row));
      callAndAssign(symbolName, functionName);
    } else {
      setFunctionCallExpectation(col, row);
      callFunction(getTableName(), functionName);
    }
  }

  private Matcher cellMatchesVariableAssignment(int row, int col) {
    String expected = table.getCellContents(col, row);
    return symbolAssignmentPattern.matcher(expected);
  }

  private void callAndAssign(String symbolName, String functionName) {
    List<Object> callAndAssignInstruction = prepareInstruction();
    callAndAssignInstruction.add("callAndAssign");
    callAndAssignInstruction.add(symbolName);
    callAndAssignInstruction.add(getTableName());
    callAndAssignInstruction.add(disgraceMethodName(functionName));
    addInstruction(callAndAssignInstruction);
  }

  private void setFunctionCallExpectation(int col, int row) {
    String expectedValue = table.getCellContents(col, row);
    addExpectation(new ReturnedValueExpectation(expectedValue, getInstructionNumber(), col, row));
  }

  private void setVariables(int row) {
    Set<String> varKeys = vars.keySet();
    for (String var : varKeys) {
      int col = vars.get(var);
      String valueToSet = table.getCellContents(col, row);
      setVariableExpectation(col, row);
      List<Object> setInstruction = prepareInstruction();
      addCall(setInstruction, getTableName(), "set" + " " + var);
      setInstruction.add(valueToSet);
      addInstruction(setInstruction);
    }
  }

  private void setVariableExpectation(int col, int row) {
    addExpectation(new VoidReturnExpectation(getInstructionNumber(), col, row));
  }
}