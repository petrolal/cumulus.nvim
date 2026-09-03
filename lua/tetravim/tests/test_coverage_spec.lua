local coverage = require("tetravim.util.coverage")
local engine = require("tetravim.util.engine")

describe("Test Coverage Module (SPEC-1.3)", function()
  local sample_xml = [[<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<!DOCTYPE report PUBLIC "-//JACOCO//DTD Report 1.1//EN" "report.dtd">
<report name="demo-project">
  <package name="com/example/demo">
    <class name="com/example/demo/DemoService" sourcefilename="DemoService.java">
      <method name="calculate" desc="()V" line="10">
        <counter type="INSTRUCTION" missed="0" covered="5"/>
        <counter type="LINE" missed="0" covered="1"/>
      </method>
    </class>
    <sourcefile name="DemoService.java">
      <line nr="10" mi="0" ci="5" mb="0" cb="0"/>
      <line nr="12" mi="4" ci="0" mb="0" cb="0"/>
      <line nr="15" mi="0" ci="3" mb="1" cb="1"/>
      <counter type="INSTRUCTION" missed="4" covered="8"/>
      <counter type="BRANCH" missed="1" covered="1"/>
      <counter type="LINE" missed="1" covered="2"/>
    </sourcefile>
  </package>
</report>]]

  it("should expose all required public APIs", function()
    assert.is_function(coverage.load)
    assert.is_function(coverage.clear)
    assert.is_function(coverage.toggle)
    assert.is_function(coverage.summary)
    assert.is_function(coverage.parse)
    assert.is_function(coverage.parse_xml)
    assert.is_function(coverage.find_report_file)
    assert.is_function(coverage.apply_to_buffer)
    assert.is_function(coverage.apply_to_all_buffers)
  end)

  it("should parse JaCoCo XML into covered, uncovered, and partial lines", function()
    local res, err = coverage.parse_xml(sample_xml)
    assert.is_nil(err)
    assert.is_table(res)
    assert.is_table(res.summary)
    assert.equals(3, res.summary.lines_total)
    assert.equals(1, res.summary.lines_covered)
    assert.equals(1, res.summary.lines_missed)
    assert.equals(1, res.summary.lines_partial)
    assert.equals(33.33, res.summary.coverage_pct)

    assert.is_table(res.files["com/example/demo/DemoService.java"])
    local file_rec = res.files["com/example/demo/DemoService.java"]
    assert.equals("covered", file_rec.lines[10])
    assert.equals("uncovered", file_rec.lines[12])
    assert.equals("partial", file_rec.lines[15])

    assert.equals(1, #file_rec.covered_lines)
    assert.equals(10, file_rec.covered_lines[1])
    assert.equals(1, #file_rec.missed_lines)
    assert.equals(12, file_rec.missed_lines[1])
    assert.equals(1, #file_rec.partial_lines)
    assert.equals(15, file_rec.partial_lines[1])

    -- Entries array format for backwards compatibility
    assert.equals(1, #res.entries)
    assert.equals("com/example/demo/DemoService.java", res.entries[1].file)
  end)

  it("should handle multi-package and multi-sourcefile reports", function()
    local multi_xml = [[<?xml version="1.0" encoding="UTF-8"?>
<report name="multi-module">
  <package name="org.example.pkg1">
    <sourcefile name="ServiceA.java">
      <line nr="5" mi="0" ci="2" mb="0" cb="0"/>
    </sourcefile>
  </package>
  <package name="org/example/pkg2">
    <sourcefile name="ServiceB.kt">
      <line nr="20" mi="3" ci="0" mb="0" cb="0"/>
      <line nr="21" mi="0" ci="2" mb="2" cb="2"/>
    </sourcefile>
  </package>
</report>]]
    local res, err = coverage.parse_xml(multi_xml)
    assert.is_nil(err)
    assert.equals(3, res.summary.lines_total)
    assert.equals(1, res.summary.lines_covered)
    assert.equals(1, res.summary.lines_missed)
    assert.equals(1, res.summary.lines_partial)
    assert.is_table(res.files["org/example/pkg1/ServiceA.java"])
    assert.is_table(res.files["org/example/pkg2/ServiceB.kt"])
    assert.equals(2, #res.entries)
  end)

  it("should handle error edge cases gracefully without throwing", function()
    -- Non-existent file
    local res1, err1 = coverage.parse("/tmp/non_existent_jacoco_file_xyz.xml")
    assert.is_nil(res1)
    assert.is_string(err1)

    -- Empty XML content
    local res2, err2 = coverage.parse_xml("")
    assert.is_nil(res2)
    assert.is_string(err2)

    -- Malformed non-XML string
    local res3, err3 = coverage.parse_xml("this is not xml at all")
    assert.is_nil(res3)
    assert.is_string(err3)
  end)

  it("should place signs and diagnostics on a matching buffer and support clear/toggle", function()
    -- Create a temporary XML file
    local tmp_xml = vim.fn.tempname() .. ".xml"
    local f = io.open(tmp_xml, "w")
    f:write(sample_xml)
    f:close()

    -- Create a buffer matching DemoService.java
    local bufnr = vim.api.nvim_create_buf(false, true)
    local test_buf_name = "/workspace/src/main/java/com/example/demo/DemoService.java"
    vim.api.nvim_buf_set_name(bufnr, test_buf_name)
    local lines = {}
    for i = 1, 20 do
      table.insert(lines, "line " .. i)
    end
    vim.api.nvim_buf_set_lines(bufnr, 0, -1, false, lines)

    -- Load coverage
    local ok, res = coverage.load(tmp_xml)
    assert.is_true(ok)
    assert.is_true(coverage.is_visible)

    -- Check signs placed
    local placed = vim.fn.sign_getplaced(bufnr, { group = "tetravim_coverage_signs" })
    assert.is_table(placed)
    assert.is_table(placed[1].signs)
    local sign_lines = {}
    for _, s in ipairs(placed[1].signs) do
      sign_lines[s.lnum] = s.name
    end
    assert.equals("TetraVimCoverageCovered", sign_lines[10])
    assert.equals("TetraVimCoverageUncovered", sign_lines[12])
    assert.equals("TetraVimCoveragePartial", sign_lines[15])

    -- Check diagnostics (missed & partial)
    local diags = vim.diagnostic.get(bufnr, { namespace = vim.api.nvim_create_namespace("tetravim_coverage") })
    assert.equals(2, #diags)

    -- Summary API
    local s = coverage.summary()
    assert.is_table(s)
    assert.equals(3, s.lines_total)

    -- Toggle coverage off
    coverage.toggle()
    assert.is_false(coverage.is_visible)
    local placed_after_toggle = vim.fn.sign_getplaced(bufnr, { group = "tetravim_coverage_signs" })
    assert.equals(0, #placed_after_toggle[1].signs)

    -- Toggle coverage back on
    coverage.toggle()
    assert.is_true(coverage.is_visible)
    local placed_restored = vim.fn.sign_getplaced(bufnr, { group = "tetravim_coverage_signs" })
    assert.equals(3, #placed_restored[1].signs)

    -- Clear coverage completely
    coverage.clear(true)
    assert.is_false(coverage.is_visible)
    assert.is_nil(coverage.last_coverage)
    local placed_after_clear = vim.fn.sign_getplaced(bufnr, { group = "tetravim_coverage_signs" })
    assert.equals(0, #placed_after_clear[1].signs)

    -- Cleanup
    vim.api.nvim_buf_delete(bufnr, { force = true })
    os.remove(tmp_xml)
  end)

  it("should delegate engine.parse_coverage and engine.view_coverage to coverage.lua", function()
    local tmp_xml = vim.fn.tempname() .. ".xml"
    local f = io.open(tmp_xml, "w")
    f:write(sample_xml)
    f:close()

    local entries = engine.parse_coverage(tmp_xml)
    assert.is_table(entries)
    assert.equals(1, #entries)
    assert.equals("com/example/demo/DemoService.java", entries[1].file)
    assert.equals(1, #entries[1].covered_lines)
    assert.equals(1, #entries[1].missed_lines)

    local ok = engine.view_coverage(tmp_xml)
    assert.is_true(ok)
    assert.is_true(coverage.is_visible)

    coverage.clear(true)
    os.remove(tmp_xml)
  end)
end)
