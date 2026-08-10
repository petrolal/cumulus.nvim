use std::net::{TcpStream, ToSocketAddrs};
use std::time::Duration;

pub fn check_connectivity(host_port: &str, timeout_ms: u64) -> bool {
    let addrs = match host_port.to_socket_addrs() {
        Ok(addrs) => addrs,
        Err(_) => return false,
    };

    let timeout = Duration::from_millis(timeout_ms);

    for addr in addrs {
        if TcpStream::connect_timeout(&addr, timeout).is_ok() {
            return true;
        }
    }

    false
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_check_connectivity_invalid() {
        let ok = check_connectivity("192.0.2.1:80", 100);
        assert!(!ok);
    }
}
