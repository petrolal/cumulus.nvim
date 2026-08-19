package cumulus.devops

import munit.FunSuite

class AnsibleParserSpec extends FunSuite:

  test("AnsibleParser.inspectPlaybook extracts plays, hosts, tasks, and roles from YAML") {
    val yaml =
      """---
        |- name: Provision web cluster
        |  hosts: webservers
        |  gather_facts: true
        |  roles:
        |    - common
        |    - role: nginx
        |  tasks:
        |    - name: Ensure nginx is installed
        |      apt:
        |        name: nginx
        |        state: present
        |    - name: Start nginx service
        |      service:
        |        name: nginx
        |        state: started
        |
        |- name: Configure database
        |  hosts: dbservers
        |  gather_facts: false
        |  tasks:
        |    - name: Run DB migration script
        |      ansible.builtin.command: /usr/local/bin/migrate.sh
        |""".stripMargin

    val info = AnsibleParser.inspectPlaybook(yaml)

    assertEquals(info.plays.length, 2)
    assertEquals(info.hosts, Seq("webservers", "dbservers"))
    assertEquals(info.roles, Seq("common", "nginx"))
    assertEquals(info.total_tasks, 3)

    val play1 = info.plays.head
    assertEquals(play1.name, "Provision web cluster")
    assertEquals(play1.hosts, "webservers")
    assertEquals(play1.gather_facts, Some(true))
    assertEquals(play1.roles, Seq("common", "nginx"))
    assertEquals(play1.tasks.length, 2)
    assertEquals(play1.tasks.head.name, "Ensure nginx is installed")
    assertEquals(play1.tasks.head.module, "apt")
    assertEquals(play1.tasks(1).name, "Start nginx service")
    assertEquals(play1.tasks(1).module, "service")

    val play2 = info.plays(1)
    assertEquals(play2.name, "Configure database")
    assertEquals(play2.hosts, "dbservers")
    assertEquals(play2.gather_facts, Some(false))
    assertEquals(play2.tasks.length, 1)
    assertEquals(play2.tasks.head.name, "Run DB migration script")
    assertEquals(play2.tasks.head.module, "ansible.builtin.command")
  }

  test("AnsibleParser.validatePlaybook detects unquoted Jinja templates and missing plays") {
    val invalidJinja =
      """---
        |- name: Bad Jinja quoting
        |  hosts: all
        |  tasks:
        |    - name: Debug something
        |      debug:
        |        msg: {{ my_unquoted_var }}
        |""".stripMargin

    val issues = AnsibleParser.validatePlaybook(invalidJinja)
    assert(issues.nonEmpty)
    assert(issues.exists(i => i.severity == "ERROR" && i.message.contains("Unquoted template expression")))

    val emptyIssues = AnsibleParser.validatePlaybook("")
    assert(emptyIssues.exists(_.message.contains("empty")))
  }

  test("AnsibleParser.parseInventory parses graph tree into hierarchical groups") {
    val graphOutput =
      """@all:
        |  |--@ungrouped:
        |  |--@webservers:
        |  |  |--web1.example.com
        |  |  |--web2.example.com
        |  |--@dbservers:
        |  |  |--db-primary.example.com
        |  |  |--db-replica.example.com
        |""".stripMargin

    val groups = AnsibleParser.parseInventory(graphOutput)
    assert(groups.nonEmpty)

    val webGroup = groups.find(_.name == "webservers")
    assert(webGroup.isDefined)
    assertEquals(webGroup.get.hosts, Seq("web1.example.com", "web2.example.com"))

    val dbGroup = groups.find(_.name == "dbservers")
    assert(dbGroup.isDefined)
    assertEquals(dbGroup.get.hosts, Seq("db-primary.example.com", "db-replica.example.com"))
  }

  test("AnsibleParser.parseInventory parses JSON inventory format") {
    val jsonInventory =
      """{
        |  "_meta": {
        |    "hostvars": {}
        |  },
        |  "all": {
        |    "children": ["ungrouped", "webservers"]
        |  },
        |  "webservers": {
        |    "hosts": ["web1.local", "web2.local"],
        |    "vars": {
        |      "http_port": "80"
        |    }
        |  }
        |}""".stripMargin

    val groups = AnsibleParser.parseInventory(jsonInventory)
    assert(groups.nonEmpty)
    val webGroup = groups.find(_.name == "webservers")
    assert(webGroup.isDefined)
    assertEquals(webGroup.get.hosts, Seq("web1.local", "web2.local"))
    assertEquals(webGroup.get.vars.get("http_port"), Some("80"))
  }

  test("AnsibleParser file-based operations and error handling") {
    val tempFile = java.nio.file.Files.createTempFile("playbook", ".yml").toFile
    try
      val yaml =
        """---
          |- name: Test Play
          |  hosts: all
          |  tasks:
          |    - name: Ping host
          |      ansible.builtin.ping:
          |""".stripMargin
      java.nio.file.Files.writeString(tempFile.toPath, yaml)

      val inspectRes = AnsibleParser.inspectPlaybookFile(tempFile.getAbsolutePath)
      assert(inspectRes.success)
      assert(inspectRes.data.isDefined)
      assertEquals(inspectRes.data.get.plays.length, 1)
      assertEquals(inspectRes.data.get.plays.head.tasks.head.module, "ansible.builtin.ping")

      val validRes = AnsibleParser.validatePlaybookFile(tempFile.getAbsolutePath)
      assert(validRes.success)
      assert(validRes.data.isDefined)
      assertEquals(validRes.data.get.length, 0)
    finally
      tempFile.delete()

    val missingRes = AnsibleParser.inspectPlaybookFile("/nonexistent/playbook.yml")
    assert(!missingRes.success)
    assertEquals(missingRes.error_code, Some("FILE_NOT_FOUND"))

    val missingValRes = AnsibleParser.validatePlaybookFile("/nonexistent/playbook.yml")
    assert(!missingValRes.success)
    assertEquals(missingValRes.error_code, Some("FILE_NOT_FOUND"))

    val missingInvRes = AnsibleParser.parseInventoryFile("/nonexistent/inventory.json")
    assert(!missingInvRes.success)
    assertEquals(missingInvRes.error_code, Some("FILE_NOT_FOUND"))
  }

