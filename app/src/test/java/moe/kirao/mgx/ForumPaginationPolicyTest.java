package moe.kirao.mgx;

import moe.kirao.mgx.utils.ForumPaginationPolicy;
import org.junit.Test;

import static org.junit.Assert.assertFalse;

public class ForumPaginationPolicyTest {
  @Test public void nullResultCannotRequestAnotherPage() {
    assertFalse(ForumPaginationPolicy.hasNextPage(null, 0, 0L, 0));
  }
}
