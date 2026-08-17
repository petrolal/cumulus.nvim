package cumulus.devops

import munit.FunSuite
import cumulus.protocol.CumulusError

class TfSecurityParserTest extends FunSuite:

  test("TfSecurityParser should parse Trivy JSON format correctly") {
    val trivyJson =
      """
        |{
        |  "SchemaVersion": 2,
        |  "Results": [
        |    {
        |      "Target": "main.tf",
        |      "Class": "config",
        |      "Type": "terraform",
        |      "Misconfigurations": [
        |        {
        |          "ID": "AVD-AWS-0001",
        |          "Title": "S3 Bucket Encryption Disabled",
        |          "Description": "Ensure S3 bucket has server-side encryption enabled",
        |          "Message": "Bucket does not have server-side encryption configured",
        |          "Severity": "HIGH",
        |          "Resolution": "Enable KMS or AES256 server-side encryption",
        |          "Status": "FAIL",
        |          "CauseMetadata": {
        |            "StartLine": 12,
        |            "EndLine": 18
        |          }
        |        }
        |      ]
        |    }
        |  ]
        |}
      """.stripMargin

    val res = TfSecurityParser.parseSecurityJson(trivyJson)
    assert(res.isRight)
    val findings = res.toOption.get
    assertEquals(findings.length, 1)
    val f = findings.head
    assertEquals(f.rule_id, "AVD-AWS-0001")
    assertEquals(f.title, "S3 Bucket Encryption Disabled")
    assertEquals(f.severity, "HIGH")
    assertEquals(f.file, "main.tf")
    assertEquals(f.line, 12)
    assertEquals(f.end_line, Some(18))
    assertEquals(f.resolution, Some("Enable KMS or AES256 server-side encryption"))
  }

  test("TfSecurityParser should parse tfsec JSON format correctly") {
    val tfsecJson =
      """
        |{
        |  "results": [
        |    {
        |      "rule_id": "AWS002",
        |      "long_id": "aws-s3-enable-bucket-logging",
        |      "rule_description": "S3 Access Logging Not Enabled",
        |      "description": "Bucket logging is not configured for audit trails",
        |      "severity": "MEDIUM",
        |      "resolution": "Configure logging block in resource",
        |      "location": {
        |        "filename": "s3.tf",
        |        "start_line": 25,
        |        "end_line": 30
        |      }
        |    }
        |  ]
        |}
      """.stripMargin

    val res = TfSecurityParser.parseSecurityJson(tfsecJson)
    assert(res.isRight)
    val findings = res.toOption.get
    assertEquals(findings.length, 1)
    val f = findings.head
    assertEquals(f.rule_id, "AWS002")
    assertEquals(f.title, "S3 Access Logging Not Enabled")
    assertEquals(f.severity, "MEDIUM")
    assertEquals(f.file, "s3.tf")
    assertEquals(f.line, 25)
    assertEquals(f.end_line, Some(30))
    assertEquals(f.resolution, Some("Configure logging block in resource"))
  }

  test("TfSecurityParser should handle malformed JSON safely") {
    val res = TfSecurityParser.parseSecurityJson("{ invalid: json ")
    assert(res.isLeft)
  }

  test("TfSecurityParser should return FILE_NOT_FOUND for non-existent report file") {
    val res = TfSecurityParser.parseSecurityFile("/missing/security-report.json")
    assert(!res.success)
    assertEquals(res.error_code, Some(CumulusError.FILE_NOT_FOUND.toString))
  }
