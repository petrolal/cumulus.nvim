package cumulus.util

import munit.FunSuite

class NetworkCheckerTest extends FunSuite:

  test("rejects invalid host:port format with INVALID_INPUT error") {
    val res = NetworkChecker.checkNetwork("invalid_host_no_port")
    assert(!res.success)
    assertEquals(res.error_code, Some("INVALID_INPUT"))
  }

  test("rejects non-numeric port with INVALID_INPUT error") {
    val res = NetworkChecker.checkNetwork("localhost:abc")
    assert(!res.success)
    assertEquals(res.error_code, Some("INVALID_INPUT"))
  }

  test("handles connection failure gracefully with connected=false") {
    // 192.0.2.1 is TEST-NET-1 (RFC 5737), guaranteed to timeout or reject
    val res = NetworkChecker.checkNetwork("192.0.2.1:80", timeoutMs = 100)
    assert(res.success)
    val status = res.data.get
    assertEquals(status.connected, false)
    assertEquals(status.host, "192.0.2.1")
    assertEquals(status.port, 80)
    assert(status.elapsed_ms >= 0)
  }
