package com.carrotsearch.randomizedtesting;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Just an eyeballing test at the output ;)
 */
@RunWith(RandomizedRunner.class)
public class TestEyeBalling {
  @BeforeClass
  public static void setup() {
    info("before class");
  }

  @Before
  public void testSetup() {
    info("before test");
  }

  @Repeat(iterations = 4)
  @Test
  public void test1() {
    info("test1");
  }

  @Test
  public void test2() {
    info("test2");
  }

  @After
  public void testCleanup() {
    info("after test");
  }

  @AfterClass
  public static void cleanup() {
    info("after class");
  }

  private static void info(String msg) {
    System.out.println(msg + " " + 
        RandomizedContext.current().getRandomness());
  }
}
