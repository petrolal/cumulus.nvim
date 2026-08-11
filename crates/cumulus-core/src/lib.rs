// Cumulus Core Error Handling (SPEC-031)
//
// Defines standardized error envelope and response types for all Rust subcommands,
// enabling robust Lua error handling and improved debuggability.

use serde::{Deserialize, Serialize};
use std::fmt;

/// Unified error type for all cumulus-core subcommands.
#[derive(Debug, Clone)]
pub enum CumulusError {
    FileNotFound(String),
    ParseError(String),
    InvalidInput(String),
    NetworkError(String),
    Timeout,
    Internal(String),
}

impl fmt::Display for CumulusError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            CumulusError::FileNotFound(msg) => write!(f, "File not found: {}", msg),
            CumulusError::ParseError(msg) => write!(f, "Parse error: {}", msg),
            CumulusError::InvalidInput(msg) => write!(f, "Invalid input: {}", msg),
            CumulusError::NetworkError(msg) => write!(f, "Network error: {}", msg),
            CumulusError::Timeout => write!(f, "Operation timed out"),
            CumulusError::Internal(msg) => write!(f, "Internal error: {}", msg),
        }
    }
}

impl CumulusError {
    /// Return the error code for this error type (used in JSON envelope).
    pub fn error_code(&self) -> &'static str {
        match self {
            CumulusError::FileNotFound(_) => "FILE_NOT_FOUND",
            CumulusError::ParseError(_) => "PARSE_ERROR",
            CumulusError::InvalidInput(_) => "INVALID_INPUT",
            CumulusError::NetworkError(_) => "NETWORK_ERROR",
            CumulusError::Timeout => "TIMEOUT",
            CumulusError::Internal(_) => "INTERNAL_ERROR",
        }
    }
}

/// Standardized JSON response envelope for all cumulus-core subcommands.
#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct CumulusResponse<T> {
    pub success: bool,
    pub data: Option<T>,
    pub error: Option<String>,
    pub error_code: Option<String>,
}

impl<T: Serialize> CumulusResponse<T> {
    /// Create a success response.
    pub fn ok(data: T) -> Self {
        CumulusResponse {
            success: true,
            data: Some(data),
            error: None,
            error_code: None,
        }
    }

    /// Create an error response.
    pub fn err(err: CumulusError) -> CumulusResponse<()> {
        CumulusResponse {
            success: false,
            data: None,
            error: Some(err.to_string()),
            error_code: Some(err.error_code().to_string()),
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_file_not_found_error() {
        let err = CumulusError::FileNotFound("pom.xml".to_string());
        assert_eq!(err.error_code(), "FILE_NOT_FOUND");
        assert_eq!(err.to_string(), "File not found: pom.xml");
    }

    #[test]
    fn test_parse_error() {
        let err = CumulusError::ParseError("Invalid JSON".to_string());
        assert_eq!(err.error_code(), "PARSE_ERROR");
    }

    #[test]
    fn test_success_response_serialization() {
        let resp = CumulusResponse::ok(vec!["goal1", "goal2"]);
        let json = serde_json::to_string(&resp).unwrap();
        assert!(json.contains("\"success\":true"));
        assert!(json.contains("\"goal1\""));
    }

    #[test]
    fn test_error_response_serialization() {
        let resp = CumulusResponse::<()>::err(CumulusError::FileNotFound("test.txt".to_string()));
        let json = serde_json::to_string(&resp).unwrap();
        assert!(json.contains("\"success\":false"));
        assert!(json.contains("\"FILE_NOT_FOUND\""));
        assert!(json.contains("File not found: test.txt"));
    }

    #[test]
    fn test_timeout_error() {
        let err = CumulusError::Timeout;
        assert_eq!(err.error_code(), "TIMEOUT");
    }
}
