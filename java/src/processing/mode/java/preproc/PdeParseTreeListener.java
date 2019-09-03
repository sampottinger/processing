/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
  Part of the Processing project - http://processing.org

  Copyright (c) 2019 The Processing Foundation

  This program is free software; you can redistribute it and/or modify
  it under the terms of the GNU General Public License version 2
  as published by the Free Software Foundation.

  This program is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  GNU General Public License for more details.

  You should have received a copy of the GNU General Public License
  along with this program; if not, write to the Free Software Foundation,
  Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
*/

package processing.mode.java.preproc;

import java.util.*;

import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.misc.Interval;

import org.antlr.v4.runtime.tree.ParseTree;
import processing.core.PApplet;
import processing.mode.java.pdex.TextTransform;
import processing.mode.java.preproc.PdePreprocessor.Mode;
import processing.mode.java.preproc.code.*;
import processing.mode.java.preproc.issue.PdePreprocessIssue;
import processing.mode.java.preproc.issue.strategy.MessageSimplifierUtil;

/**
 * ANTLR tree traversal listener that preforms code rewrites as part of sketch preprocessing.
 *
 * <p>
 *   ANTLR tree traversal listener that preforms code rewrites as part of sketch preprocessing,
 *   turning sketch source into compilable Java code. Note that this emits both the Java source
 *   when using javac directly as part of {JavaBuild} as well as {TextTransform.Edit}s when using
 *   the JDT via the {PreprocessingService}.
 * </p>
 */
public class PdeParseTreeListener extends ProcessingBaseListener {

  private final static String VERSION_STR = "3.0.0";
  private static final String SIZE_METHOD_NAME = "size";
  private static final String FULLSCREEN_METHOD_NAME = "fullScreen";
  private final int tabSize;

  private int headerOffset;

  private String sketchName;
  private boolean isTesting;
  private TokenStreamRewriter rewriter;

  protected Mode mode = Mode.JAVA;
  private boolean foundMain;

  private int lineOffset;

  private ArrayList<String> coreImports = new ArrayList<>();
  private ArrayList<String> defaultImports = new ArrayList<>();
  private ArrayList<String> codeFolderImports = new ArrayList<>();
  private ArrayList<String> foundImports = new ArrayList<>();
  private ArrayList<TextTransform.Edit> edits = new ArrayList<>();

  private String sketchWidth;
  private String sketchHeight;
  private String sketchRenderer;

  private boolean sizeRequiresRewrite = false;
  private boolean sizeIsFullscreen = false;
  private RewriteResult headerResult;
  private RewriteResult footerResult;

  private Optional<PdeParseTreeErrorListener> pdeParseTreeErrorListenerMaybe;

  /**
   * Create a new listener.
   *
   * @param tokens The tokens over which to rewrite.
   * @param newSketchName The name of the sketch being traversed.
   * @param newTabSize Size of tab / indent.
   */
  PdeParseTreeListener(TokenStream tokens, String newSketchName, int newTabSize) {
    rewriter = new TokenStreamRewriter(tokens);
    sketchName = newSketchName;
    tabSize = newTabSize;

    pdeParseTreeErrorListenerMaybe = Optional.empty();
  }

  /**
   * Indicate imports for code folders.
   *
   * @param codeFolderImports List of imports for sources sitting in the sketch code folder.
   */
  public void setCodeFolderImports(List<String> codeFolderImports) {
    this.codeFolderImports.clear();
    this.codeFolderImports.addAll(codeFolderImports);
  }

  /**
   * Indicate list of imports required for all sketches to be inserted in preprocessing.
   *
   * @param coreImports The list of imports required for all sketches.
   */
  public void setCoreImports(String[] coreImports) {
    setCoreImports(Arrays.asList(coreImports));
  }

  /**
   * Indicate list of imports required for all sketches to be inserted in preprocessing.
   *
   * @param coreImports The list of imports required for all sketches.
   */
  public void setCoreImports(List<String> coreImports) {
    this.coreImports.clear();
    this.coreImports.addAll(coreImports);
  }

  /**
   * Indicate list of default convenience imports.
   *
   * <p>
   *    Indicate list of imports that are not required for sketch operation but included for the
   *    user's convenience regardless.
   * </p>
   *
   * @param defaultImports The list of imports to include for user convenience.
   */
  public void setDefaultImports(String[] defaultImports) {
    setDefaultImports(Arrays.asList(defaultImports));
  }

  /**
   * Indicate list of default convenience imports.
   *
   * <p>
   *    Indicate list of imports that are not required for sketch operation but included for the
   *    user's convenience regardless.
   * </p>
   *
   * @param defaultImports The list of imports to include for user convenience.
   */
  public void setDefaultImports(List<String> defaultImports) {
    this.defaultImports.clear();
    this.defaultImports.addAll(defaultImports);
  }

  /**
   * Indicate if running in unit tests.
   *
   * @param isTesting True if running as part of tests and false otherwise.
   */
  public void setTesting(boolean isTesting) {
    this.isTesting = isTesting;
  }

  /**
   * Indicate which listener should be informed of parse tree processing issues.
   *
   * @param newListener listener to be informed when an issue is encoutnered in processing the
   *    parse tree.
   */
  public void setTreeErrorListener(PdeParseTreeErrorListener newListener) {
    pdeParseTreeErrorListenerMaybe = Optional.of(newListener);
  }

  /**
   * Determine if the user provided their own "main" method.
   *
   * @return True if the sketch code provides a main method. False otherwise.
   */
  public boolean foundMain() {
    return foundMain;
  }

  /**
   * Get the sketch code transformed to grammatical Java.
   *
   * @return Complete sketch code as Java.
   */
  public String getOutputProgram() {
    return rewriter.getText();
  }

  /**
   * Get the rewriter used by this listener.
   *
   * @return Listener's rewriter.
   */
  public TokenStreamRewriter getRewriter() {
    return rewriter;
  }

  /**
   * Get the result of the last preprocessing.
   *
   * @return The result of the last preprocessing.
   */
  public PreprocessorResult getResult() {
    List<String> allImports = new ArrayList<>();

    allImports.addAll(coreImports);
    allImports.addAll(defaultImports);
    allImports.addAll(codeFolderImports);
    allImports.addAll(foundImports);

    List<TextTransform.Edit> allEdits = new ArrayList<>();
    allEdits.addAll(headerResult.getEdits());
    allEdits.addAll(edits);
    allEdits.addAll(footerResult.getEdits());

    return new PreprocessorResult(
        mode,
        lineOffset,
        sketchName,
        allImports,
        allEdits,
        sketchWidth,
        sketchHeight
    );
  }

  // --------------------------------------------------- listener impl

  /**
   * Endpoint for ANTLR to call when having finished parsing a processing sketch.
   *
   * @param ctx The context from ANTLR for the processing sketch.
   */
  public void exitProcessingSketch(ProcessingParser.ProcessingSketchContext ctx) {
    // header
    RewriteParams rewriteParams = createRewriteParams();

    RewriterCodeGenerator codeGen = new RewriterCodeGenerator(tabSize);

    headerResult = codeGen.writeHeader(rewriter, rewriteParams);

    lineOffset = headerResult.getLineOffset();

    // footer
    TokenStream tokenStream = rewriter.getTokenStream();
    int tokens = tokenStream.size();
    int length = tokenStream.get(tokens-1).getStopIndex();

    footerResult = codeGen.writeFooter(rewriter, rewriteParams, length);
  }

  /**
   * Endpoint for ANTLR to call when finished parsing a method invocatino.
   *
   * @param ctx The ANTLR context for the method call.
   */
  public void exitMethodCall(ProcessingParser.MethodCallContext ctx) {
    String methodName = ctx.getChild(0).getText();

    if (SIZE_METHOD_NAME.equals(methodName) || FULLSCREEN_METHOD_NAME.equals(methodName)) {
      handleSizeCall(ctx);
    }
  }

  /**
   * Manage parsing out a size or fullscreen call.
   *
   * @param ctx The context of the call.
   */
  private void handleSizeCall(ParserRuleContext ctx) {
    ParserRuleContext testCtx = ctx.getParent()
      .getParent()
      .getParent()
      .getParent();

    boolean isInGlobal =
      testCtx instanceof ProcessingParser.StaticProcessingSketchContext;

    boolean isInSetup;
    if (!isInGlobal) {
      ParserRuleContext methodDeclaration = testCtx.getParent()
          .getParent();

      isInSetup = isMethodSetup(methodDeclaration);
    } else {
      isInSetup = false;
    }

    ParseTree argsContext = ctx.getChild(2);

    boolean thisRequiresRewrite = false;

    boolean isSize = ctx.getChild(0).getText().equals(SIZE_METHOD_NAME);
    boolean isFullscreen = ctx.getChild(0).getText().equals(FULLSCREEN_METHOD_NAME);

    if (isInGlobal || isInSetup) {
      thisRequiresRewrite = true;

      if (isSize && argsContext.getChildCount() > 2) {
        sketchWidth = argsContext.getChild(0).getText();
        if (PApplet.parseInt(sketchWidth, -1) == -1 &&
            !sketchWidth.equals("displayWidth")) {
          thisRequiresRewrite = false;
        }

        sketchHeight = argsContext.getChild(2).getText();
        if (PApplet.parseInt(sketchHeight, -1) == -1 &&
            !sketchHeight.equals("displayHeight")) {
          thisRequiresRewrite = false;
        }

        if (argsContext.getChildCount() > 3) {
          sketchRenderer = argsContext.getChild(4).getText();
          if (!(sketchRenderer.equals("P2D") ||
              sketchRenderer.equals("P3D") ||
              sketchRenderer.equals("OPENGL") ||
              sketchRenderer.equals("JAVA2D") ||
              sketchRenderer.equals("FX2D"))) {
            thisRequiresRewrite = false;
          }
        }
      }

      if (isFullscreen) {
        sketchWidth = "displayWidth";
        sketchWidth = "displayHeight";

        thisRequiresRewrite = true;
        sizeIsFullscreen = true;

        if (argsContext.getChildCount() > 0) {
          sketchRenderer = argsContext.getChild(0).getText();
          if (!(sketchRenderer.equals("P2D") ||
              sketchRenderer.equals("P3D") ||
              sketchRenderer.equals("OPENGL") ||
              sketchRenderer.equals("JAVA2D") ||
              sketchRenderer.equals("FX2D"))) {
            thisRequiresRewrite = false;
          }
        }
      }
    }

    if (thisRequiresRewrite) {
      createDelete(ctx.start, ctx.stop);
      createInsertAfter(ctx.stop, "/* size commented out by preprocessor */");
      sizeRequiresRewrite = true;
    }
  }

  /**
   * Determine if a method declaration is for setup.
   *
   * @param declaration The method declaration to parse.
   * @return True if setup and false otherwise.
   */
  private boolean isMethodSetup(ParserRuleContext declaration) {
    if (declaration.getChildCount() < 2) {
      return false;
    }
    return declaration.getChild(1).getText().equals("setup");
  }

  /**
   * Endpoint for ANTLR to call when finished parsing an import declaration.
   *
   * <p>
   *    Endpoint for ANTLR to call when finished parsing an import declaration, remvoing those
   *    declarations from sketch body so that they can be included in the header.
   * </p>
   *
   * @param ctx ANTLR context for the import declaration.
   */
  public void exitImportDeclaration(ProcessingParser.ImportDeclarationContext ctx) {
    ProcessingParser.QualifiedNameContext startCtx = null;

    // Due to imports pre-procesing, cannot allow class-body imports
    if (ctx.getParent() instanceof ProcessingParser.ClassBodyDeclarationContext) {
      pdeParseTreeErrorListenerMaybe.ifPresent((listener) -> {
        Token token = ctx.getStart();
        int line = token.getLine();
        int charOffset = token.getCharPositionInLine();

        listener.onError(new PdePreprocessIssue(
          line,
          charOffset,
          MessageSimplifierUtil.getLocalStr("editor.status.bad.import")
        ));
      });
    }

    for(int i = 0; i < ctx.getChildCount(); i++) {
      ParseTree candidate = ctx.getChild(i);
      if (candidate instanceof ProcessingParser.QualifiedNameContext) {
        startCtx = (ProcessingParser.QualifiedNameContext) ctx.getChild(i);
      }
    }

    if (startCtx == null) {
      return;
    }

    Interval interval =
        new Interval(startCtx.start.getStartIndex(), ctx.stop.getStopIndex());
    String importString = ctx.start.getInputStream().getText(interval);
    String importStringNoSemi = importString.substring(0, importString.length() - 1);
    foundImports.add(importStringNoSemi);

    createDelete(ctx.start, ctx.stop);
  }

  /**
   * Endpoint for ANTLR to call after parsing a decimal point literal.
   *
   * <p>
   *   Endpoint for ANTLR to call when finished parsing a floating point literal, adding an 'f' at
   *   the end to force it float instead of double for API compatability.
   * </p>
   *
   * @param ctx ANTLR context for the literal.
   */
  public void exitFloatLiteral(ProcessingParser.FloatLiteralContext ctx) {
    String cTxt = ctx.getText().toLowerCase();
    if (!cTxt.endsWith("f") && !cTxt.endsWith("d")) {
      createInsertAfter(ctx.stop, "f");
    }
  }

  /**
   * Endpoint for ANTLR to call after parsing a static processing sketch.
   *
   * <p>
   *   Endpoint for ANTLR to call after parsing a static processing sketch, informing this parser
   *   that it is operating on a static sketch (no method or class declarations) so that it writes
   *   the correct header / footer.
   * </p>
   *
   * @param ctx ANTLR context for the sketch.
   */
  public void exitStaticProcessingSketch(ProcessingParser.StaticProcessingSketchContext ctx) {
    mode = Mode.STATIC;
  }

  /**
   * Endpoint for ANTLR to call after parsing a "active" processing sketch.
   *
   * <p>
   *   Endpoint for ANTLR to call after parsing a "active" processing sketch, informing this parser
   *   that it is operating on an active sketch so that it writes the correct header / footer.
   * </p>
   *
   * @param ctx ANTLR context for the sketch.
   */
  public void exitActiveProcessingSketch(ProcessingParser.ActiveProcessingSketchContext ctx) {
    mode = Mode.ACTIVE;
  }

  /**
   * Endpoint for ANTLR to call after parsing a method declaration.
   *
   * <p>
   *   Endpoint for ANTLR to call after parsing a method declaration, making any method "public"
   *   that has:
   *
   *   <ul>
   *     <li>no other access modifier</li>
   *     <li>return type "void"</li>
   *     <li>is either in the context of the sketch class</li>
   *     <li>is in the context of a class definition that extends PApplet</li>
   *   </ul>
   * </p>
   *
   * @param ctx ANTLR context for the method declaration
   */
  public void exitMethodDeclaration(ProcessingParser.MethodDeclarationContext ctx) {
    ParserRuleContext memCtx = ctx.getParent();
    ParserRuleContext clsBdyDclCtx = memCtx.getParent();
    ParserRuleContext clsBdyCtx = clsBdyDclCtx.getParent();
    ParserRuleContext clsDclCtx = clsBdyCtx.getParent();

    boolean inSketchContext =
      clsBdyCtx instanceof ProcessingParser.StaticProcessingSketchContext ||
      clsBdyCtx instanceof ProcessingParser.ActiveProcessingSketchContext;

    boolean inPAppletContext =
      inSketchContext || (
        clsDclCtx instanceof ProcessingParser.ClassDeclarationContext &&
        clsDclCtx.getChildCount() >= 4 &&
        clsDclCtx.getChild(2).getText().equals("extends") &&
        clsDclCtx.getChild(3).getText().endsWith("PApplet"));

    // Find modifiers
    ParserRuleContext possibleModifiers = ctx;

    while (!(possibleModifiers instanceof ProcessingParser.ClassBodyDeclarationContext)) {
      possibleModifiers = possibleModifiers.getParent();
    }

    // Look for visibility modifiers and annotations
    boolean hasVisibilityModifier = false;

    int numChildren = possibleModifiers.getChildCount();

    ParserRuleContext annoationPoint = null;

    for (int i = 0; i < numChildren; i++) {
      boolean childIsVisibility;

      ParseTree child = possibleModifiers.getChild(i);
      String childText = child.getText();

      childIsVisibility = childText.equals("public");
      childIsVisibility = childIsVisibility || childText.equals("private");
      childIsVisibility = childIsVisibility || childText.equals("protected");

      hasVisibilityModifier = hasVisibilityModifier || childIsVisibility;

      boolean isModifier = child instanceof ProcessingParser.ModifierContext;
      if (isModifier && isAnnoation((ProcessingParser.ModifierContext) child)) {
        annoationPoint = (ParserRuleContext) child;
      }
    }

    // Insert at start of method or after annoation
    if (!hasVisibilityModifier) {
      if (annoationPoint == null) {
        createInsertBefore(possibleModifiers.getStart(), " public ");
      } else {
        createInsertAfter(annoationPoint.getStop(), " public ");
      }
    }

    // Check if this was main
    if ((inSketchContext || inPAppletContext) &&
        hasVisibilityModifier &&
        ctx.getChild(1).getText().equals("main")) {
      foundMain = true;
    }
  }

  /**
   * Check if this contains an annation.
   *
   * @param child The modifier context to check.
   * @return True if annotation. False otherwise
   */
  private boolean isAnnoation(ProcessingParser.ModifierContext context) {
    if (context.getChildCount() == 0) {
      return false;
    }

    ProcessingParser.ClassOrInterfaceModifierContext classModifierCtx;
    if (!(context.getChild(0) instanceof ProcessingParser.ClassOrInterfaceModifierContext)) {
      return false;
    }

    classModifierCtx = (ProcessingParser.ClassOrInterfaceModifierContext) context.getChild(0);

    return classModifierCtx.getChild(0) instanceof ProcessingParser.AnnotationContext;
  }

  /**
   * Endpoint for ANTLR to call after parsing a primitive type name.
   *
   * <p>
   *   Endpoint for ANTLR to call after parsing a primitive type name, possibly converting that type
   *   to a parse function as part of the Processing API.
   * </p>
   *
   * @param ctx ANTLR context for the primitive name token.
   */
  public void exitFunctionWithPrimitiveTypeName(
      ProcessingParser.FunctionWithPrimitiveTypeNameContext ctx) {

    String fn = ctx.getChild(0).getText();
    if (!fn.equals("color")) {
      fn = "PApplet.parse" + fn.substring(0,1).toUpperCase() + fn.substring(1);
      createInsertBefore(ctx.start, fn);
      createDelete(ctx.start);
    }
  }

  /**
   * Endpoint for ANTLR to call after parsing a color primitive token.
   *
   * <p>
   *   Endpoint for ANTLR to call after parsing a color primitive token, fixing "color type" to be
   *   "int" as part of the processing API.
   * </p>
   *
   * @param ctx ANTLR context for the type token.
   */
  public void exitColorPrimitiveType(ProcessingParser.ColorPrimitiveTypeContext ctx) {
    if (ctx.getText().equals("color")) {
      createInsertBefore(ctx.start, "int");
      createDelete(ctx.start, ctx.stop);
    }
  }

  /**
   * Endpoint for ANTLR to call after parsing a hex color literal.
   *
   * @param ctx ANTLR context for the literal.
   */
  public void exitHexColorLiteral(ProcessingParser.HexColorLiteralContext ctx) {
    if (ctx.getText().length() == 7) {
      createInsertBefore(
          ctx.start,
          ctx.getText().toUpperCase().replace("#","0xFF")
      );
    } else {
      createInsertBefore(
          ctx.start,
          ctx.getText().toUpperCase().replace("#", "0x")
      );
    }

    createDelete(ctx.start, ctx.stop);
  }

  // -- Wrappers around CodeEditOperationUtil --

  /**
   * Insert text before a token.
   *
   * @param location The token before which code should be added.
   * @param text The text to add.
   */
  private void createInsertBefore(Token location, String text) {
    edits.add(CodeEditOperationUtil.createInsertBefore(location, text, rewriter));
  }

  /**
   * Insert text before a location in code.
   *
   * @param locationToken Character offset from start.
   * @param locationOffset
   * @param text Text to add.
   */
  private void createInsertBefore(int locationToken, int locationOffset, String text) {
    edits.add(CodeEditOperationUtil.createInsertBefore(
        locationToken,
        locationOffset,
        text,
        rewriter
    ));
  }

  /**
   * Insert text after a location in code.
   *
   * @param location The token after which to insert code.
   * @param text The text to insert.
   */
  private void createInsertAfter(Token location, String text) {
    edits.add(CodeEditOperationUtil.createInsertAfter(location, text, rewriter));
  }

  /**
   * Delete from a token to a token inclusive.
   *
   * @param start First token to delete.
   * @param stop Last token to delete.
   */
  private void createDelete(Token start, Token stop) {
    edits.add(CodeEditOperationUtil.createDelete(start, stop, rewriter));
  }

  /**
   * Delete a single token.
   *
   * @param location Token to delete.
   */
  private void createDelete(Token location) {
    edits.add(CodeEditOperationUtil.createDelete(location, rewriter));
  }

  /**
   * Create parameters required by the RewriterCodeGenerator.
   *
   * @return Newly created rewrite params.
   */
  private RewriteParams createRewriteParams() {
    RewriteParamsBuilder builder = new RewriteParamsBuilder(VERSION_STR);

    builder.setSketchName(sketchName);
    builder.setisTesting(isTesting);
    builder.setRewriter(rewriter);
    builder.setMode(mode);
    builder.setFoundMain(foundMain);
    builder.setLineOffset(lineOffset);
    builder.setSketchWidth(sketchWidth);
    builder.setSketchHeight(sketchHeight);
    builder.setSketchRenderer(sketchRenderer);
    builder.setIsSizeValidInGlobal(sizeRequiresRewrite);
    builder.setIsSizeFullscreen(sizeIsFullscreen);

    builder.addCoreImports(coreImports);
    builder.addDefaultImports(defaultImports);
    builder.addCodeFolderImports(codeFolderImports);
    builder.addFoundImports(foundImports);

    return builder.build();
  }
}
