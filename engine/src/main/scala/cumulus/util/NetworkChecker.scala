package cumulus.util

import cumulus.protocol.{CumulusResponse, CumulusError}
import cumulus.devops.NetworkStatus
import java.net.{InetSocketAddress, Socket}

/**
 * Checks TCP socket connectivity to a specified host:port.
 */
object NetworkChecker:

  def checkNetwork(hostPort: String, timeoutMs: Long = 3000): CumulusResponse[NetworkStatus] =
    val trimmed = hostPort.trim
    val (host, portStr) = if trimmed.startsWith("[") && trimmed.contains("]:") then
      val closeBracket = trimmed.indexOf("]:")
      (trimmed.substring(1, closeBracket), trimmed.substring(closeBracket + 2))
    else
      val lastColon = trimmed.lastIndexOf(':')
      if lastColon <= 0 || lastColon == trimmed.length - 1 then
        ("", "")
      else
        (trimmed.substring(0, lastColon), trimmed.substring(lastColon + 1))

    if host.isEmpty || portStr.isEmpty then
      return CumulusResponse(
        success = false,
        data = None,
        error = Some(s"Invalid host:port format: '$hostPort'. Expected format: <host>:<port> or [<ipv6>]:<port>"),
        error_code = Some(CumulusError.INVALID_INPUT.toString)
      )

    val port = try {
      val p = portStr.toInt
      if p < 1 || p > 65535 then -1 else p
    } catch {
      case _: NumberFormatException => -1
    }

    if port == -1 then
      return CumulusResponse(
        success = false,
        data = None,
        error = Some(s"Invalid port number in '$hostPort'. Port must be between 1 and 65535"),
        error_code = Some(CumulusError.INVALID_INPUT.toString)
      )

    val startTime = System.currentTimeMillis()
    var connected = false
    var socket: Socket = null

    try
      socket = new Socket()
      val timeout = if timeoutMs > 0 then timeoutMs.toInt else 3000
      socket.connect(new InetSocketAddress(host, port), timeout)
      connected = socket.isConnected
    catch
      case _: Exception =>
        connected = false
    finally
      if socket != null then
        try socket.close() catch { case _: Exception => () }

    val elapsed = System.currentTimeMillis() - startTime

    CumulusResponse(
      success = true,
      data = Some(NetworkStatus(
        connected = connected,
        host = host,
        port = port,
        elapsed_ms = elapsed
      )),
      error = None,
      error_code = None
    )
