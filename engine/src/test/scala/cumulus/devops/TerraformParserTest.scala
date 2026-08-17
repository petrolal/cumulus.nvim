package cumulus.devops

import munit.FunSuite
import cumulus.protocol.CumulusError

class TerraformParserTest extends FunSuite:

  test("TerraformParser should parse resources, variables, outputs, and providers") {
    val hcl =
      """
        |terraform {
        |  required_version = ">= 1.0"
        |}
        |
        |provider "aws" {
        |  region = "us-east-1"
        |  alias  = "primary"
        |}
        |
        |variable "environment" {
        |  type        = string
        |  default     = "production"
        |  description = "Target deployment environment"
        |}
        |
        |resource "aws_vpc" "main" {
        |  cidr_block = "10.0.0.0/16"
        |  tags = {
        |    Name = "main-vpc"
        |  }
        |}
        |
        |resource "aws_subnet" "subnet_a" {
        |  vpc_id            = aws_vpc.main.id
        |  cidr_block        = "10.0.1.0/24"
        |  availability_zone = "us-east-1a"
        |}
        |
        |data "aws_ami" "ubuntu" {
        |  most_recent = true
        |  owners      = ["099720109477"]
        |}
        |
        |output "vpc_id" {
        |  value       = aws_vpc.main.id
        |  description = "The VPC ID"
        |  sensitive   = false
        |}
      """.stripMargin

    val info = TerraformParser.inspectContent(hcl, "main.tf")

    assertEquals(info.providers.length, 1)
    assertEquals(info.providers.head.name, "aws")
    assertEquals(info.providers.head.alias, Some("primary"))

    assertEquals(info.variables.length, 1)
    val v = info.variables.head
    assertEquals(v.name, "environment")
    assertEquals(v.var_type, Some("string"))
    assertEquals(v.default_value, Some("production"))
    assertEquals(v.description, Some("Target deployment environment"))

    assertEquals(info.resources.length, 3)
    val vpc = info.resources.find(r => r.resource_type == "aws_vpc" && r.name == "main").get
    assert(!vpc.is_data)
    assertEquals(vpc.attributes.get("cidr_block"), Some("10.0.0.0/16"))

    val subnet = info.resources.find(r => r.resource_type == "aws_subnet" && r.name == "subnet_a").get
    assert(!subnet.is_data)
    assert(subnet.dependencies.contains("aws_vpc.main"))

    val ami = info.resources.find(r => r.name == "ubuntu").get
    assert(ami.is_data)

    assertEquals(info.outputs.length, 1)
    val out = info.outputs.head
    assertEquals(out.name, "vpc_id")
    assertEquals(out.value, Some("aws_vpc.main.id"))

    val edges = TerraformParser.buildDependencyGraph(info.resources)
    assertEquals(edges.length, 1)
    assertEquals(edges.head, TfDependencyEdge(from = "aws_subnet.subnet_a", to = "aws_vpc.main"))
  }

  test("TerraformParser should handle empty content gracefully") {
    val info = TerraformParser.inspectContent("")
    assertEquals(info.resources.length, 0)
    assertEquals(info.variables.length, 0)
    assertEquals(info.outputs.length, 0)
    assertEquals(info.providers.length, 0)
    assertEquals(info.dependency_graph.length, 0)
  }

  test("TerraformParser should detect cyclic or multiple dependencies without infinite loops") {
    val hcl =
      """
        |resource "aws_security_group" "sg_a" {
        |  name = "sg_a"
        |  ingress_sg = aws_security_group.sg_b.id
        |}
        |
        |resource "aws_security_group" "sg_b" {
        |  name = "sg_b"
        |  egress_sg = aws_security_group.sg_a.id
        |}
      """.stripMargin

    val info = TerraformParser.inspectContent(hcl)
    assertEquals(info.resources.length, 2)
    val edges = TerraformParser.buildDependencyGraph(info.resources)
    assertEquals(edges.length, 2)
    assert(edges.contains(TfDependencyEdge(from = "aws_security_group.sg_a", to = "aws_security_group.sg_b")))
    assert(edges.contains(TfDependencyEdge(from = "aws_security_group.sg_b", to = "aws_security_group.sg_a")))
  }

  test("TerraformParser should return FILE_NOT_FOUND error for non-existent paths") {
    val res = TerraformParser.inspectPath("/non/existent/path/main.tf")
    assert(!res.success)
    assertEquals(res.error_code, Some(CumulusError.FILE_NOT_FOUND.toString))
  }

  test("TerraformParser should aggregate directory files and ignore .terraform directory") {
    val tempDir = os.temp.dir()
    try
      os.write(tempDir / "main.tf", "resource \"aws_s3_bucket\" \"b1\" { bucket = \"my-b1\" }")
      os.write(tempDir / "vars.tfvars", "region = \"us-west-2\"")
      os.write(tempDir / "app.tofu", "resource \"aws_s3_bucket\" \"b2\" { bucket = aws_s3_bucket.b1.id }")
      
      val tfCache = tempDir / ".terraform" / "modules"
      os.makeDir.all(tfCache)
      os.write(tfCache / "cached.tf", "resource \"aws_s3_bucket\" \"ignored\" {}")

      val res = TerraformParser.inspectPath(tempDir.toString)
      assert(res.success)
      val info = res.data.get
      assertEquals(info.resources.length, 2)
      assert(info.resources.exists(_.name == "b1"))
      assert(info.resources.exists(_.name == "b2"))
      assert(!info.resources.exists(_.name == "ignored"))
      assertEquals(info.dependency_graph.length, 1)
    finally
      os.remove.all(tempDir)
  }
