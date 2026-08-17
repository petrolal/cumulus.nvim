package cumulus.devops

import munit.FunSuite
import os.Path

class CfnSamParserSpec extends FunSuite:

  test("inspect standard CloudFormation YAML template") {
    val yaml =
      """AWSTemplateFormatVersion: "2010-09-09"
        |Description: Sample CloudFormation Template
        |Parameters:
        |  Environment:
        |    Type: String
        |    Default: dev
        |    Description: Deployment target environment
        |Resources:
        |  MyBucket:
        |    Type: AWS::S3::Bucket
        |    Properties:
        |      BucketName: my-sample-bucket
        |Outputs:
        |  BucketNameOutput:
        |    Value: !Ref MyBucket
        |""".stripMargin

    val info = CfnSamParser.inspectTemplate(yaml)
    assertEquals(info.format_version, Some("2010-09-09"))
    assertEquals(info.description, Some("Sample CloudFormation Template"))
    assertEquals(info.is_sam, false)
    assertEquals(info.parameters.length, 1)
    assertEquals(info.parameters.head.name, "Environment")
    assertEquals(info.parameters.head.default_value, Some("dev"))
    assertEquals(info.resources.length, 1)
    assertEquals(info.resources.head.logical_id, "MyBucket")
    assertEquals(info.resources.head.resource_type, "AWS::S3::Bucket")
    assertEquals(info.outputs, Seq("BucketNameOutput"))
  }

  test("inspect SAM template with Serverless Function") {
    val yaml =
      """AWSTemplateFormatVersion: '2010-09-09'
        |Transform: AWS::Serverless-2016-10-31
        |Description: Sample SAM Template
        |Resources:
        |  GetUsersFunction:
        |    Type: AWS::Serverless::Function
        |    Properties:
        |      CodeUri: users/
        |      Handler: app.lambda_handler
        |      Runtime: python3.11
        |  DataTable:
        |    Type: AWS::DynamoDB::Table
        |""".stripMargin

    val info = CfnSamParser.inspectTemplate(yaml)
    assertEquals(info.is_sam, true)
    assertEquals(info.transform, Some("AWS::Serverless-2016-10-31"))
    assertEquals(info.resources.length, 2)
    assertEquals(info.functions.length, 1)
    val fn = info.functions.head
    assertEquals(fn.logical_id, "GetUsersFunction")
    assertEquals(fn.handler, Some("app.lambda_handler"))
    assertEquals(fn.runtime, Some("python3.11"))
    assertEquals(fn.code_uri, Some("users/"))
  }

  test("inspect CloudFormation JSON template") {
    val json =
      """{
        |  "AWSTemplateFormatVersion": "2010-09-09",
        |  "Transform": "AWS::Serverless-2016-10-31",
        |  "Parameters": {
        |    "AppName": {
        |      "Type": "String",
        |      "Default": "cumulus-app"
        |    }
        |  },
        |  "Resources": {
        |    "ApiFunction": {
        |      "Type": "AWS::Serverless::Function",
        |      "Properties": {
        |        "Handler": "index.handler",
        |        "Runtime": "nodejs20.x"
        |      }
        |    }
        |  },
        |  "Outputs": {
        |    "ApiEndpoint": {
        |      "Value": "https://example.com"
        |    }
        |  }
        |}""".stripMargin

    val info = CfnSamParser.inspectTemplate(json)
    assertEquals(info.format_version, Some("2010-09-09"))
    assertEquals(info.is_sam, true)
    assertEquals(info.parameters.length, 1)
    assertEquals(info.parameters.head.name, "AppName")
    assertEquals(info.resources.length, 1)
    assertEquals(info.functions.length, 1)
    assertEquals(info.functions.head.logical_id, "ApiFunction")
    assertEquals(info.functions.head.runtime, Some("nodejs20.x"))
    assertEquals(info.outputs, Seq("ApiEndpoint"))
  }

  test("validate valid template returns no errors") {
    val yaml =
      """AWSTemplateFormatVersion: '2010-09-09'
        |Parameters:
        |  BucketPrefix:
        |    Type: String
        |Resources:
        |  AppBucket:
        |    Type: AWS::S3::Bucket
        |    Properties:
        |      BucketName: !Ref BucketPrefix
        |""".stripMargin

    val issues = CfnSamParser.validateTemplate(yaml)
    assertEquals(issues.filter(_.severity == "ERROR"), Seq.empty)
  }

  test("validate missing Resources section flags error") {
    val yaml =
      """AWSTemplateFormatVersion: '2010-09-09'
        |Description: Template with no resources
        |""".stripMargin

    val issues = CfnSamParser.validateTemplate(yaml)
    assert(issues.exists(_.message.contains("Missing required top-level 'Resources'")))
  }

  test("validate undeclared !Ref and !GetAtt references flags warnings") {
    val yaml =
      """Resources:
        |  MyBucket:
        |    Type: AWS::S3::Bucket
        |    Properties:
        |      BucketName: !Ref UndeclaredParam
        |      RoleArn: !GetAtt MissingResource.Arn
        |""".stripMargin

    val issues = CfnSamParser.validateTemplate(yaml)
    assert(issues.exists(_.message.contains("UndeclaredParam")))
    assert(issues.exists(_.message.contains("MissingResource")))
  }

  test("inspectTemplateFile and validateTemplateFile handle missing and valid files") {
    val tempFile = os.temp(prefix = "cfn-test-", suffix = ".yaml")
    try
      os.write.over(tempFile,
        """Resources:
          |  MyQueue:
          |    Type: AWS::SQS::Queue
          |""".stripMargin
      )

      val inspectRes = CfnSamParser.inspectTemplateFile(tempFile.toString)
      assert(inspectRes.success)
      assertEquals(inspectRes.data.get.resources.length, 1)

      val valRes = CfnSamParser.validateTemplateFile(tempFile.toString)
      assert(valRes.success)
      assertEquals(valRes.data.get.filter(_.severity == "ERROR"), Seq.empty)

      val missingRes = CfnSamParser.inspectTemplateFile("/nonexistent/template.yaml")
      assert(!missingRes.success)
      assertEquals(missingRes.error_code, Some("FILE_NOT_FOUND"))
    finally
      os.remove(tempFile)
  }
