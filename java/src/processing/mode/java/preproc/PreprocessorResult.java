package processing.mode.java.preproc;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import processing.app.SketchException;
import processing.mode.java.pdex.ImportStatement;
import processing.mode.java.pdex.TextTransform;
import processing.mode.java.preproc.PdePreprocessor;
import processing.mode.java.preproc.issue.PdePreprocessIssue;


/**
 * Result of sketch Preprocessing.
 */
public class PreprocessorResult {

  private final int headerOffset;
  private final String className;
  private final List<String> extraImports;
  private final List<ImportStatement> importStatements;
  private final PdePreprocessor.Mode programType;
  private final List<TextTransform.Edit> edits;
  private final List<PdePreprocessIssue> preprocessIssues;

  /**
   * Create a new PreprocessorResult indicating that there were issues in preprocessing.
   *
   * @param newPreprocessIssues The list of issues encoutnered.
   * @return New preprocessor result.
   */
  public static PreprocessorResult reportPreprocessIssues(
      List<PdePreprocessIssue> newPreprocessIssues) {

    assert newPreprocessIssues.size() > 0;
    return new PreprocessorResult(newPreprocessIssues);
  }

  /**
   * Create a new preprocessing result.
   *
   * @param newProgramType The type of program that has be preprocessed.
   * @param newHeaderOffset The offset (in number of chars) from the start of the program at which
   *    the header finishes.
   * @param newClassName The name of the class containing the sketch.
   * @param newExtraImports Additional imports beyond the defaults and code folder.
   * @param newEdits The edits made during preprocessing.
   */
  public PreprocessorResult(PdePreprocessor.Mode newProgramType, int newHeaderOffset,
        String newClassName, List<String> newExtraImports, List<TextTransform.Edit> newEdits) {

    if (newClassName == null) {
      throw new RuntimeException("Could not find main class");
    }

    headerOffset = newHeaderOffset;
    className = newClassName;
    extraImports = Collections.unmodifiableList(new ArrayList<>(newExtraImports));
    programType = newProgramType;
    edits = newEdits;
    preprocessIssues = new ArrayList<>();

    importStatements = extraImports.stream()
        .map(ImportStatement::parse)
        .collect(Collectors.toList());
  }

  /**
   * Private constructor allowing creation of result indicating preprocess issues.
   *
   * @param newPreprocessIssues The list of preprocess issues encountered.
   */
  private PreprocessorResult(List<PdePreprocessIssue> newPreprocessIssues) {
    preprocessIssues = Collections.unmodifiableList(newPreprocessIssues);
    headerOffset = 0;
    className = "unknown";
    extraImports = new ArrayList<>();
    programType = PdePreprocessor.Mode.STATIC;
    edits = new ArrayList<>();
    importStatements = new ArrayList<>();
  }

  /**
   * Get the list of preprocess issues encountered.
   *
   * @return List of preprocess issues encountered.
   */
  public List<PdePreprocessIssue> getPreprocessIssues() {
    return preprocessIssues;
  }

  /**
   * Get the end point of the header.
   *
   * @return The offset (in number of lines) from the start of the program at which the header
   *    finishes.
   */
  public int getHeaderOffset() {
    return headerOffset;
  }

  /**
   * Get the name of the Java class containing the sketch after preprocessing.
   *
   * @return The name of the class containing the sketch.
   */
  public String getClassName() {
    return className;
  }

  /**
   * Get the imports beyond the default set that are included in the sketch.
   *
   * @return Additional imports beyond the defaults and code folder.
   */
  public List<String> getExtraImports() {
    return extraImports;
  }

  /**
   * Get the type of program that was parsed.
   *
   * @return Type of program parsed like STATIC (no function) or ACTIVE.
   */
  public PdePreprocessor.Mode getProgramType() {
    return programType;
  }

  /**
   * Get the edits generated during preprocessing.
   *
   * @return List of edits generated during preprocessing.
   */
  public List<TextTransform.Edit> getEdits() {
    return edits;
  }

  /**
   * Get the found import statements as {ImportStatement}s.
   *
   * @return The import statements found for the user.
   */
  public List<ImportStatement> getImportStatements() {
    return importStatements;
  }
}
