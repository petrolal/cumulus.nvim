package cumulus.testing

import upickle.default.ReadWriter

/**
 * Represents the context (class and method) of a test found at cursor position.
 *
 * @param class_name The name of the test class (e.g., "MyTest")
 * @param method_name The name of the test method (e.g., "testFoo")
 */
case class TestContext(
  class_name: String,
  method_name: String
) derives ReadWriter

/**
 * Represents a single test result from parsed test output.
 *
 * @param class_name The name of the test class
 * @param method_name The name of the test method
 * @param status The test status: PASSED, FAILED, or SKIPPED
 * @param message Optional failure/error message or stack trace
 */
case class TestResult(
  class_name: String,
  method_name: String,
  status: String,
  message: Option[String] = None
) derives ReadWriter

/**
 * Represents a test command to be executed.
 *
 * @param command The full CLI command string (e.g., "mvn test -Dtest=MyTest#testFoo")
 * @param cwd The working directory to execute the command in
 */
case class TestCommand(
  command: String,
  cwd: String
) derives ReadWriter
