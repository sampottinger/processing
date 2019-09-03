package processing.mode.java.preproc.issue.strategy;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import processing.mode.java.preproc.issue.IssueMessageSimplification;

import java.util.Optional;


public class MissingParenMessageSimplifierStrategyTest {

  private MissingParenMessageSimplifierStrategy strategy;

  @Before
  public void setup() {
    strategy = new MissingParenMessageSimplifierStrategy();
  }

  @Test
  public void testPresent() {
    Optional<IssueMessageSimplification> msg = strategy.simplify("int x = ((5 + 4) / 3");
    Assert.assertTrue(msg.isPresent());
  }

  @Test
  public void testNotPresent() {
    Optional<IssueMessageSimplification> msg = strategy.simplify("int x = (y/5)/(\n4)");
    Assert.assertTrue(msg.isEmpty());
  }

}