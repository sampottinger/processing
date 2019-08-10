package processing.mode.java.preproc.util;

import org.junit.Assert;
import org.junit.Test;
import processing.mode.java.preproc.issue.PreprocessIssueMessageSimplifierFacade;


public class PreprocessIssueMessageSimplifierFacadeTest {

  @Test
  public void testAssignment() {
    String input = "List<ColoredCircle> =";
    String output = PreprocessIssueMessageSimplifierFacade.get().simplify(input).getMessage();
    Assert.assertTrue(output.contains("assignment"));
  }

  @Test
  public void testBadIdentifier() {
    String input = "List<ColoredCircle> 9";
    String output = PreprocessIssueMessageSimplifierFacade.get().simplify(input).getMessage();
    Assert.assertTrue(output.contains("digit"));
  }

  @Test
  public void testBadParamLead() {
    String input = "x,";
    String output = PreprocessIssueMessageSimplifierFacade.get().simplify(input).getMessage();
    Assert.assertTrue(output.contains("parameter"));
  }

  @Test
  public void testBadParamEnd() {
    String input = "colorGen),";
    String output = PreprocessIssueMessageSimplifierFacade.get().simplify(input).getMessage();
    Assert.assertTrue(output.contains("parameter"));
  }

  @Test
  public void testCaret() {
    String input = "List<ColoredCircle circles";
    String output = PreprocessIssueMessageSimplifierFacade.get().simplify(input).getMessage();
    Assert.assertTrue(output.contains(">"));
  }

  @Test
  public void testMissingIdentifier() {
    String input = "missing Identifier at '{'";
    String output = PreprocessIssueMessageSimplifierFacade.get().simplify(input).getMessage();
    Assert.assertTrue(output.contains("{"));
  }

  @Test
  public void simplifyParen() {
    String input = "no viable alternative at input 'ellipse(\n\nellipse();'";
    String output = PreprocessIssueMessageSimplifierFacade.get().simplify(input).getMessage();
    Assert.assertNotNull(output);
  }

  @Test
  public void simplifySemicolon() {
    String input = "no viable alternative at input 'ellipse(\n\nellipse())'";
    String output = PreprocessIssueMessageSimplifierFacade.get().simplify(input).getMessage();
    Assert.assertNotNull(output);
  }

}